package com.google.dummyjdbc.connection.impl;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import com.google.dummyjdbc.connection.ConnectionAdapter;
import com.google.dummyjdbc.statement.impl.CsvPreparedStatement;
import com.google.dummyjdbc.statement.impl.CsvStatement;

/**
 * Connection which implements the methods {@link #createStatement()} and {@link #prepareStatement(String)}. The
 * {@link DummyConnection} tries to open a CSV file in the directory <code>./table/</code> with the name
 * <code>&lt;tablename&gt;.csv</code> and returns the contained values. The query result will contain all values from
 * the file, it will not be narrowed by the query. The first line of the CSV file has to contain the column names.
 * 
 * @author Kai Winter
 */
public class DummyConnection extends ConnectionAdapter {

	@Override
	public Statement createStatement() throws SQLException {
		return new CsvStatement();
	}

	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		return new CsvPreparedStatement(sql);
	};
}
