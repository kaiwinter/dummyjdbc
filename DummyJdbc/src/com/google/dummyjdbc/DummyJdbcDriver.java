package com.google.dummyjdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;

import com.google.dummyjdbc.connection.impl.DummyConnection;

/**
 * The {@link DummyJdbcDriver}. The {@link #connect(String, Properties)} method returns the {@link DummyConnection}.
 * 
 * @author Kai Winter
 */
public class DummyJdbcDriver implements Driver {

	static {
		try {
			// Register this with the DriverManager
			DriverManager.registerDriver(new DummyJdbcDriver());
		} catch (SQLException e) {
		}
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
