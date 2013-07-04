package com.googlecode.dummyjdbc.statement.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import junit.framework.Assert;

import org.junit.Test;

import com.googlecode.dummyjdbc.DummyJdbcDriver;

public final class CsvStatementTest {

	/**
	 * Tests the case if there is no csv file available for a table. In this case an empty ResultSet should be returned.
	 */
	@Test
	public void notAvailableCsvFileTest() throws Exception {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		Connection connection = DriverManager.getConnection("any");
		Statement statement = connection.createStatement();

		Assert.assertTrue(statement instanceof CsvStatement);
		ResultSet resultSet = statement.executeQuery("SELECT * FROM unknown_table");
		boolean next = resultSet.next();

		Assert.assertFalse(next);
	}
}
