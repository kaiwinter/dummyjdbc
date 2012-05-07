package com.google.dummyjdbc;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.google.dummyjdbc.connection.impl.DummyConnection;

/**
 * The {@link DummyJdbcDriver}. The {@link #connect(String, Properties)} method returns the {@link DummyConnection}.
 *
 * @author Kai Winter
 */
public class DummyJdbcDriver implements Driver {

	private static Map<String, File> tableRessources = Collections.synchronizedMap(new HashMap<String, File>());

	static {
		try {
			// Register this with the DriverManager
			DriverManager.registerDriver(new DummyJdbcDriver());
		} catch (SQLException e) {
		}
	}

	public static File getTableRessource(String filename) {
		return tableRessources.get(filename.toLowerCase());
	}

	/**
	 * Registers a CSV file for a database table. When a Query is executed like <code>SELECT * FROM ADDRESSES</code> the
	 * given <code>csvFile</code> for the given <code>tablename</code> <code>addresses</code> will be used.
	 *
	 * @param tablename
	 *            The name of the database table like in the SQL statement.
	 * @param csvFile
	 *            A {@link File} object of a CSV file which should be parsed in order to return table data.
	 */
	public static void registerTableRessource(String tablename, File csvFile) {
		tableRessources.put(tablename, csvFile);
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
		return true;
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		return new DummyConnection();
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties props) throws SQLException {
		return new DriverPropertyInfo[0];
	}
}
