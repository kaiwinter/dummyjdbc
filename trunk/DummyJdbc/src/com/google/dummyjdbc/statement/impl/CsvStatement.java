package com.google.dummyjdbc.statement.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.bytecode.opencsv.CSVReader;

import com.google.dummyjdbc.DummyJdbcDriver;
import com.google.dummyjdbc.resultset.DummyResultSet;
import com.google.dummyjdbc.resultset.impl.CSVResultSet;
import com.google.dummyjdbc.statement.StatementAdapter;

/**
 * This class does the actual work of the Generic... classes. It tries to open a CSV file for the table name in the
 * query and parses the contained data.
 *
 * @author Kai Winter
 */
public class CsvStatement extends StatementAdapter {

	/** Pattern to get table name from an SQL statement. */
	private static final Pattern TABLENAME_PATTERN = Pattern.compile(".*from (\\S*)\\s?.*", Pattern.CASE_INSENSITIVE);

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {

		Matcher matcher = TABLENAME_PATTERN.matcher(sql);
		if (matcher.matches()) {
			String tableName = matcher.group(1);

			// Does a text file for the dummy table exist?
			File ressource = DummyJdbcDriver.getTableRessource(tableName.toLowerCase());
			if (ressource == null) {
				System.err.println(MessageFormat.format("No table definition found for ''{0}'', using DummyResultSet.",
						tableName));
				return new DummyResultSet();
			}

			FileInputStream dummyTableDataStream = null;
			try {
				dummyTableDataStream = new FileInputStream(ressource);
				return createGenericResultSet(tableName, dummyTableDataStream);
			} catch (FileNotFoundException e) {
				System.err.println(MessageFormat.format("No table definition found for ''{0}'', using DummyResultSet.",
						tableName));
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} finally {
				if (dummyTableDataStream != null) {
					try {
						dummyTableDataStream.close();
					} catch (IOException e) {
						// ignore
						e.printStackTrace();
					}
				}
			}

		}

		return new DummyResultSet();
	}

	private ResultSet createGenericResultSet(String tableName, InputStream dummyTableDataStream)
			throws IllegalAccessException {

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
								throw new IllegalAccessException(message);
							}
							map.put(header[i].trim().toUpperCase(), data[i].trim());

						}
						entries.add(map);
					} else {
						throw new IllegalArgumentException("Length of data does not fit header length.");
					}

				}
			}
			return new CSVResultSet(entries);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (dummyTableReader != null) {
				try {
					dummyTableReader.close();
				} catch (IOException e) {
					// ignore
					e.printStackTrace();
				}
			}
		}

		return new DummyResultSet();
	}
}
