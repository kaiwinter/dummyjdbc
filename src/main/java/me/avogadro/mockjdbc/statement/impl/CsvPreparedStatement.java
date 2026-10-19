package me.avogadro.mockjdbc.statement.impl;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.avogadro.mockjdbc.MockJdbcDriver;
import me.avogadro.mockjdbc.boomi.BoomiExecutionUtil;
import me.avogadro.mockjdbc.statement.PreparedStatementAdapter;

/**
 * Wraps the {@link CsvStatement} as a prepared statement.
 *
 * @author Kai Winter
 */
public class CsvPreparedStatement extends PreparedStatementAdapter {

	/** INSERT INTO table ( ... possibly on several lines, optionally after a comment line */
	private static final Pattern INSERT_INTO_PATTERN = Pattern.compile("(?:--[^\\n]*)?\\s*insert\\s*into\\s*([a-zA-Z]*)\\s*\\(.*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

	/** UPDATE table ... possibly on several lines, optionally after a comment line; the name must be followed by whitespace */
	private static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile("(?:--[^\\n]*)?\\s*update\\s+([a-zA-Z]+)(?:\\s.*)?", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

	/** DELETE [FROM] table ... possibly on several lines, optionally after a comment line */
	private static final Pattern DELETE_TABLE_PATTERN = Pattern.compile("(?:--[^\\n]*)?\\s*delete\\s+(?:from\\s+)?([a-zA-Z]+)(?:\\s.*)?", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

	/** SELECT statement, after optional comment lines: its parameters are not captured */
	private static final Pattern SELECT_PATTERN = Pattern.compile("(?:\\s*--[^\\n]*\\n)*[\\s(]*select\\b.*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);

	/**
	 * Suffix used for storing the parameters last seen when running a query
	 */
	public static final String PARAMS_SUFFIX = "_PARAMS";
	
	static final int MAX_PARAMS = 500;
	
	/**
	 * Params received during the insert/update operation
	 */
	Object[] params = new Object[MAX_PARAMS];
	
    /**
     * Target table for insert/update operations
     */
    private String targetTable4Updates = null;
    
	private final CsvStatement statement;
	private final String sql;

	private ResultSet currentResultSet;

	/** true while {@link #execute(String)} runs: the capture reuses the occurrence number of its query */
	private boolean reuseQueryOccurrence = false;

	/** copies of the parameters added with {@link #addBatch()} */
	private final List<Object[]> batch = new ArrayList<Object[]>();
	
	/**
	 * Constructs a new {@link CsvPreparedStatement}.
	 *
	 * @param tableResources {@link Map} of table name to CSV file.
	 * @param sql
	 *            the SQL statement.
	 */
	public CsvPreparedStatement(Map<String, File> tableResources, String sql) {
		this.statement = new CsvStatement(tableResources);
		this.sql = sql;
	}

	
	/**
	 * Always reply that 1 row was affected (most common case) if we can identify the target table name
	 * otherwise return 0.
	 * The parameters are captured as <code>&lt;name&gt;_PARAMS</code> for every statement except SELECTs
	 * (INSERT, UPDATE, DELETE, stored procedure calls, statements with a QUERY_ID or TESTCASE comment or a step resource).
	 */
	@Override
	public int executeUpdate(String sql) throws SQLException {

		targetTable4Updates = null;
		final boolean isSelect = SELECT_PATTERN.matcher(sql).matches();
		boolean isStep = false;
		if (!reuseQueryOccurrence) {
			MockJdbcDriver.beforeExecution();
		}
		try {
			int res = 1;
			String stepRes = MockJdbcDriver.getInMemoryTableResourceForCurrentStep();
			
			if (stepRes!=null) {
				targetTable4Updates = MockJdbcDriver.getStepName();
				isStep = true;
				try {
					return Integer.parseInt(stepRes);
				} catch (NumberFormatException nfe) {
					// that's ok, it's a simple resultset rather then an update query
					return 0;
				}
			}

			// Try to check for a special heading comment within SQL
			String queryId = CsvStatement.matchQueryId(sql);
			if (queryId != null) {
				targetTable4Updates = queryId;
				return res;
			}
			
			// Try to interpret SQL as a SELECT on a table
			Matcher insertMatcher = INSERT_INTO_PATTERN.matcher(sql);
			if (insertMatcher.matches()) {
				targetTable4Updates = insertMatcher.group(1);
				return res;
			}
	
			// Try to interpret SQL as call of a stored procedure
			Matcher updateMatcher = UPDATE_TABLE_PATTERN.matcher(sql);
			if (updateMatcher.matches()) {
				targetTable4Updates = updateMatcher.group(1);
				return res;
			}

			Matcher deleteMatcher = DELETE_TABLE_PATTERN.matcher(sql);
			if (deleteMatcher.matches()) {
				targetTable4Updates = deleteMatcher.group(1);
				return res;
			}

			String procedure = CsvStatement.matchStoredProcedureCall(sql);
			if (procedure != null) {
				targetTable4Updates = procedure;
				return res;
			}

			return 0;
		
		} finally {
			// the parameters of a SELECT are not captured: they only select data
			if (targetTable4Updates!=null && !isSelect) {
				String paramsResource = targetTable4Updates+PARAMS_SUFFIX;
				String paramsValue = buildParamsString();
				// <name>_PARAMS always holds the latest parameters (the common single-write test)
				MockJdbcDriver.addInMemoryTableResource( paramsResource, paramsValue );
				// inside Boomi also expose them as Dynamic Process Properties (e.g. mockjdbc_users_PARAMS and mockjdbc_users_params)
				BoomiExecutionUtil.setResourceProperty( paramsResource, paramsValue );
				// <name>_PARAMS#n keeps the parameters of the n-th use of the name (steps are not numbered)
				if (!isStep) {
					String occurrenceResource = paramsResource + MockJdbcDriver.OCCURRENCE_SEPARATOR + captureOccurrence();
					MockJdbcDriver.addInMemoryTableResource( occurrenceResource, paramsValue );
					BoomiExecutionUtil.setResourceProperty( occurrenceResource, paramsValue );
				}
			}
			
			params = new Object[MAX_PARAMS];
			
			
		}

	}

	/**
	 * Occurrence number of the captured statement: the one of the query run by the same {@link #execute(String)} on the
	 * same name, otherwise one more occurrence of the name.
	 */
	private int captureOccurrence() {
		if (reuseQueryOccurrence && statement.lastOccurrenceName != null
				&& statement.lastOccurrenceName.equalsIgnoreCase(targetTable4Updates)) {
			return statement.lastOccurrence;
		}
		return MockJdbcDriver.nextOccurrence(targetTable4Updates);
	}

	/**
	 * build a string representing the objects which have been received
	 * @return
	 */
	String buildParamsString() {
		// 1: build the string (space separated)
		StringBuilder s = new StringBuilder();
		for (int i = 1; i < params.length; i++) { // SQL param index starts from 1 !!
			if (i>1)
				s.append(",");
			if (params[i]!=null) {
				if (params[i] instanceof java.util.Date) {
					if (params[i] instanceof java.sql.Date) {
						s.append(MockJdbcDriver.THREAD_LOCAL_DATEFORMAT.get().format((java.util.Date)params[i]));
					} else if (params[i] instanceof java.sql.Time) {
						s.append(MockJdbcDriver.THREAD_LOCAL_TIMEFORMAT.get().format((java.util.Date)params[i]));
					} else {
						s.append(MockJdbcDriver.THREAD_LOCAL_TIMESTAMPFORMAT.get().format((java.util.Date)params[i]));
					}
				} else {
					s.append(params[i]);									
				}
			}
		}
		
		// 2: trim and replace tailing commas
		String res = s.toString().trim();
		res = res.replaceAll(",*$", "");
		
		return res;
	}
	
	@Override
	public void setObject(int parameterIndex, Object x) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		params[parameterIndex]=x;
	}

	/** a NULL parameter is captured as an empty value */
	@Override
	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		params[parameterIndex]=null;
	}

	@Override
	public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
		params[parameterIndex]=null;
	}

	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		params[parameterIndex]=x;
	}
	
	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		params[parameterIndex]=x;
	}

	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		params[parameterIndex]=x;
	}
	
	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		params[parameterIndex]=x;
	}
	
	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		params[parameterIndex]=x;
	}
	
	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		params[parameterIndex]=x;
	}
	
	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		params[parameterIndex]=x;
	}
	
	@Override
	public int executeUpdate() throws SQLException {
		return executeUpdate(sql);
	}
	
	@Override
	public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
		return executeUpdate(sql);
	}
	
	@Override
	public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
		return executeUpdate(sql);
	}
	
	@Override
	public int executeUpdate(String sql, String[] columnNames) throws SQLException {
		return executeUpdate(sql);
	}
	
	
	@Override
	public ResultSet executeQuery() throws SQLException {
		execute(sql);
		return currentResultSet;
	}

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		execute(sql);
		return currentResultSet;
	}

	@Override
	public boolean execute() throws SQLException {
		return execute(sql);
	}

	@Override
	public boolean execute(String sql) throws SQLException {
		try {
			statement.paramsString = buildParamsString();
			currentResultSet = statement.executeQuery(sql);
			// try up update (generate params), counted as the same use of the name as the query
			reuseQueryOccurrence = true;
			executeUpdate(sql);
			return true;
		} finally {
			reuseQueryOccurrence = false;
			statement.paramsString = null;		
		}
	}

	@Override
	public boolean execute(String sql, int[] columnIndexes) throws SQLException {
		return execute(sql);
	}

	@Override
	public boolean execute(String sql, String[] columnNames) throws SQLException {
		return execute(sql);
	}

	@Override
	public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
		return execute(sql);
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return currentResultSet;
	}

	@Override
	public void setMaxRows(int max) throws SQLException {
		statement.setMaxRows(max);
	}

	@Override
	public int getMaxRows() throws SQLException {
		return statement.getMaxRows();
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		return statement.getGeneratedKeys();
	}

	/**
	 * Keeps a copy of the current parameters; {@link #executeBatch()} runs the statement once per copy.
	 */
	@Override
	public void addBatch() throws SQLException {
		batch.add(params.clone());
	}

	@Override
	public void clearBatch() throws SQLException {
		batch.clear();
	}

	/**
	 * Runs {@link #executeUpdate()} once for every set of parameters added with {@link #addBatch()} (each run captures
	 * its parameters as usual).
	 */
	@Override
	public int[] executeBatch() throws SQLException {
		int[] results = new int[batch.size()];
		try {
			for (int i = 0; i < results.length; i++) {
				params = batch.get(i).clone();
				results[i] = executeUpdate(sql);
			}
		} finally {
			batch.clear();
		}
		return results;
	}

	@Override
	public void clearParameters() throws SQLException {
		params = new Object[MAX_PARAMS];
	}
	

}
