package me.avogadro.mockjdbc.statement.impl;

import org.junit.Assert;
import org.junit.Test;

/**
 * The table name read from SELECT, INSERT, UPDATE and DELETE: the same identifier rule for all of them.
 */
public final class SqlNamesTest {

	// SELECT: the first FROM outside any parenthesis

	@Test
	public void testSelectSimpleTable() {
		Assert.assertEquals("TEST1", SqlNames.selectTable("SELECT *\nFROM TEST1\nWHERE age= ? AND other = ?"));
	}

	@Test
	public void testSelectSubqueryInWhereIsIgnored() {
		Assert.assertEquals("orders",
				SqlNames.selectTable("SELECT * FROM orders WHERE id IN (SELECT id FROM blocked)"));
	}

	@Test
	public void testSelectSubqueryInColumnsIsIgnored() {
		Assert.assertEquals("orders", SqlNames.selectTable("SELECT (SELECT max(x) FROM t2) AS m FROM orders"));
	}

	@Test
	public void testSelectFunctionWithFromIsIgnored() {
		Assert.assertEquals("orders", SqlNames.selectTable("SELECT EXTRACT(YEAR FROM d), TRIM(BOTH FROM n) FROM orders"));
	}

	@Test
	public void testSelectColumnEndingWithFromIsNotAKeyword() {
		Assert.assertEquals("orders", SqlNames.selectTable("SELECT created_from FROM orders WHERE created_from = ?"));
	}

	@Test
	public void testSelectFromInStringOrCommentIsIgnored() {
		Assert.assertEquals("orders", SqlNames.selectTable(
				"-- from comments\nSELECT 'from x' AS a, /* from y */ b FROM orders WHERE note = 'from z'"));
	}

	@Test
	public void testSelectWithJoinGivesTheFirstTable() {
		Assert.assertEquals("orders",
				SqlNames.selectTable("SELECT * FROM orders o JOIN customers c ON o.cid = c.id"));
	}

	@Test
	public void testSelectWithCommaJoinGivesTheFirstTable() {
		Assert.assertEquals("a", SqlNames.selectTable("SELECT * FROM a, b WHERE a.id = b.id"));
	}

	@Test
	public void testSelectUnionGivesTheFirstTable() {
		Assert.assertEquals("t1", SqlNames.selectTable("SELECT a FROM t1 UNION SELECT a FROM t2"));
	}

	@Test
	public void testSelectFromDerivedTableLooksInside() {
		Assert.assertEquals("orders",
				SqlNames.selectTable("SELECT * FROM (SELECT id FROM orders WHERE x IN (SELECT y FROM z)) o"));
	}

	@Test
	public void testSelectWithCteGivesTheCteName() {
		Assert.assertEquals("x", SqlNames.selectTable("WITH x AS (SELECT * FROM orders) SELECT * FROM x"));
	}

	@Test
	public void testSelectWithoutFrom() {
		Assert.assertNull(SqlNames.selectTable("SELECT 1"));
		Assert.assertNull(SqlNames.selectTable("SELECT GETDATE()"));
	}

	@Test
	public void testSelectWithUnterminatedStringGivesNoName() {
		Assert.assertNull(SqlNames.selectTable("SELECT 'abc FROM orders"));
	}

	// the identifier rule (shared by all statements)

	@Test
	public void testNamesWithDigitsUnderscoresAndDollar() {
		Assert.assertEquals("user_roles", SqlNames.selectTable("SELECT * FROM user_roles"));
		Assert.assertEquals("table2", SqlNames.selectTable("SELECT * FROM table2 t"));
		Assert.assertEquals("a$b", SqlNames.selectTable("SELECT * FROM a$b"));
	}

	@Test
	public void testSchemaQualifiedNames() {
		Assert.assertEquals("dbo.customers", SqlNames.selectTable("SELECT * FROM dbo.customers WHERE id = ?"));
		Assert.assertEquals("srv.db.dbo.customers", SqlNames.selectTable("SELECT * FROM srv.db.dbo.customers"));
	}

	@Test
	public void testQuotedNames() {
		Assert.assertEquals("customers", SqlNames.selectTable("SELECT * FROM [customers]"));
		Assert.assertEquals("customers", SqlNames.selectTable("SELECT * FROM \"customers\""));
		Assert.assertEquals("customers", SqlNames.selectTable("SELECT * FROM `customers`"));
		Assert.assertEquals("dbo.utenti", SqlNames.selectTable("SELECT * FROM [dbo.utenti]"));
		Assert.assertEquals("dbo.utenti", SqlNames.selectTable("SELECT * FROM [dbo].[utenti]"));
		Assert.assertEquals("dbo.utenti", SqlNames.selectTable("SELECT * FROM \"dbo\".\"utenti\""));
		Assert.assertEquals("dbo.utenti", SqlNames.selectTable("SELECT * FROM dbo.[utenti]"));
	}

	@Test
	public void testQuotedNamesWithEscapes() {
		Assert.assertEquals("a]b", SqlNames.selectTable("SELECT * FROM [a]]b]"));
		Assert.assertEquals("a\"b", SqlNames.selectTable("SELECT * FROM \"a\"\"b\""));
	}

	@Test
	public void testUnsupportedNamesGiveNoName() {
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM #tmp"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM @rows"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM [order details]"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM [a?b]"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM [a#b]"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM [a,b]"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM []"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM [unterminated"));
		Assert.assertNull(SqlNames.selectTable("SELECT * FROM dbo."));
	}

	// INSERT, UPDATE, DELETE

	@Test
	public void testInsert() {
		Assert.assertEquals("orders", SqlNames.writeTable("INSERT INTO orders (a, b) VALUES (?, ?)"));
		Assert.assertEquals("orders", SqlNames.writeTable("insert into orders(a,b) values (?,?)"));
		Assert.assertEquals("orders", SqlNames.writeTable("INSERT INTO orders VALUES (?, ?)"));
		Assert.assertEquals("user_roles", SqlNames.writeTable("INSERT INTO user_roles\r\n(a)\r\nVALUES (?)"));
		Assert.assertEquals("dbo.utenti", SqlNames.writeTable("INSERT INTO [dbo].[utenti] (a) VALUES (?)"));
		Assert.assertEquals("orders", SqlNames.writeTable("INSERT INTO orders SELECT * FROM other"));
	}

	@Test
	public void testUpdate() {
		Assert.assertEquals("Customers", SqlNames.writeTable("UPDATE Customers SET city = ? WHERE id = ?"));
		Assert.assertEquals("dbo.utenti", SqlNames.writeTable("UPDATE [dbo.utenti] SET a = ?"));
		Assert.assertEquals("table2", SqlNames.writeTable("-- change\r\nUPDATE table2\r\nSET a = ?"));
	}

	@Test
	public void testDelete() {
		Assert.assertEquals("orders", SqlNames.writeTable("DELETE FROM orders WHERE id = ?"));
		Assert.assertEquals("orders", SqlNames.writeTable("DELETE orders WHERE id = ?"));
		Assert.assertEquals("dbo.orders", SqlNames.writeTable("delete from \"dbo\".\"orders\" where id = ?"));
	}

	@Test
	public void testWriteAfterBlockComment() {
		Assert.assertEquals("orders", SqlNames.writeTable("/* audit */ DELETE FROM orders"));
	}

	@Test
	public void testNotAWrite() {
		Assert.assertNull(SqlNames.writeTable("SELECT * FROM orders"));
		Assert.assertNull(SqlNames.writeTable("EXEC update_orders ?"));
		Assert.assertNull(SqlNames.writeTable("UPDATE #tmp SET a = ?"));
	}

	@Test
	public void testInsertWithoutInto() {
		// T-SQL allows INSERT without INTO, as DELETE without FROM
		Assert.assertEquals("orders", SqlNames.writeTable("INSERT orders VALUES (?)"));
	}

	// fallback name

	@Test
	public void testLastPart() {
		Assert.assertEquals("utenti", SqlNames.lastPart("dbo.utenti"));
		Assert.assertEquals("customers", SqlNames.lastPart("srv.db.dbo.customers"));
		Assert.assertNull(SqlNames.lastPart("utenti"));
	}
}
