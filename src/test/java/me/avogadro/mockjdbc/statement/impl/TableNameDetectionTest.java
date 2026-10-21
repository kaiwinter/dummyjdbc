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
 * The resource used for a table name: the full name first, then the last part (<code>dbo.customers</code>, then
 * <code>customers</code>); writes capture <code>&lt;full name&gt;_PARAMS</code>.
 */
public final class TableNameDetectionTest {

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
	public void testQualifiedNameFallsBackToTheLastPart() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers", "name\nshort");

		Assert.assertEquals("short", name("SELECT * FROM dbo.customers"));
		Assert.assertEquals("short", name("SELECT * FROM [dbo].[customers]"));
		Assert.assertEquals("short", name("SELECT * FROM [dbo.customers]"));
	}

	@Test
	public void testFullNameWinsOverTheLastPart() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers", "name\nshort");
		MockJdbcDriver.addInMemoryTableResource("dbo.customers", "name\nfull");

		Assert.assertEquals("full", name("SELECT * FROM \"dbo\".\"customers\""));
	}

	@Test
	public void testFallbackKeepsTheOccurrenceOfTheFullName() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers#1", "name\nfirst");
		MockJdbcDriver.addInMemoryTableResource("customers#2", "name\nsecond");

		Assert.assertEquals("first", name("SELECT * FROM dbo.customers"));
		Assert.assertEquals("second", name("SELECT * FROM dbo.customers"));
	}

	@Test
	public void testFirstTableIsUsedNotTheSubquery() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("orders", "name\norders");
		MockJdbcDriver.addInMemoryTableResource("blocked", "name\nblocked");

		Assert.assertEquals("orders", name("SELECT * FROM orders WHERE id NOT IN (SELECT id FROM blocked)"));
	}

	@Test
	public void testPreparedSelectUsesTheFirstTable() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("orders", "name\norders");

		PreparedStatement statement = connection.prepareStatement(
				"SELECT * FROM orders WHERE created_from = ? AND id IN (SELECT id FROM blocked)");
		statement.setString(1, "x");
		ResultSet resultSet = statement.executeQuery();
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("orders", resultSet.getString("name"));
	}

	@Test
	public void testInsertIntoQualifiedNameCapturesTheFullName() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO [dbo].[utenti] (a, b) VALUES (?, ?)");
		statement.setString(1, "x");
		statement.setInt(2, 7);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("x,7", MockJdbcDriver.getInMemoryTableResource("dbo.utenti_PARAMS"));
		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("utenti_PARAMS"));
	}

	@Test
	public void testInsertWithoutColumnListIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO orders VALUES (?, ?)");
		statement.setString(1, "x");
		statement.setInt(2, 7);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("x,7", MockJdbcDriver.getInMemoryTableResource("orders_PARAMS"));
	}

	@Test
	public void testUpdateOfNameWithUnderscoreIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("UPDATE user_roles SET role = ?");
		statement.setString(1, "admin");

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("admin", MockJdbcDriver.getInMemoryTableResource("user_roles_PARAMS"));
	}

	@Test
	public void testDeleteFromQuotedNameIsCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("DELETE FROM [order_lines] WHERE id = ?");
		statement.setInt(1, 3);

		Assert.assertEquals(1, statement.executeUpdate());
		Assert.assertEquals("3", MockJdbcDriver.getInMemoryTableResource("order_lines_PARAMS"));
	}

	@Test
	public void testTemporaryTableIsNotCaptured() throws SQLException {
		PreparedStatement statement = connection.prepareStatement("UPDATE #tmp SET a = ?");
		statement.setString(1, "x");

		Assert.assertEquals(0, statement.executeUpdate());
		Assert.assertNull(MockJdbcDriver.getInMemoryTableResource("#tmp_PARAMS"));
	}
}
