package com.mindmercatis.dummyjdbc.statement.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.mindmercatis.dummyjdbc.DummyJdbcDriver;

public final class CsvPreparedStatementTest {

	private ResultSet resultSet;
	CsvPreparedStatement csvStatement;

	@Before
	public void setup() throws ClassNotFoundException, SQLException, URISyntaxException {
		TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
		
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		DummyJdbcDriver.addTableResource("test_table", new File(CsvGenericStatementTest.class.getResource(
				"test_table.csv").toURI()));
		Connection connection = DriverManager.getConnection("any");
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM test_table");

		Assert.assertTrue(statement instanceof CsvPreparedStatement);
		csvStatement = (CsvPreparedStatement)statement;
		resultSet = statement.executeQuery();
	}

	@Test
	public void testGetByColumnName() throws SQLException {

		Assert.assertTrue(resultSet.next());

		Assert.assertEquals(1, resultSet.getInt("id"));
		Assert.assertEquals("Germany", resultSet.getString("country_name"));
		Assert.assertEquals("DE", resultSet.getString("country_iso"));
	}

	@Test
	public void testBuildParamsString() {
		csvStatement.params[1]=new java.sql.Timestamp(1570623440352L);  // 2019-10-09,14:17:20.352 GMT+2
		csvStatement.params[2]=new Integer(920);
		csvStatement.params[3]="hello";
		
		String res = csvStatement.buildParamsString();
		// System.out.println("RES: "+res);
		Assert.assertEquals("20191009 121720.352,920,hello", res);
	}
	
	@Test
	public void testGetByColumnIndex() throws SQLException {

		Assert.assertTrue(resultSet.next());

		Assert.assertEquals(1, resultSet.getInt(1));
		Assert.assertEquals("Germany", resultSet.getString(2));
		Assert.assertEquals("DE", resultSet.getString(3));
	}

	@Test(expected = SQLException.class)
	public void testGetInvalidColumnName() throws SQLException {

		Assert.assertTrue(resultSet.next());

		resultSet.getInt("undefined");

		Assert.fail("Expected exception not thrown");
	}

	@Test(expected = SQLException.class)
	public void testGetInvalidColumnindex() throws SQLException {

		Assert.assertTrue(resultSet.next());
		resultSet.getInt(17);

		Assert.fail("Expected exception not thrown");
	}

	@Test
	public void testInMemoryCSVFromString() throws ClassNotFoundException, URISyntaxException, SQLException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		DummyJdbcDriver.addInMemoryTableResource("TEST1", 
								"\n"+
								"name, age\n"+
								"John, 20"+
								"\n"
		);
		
		Connection connection = DriverManager.getConnection("any");
		PreparedStatement statement = connection.prepareStatement(
				"-- TESTCASE:test1\n"+
				"SELECT * FROM test_table");

		Assert.assertTrue(statement instanceof CsvPreparedStatement);
		resultSet = statement.executeQuery();
		
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString(1));
		Assert.assertEquals(20, resultSet.getInt(2));
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertEquals(20, resultSet.getInt("age"));
	}
	

	@Test
	public void testInMemoryCSVFromInputStream() throws ClassNotFoundException, URISyntaxException, SQLException, UnsupportedEncodingException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		DummyJdbcDriver.addInMemoryTableResource("TEST1", 
				new ByteArrayInputStream(
								("name, age\n"+
								"John, 20").getBytes("ISO-8859-1")
								)
		);
		
		Connection connection = DriverManager.getConnection("any");
		PreparedStatement statement = connection.prepareStatement(
				"-- TESTCASE:test1\n"+
				"SELECT * FROM test_table");

		Assert.assertTrue(statement instanceof CsvPreparedStatement);
		resultSet = statement.executeQuery();
		
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString(1));
		Assert.assertEquals(20, resultSet.getInt(2));
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertEquals(20, resultSet.getInt("age"));
	}
	
	@Test
	public void testInsertWithInMemoryCSVFromInputStream() throws ClassNotFoundException, URISyntaxException, SQLException, UnsupportedEncodingException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		DummyJdbcDriver.addInMemoryTableResource("TEST1", 
				new ByteArrayInputStream(
								("name, age\n"+
								"John, 20").getBytes("ISO-8859-1")
								)
		);
		
		Connection connection = DriverManager.getConnection("any");
		PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO Target(nome,cognome)"
				+ "VALUES (?, ?)");

		Assert.assertTrue(statement instanceof CsvPreparedStatement);
		int rows = statement.executeUpdate();		
	}
	
	@Test
	public void testInsertWithInMemoryCSVFromInputStreamWithParameters() throws ClassNotFoundException, URISyntaxException, SQLException, UnsupportedEncodingException {
		Class.forName(DummyJdbcDriver.class.getCanonicalName());

		DummyJdbcDriver.addInMemoryTableResource("TEST1?20,hello", 
				new ByteArrayInputStream(
								("name, age\n"+
								"John, 20").getBytes("ISO-8859-1")
								)
		);
		
		Connection connection = DriverManager.getConnection("any");
		PreparedStatement statement = connection.prepareStatement(
				"SELECT *\n"
				+ "FROM TEST1\n"
				+ "WHERE age= ? AND other = ?");

		statement.setInt(1, 20);
		statement.setString(2, "hello");
		
		resultSet = statement.executeQuery();
		
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString(1));
		Assert.assertEquals(20, resultSet.getInt(2));
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertEquals(20, resultSet.getInt("age"));	
	}
	
    @Test
    public void insertSQL() throws Exception {
        Class.forName(DummyJdbcDriver.class.getCanonicalName());

        Connection connection = DriverManager.getConnection("any");
        PreparedStatement statement = connection.prepareStatement("INSERT INTO users (name,age) VALUES (?,?) ");

        // 1: 0 params
        Assert.assertTrue(statement instanceof CsvPreparedStatement);
        boolean status = statement.execute();
        String params = DummyJdbcDriver.getInMemoryTableResource("users_PARAMS");
        Assert.assertEquals("", params);
        
        // 2: 2 params
        statement.setString(1, "hello");
        statement.setInt(2, 30);
        status = statement.execute();
        params = DummyJdbcDriver.getInMemoryTableResource("users_PARAMS");
        Assert.assertEquals("hello,30", params);
        
        // 3: test params reset
        status = statement.execute();
        params = DummyJdbcDriver.getInMemoryTableResource("users_PARAMS");
        Assert.assertEquals("", params);

    }
    
	/**
	 * Within this text we expect the results to be read from memory based on the number of the execution step,
	 * ignoring the table name and comments
	 * 
	 * @throws SQLException
	 * @throws URISyntaxException
	 */
	@Test
	public void testQueryByStep() throws SQLException, URISyntaxException {

		DummyJdbcDriver.reset();
		DummyJdbcDriver.addInMemoryTableResource(0, 
				"\n"
				+ "name, age\n"+
				"John, 20"+
				"\n"
				);
		DummyJdbcDriver.addInMemoryTableResource(1, 
				"\n"
				+ "where, who\n"+
				"London, Sherlock"+
				"\n"
				);
		
		Connection connection = DriverManager.getConnection("any");
		Statement statement = connection.prepareStatement("ignore me");

		Assert.assertTrue(statement instanceof CsvPreparedStatement);
		resultSet = statement.executeQuery(
				  "-- TESTCASE:test1\n"
				+ "SELECT *\n"
				+ "FROM test_table");
		
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("John", resultSet.getString("name"));
		Assert.assertEquals(20, resultSet.getInt("age"));
		
		// test 2nd step
		statement = connection.prepareStatement("ignore me");

		Assert.assertTrue(statement instanceof CsvPreparedStatement);
		resultSet = statement.executeQuery(
				  "-- TESTCASE:test2\n"
				+ "SELECT *\n"
				+ "FROM test_table");
		
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("London", resultSet.getString("where"));
		Assert.assertEquals("Sherlock", resultSet.getString("who"));
	}

    
}
