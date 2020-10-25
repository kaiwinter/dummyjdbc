package com.mindmercatis.dummyjdbc.statement.impl;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mindmercatis.dummyjdbc.DummyJdbcDriver;
import com.mindmercatis.dummyjdbc.statement.PreparedStatementAdapter;

/**
 * Wraps the {@link CsvStatement} as a prepared statement.
 *
 * @author Kai Winter
 */
public class CsvPreparedStatement extends PreparedStatementAdapter {

	private static final Pattern INSERT_INTO_PATTERN = Pattern.compile("(?:--[^\\n]*)?\\s*insert\\s*into\\s*([a-zA-Z]*)\\s*\\(.*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);

	private static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile("(?:--[^\\\\n]*)?\\s*update\\s*([a-zA-Z]*)\\s*", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);

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
	 * otherwise return 0
	 */
	@Override
	public int executeUpdate(String sql) throws SQLException {

		targetTable4Updates = null;
		try {
			int res = 1;
			String stepRes = DummyJdbcDriver.getInMemoryTableResourceForCurrentStep();
			
			if (stepRes!=null) {
				targetTable4Updates = DummyJdbcDriver.getStepName();
				try {
					return Integer.parseInt(stepRes);
				} catch (NumberFormatException nfe) {
					// that's ok, it's a simple resultset rather then an update query
					return 0;
				}
			}

			// Try to check for a special heading comment within SQL
			Matcher commentMatcher = CsvStatement.COMMENT_HEADLINE_PATTERN.matcher(sql);
			if (commentMatcher.matches()) {
				targetTable4Updates = commentMatcher.group(1);
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
	
			return 0;
		
		} finally {
			if (targetTable4Updates!=null)
				DummyJdbcDriver.addInMemoryTableResource( targetTable4Updates+PARAMS_SUFFIX, buildParamsString() );
			
			params = new Object[MAX_PARAMS];
			
			
		}

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
						s.append(DummyJdbcDriver.THREAD_LOCAL_DATEFORMAT.get().format((java.util.Date)params[i]));
					} else if (params[i] instanceof java.sql.Time) {
						s.append(DummyJdbcDriver.THREAD_LOCAL_TIMEFORMAT.get().format((java.util.Date)params[i]));
					} else {
						s.append(DummyJdbcDriver.THREAD_LOCAL_TIMESTAMPFORMAT.get().format((java.util.Date)params[i]));
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
			// try up update (generate params)
			executeUpdate(sql);
			return true;
		} finally {
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
	

}
