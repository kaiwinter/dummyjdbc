package com.google.dummyjdbc.resultset.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;

import com.google.dummyjdbc.resultset.DummyResultSet;

/**
 * The {@link CSVResultSet} which iterates over the CSV file data.
 * 
 * @author Kai Winter
 */
public class CSVResultSet extends DummyResultSet {

	/** Column name 2 column value. */
	private Collection<LinkedHashMap<String, String>> dummyData;

	/** Iterator of dummyData. */
	private Iterator<LinkedHashMap<String, String>> resultIterator;

	/** The current value of the iterator. */
	private LinkedHashMap<String, String> currentEntry;

	/**
	 * Constructs a new {@link CSVResultSet}.
	 * 
	 * @param entries
	 *            Collection of entries from the CSV file. Each {@link LinkedHashMap} maps column name to column value.
	 */
	public CSVResultSet(Collection<LinkedHashMap<String, String>> entries) {
		this.dummyData = entries;
		this.resultIterator = dummyData.iterator();
	}

	@Override
	public boolean next() throws SQLException {
		if (resultIterator.hasNext()) {
			currentEntry = resultIterator.next();
			return true;
		}

		return false;
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex);

		return value;
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex);

		return Boolean.valueOf(value);
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex);

		return Integer.valueOf(value);
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex);

		if (value.isEmpty()) {
			return BigDecimal.valueOf(0);
		}
		return new BigDecimal(value);
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel);

		if (string.isEmpty()) {
			return BigDecimal.valueOf(0);
		}
		return new BigDecimal(string);
	}

	@Override
	public String getString(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel);
		return string;
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel);

		return Boolean.valueOf(string);
	}

	@Override
	public int getInt(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel);

		return Integer.valueOf(string);
	}

	@Override
	public Date getDate(String columnLabel) throws SQLException {
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy");
		String string = getValueForColumnLabel(columnLabel);

		Date date = null;
		try {
			java.util.Date utilDate = sdf.parse(string);
			date = new Date(utilDate.getTime());

		} catch (ParseException e) {
			System.err.println("Could not parse date: " + string);
		}
		return date;
	}

	private String getValueForColumnIndex(int columnIndex) throws SQLException {
		String[] columns = currentEntry.keySet().toArray(new String[0]);

		if (columnIndex > columns.length) {
			throw new SQLException("Column " + columnIndex + " does not exist.");
		}

		String key = columns[columnIndex - 1];
		String value = currentEntry.get(key);
		return value;
	}

	private String getValueForColumnLabel(String columnLabel) throws SQLException {
		if (!currentEntry.containsKey(columnLabel.toUpperCase())) {
			throw new SQLException(MessageFormat.format("Column {0} does not exist.", columnLabel));
		}

		return currentEntry.get(columnLabel.toUpperCase());
	}

}
