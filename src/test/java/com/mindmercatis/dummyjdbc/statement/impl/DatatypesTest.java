package com.mindmercatis.dummyjdbc.statement.impl;

import java.io.File;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.mindmercatis.dummyjdbc.DummyJdbcDriver;

public final class DatatypesTest {

	private ResultSet resultSet;

	@Before
	public void setup() throws ClassNotFoundException, SQLException, URISyntaxException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		DummyJdbcDriver.addTableResource("datatypes",
				new File(CsvGenericStatementTest.class.getResource("datatypes.csv").toURI()));
		Connection connection = DriverManager.getConnection("any");
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

	@Test
	public void testDateByName() throws SQLException {
		Locale.setDefault(Locale.ENGLISH);
		Date date = resultSet.getDate("date_column");
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		Assert.assertEquals(17, calendar.get(Calendar.DAY_OF_MONTH));
		Assert.assertEquals(Calendar.MAY, calendar.get(Calendar.MONTH));
		Assert.assertEquals(2012, calendar.get(Calendar.YEAR));
	}

	@Test
	public void testDateByIndex() throws SQLException {
		Locale.setDefault(Locale.ENGLISH);
		Date date = resultSet.getDate(5);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);

		Assert.assertEquals(17, calendar.get(Calendar.DAY_OF_MONTH));
		Assert.assertEquals(Calendar.MAY, calendar.get(Calendar.MONTH));
		Assert.assertEquals(2012, calendar.get(Calendar.YEAR));
	}

	@Test(expected = SQLException.class)
	public void testDateInvalidByName() throws SQLException {
		Locale.setDefault(Locale.ENGLISH);
		resultSet.getDate("date_column_invalid");

	}

	@Test(expected = SQLException.class)
	public void testDateInvalidByIndex() throws SQLException {
		Locale.setDefault(Locale.ENGLISH);
		resultSet.getDate(6);
	}
}
