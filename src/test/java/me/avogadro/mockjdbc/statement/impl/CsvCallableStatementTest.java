package me.avogadro.mockjdbc.statement.impl;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

/**
 * Stored procedure calls through {@link Connection#prepareCall(String)}: OUT parameters declared by the header of the
 * resource (in_, out_, inout_, ret_ columns), result sets, captured IN parameters.
 */
public final class CsvCallableStatementTest {

	/** spSave(in name, in value, out size, out status, out message) */
	private static final String SP_SAVE = "in_name,in_value|integer,out_size|integer,out_status,out_message\n"
			+ ",,42,OK,Saved";

	private Connection connection;

	@Before
	public void setup() throws ClassNotFoundException, SQLException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
		connection = DriverManager.getConnection("any");
	}

	private CallableStatement callSpSave() throws SQLException {
		CallableStatement call = connection.prepareCall("{ call spSave(?,?,?,?,?) }");
		call.setString(1, "mario");
		call.setInt(2, 5);
		call.registerOutParameter(3, Types.INTEGER);
		call.registerOutParameter(4, Types.VARCHAR);
		call.registerOutParameter(5, Types.VARCHAR);
		return call;
	}

	@Test
	public void testPrepareCallReturnsACallableStatement() throws SQLException {
		Assert.assertNotNull(connection.prepareCall("{call p}"));
		Assert.assertNotNull(connection.prepareCall("{call p}", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
		Assert.assertNotNull(connection.prepareCall("{call p}", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
				ResultSet.CLOSE_CURSORS_AT_COMMIT));
	}

	@Test
	public void testOutParametersByIndex() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = callSpSave();

		Assert.assertFalse(call.execute());
		Assert.assertEquals(42, call.getInt(3));
		Assert.assertEquals("OK", call.getString(4));
		Assert.assertEquals("Saved", call.getString(5));
		Assert.assertFalse(call.wasNull());
	}

	@Test
	public void testOutParametersHaveNoResultSetNorUpdateCount() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = callSpSave();

		Assert.assertFalse(call.execute());
		Assert.assertNull(call.getResultSet());
		Assert.assertEquals(-1, call.getUpdateCount());
		Assert.assertFalse(call.getMoreResults());
	}

	@Test
	public void testInputParametersAreCapturedWithoutOutParameters() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		callSpSave().execute();

		Assert.assertEquals("mario,5", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS"));
		Assert.assertEquals("mario,5", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS#1"));
	}

	@Test
	public void testInputParametersAreCapturedWithoutAResource() throws SQLException {
		CallableStatement call = connection.prepareCall("{ call spWrite(?,?) }");
		call.setString(1, "a");
		call.setInt(2, 7);
		call.execute();

		Assert.assertEquals("a,7", MockJdbcDriver.getInMemoryTableResource("spWrite_PARAMS"));
	}

	@Test
	public void testInOutParameterIsCapturedAndReadBack() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spCounter", "in_id,inout_counter|integer\n,11");
		CallableStatement call = connection.prepareCall("{call spCounter(?, ?)}");
		call.setString(1, "x");
		call.setInt(2, 10);
		call.registerOutParameter(2, Types.INTEGER);
		call.execute();

		Assert.assertEquals(11, call.getInt(2));
		Assert.assertEquals("x,10", MockJdbcDriver.getInMemoryTableResource("spCounter_PARAMS"));
	}

	@Test
	public void testInValuesInTheResourceAreIgnored() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave",
				"in_name,in_value|integer,out_size|integer,out_status,out_message\nignored,99,42,OK,Saved");
		CallableStatement call = callSpSave();
		call.execute();

		Assert.assertEquals(42, call.getInt(3));
		Assert.assertEquals("mario,5", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS"));
	}

	@Test(expected = SQLException.class)
	public void testReadingAnInParameterFails() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = callSpSave();
		call.execute();

		call.getString(1);
	}

	@Test(expected = SQLException.class)
	public void testReadingAnUndeclaredParameterFails() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = callSpSave();
		call.execute();

		call.getString(6);
	}

	@Test(expected = SQLException.class)
	public void testReadingOutParametersOfAResultSetResourceFails() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spList", "id,name\n1,a");
		CallableStatement call = connection.prepareCall("{call spList(?)}");
		call.execute();

		call.getString(1);
	}

	@Test
	public void testReturnValueIsParameterOne() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spStatus", "ret_status|integer,in_id,out_message\n0,,done");
		CallableStatement call = connection.prepareCall("{ ? = call spStatus(?, ?) }");
		call.registerOutParameter(1, Types.INTEGER);
		call.setString(2, "id7");
		call.registerOutParameter(3, Types.VARCHAR);

		Assert.assertFalse(call.execute());
		Assert.assertEquals(0, call.getInt(1));
		Assert.assertFalse(call.wasNull());
		Assert.assertEquals("done", call.getString(3));
		Assert.assertEquals("id7", MockJdbcDriver.getInMemoryTableResource("spStatus_PARAMS"));
	}

	@Test
	public void testReturnColumnIsOptionalWithAReturnPlaceholder() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = connection.prepareCall("{ ? = call spSave(?,?,?,?,?) }");
		call.registerOutParameter(1, Types.INTEGER);
		call.setString(2, "mario");
		call.setInt(3, 5);
		call.registerOutParameter(4, Types.INTEGER);
		call.execute();

		Assert.assertEquals(42, call.getInt(4));
		Assert.assertEquals("Saved", call.getString(6));
		Assert.assertEquals("mario,5", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS"));
		try {
			call.getInt(1);
			Assert.fail("no ret_ column declared");
		} catch (SQLException e) {
			// expected
		}
	}

	@Test
	public void testReturnColumnIsSkippedWithoutAReturnPlaceholder() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spStatus", "ret_status|integer,in_id,out_message\n0,,done");
		CallableStatement call = connection.prepareCall("{call spStatus(?, ?)}");
		call.setString(1, "id7");
		call.registerOutParameter(2, Types.VARCHAR);
		call.execute();

		Assert.assertEquals("done", call.getString(2));
	}

	@Test
	public void testCallWithoutBracesAndExec() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = connection.prepareCall("call spSave(?,?,?,?,?)");
		call.setString(1, "mario");
		call.setNull(2, Types.VARCHAR);
		call.registerOutParameter(3, Types.INTEGER);
		call.execute();
		CallableStatement exec = connection.prepareCall("EXEC spSave ?, ?, ?, ?, ?");
		exec.setString(1, "luigi");
		exec.setInt(2, 1);
		exec.execute();

		Assert.assertEquals("42", call.getString(3));
		Assert.assertEquals("OK", exec.getString(4));
		Assert.assertEquals("mario", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS#1"));
		Assert.assertEquals("luigi,1", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS#2"));
	}

	@Test
	public void testOccurrencesGiveDifferentOutValues() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave#1", "in_name,out_status\n,FIRST");
		MockJdbcDriver.addInMemoryTableResource("spSave#2", "in_name,out_status\n,SECOND");
		CallableStatement first = connection.prepareCall("{call spSave(?, ?)}");
		first.setString(1, "a");
		first.execute();
		CallableStatement second = connection.prepareCall("{call spSave(?, ?)}");
		second.setString(1, "b");
		second.execute();

		Assert.assertEquals("FIRST", first.getString(2));
		Assert.assertEquals("SECOND", second.getString(2));
	}

	@Test
	public void testInputValuesSelectTheOutValues() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", "in_name,out_status\n,DEFAULT");
		MockJdbcDriver.addInMemoryTableResource("spSave?bad", "in_name,out_status\n,KO");
		CallableStatement call = connection.prepareCall("{call spSave(?, ?)}");
		call.setString(1, "bad");
		call.registerOutParameter(2, Types.VARCHAR);
		call.execute();

		Assert.assertEquals("KO", call.getString(2));
	}

	@Test
	public void testTypedEmptyValueIsNull() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave",
				"in_name,in_value|integer,out_size|integer,out_status,out_message\n,,,,");
		CallableStatement call = callSpSave();
		call.execute();

		Assert.assertEquals(0, call.getInt(3));
		Assert.assertTrue(call.wasNull());
		Assert.assertNull(call.getObject(3));
		Assert.assertEquals("", call.getString(4));
		Assert.assertFalse(call.wasNull());
	}

	@Test
	public void testHeaderWithoutRowsGivesNullOutValues() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", "in_name,out_status,out_size|integer");
		CallableStatement call = connection.prepareCall("{call spSave(?, ?, ?)}");
		call.setString(1, "a");

		Assert.assertFalse(call.execute());
		Assert.assertNull(call.getString(2));
		Assert.assertTrue(call.wasNull());
		Assert.assertEquals(0, call.getInt(3));
		Assert.assertTrue(call.wasNull());
	}

	@Test
	public void testGetObjectFollowsTheRegisteredType() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spTypes",
				"in_id,out_count,out_amount,out_when,out_text|integer,out_flag|boolean\n,7,12.50,20261021 101500.000,3,true");
		CallableStatement call = connection.prepareCall("{call spTypes(?,?,?,?,?,?)}");
		call.setString(1, "x");
		call.registerOutParameter(2, Types.INTEGER);
		call.registerOutParameter(3, Types.NUMERIC);
		call.registerOutParameter(4, Types.TIMESTAMP);
		call.registerOutParameter(5, Types.CHAR);
		call.execute();

		Assert.assertEquals(Integer.valueOf(7), call.getObject(2));
		Assert.assertEquals(new BigDecimal("12.50"), call.getObject(3));
		Assert.assertTrue(call.getObject(4) instanceof Timestamp);
		Assert.assertEquals("3", call.getObject(5));
		// not registered: the type declared in the header
		Assert.assertEquals(Boolean.TRUE, call.getObject(6));
		Assert.assertEquals(new BigDecimal("12.50"), call.getBigDecimal(3));
		Assert.assertEquals(12.5, call.getDouble(3), 0.0001);
	}

	@Test
	public void testParametersByName() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = connection.prepareCall("{ call spSave(?,?,?,?,?) }");
		call.setString("name", "mario");
		call.setInt("@value", 5);
		call.registerOutParameter("size", Types.INTEGER);
		call.execute();

		Assert.assertEquals(42, call.getInt("size"));
		Assert.assertEquals(42, call.getInt("out_size"));
		Assert.assertEquals("OK", call.getString("STATUS"));
		Assert.assertEquals("mario,5", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS"));
	}

	@Test
	public void testResultSetResource() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spList", "id,name\n1,a\n2,b");
		CallableStatement call = connection.prepareCall("{call spList(?)}");
		call.setString(1, "x");

		Assert.assertTrue(call.execute());
		ResultSet resultSet = call.getResultSet();
		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("a", resultSet.getString("name"));
		Assert.assertEquals(2, call.getMetaData().getColumnCount());
		Assert.assertEquals(-1, call.getUpdateCount());
		Assert.assertFalse(call.getMoreResults());
		Assert.assertNull(call.getResultSet());
		Assert.assertEquals("x", MockJdbcDriver.getInMemoryTableResource("spList_PARAMS"));
	}

	@Test
	public void testExecuteQueryOnAResultSetResource() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spList", "id,name\n1,a");
		ResultSet resultSet = connection.prepareCall("{call spList}").executeQuery();

		Assert.assertTrue(resultSet.next());
		Assert.assertEquals("1", resultSet.getString(1));
	}

	@Test
	public void testExecuteQueryOnASignatureGivesAnEmptyResultSet() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = callSpSave();

		Assert.assertFalse(call.executeQuery().next());
		Assert.assertEquals("OK", call.getString(4));
	}

	@Test
	public void testUpdateCountLoopEnds() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = callSpSave();
		boolean results = call.execute();
		int loops = 0;
		while (!results && call.getUpdateCount() > -1 && loops < 10) {
			results = call.getMoreResults();
			loops++;
		}

		Assert.assertEquals(0, loops);
	}

	@Test
	public void testBatchCapturesEveryCall() throws SQLException {
		CallableStatement call = connection.prepareCall("{ call spWrite(?,?) }");
		call.setString(1, "a");
		call.setInt(2, 1);
		call.addBatch();
		call.setString(1, "b");
		call.setInt(2, 2);
		call.addBatch();

		Assert.assertEquals(2, call.executeBatch().length);
		Assert.assertEquals("a,1", MockJdbcDriver.getInMemoryTableResource("spWrite_PARAMS#1"));
		Assert.assertEquals("b,2", MockJdbcDriver.getInMemoryTableResource("spWrite_PARAMS#2"));
	}

	@Test
	public void testOutputPositionAfterTheInputs() throws SQLException {
		// a single trailing ? for one OUT value, as a caller reading only the first output
		MockJdbcDriver.addInMemoryTableResource("spSave", SP_SAVE);
		CallableStatement call = connection.prepareCall("{call spSave(?,?,?)}");
		call.setString(1, "mario");
		call.setString(2, "5");
		call.registerOutParameter(3, Types.CHAR);
		call.setString(3, null);
		call.execute();

		Assert.assertEquals("42", call.getObject(3));
		Assert.assertEquals("mario,5", MockJdbcDriver.getInMemoryTableResource("spSave_PARAMS"));
	}

	@Test
	public void testPreparedStatementMetaDataAfterExecute() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("customers", "id,name|varchar\n1,a");
		java.sql.PreparedStatement statement = connection.prepareStatement("SELECT id, name FROM customers WHERE id = ?");

		Assert.assertNull(statement.getMetaData());
		statement.setInt(1, 1);
		Assert.assertTrue(statement.execute());
		Assert.assertEquals(2, statement.getMetaData().getColumnCount());
		Assert.assertEquals("name", statement.getMetaData().getColumnName(2));
	}

	@Test(expected = SQLException.class)
	public void testTwoReturnColumnsAreRejected() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spBad", "ret_a,ret_b\n1,2");
		connection.prepareCall("{? = call spBad}").execute();
	}

	@Test
	public void testColumnsWithoutPrefixMakeAResultSet() throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("spMixed", "out_size,name\n1,a");
		CallableStatement call = connection.prepareCall("{call spMixed}");

		Assert.assertTrue(call.execute());
		Assert.assertTrue(call.getResultSet().next());
	}
}
