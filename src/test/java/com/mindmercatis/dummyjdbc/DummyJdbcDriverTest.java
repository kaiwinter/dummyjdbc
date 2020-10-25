package com.mindmercatis.dummyjdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

import com.mindmercatis.dummyjdbc.DummyJdbcDriver;
import com.mindmercatis.dummyjdbc.connection.impl.DummyConnection;

public final class DummyJdbcDriverTest {

	@Test
	public void testGetGenericConnection() throws ClassNotFoundException, SQLException {

		Class.forName(DummyJdbcDriver.class.getCanonicalName());
		Connection connection = DriverManager.getConnection("any");

		Assert.assertTrue(connection instanceof DummyConnection);
	}

	@Test
	public void testLoadDatabase() throws ClassNotFoundException, SQLException {

		Class.forName(DummyJdbcDriver.class.getCanonicalName());
		Connection connection = DriverManager.getConnection("jdbc::mock::database");

		Assert.assertTrue(connection instanceof DummyConnection);
	}

}
