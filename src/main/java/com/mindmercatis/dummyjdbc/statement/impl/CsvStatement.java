package com.mindmercatis.dummyjdbc.statement.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mindmercatis.dummyjdbc.DummyJdbcDriver;
import com.mindmercatis.dummyjdbc.resultset.DummyResultSet;
import com.mindmercatis.dummyjdbc.resultset.DummyResultSetMetaData;
import com.mindmercatis.dummyjdbc.resultset.impl.CSVResultSet;
import com.mindmercatis.dummyjdbc.statement.StatementAdapter;

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
	 * Pattern used to recognize explicitly declared table names inside an heading comment
	 */
			static final Pattern COMMENT_HEADLINE_PATTERN = Pattern.compile("\\s*--\\s*TESTCASE:\\s*(.*)\\n.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	
	/** Pattern to get table name from an SQL statement. */
	private static final Pattern TABLENAME_PATTERN = Pattern.compile(".*from\\s*(\\S*)\\s?.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL );

	/** Pattern to get the name of a stored procedure from an SQL statement. */
	private static final Pattern STORED_PROCEDURE_PATTERN = Pattern.compile(".*(EXEC|EXECUTE) (\\S*)\\s?.*",	Pattern.CASE_INSENSITIVE);

	private static final Pattern PURE_SELECT_PATTERN = Pattern.compile("select .*", Pattern.CASE_INSENSITIVE);

    private final Map<String, File> tableResources;

    /**
     * used to describe the "current" params.
     * Always null when invoked from {@link CsvStatement} and may hold values when used from {@link CsvPreparedStatement}
     */
    String paramsString = null;
    
    
    /**
     * Initializer
     * 
     */
    {
    	DummyJdbcDriver.nextStep();
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

		try {
			String stepRes = DummyJdbcDriver.getInMemoryTableResourceForCurrentStep();
			
			// match based on the current step (sequence number)
			if (stepRes!=null) {
				String tableName = DummyJdbcDriver.getStepName();
				return createResultSet(tableName);
			}
						
			// Try to check for a special heading comment within SQL
			Matcher commentMatcher = COMMENT_HEADLINE_PATTERN.matcher(sql);
			if (commentMatcher.matches()) {
				String tableName = commentMatcher.group(1);
				return createResultSet(tableName);
			}
			
			// Try to interpret SQL as a SELECT on a table
			Matcher tableMatcher = TABLENAME_PATTERN.matcher(sql);
			if (tableMatcher.matches()) {
				String tableName = tableMatcher.group(1);
				return createResultSet(tableName);
			}

			// Try to interpret SQL as call of a stored procedure
			Matcher storedProcedureMatcher = STORED_PROCEDURE_PATTERN.matcher(sql);
			if (storedProcedureMatcher.matches()) {
				String storedProcedureName = storedProcedureMatcher.group(2);
				return createResultSet(storedProcedureName);
			}

	        //  Try  to  interpret  SQL  as  a  pure  select
			Matcher  pureSelectMatcher  =  PURE_SELECT_PATTERN.matcher(sql);
			if  (pureSelectMatcher.matches())  {
				return  createPureResultSet();
			}

	        return new DummyResultSet();
	        
		} finally {
			// DummyJdbcDriver.nextStep();
		}
	}
	
	static String matchTablename(String sql) {
		Matcher tableMatcher = TABLENAME_PATTERN.matcher(sql);
		if (tableMatcher.matches()) {
			return tableMatcher.group(1);
		} else {
			return null;
		}
	}

	private ResultSet createResultSet(String tableName) {
		InputStream inMemoryDataStream = null;
		String inMemoryCSV = null;
		
		// search for "tablename?param1,param2" etc...
		if (paramsString!=null) {
			inMemoryCSV = DummyJdbcDriver.getInMemoryTableResource(tableName+"?"+paramsString);
		} // then search just for "tablename"
		if (inMemoryCSV==null) {
			inMemoryCSV = DummyJdbcDriver.getInMemoryTableResource(tableName);			
		} // if any in memory CSV is found convert to InputStream 
		if (inMemoryCSV!=null) {
			try {
				inMemoryDataStream = new ByteArrayInputStream(inMemoryCSV.getBytes("ISO-8859-1"));
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		
		// Does a text file for the dummy table exist?
		File resource = tableResources.get(tableName.toLowerCase());
		if (resource == null && inMemoryDataStream == null) {
			// Try to load a file from the ./tables/ directory
			CodeSource src = CsvStatement.class.getProtectionDomain().getCodeSource();

			String path = src.getLocation().getPath();
			path = path.substring(0, path.lastIndexOf("/"));
			try {
				URL url = CsvStatement.class.getResource("/tables/" + tableName.toLowerCase() + ".csv");
				if (url == null) {
					LOGGER.info("No table definition found for '{}', using DummyResultSet.", tableName);
					return new DummyResultSet();
				} else {
					resource = new File(url.toURI());
				}
			} catch (URISyntaxException e) {
				LOGGER.error("Error creating URI for table file: {}", e.getMessage(), e);
			}
		}

		InputStream dummyTableDataStream = null;
		try {
			if (resource==null) {
				dummyTableDataStream = inMemoryDataStream;	// might be null => no inMemoryCSV
			} else {
				dummyTableDataStream = new FileInputStream(resource);
			}
			return createGenericResultSet(tableName, dummyTableDataStream);
		} catch (FileNotFoundException e) {
			LOGGER.info("No table definition found for '{}', using DummyResultSet.", tableName);
		} finally {
			if (dummyTableDataStream != null) {
				try {
					dummyTableDataStream.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return new DummyResultSet();
	}

	private ResultSet createGenericResultSet(String tableName, InputStream dummyTableDataStream) {

		// Maps table columns to a number of available values.
		Collection<LinkedHashMap<String, String>> entries = new ArrayList<LinkedHashMap<String, String>>();

		CSVReader dummyTableReader = null;
		try {

			dummyTableReader = new CSVReader(new InputStreamReader(dummyTableDataStream));

			// Read header
			String[] header = dummyTableReader.readNext();
			if (header != null) {

				String[] data;
				// Read data
				while ((data = dummyTableReader.readNext()) != null) {
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
			return new CSVResultSet(tableName, new DummyResultSetMetaData(tableName, header), entries);

		} catch (IOException e) {
			LOGGER.error("Error while reading data from CSV", e);
		} finally {
			if (dummyTableReader != null) {
				try {
					dummyTableReader.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return new DummyResultSet();
	}

	private DummyResultSet createPureResultSet() {

        // Maps table columns to a number of available values.
		Collection<LinkedHashMap<String, String>> entries = new ArrayList<LinkedHashMap<String, String>>();

		LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
		map.put("1", "1");

		entries.add(map);

		return new CSVResultSet(null, null, entries);
	}

	private String resolveHeaderName(String str) {
	    return str.trim().toUpperCase();
    }


}
