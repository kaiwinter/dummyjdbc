package com.google.dummyjdbc.statement.impl;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.google.dummyjdbc.statement.PreparedStatementAdapter;

/**
 * Wraps the {@link CsvStatement} as a prepared statement.
 * 
 * @author Kai Winter
 */
public class CsvPreparedStatement extends PreparedStatementAdapter {

	private final CsvStatement statement;
	private final String sql;

	/**
	 * Constructs a new {@link CsvPreparedStatement}.
	 * 
	 * @param sql
	 *            the SQL statement.
	 */
	public CsvPreparedStatement(String sql) {
		this.statement = new CsvStatement();
		this.sql = sql;
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		return statement.executeQuery(sql);
	}

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		return statement.executeQuery(sql);
	}

}
