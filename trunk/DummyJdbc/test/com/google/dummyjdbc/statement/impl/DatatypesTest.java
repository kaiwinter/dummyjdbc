package com.google.dummyjdbc.statement.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.google.dummyjdbc.DummyJdbcDriver;


public final class DatatypesTest {

	private ResultSet resultSet;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		Connection connection = DriverManager.getConnection("any");
		// PreparedStatement prepareStatement =
		// connection.prepareStatement("SELECT * FROM datatypes");
		// resultSet = prepareStatement.executeQuery();
		Statement statement = connection.createStatement();
		resultSet = statement.executeQuery("SELECT * FROM datatypes");

		Assert.assertTrue(resultSet.next());
	}

	@Test
	public void testStringByName() throws SQLException {
		Assert.assertEquals("string_value", resultSet.getString("string_column"));
	}

	@Test
	public void testStringByIndex() throws SQLException {
		Assert.assertEquals("string_value", resultSet.getString(1));
	}

	@Test
	public void testIntByName() throws SQLException {
		Assert.assertEquals(17, resultSet.getInt("int_column"));
	}

	@Test
	public void testIntByIndex() throws SQLException {
		Assert.assertEquals(17, resultSet.getInt(2));
	}

	@Test
	public void testBooleanByName() throws SQLException {
		Assert.assertEquals(true, resultSet.getBoolean("boolean_column"));
	}

	@Test
	public void testBooleanByIndex() throws SQLException {
		Assert.assertEquals(true, resultSet.getBoolean(3));
	}

	@Test
	public void testBigDecimalByName() throws SQLException {
		Assert.assertEquals(new BigDecimal("123456789123456789"), resultSet.getBigDecimal("bigdecimal_column"));
	}

	@Test
	public void testBigDecimalByIndex() throws SQLException {
		Assert.assertEquals(new BigDecimal("123456789123456789"), resultSet.getBigDecimal(4));
	}

}
