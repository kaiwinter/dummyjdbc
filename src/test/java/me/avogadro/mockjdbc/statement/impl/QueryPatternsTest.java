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
 * How table names and test cases are recognized in the SQL text (INSERT, UPDATE, TESTCASE comments).
 */
public final class QueryPatternsTest {

	private Connection connection;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		connection = DriverManager.getConnection("any");
	}

	@Test
	public void testUpdateWithoutTestCaseIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("UPDATE Customers SET city = ? WHERE id = ?");
		statement.setString(1, "Rome");
		statement.setInt(2, 42);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("Rome,42", MockJdbcDriver.getInMemoryTableResource("Customers_PARAMS"));
	}

	@Test
	public void testUpdateOnSeveralLinesAfterAComment() throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"-- change the city\r\nUPDATE customers\r\nSET city = ?\r\nWHERE id = ?");
		statement.setString(1, "Rome");
		statement.setInt(2, 42);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("Rome,42", MockJdbcDriver.getInMemoryTableResource("customers_PARAMS"));
	}

	@Test
	public void testInsertOnSeveralLinesIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO orders (a, b)\nVALUES (?, ?)");
		statement.setString(1, "x");
		statement.setInt(2, 7);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("x,7", MockJdbcDriver.getInMemoryTableResource("orders_PARAMS"));
	}

	@Test
	public void testInsertWithWindowsLineEndingsIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO orders\r\n(a, b)\r\nVALUES (?, ?)");
		statement.setString(1, "x");
		statement.setInt(2, 7);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("x,7", MockJdbcDriver.getInMemoryTableResource("orders_PARAMS"));
	}

	@Test
	public void testTestCaseWithWindowsLineEndingAndParameters() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("T_014a?42", "name\nspecific");
		MockJdbcDriver.addInMemoryTableResource("T_014a", "name\ngeneric");

		PreparedStatement statement = connection.prepareStatement("-- TESTCASE: T_014a\r\nSELECT * FROM x WHERE id = ?");
		statement.setInt(1, 42);
		ResultSet resultSet = statement.executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("specific", resultSet.getString("name"));
	}

	@Test
	public void testTestCaseFollowedByMultiLineQuery() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("T1", "name\nfrom test case");

		ResultSet resultSet = connection.createStatement()
				.executeQuery("-- TESTCASE: T1  \nSELECT *\nFROM some_table\nWHERE 1 = 1");

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("from test case", resultSet.getString("name"));
	}

	@Test
	public void testUpdateWithTestCaseAndWindowsLineEnding() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("-- TESTCASE: T_015\r\nUPDATE Customers SET city = ?");
		statement.setString(1, "Rome");
		statement.executeUpdate();

		Assert.assertEquals("Rome", MockJdbcDriver.getInMemoryTableResource("T_015_PARAMS"));
	}

	@Test
	public void testSelectWithTestCaseDoesNotCaptureParameters() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("T_014a", "name\nJohn");

		PreparedStatement statement = connection.prepareStatement("-- TESTCASE: T_014a\r\nSELECT * FROM x WHERE id = ?");
		statement.setInt(1, 42);
		ResultSet resultSet = statement.executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("T_014a_PARAMS"));
	}

	@Test
	public void testSelectAfterCommentDoesNotCaptureParameters() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("-- a comment\n  select name\nfrom users where id = ?");
		statement.setInt(1, 42);
		Assert.assertTrue(statement.execute());

		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("users_PARAMS"));
	}

	@Test
	public void testStepBasedSelectDoesNotCaptureParameters() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource(0, "name\nJohn");

		PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?");
		statement.setInt(1, 42);
		statement.executeQuery();

		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource(MockJdbcDriver.STEP_PREFIX + "0_PARAMS"));
	}

	@Test
	public void testStepBasedUpdateCapturesParameters() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource(0, "3");

		PreparedStatement statement = connection.prepareStatement("UPDATE users SET name = ?");
		statement.setString(1, "bob");

		Assert.assertEquals(3, statement.executeUpdate());
		Assert.assertEquals("bob", MockJdbcDriver.getInMemoryTableResource(MockJdbcDriver.STEP_PREFIX + "0_PARAMS"));
	}

	@Test
	public void testExecStoredProcedureCapturesParametersAndReturnsItsResult() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("my_proc", "result\nok");

		PreparedStatement statement = connection.prepareStatement("EXEC my_proc ?, ?");
		statement.setString(1, "a");
		statement.setInt(2, 1);
		ResultSet resultSet = statement.executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("ok", resultSet.getString("result"));
		Assert.assertEquals("a,1", MockJdbcDriver.getInMemoryTableResource("my_proc_PARAMS"));
	}

	@Test
	public void testExecuteStoredProcedureOnSeveralLinesAfterAComment() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("-- run it\r\nEXECUTE dbo.my_proc\r\n  @a = ?");
		statement.setString(1, "a");
		statement.execute();

		Assert.assertEquals("a", MockJdbcDriver.getInMemoryTableResource("dbo.my_proc_PARAMS"));
	}

	@Test
	public void testCallStoredProcedureCapturesParameters() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("CALL my_proc(?, ?)");
		statement.setString(1, "a");
		statement.setInt(2, 1);
		statement.execute();

		Assert.assertEquals("a,1", MockJdbcDriver.getInMemoryTableResource("my_proc_PARAMS"));
	}

	@Test
	public void testJdbcEscapeCallCapturesParameters() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("{ ? = call my_proc(?) }");
		statement.setString(2, "a");
		statement.execute();

		Assert.assertEquals(",a", MockJdbcDriver.getInMemoryTableResource("my_proc_PARAMS"));
	}

	@Test
	public void testCallStoredProcedureReturnsItsResult() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("my_proc", "result\nok");

		ResultSet call = connection.prepareStatement("CALL my_proc(?)").executeQuery();
		ResultSet escape = connection.prepareStatement("{call my_proc(?)}").executeQuery();
		ResultSet multiLine = connection.prepareStatement("EXECUTE my_proc\r\n  @a = ?").executeQuery();

		Assert.assertTrue(call.next());
		Assert.assertEquals("ok", call.getString("result"));
		Assert.assertTrue(escape.next());
		Assert.assertEquals("ok", escape.getString("result"));
		Assert.assertTrue(multiLine.next());
		Assert.assertEquals("ok", multiLine.getString("result"));
	}

	@Test
	public void testDeleteCapturesParameters() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("DELETE FROM users\nWHERE id = ?");
		statement.setInt(1, 42);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("42", MockJdbcDriver.getInMemoryTableResource("users_PARAMS"));
	}
}
