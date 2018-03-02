package com.googlecode.dummyjdbc.resultset.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;

import com.googlecode.dummyjdbc.resultset.DummyResultSet;
import com.googlecode.dummyjdbc.resultset.DummyResultSetMetaData;

/**
 * The {@link CSVResultSet} which iterates over the CSV file data.
 *
 * @author Kai Winter
 */
public class CSVResultSet extends DummyResultSet {

	/**
	 * The date format for parsing a date from a CSV file.
	 */
	private static final String DATE_FORMAT = "dd-MMM-yy";

	private static final ThreadLocal<DateFormat> THREAD_LOCAL_DATEFORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat(DATE_FORMAT);
		}
	};

	/** Table schema */
    private DummyResultSetMetaData metaData;

	/** Column name 2 column value. */
	private Collection<LinkedHashMap<String, String>> dummyData;

	/** Iterator of dummyData. */
	private Iterator<LinkedHashMap<String, String>> resultIterator;

	/** The current value of the iterator. */
	private LinkedHashMap<String, String> currentEntry;

	private final String tableName;

	/**
	 * Constructs a new {@link CSVResultSet}.
	 *
	 * @param tableName
	 *            the name of the table this {@link CSVResultSet} stands for.
	 *
     * @param metaData
     *            the table schema.
	 *
	 * @param entries
	 *            Collection of entries from the CSV file. Each {@link LinkedHashMap} maps column name to column value.
	 */
	public CSVResultSet(String tableName, DummyResultSetMetaData metaData, Collection<LinkedHashMap<String, String>> entries) {
		this.tableName = tableName;
		this.metaData = metaData;
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
	public ResultSetMetaData getMetaData() throws SQLException {
		return this.metaData;
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex, String.class);

		return value;
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex, Boolean.class);

		return Boolean.valueOf(value);
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex, Integer.class);

		return Integer.valueOf(value);
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex, BigDecimal.class);

		if (value.isEmpty()) {
			return BigDecimal.valueOf(0);
		}
		return new BigDecimal(value);
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, BigDecimal.class);

		if (string.isEmpty()) {
			return BigDecimal.valueOf(0);
		}
		return new BigDecimal(string);
	}

	@Override
	public String getString(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, String.class);
		return string;
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, Boolean.class);

		return Boolean.valueOf(string);
	}

	@Override
	public int getInt(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, Integer.class);

		return Integer.valueOf(string);
	}

	@Override
	public Date getDate(int columnIndex) throws SQLException {
		String string = getValueForColumnIndex(columnIndex, Date.class);

		return parseDate(string);
	}

	@Override
	public Date getDate(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, Date.class);

		return parseDate(string);
	}

	private Date parseDate(String string) throws SQLException {
		DateFormat sdf = THREAD_LOCAL_DATEFORMAT.get();
		Date date = null;
		try {
			java.util.Date utilDate = sdf.parse(string);
			date = new Date(utilDate.getTime());

		} catch (ParseException e) {
			String message = MessageFormat.format("Could not parse date: ''{0}'' using format ''{1}''", string,
					DATE_FORMAT);
			throw new SQLException(message, e);
		}
		return date;
	}

	private String getValueForColumnIndex(int columnIndex, Class<?> clazz) throws SQLException {
		String[] columns = currentEntry.keySet().toArray(new String[0]);

		if (columnIndex > columns.length) {
			String message = MessageFormat.format(
					"Column index {0} does not exist in table file ''{1}'' (type ''{2}'')", columnIndex, tableName,
					clazz);
			throw new SQLException(message);
		}

		String key = columns[columnIndex - 1];
		String value = currentEntry.get(key);
		return value;
	}

	private String getValueForColumnLabel(String columnLabel, Class<?> clazz) throws SQLException {
		if (!currentEntry.containsKey(columnLabel.toUpperCase())) {
			String message = MessageFormat.format("Column ''{0}'' does not exist in table file ''{1}'' (type ''{2}'')",
					columnLabel, tableName, clazz);
			throw new SQLException(message);
		}

		return currentEntry.get(columnLabel.toUpperCase());
	}

}
