package me.avogadro.mockjdbc.statement.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.avogadro.mockjdbc.MockJdbcDriver;
import me.avogadro.mockjdbc.resultset.MockResultSet;
import me.avogadro.mockjdbc.resultset.MockResultSetMetaData;
import me.avogadro.mockjdbc.resultset.impl.CSVResultSet;
import me.avogadro.mockjdbc.statement.StatementAdapter;

import au.com.bytecode.opencsv.CSVReader;

/**
 * This class does the actual work of the Generic... classes. It tries to open a CSV file for the table name in the
 * query and parses the contained data.
 *
 * @author Kai Winter
 */
public final class CsvStatement extends StatementAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(CsvStatement.class);

	/**
	 * Pattern used to recognize explicitly declared table names inside an heading comment.
	 * The name stops at the end of the first line (\n or \r\n), whatever follows on the next lines.
	 */
	private static final Pattern COMMENT_HEADLINE_PATTERN = Pattern.compile("\\s*--\\s*(QUERY_ID|TESTCASE):[ \\t]*([^\\r\\n]*)\\r?\\n.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	/** A valid QUERY_ID: no spaces and none of the separators of the lookup names (<code>?</code>, <code>#</code>, <code>,</code>). */
	private static final Pattern QUERY_ID_PATTERN = Pattern.compile("[^\\s?#,]+");
	
	/** Pattern to get the name of a stored procedure from an SQL statement. */
	private static final Pattern STORED_PROCEDURE_PATTERN = Pattern.compile(".*(EXEC|EXECUTE) (\\S*)\\s?.*",	Pattern.CASE_INSENSITIVE);

	/** Stored procedure call: EXEC/EXECUTE/CALL name ... or the JDBC escape {[? =] call name(...)}, after optional comment lines */
	private static final Pattern STORED_PROCEDURE_CALL_PATTERN = Pattern.compile("(?:\\s*--[^\\n]*\\n)*\\s*\\{?\\s*(?:\\?\\s*=\\s*)?(?:exec|execute|call)\\s+([^\\s(){};,]+).*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

	private static final Pattern PURE_SELECT_PATTERN = Pattern.compile("select .*", Pattern.CASE_INSENSITIVE);

    private final Map<String, File> tableResources;

    /**
     * used to describe the "current" params.
     * Always null when invoked from {@link CsvStatement} and may hold values when used from {@link CsvPreparedStatement}
     */
    String paramsString = null;

    /** resource name and occurrence number used by the last query (see {@link MockJdbcDriver#nextOccurrence}) */
    String lastOccurrenceName = null;
    int lastOccurrence = 0;

    /** see {@link #setMaxRows(int)}: 0 = no limit */
    private int maxRows = 0;

    /** SQL added with {@link #addBatch(String)} */
    private final List<String> batch = new ArrayList<String>();
    
    
    /**
     * Initializer
     * 
     */
    {
    	MockJdbcDriver.nextStep();
    }
    
	/**
	 * Constructs a new {@link CsvStatement}.
	 *
	 * @param tableResources
	 *            {@link Map} of table name to CSV file.
	 */
	public CsvStatement(Map<String, File> tableResources) {
		this.tableResources = tableResources;
	}
	
	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		MockJdbcDriver.beforeExecution();
		lastOccurrenceName = null;
		lastOccurrence = 0;
		ResultSet resultSet = query(sql);
		if (maxRows > 0 && resultSet instanceof CSVResultSet) {
			((CSVResultSet) resultSet).setMaxRows(maxRows);
		}
		return resultSet;
	}

	@Override
	public void setMaxRows(int max) throws SQLException {
		if (max < 0) {
			throw new SQLException("maxRows must be >= 0: " + max);
		}
		this.maxRows = max;
	}

	@Override
	public int getMaxRows() throws SQLException {
		return maxRows;
	}

	/**
	 * No key is ever generated: always an empty result set (never null, callers iterate it after an INSERT).
	 */
	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		return CSVResultSet.empty("generatedKeys", "GENERATED_KEY");
	}

	@Override
	public void addBatch(String sql) throws SQLException {
		batch.add(sql);
	}

	@Override
	public void clearBatch() throws SQLException {
		batch.clear();
	}

	@Override
	public int[] executeBatch() throws SQLException {
		int[] results = new int[batch.size()];
		try {
			for (int i = 0; i < results.length; i++) {
				results[i] = executeUpdate(batch.get(i));
			}
		} finally {
			batch.clear();
		}
		return results;
	}

	private ResultSet query(String sql) throws SQLException {

		try {
			String stepRes = MockJdbcDriver.getInMemoryTableResourceForCurrentStep();
			
			// match based on the current step (sequence number)
			if (stepRes!=null) {
				// steps are already a sequence: no occurrence number
				return createResultSet(tableResources, MockJdbcDriver.getStepName(), paramsString, 0);
			}
						
			// Try to check for a special heading comment within SQL
			String queryId = matchQueryId(sql);
			if (queryId != null) {
				return createResultSet(queryId);
			}
			
			// Try to interpret SQL as a SELECT on a table
			String tableName = SqlNames.selectTable(sql);
			if (tableName != null) {
				return createTableResultSet(tableName);
			}

			// Try to interpret SQL as call of a stored procedure
			Matcher storedProcedureMatcher = STORED_PROCEDURE_PATTERN.matcher(sql);
			if (storedProcedureMatcher.matches()) {
				String storedProcedureName = storedProcedureMatcher.group(2);
				return createResultSet(storedProcedureName);
			}

			// Try to interpret SQL as a stored procedure call in the other forms (CALL, {call ...}, several lines)
			String procedure = matchStoredProcedureCall(sql);
			if (procedure != null) {
				return createResultSet(procedure);
			}

	        //  Try  to  interpret  SQL  as  a  pure  select
			Matcher  pureSelectMatcher  =  PURE_SELECT_PATTERN.matcher(sql);
			if  (pureSelectMatcher.matches())  {
				return  createPureResultSet();
			}

	        return new MockResultSet();
	        
		} finally {
			// MockJdbcDriver.nextStep();
		}
	}
	
	/**
	 * @param sql the SQL text
	 * @return the name declared by a heading <code>-- QUERY_ID: name</code> or <code>-- TESTCASE: name</code> comment
	 *         (trimmed), or <code>null</code>
	 * @throws SQLException if the QUERY_ID is empty or contains spaces, <code>?</code>, <code>#</code> or <code>,</code>
	 */
	static String matchQueryId(String sql) throws SQLException {
		Matcher commentMatcher = COMMENT_HEADLINE_PATTERN.matcher(sql);
		if (commentMatcher.matches()) {
			String name = commentMatcher.group(2).trim();
			if ("QUERY_ID".equalsIgnoreCase(commentMatcher.group(1))) {
				if (!QUERY_ID_PATTERN.matcher(name).matches()) {
					throw new SQLException("Invalid QUERY_ID '" + name
							+ "': it must be one word without spaces, '?', '#' or ','");
				}
				return name;
			}
			return name.isEmpty() ? null : name;
		}
		return null;
	}

	/**
	 * @param sql the SQL text
	 * @return the name of the stored procedure called with EXEC, EXECUTE, CALL or <code>{call ...}</code>, or <code>null</code>
	 */
	static String matchStoredProcedureCall(String sql) {
		Matcher procedureMatcher = STORED_PROCEDURE_CALL_PATTERN.matcher(sql);
		return procedureMatcher.matches() ? procedureMatcher.group(1) : null;
	}

	/** a query on a named resource (table, test case, procedure): counts one more occurrence of that name */
	private ResultSet createResultSet(String tableName) {
		lastOccurrenceName = tableName;
		lastOccurrence = MockJdbcDriver.nextOccurrence(tableName);
		return createResultSet(tableResources, tableName, paramsString, lastOccurrence);
	}

	/**
	 * a query on a table: the full name first (<code>dbo.customers</code>), then its last part (<code>customers</code>)
	 * with the same occurrence number, counted on the full name
	 */
	private ResultSet createTableResultSet(String tableName) {
		lastOccurrenceName = tableName;
		lastOccurrence = MockJdbcDriver.nextOccurrence(tableName);
		ResultSet resultSet = findResultSet(tableResources, tableName, paramsString, lastOccurrence);
		String shortName = SqlNames.lastPart(tableName);
		if (resultSet == null && shortName != null) {
			resultSet = findResultSet(tableResources, shortName, paramsString, lastOccurrence);
		}
		if (resultSet == null) {
			LOGGER.info("No table definition found for '{}', using MockResultSet.", tableName);
			return new MockResultSet();
		}
		return resultSet;
	}

	/**
	 * Loads a table exactly as a <code>SELECT * FROM tableName</code> would, without running a statement (the step
	 * counter is not touched): in-memory resource (or Boomi Dynamic Process Property), registered file, or
	 * <code>/tables/&lt;name&gt;.csv</code>.
	 *
	 * @param tableResources {@link Map} of table name to CSV file of the connection
	 * @param tableName the table name
	 * @return a {@link CSVResultSet}, or an empty {@link MockResultSet} if the table is not defined
	 */
	public static ResultSet loadTable(Map<String, File> tableResources, String tableName) {
		return createResultSet(tableResources, tableName, null, 0);
	}

	/**
	 * @param occurrence how many times the resource name has been used (1 = first), 0 = not counted
	 */
	private static ResultSet createResultSet(Map<String, File> tableResources, String tableName, String paramsString,
			int occurrence) {
		ResultSet resultSet = findResultSet(tableResources, tableName, paramsString, occurrence);
		if (resultSet == null) {
			LOGGER.info("No table definition found for '{}', using MockResultSet.", tableName);
			return new MockResultSet();
		}
		return resultSet;
	}

	/**
	 * @return the data of the resource, or <code>null</code> if no in-memory resource, file or classpath resource has
	 *         that name
	 */
	private static ResultSet findResultSet(Map<String, File> tableResources, String tableName, String paramsString,
			int occurrence) {
		Reader inMemoryReader = null;
		String inMemoryCSV = null;
		String occurrenceSuffix = occurrence > 0 ? MockJdbcDriver.OCCURRENCE_SEPARATOR + occurrence : null;

		// search for "tablename?param1,param2#n" and "tablename?param1,param2"
		if (paramsString!=null) {
			if (occurrenceSuffix != null) {
				inMemoryCSV = MockJdbcDriver.getInMemoryTableResource(tableName+"?"+paramsString+occurrenceSuffix);
			}
			if (inMemoryCSV==null) {
				inMemoryCSV = MockJdbcDriver.getInMemoryTableResource(tableName+"?"+paramsString);
			}
		} // then "tablename#n" and just "tablename"
		if (inMemoryCSV==null && occurrenceSuffix != null) {
			inMemoryCSV = MockJdbcDriver.getInMemoryTableResource(tableName+occurrenceSuffix);
		}
		if (inMemoryCSV==null) {
			inMemoryCSV = MockJdbcDriver.getInMemoryTableResource(tableName);
		} // if any in memory CSV is found read it directly: a String needs no charset
		if (inMemoryCSV!=null) {
			inMemoryReader = new StringReader(inMemoryCSV);
		}
		
		// Does a text file for the mock table exist?
		File resource = tableResources.get(tableName.toLowerCase(Locale.ROOT));
		// a /tables/<name>.csv found on the classpath but not in a directory (e.g. inside a jar)
		URL classpathResource = null;
		if (resource == null && inMemoryReader == null) {
			// Try to load /tables/<name>.csv from the classpath: a directory or a jar
			URL url = CsvStatement.class.getResource("/tables/" + tableName.toLowerCase(Locale.ROOT) + ".csv");
			if (url == null) {
				return null;
			} else if ("file".equalsIgnoreCase(url.getProtocol())) {
				try {
					resource = new File(url.toURI());
				} catch (URISyntaxException e) {
					LOGGER.error("Error creating URI for table file: {}", e.getMessage(), e);
				}
			} else {
				classpathResource = url;
			}
		}

		Reader tableReader = null;
		try {
			// files and classpath resources are read with the default charset of the VM
			if (resource != null) {
				tableReader = new InputStreamReader(new FileInputStream(resource));
			} else if (classpathResource != null) {
				URLConnection connection = classpathResource.openConnection();
				// no cache: a cached jar stays open (and locked on Windows) until the JVM stops
				connection.setUseCaches(false);
				tableReader = new InputStreamReader(connection.getInputStream());
			} else {
				tableReader = inMemoryReader;	// might be null => no inMemoryCSV
			}
			return createGenericResultSet(tableName, tableReader);
		} catch (IOException e) {
			LOGGER.info("No table definition found for '{}', using MockResultSet.", tableName, e);
		} finally {
			if (tableReader != null) {
				try {
					tableReader.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return new MockResultSet();
	}

	private static ResultSet createGenericResultSet(String tableName, Reader tableDataReader) {

		// Maps table columns to a number of available values.
		Collection<LinkedHashMap<String, String>> entries = new ArrayList<LinkedHashMap<String, String>>();

		CSVReader tableReader = null;
		try {

			tableReader = new CSVReader(tableDataReader);

			// Read header
			String[] header = tableReader.readNext();
			if (header == null) {
				LOGGER.info("Empty table definition for '{}', using MockResultSet.", tableName);
				return new MockResultSet();
			} else {

				String[] data;
				// Read data
				while ((data = tableReader.readNext()) != null) {
					if (header.length == data.length) {
						LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
						for (int i = 0; i < header.length; i++) {
                            final String headerName = resolveHeaderName(header[i]);
							if (map.containsKey(headerName)) {
								String message = MessageFormat.format("Duplicate column in file ''{0}.txt: {1}",
										tableName, header[i]);
								throw new IllegalArgumentException(message);
							}
							map.put(headerName, data[i].trim());

						}
						entries.add(map);
					} else {
						throw new IllegalArgumentException("Length of data does not fit header length.");
					}

				}
			}
			return new CSVResultSet(tableName, new MockResultSetMetaData(tableName, header), entries);

		} catch (IOException e) {
			LOGGER.error("Error while reading data from CSV", e);
		} finally {
			if (tableReader != null) {
				try {
					tableReader.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return new MockResultSet();
	}

	private MockResultSet createPureResultSet() {

        // Maps table columns to a number of available values.
		Collection<LinkedHashMap<String, String>> entries = new ArrayList<LinkedHashMap<String, String>>();

		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		map.put("1", "1");

		entries.add(map);

		return new CSVResultSet(null, new MockResultSetMetaData(null, new String[] { "1" }), entries);
	}

	private static String resolveHeaderName(String str) {
	    return str.trim().toUpperCase(Locale.ROOT);
    }


}
