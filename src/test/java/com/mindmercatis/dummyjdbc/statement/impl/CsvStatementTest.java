package com.mindmercatis.dummyjdbc.statement.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Assert;
import org.junit.Test;

import com.mindmercatis.dummyjdbc.DummyJdbcDriver;
import com.mindmercatis.dummyjdbc.statement.impl.CsvStatement;

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

    @Test
    public void validSql() throws Exception {
        Class.forName(DummyJdbcDriver.class.getCanonicalName());

        Connection connection = DriverManager.getConnection("any");
        Statement statement = connection.createStatement();

        Assert.assertTrue(statement instanceof CsvStatement);
        ResultSet resultSet = statement.executeQuery("SELECT 1");
        boolean next = resultSet.next();
    }
    

}
