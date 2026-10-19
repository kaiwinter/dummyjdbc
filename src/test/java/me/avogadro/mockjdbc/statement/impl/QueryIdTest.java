package me.avogadro.mockjdbc.statement.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

/**
 * Queries named by a heading <code>-- QUERY_ID: name</code> comment.
 */
public final class QueryIdTest {

	private static final String ID = "ecommerce.create-order-ws.insert-order";

	private Connection connection;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		connection = DriverManager.getConnection("any");
	}

	private String name(String sql) throws SQLException {
		ResultSet resultSet = connection.createStatement().executeQuery(sql);
		return resultSet.next() ? resultSet.getString("name") : null;
	}

	@Test
	public void testSelectReadsTheDatasetOfTheId() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("ecommerce.create-order-ws.select-customer", "name\nfrom id");
		MockJdbcDriver.addInMemoryTableResource("customers", "name\nfrom table");

		Assert.assertEquals("from id",
				name("-- QUERY_ID: ecommerce.create-order-ws.select-customer\nSELECT * FROM customers"));
	}

	@Test
	public void testKeywordIsCaseInsensitive() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("q1", "name\nfrom id");

		Assert.assertEquals("from id", name("--query_id:q1\nSELECT * FROM customers"));
	}

	@Test
	public void testSelectWithParametersAndWindowsLineEnding() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("q1?C42", "name\nspecific");
		MockJdbcDriver.addInMemoryTableResource("q1", "name\ngeneric");

		PreparedStatement statement = connection.prepareStatement(
				"-- QUERY_ID: q1  \r\nSELECT *\r\nFROM customers\r\nWHERE id = ?");
		statement.setString(1, "C42");
		ResultSet resultSet = statement.executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("specific", resultSet.getString("name"));
		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("q1_PARAMS"));
	}

	@Test
	public void testWriteIsCapturedUnderTheId() throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"-- QUERY_ID: " + ID + "\nINSERT INTO orders (id, amount) VALUES (?, ?)");
		statement.setString(1, "1001");
		statement.setString(2, "250.00");

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("1001,250.00", MockJdbcDriver.getInMemoryTableResource(ID + "_PARAMS"));
		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("orders_PARAMS"));
	}

	@Test
	public void testRepeatedWritesAreNumbered() throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"-- QUERY_ID: " + ID + "\nINSERT INTO orders (id) VALUES (?)");
		statement.setString(1, "1");
		statement.executeUpdate();
		statement.setString(1, "2");
		statement.executeUpdate();

		Assert.assertEquals("1", MockJdbcDriver.getInMemoryTableResource(ID + "_PARAMS#1"));
		Assert.assertEquals("2", MockJdbcDriver.getInMemoryTableResource(ID + "_PARAMS#2"));
		Assert.assertEquals("2", MockJdbcDriver.getInMemoryTableResource(ID + "_PARAMS"));
	}

	@Test
	public void testEachIdHasItsOwnCounter() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("read#1", "name\nfirst read");
		MockJdbcDriver.addInMemoryTableResource("read#2", "name\nsecond read");

		Assert.assertEquals("first read", name("-- QUERY_ID: read\nSELECT * FROM customers"));
		PreparedStatement update = connection.prepareStatement("-- QUERY_ID: write\nUPDATE customers SET a = ?");
		update.setString(1, "x");
		update.executeUpdate();
		Assert.assertEquals("second read", name("-- QUERY_ID: read\nSELECT * FROM customers"));
		Assert.assertEquals("x", MockJdbcDriver.getInMemoryTableResource("write_PARAMS#1"));
	}

	@Test
	public void testTestCaseStillWorks() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("T_014a", "name\nfrom test case");

		Assert.assertEquals("from test case", name("-- TESTCASE: T_014a\nSELECT * FROM customers"));
	}

	@Test
	public void testOtherCommentsAreNotIds() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers", "name\nfrom table");

		Assert.assertEquals("from table", name("-- Query: reads the active customers\nSELECT * FROM customers"));
		Assert.assertEquals("from table", name("-- the QUERY_ID: is below\nSELECT * FROM customers"));
	}

	@Test
	public void testIdWithSpacesIsRejected() {
		assertRejected("-- QUERY_ID: insert order\nINSERT INTO orders (id) VALUES (1)");
	}

	@Test
	public void testEmptyIdIsRejected() {
		assertRejected("-- QUERY_ID:   \nSELECT * FROM customers");
	}

	@Test
	public void testIdWithLookupSeparatorsIsRejected() {
		assertRejected("-- QUERY_ID: a?b\nSELECT * FROM customers");
		assertRejected("-- QUERY_ID: a#2\nSELECT * FROM customers");
		assertRejected("-- QUERY_ID: a,b\nSELECT * FROM customers");
	}

	private void assertRejected(String sql) {
		try {
			connection.prepareStatement(sql).execute();
			Assert.fail("expected an invalid QUERY_ID: " + sql);
		} catch (SQLException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("QUERY_ID"));
		}
	}
}
