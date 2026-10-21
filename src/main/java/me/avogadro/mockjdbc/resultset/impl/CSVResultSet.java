package me.avogadro.mockjdbc.resultset.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import me.avogadro.mockjdbc.MockJdbcDriver;
import me.avogadro.mockjdbc.resultset.MockResultSet;
import me.avogadro.mockjdbc.resultset.MockResultSetMetaData;

/**
 * The {@link CSVResultSet} which iterates over the CSV file data.
 *
 * @author Kai Winter
 */
public class CSVResultSet extends MockResultSet {



	/** Table schema */
    private MockResultSetMetaData metaData;

	/** Column name 2 column value. */
	private Collection<LinkedHashMap<String, String>> mockData;

	/** Iterator of mockData. */
	private Iterator<LinkedHashMap<String, String>> resultIterator;

	/** The current value of the iterator. */
	private LinkedHashMap<String, String> currentEntry;

	/** Number of the current row (1 = first), 0 before the first row and after the last one */
	private int row = 0;

	/** Maximum number of rows returned, 0 = no limit (see {@link java.sql.Statement#setMaxRows(int)}) */
	private int maxRows = 0;

	/** true once {@link #next()} returned false */
	private boolean afterLast = false;

	/** true if the last value read was SQL NULL (see {@link #wasNull()}) */
	private boolean lastWasNull = false;

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
	public CSVResultSet(String tableName, MockResultSetMetaData metaData, Collection<LinkedHashMap<String, String>> entries) {
		this.tableName = tableName;
		this.metaData = metaData;
		this.mockData = entries;
		this.resultIterator = mockData.iterator();
	}

	/**
	 * An empty result set with the given columns.
	 *
	 * @param name name of the result set (used as table name)
	 * @param columns the column names
	 * @return a result set without rows
	 */
	public static CSVResultSet empty(String name, String... columns) {
		return new CSVResultSet(name, new MockResultSetMetaData(name, columns),
				Collections.<LinkedHashMap<String, String>>emptyList());
	}

	/**
	 * @param maxRows maximum number of rows returned by {@link #next()}, 0 = no limit
	 */
	public void setMaxRows(int maxRows) {
		this.maxRows = maxRows;
	}

	@Override
	public boolean next() throws SQLException {
		if (!afterLast && resultIterator.hasNext() && (maxRows <= 0 || row < maxRows)) {
			currentEntry = resultIterator.next();
			row++;
			return true;
		}

		afterLast = true;
		row = 0;
		return false;
	}

	/**
	 * @return the 1-based index of the column with the given name (case insensitive, without the "|type" part)
	 */
	@Override
	public int findColumn(String columnLabel) throws SQLException {
		if (metaData != null && columnLabel != null) {
			for (int i = 1; i <= metaData.getColumnCount(); i++) {
				if (metaData.getColumnName(i).equalsIgnoreCase(columnLabel.trim())) {
					return i;
				}
			}
		}
		throw new SQLException(MessageFormat.format("Column ''{0}'' does not exist in table file ''{1}''", columnLabel,
				tableName));
	}

	@Override
	public int getRow() throws SQLException {
		return row;
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

		return value == null ? 0 : Integer.valueOf(value);
	}

	@Override
	public long getLong(int columnIndex) throws SQLException {
		String value = numberText(getValueForColumnIndex(columnIndex, Long.class));
		return value.isEmpty() ? 0L : Long.parseLong(value);
	}

	@Override
	public long getLong(String columnLabel) throws SQLException {
		String value = numberText(getValueForColumnLabel(columnLabel, Long.class));
		return value.isEmpty() ? 0L : Long.parseLong(value);
	}

	@Override
	public short getShort(int columnIndex) throws SQLException {
		String value = numberText(getValueForColumnIndex(columnIndex, Short.class));
		return value.isEmpty() ? 0 : Short.parseShort(value);
	}

	@Override
	public short getShort(String columnLabel) throws SQLException {
		String value = numberText(getValueForColumnLabel(columnLabel, Short.class));
		return value.isEmpty() ? 0 : Short.parseShort(value);
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException {
		String value = numberText(getValueForColumnIndex(columnIndex, Byte.class));
		return value.isEmpty() ? 0 : Byte.parseByte(value);
	}

	@Override
	public byte getByte(String columnLabel) throws SQLException {
		String value = numberText(getValueForColumnLabel(columnLabel, Byte.class));
		return value.isEmpty() ? 0 : Byte.parseByte(value);
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException {
		String value = numberText(getValueForColumnIndex(columnIndex, Double.class));
		return value.isEmpty() ? 0d : Double.parseDouble(value);
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException {
		String value = numberText(getValueForColumnLabel(columnLabel, Double.class));
		return value.isEmpty() ? 0d : Double.parseDouble(value);
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException {
		String value = numberText(getValueForColumnIndex(columnIndex, Float.class));
		return value.isEmpty() ? 0f : Float.parseFloat(value);
	}

	@Override
	public float getFloat(String columnLabel) throws SQLException {
		String value = numberText(getValueForColumnLabel(columnLabel, Float.class));
		return value.isEmpty() ? 0f : Float.parseFloat(value);
	}

	/** empty text is read as 0 by the numeric getters added for the Boomi Database V2 connector, like getBigDecimal */
	private static String numberText(String value) {
		return value == null ? "" : value.trim();
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		String value = getValueForColumnIndex(columnIndex, BigDecimal.class);

		if (value == null) {
			return null;
		}
		if (value.isEmpty()) {
			return BigDecimal.valueOf(0);
		}
		return new BigDecimal(value);
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, BigDecimal.class);

		if (string == null) {
			return null;
		}
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

		return string == null ? 0 : Integer.valueOf(string);
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
	
	@Override
	public Time getTime(int columnIndex) throws SQLException {
		String string = getValueForColumnIndex(columnIndex, Time.class);

		return parseTime(string);
	}
	
	@Override
	public Time getTime(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, Time.class);

		return parseTime(string);
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException {
		String string = getValueForColumnIndex(columnIndex, Date.class);

		return parseTimestamp(string);
	}
	
	@Override
	public Timestamp getTimestamp(String columnLabel) throws SQLException {
		String string = getValueForColumnLabel(columnLabel, Timestamp.class);

		return parseTimestamp(string);
	}

	private Date parseDate(String string) throws SQLException {
		if (string == null) {
			return null;
		}
		DateFormat sdf = MockJdbcDriver.THREAD_LOCAL_DATEFORMAT.get();
		Date date = null;
		try {
			java.util.Date utilDate = sdf.parse(string);
			date = new Date(utilDate.getTime());

		} catch (ParseException e) {
			String message = MessageFormat.format("Could not parse date: ''{0}'' using format ''{1}''", string,
					sdf.toString());
			throw new SQLException(message, e);
		}
		return date;
	}
	
	private Time parseTime(String string) throws SQLException {
		if (string == null) {
			return null;
		}
		DateFormat sdf = MockJdbcDriver.THREAD_LOCAL_TIMEFORMAT.get();
		Time date = null;
		try {
			java.util.Date utilDate = sdf.parse(string);
			date = new Time(utilDate.getTime());

		} catch (ParseException e) {
			String message = MessageFormat.format("Could not parse date: ''{0}'' using format ''{1}''", string,
					sdf.toString());
			throw new SQLException(message, e);
		}
		return date;
	}
	
	private Timestamp parseTimestamp(String string) throws SQLException {
		if (string == null) {
			return null;
		}
		DateFormat sdf = MockJdbcDriver.THREAD_LOCAL_TIMESTAMPFORMAT.get();
		Timestamp date = null;
		try {
			java.util.Date utilDate = sdf.parse(string);
			date = new Timestamp(utilDate.getTime());

		} catch (ParseException e) {
			String message = MessageFormat.format("Could not parse date: ''{0}'' using format ''{1}''", string,
					sdf.toString());
			throw new SQLException(message, e);
		}
		return date;
	}

	/**
	 * Value of a column of the current row, applying the NULL convention: an empty value of a column whose declared
	 * type is not VARCHAR (e.g. <code>id|integer</code>) is SQL NULL and is returned as <code>null</code>.
	 */
	private String getValueForColumnIndex(int columnIndex, Class<?> clazz) throws SQLException {
		return nullConvention(columnIndex, rawValueForColumnIndex(columnIndex, clazz));
	}

	private String getValueForColumnLabel(String columnLabel, Class<?> clazz) throws SQLException {
		return nullConvention(columnIndexOf(columnLabel), rawValueForColumnLabel(columnLabel, clazz));
	}

	private String nullConvention(int columnIndex, String value) throws SQLException {
		lastWasNull = value == null || (value.isEmpty() && columnType(columnIndex) != Types.VARCHAR);
		return lastWasNull ? null : value;
	}

	/** declared type of a column (VARCHAR when unknown) */
	private int columnType(int columnIndex) throws SQLException {
		if (metaData == null || columnIndex < 1 || columnIndex > metaData.getColumnCount()) {
			return Types.VARCHAR;
		}
		return metaData.getColumnType(columnIndex);
	}

	/** 1-based index of a column label (also "name|type"), or -1 */
	private int columnIndexOf(String columnLabel) throws SQLException {
		if (metaData == null || columnLabel == null) {
			return -1;
		}
		String name = columnLabel;
		int typeSeparator = name.indexOf('|');
		if (typeSeparator >= 0) {
			name = name.substring(0, typeSeparator);
		}
		name = name.trim();
		for (int i = 1; i <= metaData.getColumnCount(); i++) {
			if (metaData.getColumnName(i).equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean wasNull() throws SQLException {
		return lastWasNull;
	}

	/**
	 * The value converted to the Java type of the column declared in the header (<code>String</code> when no type is
	 * declared); <code>null</code> for SQL NULL.
	 */
	@Override
	public Object getObject(int columnIndex) throws SQLException {
		return toObject(columnIndex, getValueForColumnIndex(columnIndex, Object.class));
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException {
		return toObject(columnIndexOf(columnLabel), getValueForColumnLabel(columnLabel, Object.class));
	}

	@Override
	public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
		return convert(getObject(columnIndex), type);
	}

	@Override
	public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
		return convert(getObject(columnLabel), type);
	}

	private static <T> T convert(Object value, Class<T> type) throws SQLException {
		if (value == null || type.isInstance(value)) {
			return type.cast(value);
		}
		if (type == String.class) {
			return type.cast(value.toString());
		}
		throw new SQLException("Cannot convert " + value.getClass().getName() + " to " + type.getName());
	}

	private Object toObject(int columnIndex, String value) throws SQLException {
		if (value == null) {
			return null;
		}
		switch (columnType(columnIndex)) {
		case Types.INTEGER:
			return Integer.valueOf(value.trim());
		case Types.BIGINT:
			return Long.valueOf(value.trim());
		case Types.DOUBLE:
			return Double.valueOf(value.trim());
		case Types.DECIMAL:
			return new BigDecimal(value.trim());
		case Types.BOOLEAN:
			return Boolean.valueOf(value.trim());
		case Types.DATE:
			return parseDate(value);
		case Types.TIME:
			return parseTime(value);
		case Types.TIMESTAMP:
			return parseTimestamp(value);
		default:
			return value;
		}
	}

	private String rawValueForColumnIndex(int columnIndex, Class<?> clazz) throws SQLException {
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

	private String rawValueForColumnLabel(String columnLabel, Class<?> clazz) throws SQLException {
		String key = columnLabel.toUpperCase(Locale.ROOT);

		// 1: exact match of the header as written (e.g. "ID|INTEGER" or "ID")
		if (currentEntry.containsKey(key)) {
			return currentEntry.get(key);
		}

		// 2: fallback for typed headers ("name|type"): match on the bare column name
		for (Map.Entry<String, String> entry : currentEntry.entrySet()) {
			String header = entry.getKey();
			int typeSeparator = header.indexOf('|');
			if (typeSeparator >= 0 && header.substring(0, typeSeparator).trim().equals(key.trim())) {
				return entry.getValue();
			}
		}

		String message = MessageFormat.format("Column ''{0}'' does not exist in table file ''{1}'' (type ''{2}'')",
				columnLabel, tableName, clazz);
		throw new SQLException(message);
	}

}
