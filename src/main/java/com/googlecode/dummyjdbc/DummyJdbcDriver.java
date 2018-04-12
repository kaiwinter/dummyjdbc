package com.googlecode.dummyjdbc;

import com.googlecode.dummyjdbc.utils.FilenameUtils;
import com.googlecode.dummyjdbc.utils.StringUtils;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;

import com.googlecode.dummyjdbc.connection.impl.DummyConnection;

/**
 * The {@link DummyJdbcDriver}. The {@link #connect(String, Properties)} method returns the {@link DummyConnection}.
 *
 * @author Kai Winter
 */
public final class DummyJdbcDriver implements Driver {

	/**
	 * The date format for parsing a date from a CSV file.
	 */
	private static final String DATE_FORMAT = "dd-MMM-yy";
	private static final String TIME_FORMAT = "HH:mm";
	private static final String TIMESTAMP_FORMAT = "yyyyMMdd HHmmss.SSS";

	/**
	 * CSV files stored into memory 
	 */
	public static Map<String,String> inMemoryTableResources = new HashMap<String,String>();

	
	public static final ThreadLocal<DateFormat> THREAD_LOCAL_DATEFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat(DATE_FORMAT);
		}
	};

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_TIMEFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat(TIME_FORMAT);
		}
	};

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_TIMESTAMPFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat(TIMESTAMP_FORMAT);
		}
	};

	private final static String DEFAULT_DATABASE = "any";

	private static Map<String, Map<String, File>> tableResources = Collections.synchronizedMap(new HashMap<String, Map<String, File>>());

	static {
		try {
			// Register this with the DriverManager
			DriverManager.registerDriver(new DummyJdbcDriver());
		} catch (SQLException e) {
			// ignore
		}
	}

	/**
	 * Registers a CSV file for a database table. When a Query is executed like <code>SELECT * FROM ADDRESSES</code> the
	 * given <code>csvFile</code> for the given <code>tablename</code> <code>addresses</code> will be used.
	 *
	 * @param tablename
	 *            The name of the database table like in the SQL statement (e.g. addresses).
	 * @param csvFile
	 *            A {@link File} object of a CSV file which should be parsed in order to return table data.
	 */
	public static void addTableResource(String tablename, File csvFile) {
		Map<String, File> databaseMap = Collections.synchronizedMap(new HashMap<String, File>());
		databaseMap.put(tablename, csvFile);
		tableResources.put(DEFAULT_DATABASE, databaseMap);
	}


	@Override
	public int getMajorVersion() {
		return 1;
	}

	@Override
	public int getMinorVersion() {
		return 0;
	}

	@Override
	public boolean jdbcCompliant() {
		return false;
	}

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return
			url.equals("any") ||	// used by JUnit test cases
			url.toLowerCase().startsWith("jdbc::mock::");
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		String database = parseConnectUrl(url);

		loadTableResources(database);

		return new DummyConnection(tableResources.get(database));
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties props) throws SQLException {
		return new DriverPropertyInfo[0];
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return null;
	}

	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setDateFormat(String format) {
		THREAD_LOCAL_DATEFORMAT.set(new SimpleDateFormat(format));
	}
	
	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setTimeFormat(String format) {
		THREAD_LOCAL_TIMEFORMAT.set(new SimpleDateFormat(format));
	}

	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setTimestampFormat(String format) {
		THREAD_LOCAL_TIMESTAMPFORMAT.set(new SimpleDateFormat(format));
	}

	/**
	 * Parse jdbc url to database file path
	 *
	 * @param url 	jdbc url
	 * @return database file path
	 */
	private String parseConnectUrl(String url) {
		if (url == null) {
			throw new RuntimeException("You should defined jdbc url first");
		}

		final int index = url.indexOf("jdbc::mock::");
		if (index == -1) {
			return DEFAULT_DATABASE;
		}

		final String others = url.substring("jdbc::mock::".length());
		final String[] items = others.split("::");
		switch(items.length) {
			case 0:
				throw new RuntimeException("No database directory defined");
			default:
				return StringUtils.join(items, "/");
		}

	}

	/**
	 * load table resources from database directory
	 *
	 * @param database database path
	 */
	private void loadTableResources(String database) {
		// ignore database name is any
		if (DEFAULT_DATABASE.equals(database)) {
			return;
		}

		// check database is exists
		URL dirUrl = getClass().getClassLoader().getResource(database);
		if (dirUrl == null) {
			throw new RuntimeException("The database directory is not exists");
		}

		File dir = new File(dirUrl.getFile());
		if (!dir.canRead() || !dir.isDirectory()) {
			throw new RuntimeException("The database directory is not a directory or cannot read");
		}

		// get all table files
		File[] files = dir.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && FilenameUtils.isExtension(pathname.getName(), "csv");
			}

		});

		// registry table resources
		for (File file : files) {
			Map<String, File> databaseMap = tableResources.get(database);
			if (databaseMap == null) {
				databaseMap = Collections.synchronizedMap(new HashMap<String, File>());
				tableResources.put(database, databaseMap);
			}
			databaseMap.put(FilenameUtils.getBaseName(file.getName()), file);
		}
	}


	/**
	 * Get the current value of the resource, used mainly to examine the parameters used for INSERT/UPDATE queries
	 * @param testID
	 * @return
	 */
	public static String getInMemoryTableResource(String testID) {
		return inMemoryTableResources.get(testID.toLowerCase().trim());
	}


	public static void clearInMemoryTableResources() {
		inMemoryTableResources.clear();
	}


	/**
	 * Add the CSV contained the string 'value' to the list of available resultsets
	 *  
	 * @param testID
	 * @param value
	 */
	public static void addInMemoryTableResource(String testID, String value) {
		inMemoryTableResources.put(testID.toLowerCase().trim(), value.trim());
	}


	/**
	 * Add the CSV contained the InputStream 'valueStream' to the list of available resultsets
	 *  
	 * @param testID
	 * @param value
	 */
	@SuppressWarnings("resource")
	public static void addInMemoryTableResource(String testID, InputStream valueStream) {
		// search for "end of stream" => read all the stream into the string !
		Scanner s = new Scanner(valueStream).useDelimiter("\\A");
	    String value = s.hasNext() ? s.next() : "";
	    s.close();
	    DummyJdbcDriver.addInMemoryTableResource(testID,value);
	}


}
