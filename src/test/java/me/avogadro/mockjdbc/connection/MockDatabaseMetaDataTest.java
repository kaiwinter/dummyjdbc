package me.avogadro.mockjdbc.connection;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

/**
 * Tools such as the Boomi Database V2 connector read the metadata before running a query: nothing may be null.
 */
public final class MockDatabaseMetaDataTest {

	private Connection connection;
	private DatabaseMetaData metaData;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		connection = DriverManager.getConnection("any");
		metaData = connection.getMetaData();
	}

	@Test
	public void testDescriptiveValues() throws SQLException {
		Assert.assertEquals("MockJDBC", metaData.getDatabaseProductName());
		Assert.assertEquals("MockJDBC", metaData.getDriverName());
		Assert.assertEquals("2.1", metaData.getDriverVersion());
		Assert.assertEquals("2.1", metaData.getDatabaseProductVersion());
		Assert.assertEquals(2, metaData.getDriverMajorVersion());
		Assert.assertEquals(1, metaData.getDriverMinorVersion());
		Assert.assertEquals("any", metaData.getURL());
		Assert.assertEquals("", metaData.getUserName());
		Assert.assertEquals(" ", metaData.getIdentifierQuoteString());
		Assert.assertSame(connection, metaData.getConnection());
		Assert.assertEquals(RowIdLifetime.ROWID_UNSUPPORTED, metaData.getRowIdLifetime());
	}

	@Test
	public void testUrlOfMockDatabase() throws SQLException {
		Assert.assertEquals("jdbc::mock::database",
				DriverManager.getConnection("jdbc::mock::database").getMetaData().getURL());
	}

	@Test
	public void testGetColumnsIsEmptyWithStandardColumns() throws SQLException {
		ResultSet columns = metaData.getColumns(null, null, "users", "%");

		Assert.assertFalse(columns.next());
		Assert.assertEquals(24, columns.getMetaData().getColumnCount());
		Assert.assertEquals("COLUMN_NAME", columns.getMetaData().getColumnName(4));
	}

	@Test
	public void testGetTablesAndPrimaryKeysAreEmpty() throws SQLException {
		Assert.assertFalse(metaData.getTables(null, null, "%", null).next());
		Assert.assertFalse(metaData.getPrimaryKeys(null, null, "users").next());
		Assert.assertFalse(metaData.getProcedures(null, null, "%").next());
	}

	@Test
	public void testUnwrap() throws SQLException {
		Assert.assertSame(metaData, metaData.unwrap(DatabaseMetaData.class));
	}

	@Test(expected = SQLException.class)
	public void testUnwrapUnsupportedClass() throws SQLException {
		metaData.unwrap(String.class);
	}

	/** every String or ResultSet returned by the metadata is not null */
	@Test
	public void testNoMethodReturnsNull() throws Exception {
		List<String> nulls = new ArrayList<String>();
		for (Method method : DatabaseMetaData.class.getMethods()) {
			Class<?> type = method.getReturnType();
			if (type != String.class && type != ResultSet.class) {
				continue;
			}
			Object[] args = new Object[method.getParameterTypes().length];
			for (int i = 0; i < args.length; i++) {
				Class<?> param = method.getParameterTypes()[i];
				args[i] = param == int.class ? Integer.valueOf(0) : param == boolean.class ? Boolean.FALSE : null;
			}
			if (method.invoke(metaData, args) == null) {
				nulls.add(method.getName());
			}
		}
		Assert.assertEquals("methods returning null", new ArrayList<String>(), nulls);
	}

	/** "COLUMN_NAME:DATA_TYPE:TYPE_NAME:DECIMAL_DIGITS" for every row of getColumns */
	private static String describe(ResultSet columns) throws SQLException {
		StringBuilder description = new StringBuilder();
		while (columns.next()) {
			description.append(description.length() == 0 ? "" : " ").append(columns.getString("COLUMN_NAME"))
					.append(':').append(columns.getInt("DATA_TYPE")).append(':').append(columns.getString("TYPE_NAME"))
					.append(':').append(columns.getInt("DECIMAL_DIGITS"));
			Assert.assertEquals("NO", columns.getString("IS_AUTOINCREMENT"));
		}
		return description.toString();
	}

	@Test
	public void testGetColumnsOfInMemoryTableWithOnlyTheHeader() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("payments",
				"id|integer, Amount|double, note, paid_on|date, at|time, ts|timestamp, ok|boolean, total|decimal, big|bigint");

		ResultSet columns = metaData.getColumns(null, null, "PAYMENTS", null);

		Assert.assertEquals("id:" + Types.INTEGER + ":INTEGER:0 Amount:" + Types.DOUBLE + ":DOUBLE:10 note:"
				+ Types.VARCHAR + ":VARCHAR:0 paid_on:" + Types.DATE + ":DATE:0 at:" + Types.TIME + ":TIME:0 ts:"
				+ Types.TIMESTAMP + ":TIMESTAMP:0 ok:" + Types.BOOLEAN + ":BOOLEAN:0 total:" + Types.DECIMAL
				+ ":DECIMAL:10 big:" + Types.BIGINT + ":BIGINT:0", describe(columns));
	}

	@Test
	public void testGetColumnsReportsTableNameAndStandardValues() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers", "id|integer, city\n1, Milano");

		ResultSet columns = metaData.getColumns("cat", "schema", "customers", "%");

		Assert.assertTrue(columns.next());
		Assert.assertEquals("customers", columns.getString("TABLE_NAME"));
		Assert.assertEquals("id", columns.getString("COLUMN_NAME"));
		Assert.assertEquals("4", columns.getString("DATA_TYPE"));
		Assert.assertEquals(DatabaseMetaData.columnNullable, columns.getInt("NULLABLE"));
		Assert.assertNull(columns.getString("COLUMN_DEF"));
		Assert.assertTrue(columns.next());
		Assert.assertEquals("city", columns.getString(4));
		Assert.assertFalse(columns.next());
	}

	@Test
	public void testGetColumnsWithColumnPattern() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers", "id|integer, city, country");

		ResultSet city = metaData.getColumns(null, null, "customers", "CITY");
		Assert.assertTrue(city.next());
		Assert.assertEquals(2, city.getInt("ORDINAL_POSITION"));
		Assert.assertEquals("city", city.getString("COLUMN_NAME"));
		Assert.assertFalse(city.next());
		Assert.assertEquals("city:12:VARCHAR:0 country:12:VARCHAR:0",
				describe(metaData.getColumns(null, null, "customers", "c%")));
	}

	@Test
	public void testGetColumnsOfRegisteredFile() throws Exception {
		MockJdbcDriver.addTableResource("test_table", new java.io.File(
				MockDatabaseMetaDataTest.class.getResource("/me/avogadro/mockjdbc/statement/impl/test_table.csv").toURI()));
		DatabaseMetaData fileMetaData = DriverManager.getConnection("any").getMetaData();

		Assert.assertEquals("ID:12:VARCHAR:0 country_name:12:VARCHAR:0 country_iso:12:VARCHAR:0",
				describe(fileMetaData.getColumns(null, null, "test_table", null)));
	}

	@Test
	public void testGetColumnsOfUnknownTableIsEmpty() throws SQLException {
		Assert.assertFalse(metaData.getColumns(null, null, "nothing_here", null).next());
		Assert.assertFalse(metaData.getColumns(null, null, "%", null).next());
	}

	@Test
	public void testGetColumnsDoesNotAdvanceTheStepCounter() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers", "id|integer");
		MockJdbcDriver.addInMemoryTableResource(0, "step\nfirst");

		metaData.getColumns(null, null, "customers", null);
		ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM anything");

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("first", resultSet.getString("step"));
	}
	@Test
	public void testGetProcedureColumnsFromTheSignature() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave",
				"ret_status|integer,in_name,in_value|integer,inout_counter|decimal,out_message\n0,,,1,ok");
		ResultSet columns = metaData.getProcedureColumns(null, null, "spSave", null);

		Assert.assertEquals(20, columns.getMetaData().getColumnCount());
		Assert.assertTrue(columns.next());
		Assert.assertEquals("@RETURN_VALUE", columns.getString("COLUMN_NAME"));
		Assert.assertEquals(DatabaseMetaData.procedureColumnReturn, columns.getShort(5));
		Assert.assertEquals("0", columns.getString("ORDINAL_POSITION"));
		Assert.assertTrue(columns.next());
		Assert.assertEquals("spSave", columns.getString("PROCEDURE_NAME"));
		Assert.assertEquals("name", columns.getString(4));
		Assert.assertEquals(DatabaseMetaData.procedureColumnIn, columns.getShort(5));
		Assert.assertEquals(String.valueOf(Types.VARCHAR), columns.getString(6));
		Assert.assertEquals("1", columns.getString("ORDINAL_POSITION"));
		Assert.assertTrue(columns.next());
		Assert.assertEquals("value", columns.getString(4));
		Assert.assertEquals(String.valueOf(Types.INTEGER), columns.getString(6));
		Assert.assertTrue(columns.next());
		Assert.assertEquals("counter", columns.getString(4));
		Assert.assertEquals(DatabaseMetaData.procedureColumnInOut, columns.getShort(5));
		Assert.assertEquals(String.valueOf(Types.DECIMAL), columns.getString(6));
		Assert.assertTrue(columns.next());
		Assert.assertEquals("message", columns.getString(4));
		Assert.assertEquals(DatabaseMetaData.procedureColumnOut, columns.getShort(5));
		Assert.assertEquals("4", columns.getString("ORDINAL_POSITION"));
		Assert.assertFalse(columns.next());
	}

	@Test
	public void testGetProcedureColumnsColumnPatternAndCase() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", "in_name,out_size|integer,out_status\n,1,ok");
		ResultSet columns = metaData.getProcedureColumns(null, null, "SPSAVE", "s%");

		Assert.assertTrue(columns.next());
		Assert.assertEquals("size", columns.getString("COLUMN_NAME"));
		Assert.assertTrue(columns.next());
		Assert.assertEquals("status", columns.getString("COLUMN_NAME"));
		Assert.assertFalse(columns.next());
	}

	@Test
	public void testGetProcedureColumnsWithSchemaAndOccurrence() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("dbo.spA", "in_a,out_b\n,1");
		MockJdbcDriver.addInMemoryTableResource("spB#1", "in_x,out_y\n,1");

		ResultSet withSchema = metaData.getProcedureColumns(null, "dbo", "spA", null);
		ResultSet byOccurrence = metaData.getProcedureColumns(null, "dbo", "spB", null);

		Assert.assertTrue(withSchema.next());
		Assert.assertEquals("a", withSchema.getString("COLUMN_NAME"));
		Assert.assertTrue(byOccurrence.next());
		Assert.assertEquals("x", byOccurrence.getString("COLUMN_NAME"));
	}

	@Test
	public void testGetProcedureColumnsIsEmptyWithoutASignature() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spList", "id,name\n1,a");

		Assert.assertFalse(metaData.getProcedureColumns(null, null, "spList", null).next());
		Assert.assertFalse(metaData.getProcedureColumns(null, null, "missing", null).next());
		Assert.assertFalse(metaData.getProcedureColumns(null, null, "%", null).next());
	}
}
