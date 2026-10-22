package me.avogadro.mockjdbc;

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
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;

import me.avogadro.mockjdbc.boomi.BoomiExecutionUtil;
import me.avogadro.mockjdbc.connection.impl.MockConnection;
import me.avogadro.mockjdbc.utils.FilenameUtils;
import me.avogadro.mockjdbc.utils.StringUtils;

/**
 * The {@link MockJdbcDriver}. The {@link #connect(String, Properties)} method returns the {@link MockConnection}.
 *
 * @author Kai Winter
 */
public final class MockJdbcDriver implements Driver {

	/**
	 * The date format for parsing a date from a CSV file.
	 */
	private static final String DATE_FORMAT = "dd-MMM-yy";
	private static final String TIME_FORMAT = "HH:mm";
	private static final String TIMESTAMP_FORMAT = "yyyyMMdd HHmmss.SSS";

	/**
	 * The locale of the date, time and timestamp formats (month and day names): fixed, so that the result does not
	 * depend on the locale of the machine or of the JVM.
	 */
	private static final Locale DATE_LOCALE = Locale.ENGLISH;

	private static DateFormat dateFormat(String pattern) {
		return new SimpleDateFormat(pattern, DATE_LOCALE);
	}

	public static final String STEP_PREFIX = "##STEP";

	/** Version of the driver, also reported by the database metadata */
	public static final int VERSION_MAJOR = 2;
	public static final int VERSION_MINOR = 2;
	
	/** Separator of the occurrence number in resource names: <code>users#2</code> is the second use of <code>users</code> */
	public static final String OCCURRENCE_SEPARATOR = "#";

	/**
	 * Counters of the current thread: the step (number of statements created, starting from -1 because it is
	 * pre-incremented when each statement is created) and the occurrences (how many times each resource name was
	 * queried or written). Inside Boomi they are reset automatically when the execution changes.
	 */
	private static final ThreadLocal<Map<String, Object>> COUNTERS = new ThreadLocal<Map<String, Object>>();

	/*
	 * Keys of the per-thread counters map. Only JDK classes (HashMap, Integer, String) are stored in the ThreadLocal:
	 * a value of a driver class would keep the driver's class loader, and so its jar file, alive as long as the
	 * (pooled, long-lived) thread runs, preventing the runtime from unloading or replacing the jar.
	 */
	/** Integer: current step */
	private static final String STEP_KEY = "step";
	/** HashMap&lt;String, Integer&gt;: occurrences per lower case resource name */
	private static final String OCCURRENCES_KEY = "occurrences";
	/** String: Boomi execution these counters belong to (absent outside Boomi) */
	private static final String EXECUTION_ID_KEY = "executionId";

	/** the counters of the current thread, created on first use */
	private static Map<String, Object> counters() {
		Map<String, Object> counters = COUNTERS.get();
		if (counters == null) {
			counters = new HashMap<String, Object>();
			counters.put(STEP_KEY, Integer.valueOf(-1));
			counters.put(OCCURRENCES_KEY, new HashMap<String, Integer>());
			COUNTERS.set(counters);
		}
		return counters;
	}

	private static int currentStep() {
		return ((Integer) counters().get(STEP_KEY)).intValue();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Integer> occurrences() {
		return (Map<String, Integer>) counters().get(OCCURRENCES_KEY);
	}

	/** the instance registered in {@link DriverManager} by the static initializer (outside Boomi) */
	private static final MockJdbcDriver REGISTERED_DRIVER = new MockJdbcDriver();

	/** true while {@link #REGISTERED_DRIVER} is registered in {@link DriverManager} */
	private static volatile boolean registered = false;
	
	/**
	 * CSV files stored into memory 
	 */
	static Map<String,String> inMemoryTableResources = new HashMap<String,String>();

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_DATEFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return dateFormat(DATE_FORMAT);
		}
	};

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_TIMEFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return dateFormat(TIME_FORMAT);
		}
	};

	public static final ThreadLocal<DateFormat> THREAD_LOCAL_TIMESTAMPFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return dateFormat(TIMESTAMP_FORMAT);
		}
	};

	private final static String DEFAULT_DATABASE = "any";

	private static Map<String, Map<String, File>> tableResources = Collections.synchronizedMap(new HashMap<String, Map<String, File>>());

	static {
		// Inside Boomi the driver is instantiated by class name and connect() is called directly: no registration in
		// DriverManager, which would keep the driver's class loader (and the jars of its library) in use forever.
		if (!BoomiExecutionUtil.isExecutionManagerAvailable()) {
			try {
				DriverManager.registerDriver(REGISTERED_DRIVER);
				registered = true;
			} catch (SQLException e) {
				// ignore
			}
		}
		Logger.getLogger(MockJdbcDriver.class.getName()).info("MockJDBC " + VERSION_MAJOR + "." + VERSION_MINOR
				+ " registered in DriverManager: " + registered);
	}

	/**
	 * @return <code>true</code> if the driver is registered in {@link DriverManager}: always outside Boomi (until
	 *         {@link #deregister()}), never inside Boomi
	 */
	public static boolean isRegistered() {
		return registered;
	}
	
	/**
	 * Reset the internal data structures to restart counting
	 */
	public static void reset() {
		resetCounters();
		inMemoryTableResources = new HashMap<String,String>();
		// keep the per-database maps (connections hold a reference to them), just empty them
		synchronized (tableResources) {
			for (Map<String, File> databaseMap : tableResources.values()) {
				databaseMap.clear();
			}
		}
	}
	
	/**
	 * Restarts the step and the occurrence counters of the current thread, keeping all the resources.
	 * Inside Boomi this also happens when the Dynamic Process Property <code>mockjdbc#RESET</code> is set and when the
	 * execution ID changes.
	 */
	public static void resetCounters() {
		resetCounters(-1);
	}

	private static void resetCounters(int step) {
		counters().put(STEP_KEY, Integer.valueOf(step));
		occurrences().clear();
	}

	/**
	 * Removes the driver from {@link DriverManager}, where the static initializer registered it (outside Boomi). For
	 * containers that unload the driver: a registered driver keeps its class loader (and its jar file) in use. Does
	 * nothing when the driver is not registered (e.g. inside Boomi, or already deregistered).
	 *
	 * @throws SQLException if the driver cannot be deregistered
	 */
	public static synchronized void deregister() throws SQLException {
		if (registered) {
			DriverManager.deregisterDriver(REGISTERED_DRIVER);
			registered = false;
		}
	}

	public static String getStepName() {
		return STEP_PREFIX+currentStep();
	}

	/**
	 * Increments and returns the occurrence counter of a resource name (case insensitive): 1 the first time a table,
	 * test case or procedure is queried or written, 2 the second time, ...
	 *
	 * @param resourceName the resource name, without parameters
	 * @return the occurrence number, starting from 1
	 */
	public static int nextOccurrence(String resourceName) {
		Map<String, Integer> occurrences = occurrences();
		String key = resourceName.toLowerCase(Locale.ROOT).trim();
		Integer previous = occurrences.get(key);
		int occurrence = previous == null ? 1 : previous + 1;
		occurrences.put(key, occurrence);
		return occurrence;
	}

	/**
	 * Called before each statement is executed: inside Boomi resets the counters when requested (see
	 * {@link #resetCounters()}); the statement being executed then becomes step 0.
	 */
	public static void beforeExecution() {
		checkAutomaticReset(0);
	}

	/**
	 * Inside Boomi resets the counters when the execution ID changed (a pooled thread reused by another execution) or
	 * when the Dynamic Process Property <code>mockjdbc#RESET</code> is set (it is then emptied).
	 */
	private static void checkAutomaticReset(int stepAfterReset) {
		if (!BoomiExecutionUtil.isBoomi()) {
			return;
		}
		Map<String, Object> counters = counters();
		boolean reset = false;
		String executionId = BoomiExecutionUtil.getExecutionId();
		if (executionId != null && !executionId.equals(counters.get(EXECUTION_ID_KEY))) {
			counters.put(EXECUTION_ID_KEY, executionId);
			reset = true;
		}
		if (BoomiExecutionUtil.consumeResetRequest()) {
			reset = true;
		}
		if (reset) {
			resetCounters(stepAfterReset);
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
		Map<String, File> databaseMap;
		if (tableResources.containsKey(DEFAULT_DATABASE)) {
			databaseMap = tableResources.get(DEFAULT_DATABASE);
		} else {
			databaseMap = Collections.synchronizedMap(new HashMap<String, File>());
		}
		databaseMap.put(tablename.toLowerCase(Locale.ROOT), csvFile);
		tableResources.put(DEFAULT_DATABASE, databaseMap);
	}


	@Override
	public int getMajorVersion() {
		return VERSION_MAJOR;
	}

	@Override
	public int getMinorVersion() {
		return VERSION_MINOR;
	}

	@Override
	public boolean jdbcCompliant() {
		return false;
	}

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return
			url.equals("any") ||	// used by JUnit test cases
			url.toLowerCase(Locale.ROOT).startsWith("jdbc::mock::");
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		String database = parseConnectUrl(url);

		loadTableResources(database);

		// Hand over a map that is registered in the driver, so tables added after connecting are visible
		Map<String, File> databaseMap;
		synchronized (tableResources) {
			databaseMap = tableResources.get(database);
			if (databaseMap == null) {
				databaseMap = Collections.synchronizedMap(new HashMap<String, File>());
				tableResources.put(database, databaseMap);
			}
		}

		return new MockConnection(databaseMap, url);
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
		THREAD_LOCAL_DATEFORMAT.set(dateFormat(format));
	}
	
	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setTimeFormat(String format) {
		THREAD_LOCAL_TIMEFORMAT.set(dateFormat(format));
	}

	/**
	 * Used for parsing CSV
	 * 
	 * @param format {@link SimpleDataFormat} pattern
	 */
	public static void setTimestampFormat(String format) {
		THREAD_LOCAL_TIMESTAMPFORMAT.set(dateFormat(format));
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
	 * Get the current value of the resource, used mainly to examine the parameters used for INSERT/UPDATE queries.
	 * When running inside Boomi and the resource has not been added in memory, the Dynamic Process Property
	 * <code>mockjdbc_&lt;testID&gt;</code> is used instead (as written, then in lower case).
	 * @param testID
	 * @return
	 */
	public static String getInMemoryTableResource(String testID) {
		String value = inMemoryTableResources.get(testID.toLowerCase(Locale.ROOT).trim());
		if (value == null) {
			value = BoomiExecutionUtil.getResourceProperty(testID);
		}
		return value;
	}
	
	/**
	 * Get the current value of the resource, used mainly to examine the parameters used for INSERT/UPDATE queries
	 * @param testID
	 * @return
	 */
	public static String getInMemoryTableResourceForCurrentStep() {
		return getInMemoryTableResource(getStepName());
	}
	
	/**
	 * Signal to update to the next step
	 */
	public static void nextStep() {
		checkAutomaticReset(-1);
		counters().put(STEP_KEY, Integer.valueOf(currentStep() + 1));
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
		inMemoryTableResources.put(testID.toLowerCase(Locale.ROOT).trim(), value.trim());
	}

	/**
	 * Add the CSV contained the string 'value' to the list of available resultsets
	 *  
	 * @param testID
	 * @param value
	 */
	public static void addInMemoryTableResource(int step, String value) {
		addInMemoryTableResource(STEP_PREFIX+step, value);
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
	    MockJdbcDriver.addInMemoryTableResource(testID,value);
	}
	
	/**
	 * Add the CSV contained the InputStream 'valueStream' to the list of available resultsets
	 *  
	 * @param step number of step for using this resource
	 * @param value
	 */
	public static void addInMemoryTableResource(int step, InputStream valueStream) {
		addInMemoryTableResource(STEP_PREFIX+step,valueStream);
	}


}
