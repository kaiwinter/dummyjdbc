package com.googlecode.dummyjdbc.statement.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

import au.com.bytecode.opencsv.CSVReader;

import com.googlecode.dummyjdbc.resultset.DummyResultSet;
import com.googlecode.dummyjdbc.resultset.impl.CSVResultSet;
import com.googlecode.dummyjdbc.statement.StatementAdapter;

/**
 * This class does the actual work of the Generic... classes. It tries to open a CSV file for the table name in the
 * query and parses the contained data.
 * 
 * @author Kai Winter
 */
public final class CsvStatement extends StatementAdapter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvStatement.class);

	/** Pattern to get table name from an SQL statement. */
	private static final Pattern TABLENAME_PATTERN = Pattern.compile(".*from (\\S*)\\s?.*", Pattern.CASE_INSENSITIVE);

	/** Pattern to get the name of a stored procedure from an SQL statement. */
	private static final Pattern STORED_PROCEDURE_PATTERN = Pattern.compile(".*(EXEC|EXECUTE) (\\S*)\\s?.*",
			Pattern.CASE_INSENSITIVE);

	private final Map<String, File> tableResources;

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

		return new DummyResultSet();
	}

	private ResultSet createResultSet(String tableName) {
		// Does a text file for the dummy table exist?
		File resource = tableResources.get(tableName.toLowerCase());
		if (resource == null) {
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

		FileInputStream dummyTableDataStream = null;
		try {
			dummyTableDataStream = new FileInputStream(resource);
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
							if (map.containsKey(header[i].trim().toUpperCase())) {
								String message = MessageFormat.format("Duplicate column in file ''{0}.txt: {1}",
										tableName, header[i]);
								throw new IllegalArgumentException(message);
							}
							map.put(header[i].trim().toUpperCase(), data[i].trim());

						}
						entries.add(map);
					} else {
						throw new IllegalArgumentException("Length of data does not fit header length.");
					}

				}
			}
			return new CSVResultSet(tableName, entries);

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
}
