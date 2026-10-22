package me.avogadro.mockjdbc.statement.impl;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import me.avogadro.mockjdbc.resultset.impl.CSVResultSet;
import me.avogadro.mockjdbc.statement.impl.ProcedureSignature.Parameter;

/**
 * {@link CallableStatement} for stored procedure calls (<code>{call p(?,?)}</code>, <code>{? = call p(?)}</code>,
 * <code>call p(?)</code>, <code>EXEC p ?</code>). The resource named after the procedure is found as for
 * {@link CsvPreparedStatement} (occurrences, parameters, steps) and the IN parameters are captured as
 * <code>&lt;p&gt;_PARAMS</code>, OUT parameters left out.
 * <ul>
 * <li>When the header of the resource is a {@link ProcedureSignature} (every column starts with <code>in_</code>,
 * <code>out_</code>, <code>inout_</code> or <code>ret_</code>) its first row gives the values of the OUT
 * parameters: <code>execute()</code> returns <code>false</code> and there is no result set.</li>
 * <li>Otherwise the resource is the result set of the procedure, as for <code>EXEC p</code>.</li>
 * </ul>
 * There are never update counts (<code>getUpdateCount()</code> is -1) nor further result sets.
 */
public class CsvCallableStatement extends CsvPreparedStatement implements CallableStatement {

	/** the call has a return value placeholder: <code>{? = call ...}</code> */
	private static final Pattern RETURN_PLACEHOLDER_PATTERN = Pattern.compile(
			"(?:\\s*--[^\\n]*\\n)*\\s*\\{?\\s*\\?\\s*=.*", Pattern.DOTALL);

	private final Map<String, File> tableResources;
	/** name of the procedure in the SQL, or <code>null</code> */
	private final String procedureName;
	private final boolean returnPlaceholder;

	/** SQL type registered for each OUT parameter index */
	private final Map<Integer, Integer> outParameters = new HashMap<Integer, Integer>();

	/** signature of the last execution, or <code>null</code> (plain result set, or not executed) */
	private ProcedureSignature signature;
	/** the resource holding the OUT values, positioned on its first row */
	private CSVResultSet outValues;
	private boolean hasOutRow;
	private boolean lastWasNull;

	/**
	 * @param tableResources {@link Map} of table name to CSV file
	 * @param sql the SQL of the call
	 */
	public CsvCallableStatement(Map<String, File> tableResources, String sql) {
		super(tableResources, sql);
		this.tableResources = tableResources;
		this.procedureName = CsvStatement.matchStoredProcedureCall(sql);
		this.returnPlaceholder = RETURN_PLACEHOLDER_PATTERN.matcher(sql).matches();
	}

	/**
	 * Parameters of the call except the OUT ones (registered and without a value) and the return value placeholder.
	 */
	@Override
	String buildParamsString() {
		Object[] inputs = new Object[params.length];
		int count = 0;
		for (int i = 1; i < params.length; i++) {
			boolean returnValue = returnPlaceholder && i == 1;
			boolean outOnly = outParameters.containsKey(i) && params[i] == null;
			if (!returnValue && !outOnly) {
				inputs[++count] = params[i];
			}
		}
		return paramsString(inputs);
	}

	@Override
	public boolean execute(String sql) throws SQLException {
		signature = null;
		outValues = null;
		hasOutRow = false;
		super.execute(sql);
		ProcedureSignature declared = ProcedureSignature.of(currentResultSet);
		if (declared == null) {
			return true;
		}
		signature = declared;
		outValues = (CSVResultSet) currentResultSet;
		hasOutRow = outValues.next();
		currentResultSet = null;
		return false;
	}

	/** the result set of the procedure; an empty one when its resource declares OUT parameters */
	@Override
	public ResultSet executeQuery() throws SQLException {
		execute();
		return currentResultSet != null ? currentResultSet : CSVResultSet.empty(procedureName, "RESULT");
	}

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		execute(sql);
		return currentResultSet != null ? currentResultSet : CSVResultSet.empty(procedureName, "RESULT");
	}

	@Override
	public int executeUpdate() throws SQLException {
		execute();
		return 1;
	}

	@Override
	public int getUpdateCount() throws SQLException {
		return -1;
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		currentResultSet = null;
		return false;
	}

	@Override
	public boolean getMoreResults(int current) throws SQLException {
		return getMoreResults();
	}

	@Override
	public void clearParameters() throws SQLException {
		super.clearParameters();
		outParameters.clear();
	}

	// ---- OUT parameter registration

	@Override
	public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
		outParameters.put(parameterIndex, sqlType);
	}

	@Override
	public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
		registerOutParameter(parameterIndex, sqlType);
	}

	@Override
	public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
		registerOutParameter(parameterIndex, sqlType);
	}

	@Override
	public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
		registerOutParameter(indexOf(parameterName), sqlType);
	}

	@Override
	public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
		registerOutParameter(parameterName, sqlType);
	}

	@Override
	public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
		registerOutParameter(parameterName, sqlType);
	}

	// ---- reading OUT parameters

	@Override
	public boolean wasNull() throws SQLException {
		return lastWasNull;
	}

	/** the OUT parameter at a SQL index (the signature of the last execution) */
	private Parameter outParameter(int parameterIndex) throws SQLException {
		if (signature == null) {
			throw new SQLException("No OUT parameters for '" + procedureName + "': execute the call first, with a "
					+ "resource whose header declares them (in_, out_, inout_, ret_ columns)");
		}
		Parameter parameter = signature.atIndex(parameterIndex, returnPlaceholder);
		if (parameter == null) {
			throw new SQLException("Parameter " + parameterIndex + " of '" + procedureName
					+ "' is not declared in its resource");
		}
		if (!parameter.getMode().isOutput()) {
			throw new SQLException("Parameter " + parameterIndex + " of '" + procedureName + "' (" + parameter.getName()
					+ ") is not an OUT parameter");
		}
		return parameter;
	}

	/** column of the OUT values for a SQL index, or -1 when the resource has no data row (every value is NULL) */
	private int outColumn(int parameterIndex) throws SQLException {
		int column = outParameter(parameterIndex).getColumn();
		return hasOutRow ? column : -1;
	}

	/**
	 * @return the text of an OUT parameter, <code>null</code> for SQL NULL (an empty value in a typed column, or no
	 *         data row); sets {@link #wasNull()}
	 */
	private String text(int parameterIndex) throws SQLException {
		int column = outColumn(parameterIndex);
		String value = column < 0 ? null : outValues.getString(column);
		lastWasNull = value == null;
		return value;
	}

	/** like {@link #text(int)}, an empty value is also NULL (numbers, dates, booleans) */
	private boolean isNull(int parameterIndex) throws SQLException {
		String value = text(parameterIndex);
		lastWasNull = value == null || value.trim().isEmpty();
		return lastWasNull;
	}

	@Override
	public String getString(int parameterIndex) throws SQLException {
		return text(parameterIndex);
	}

	@Override
	public boolean getBoolean(int parameterIndex) throws SQLException {
		return !isNull(parameterIndex) && outValues.getBoolean(outColumn(parameterIndex));
	}

	@Override
	public byte getByte(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? 0 : outValues.getByte(outColumn(parameterIndex));
	}

	@Override
	public short getShort(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? 0 : outValues.getShort(outColumn(parameterIndex));
	}

	@Override
	public int getInt(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? 0 : Integer.parseInt(text(parameterIndex).trim());
	}

	@Override
	public long getLong(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? 0 : outValues.getLong(outColumn(parameterIndex));
	}

	@Override
	public float getFloat(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? 0 : outValues.getFloat(outColumn(parameterIndex));
	}

	@Override
	public double getDouble(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? 0 : outValues.getDouble(outColumn(parameterIndex));
	}

	@Override
	public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? null : outValues.getBigDecimal(outColumn(parameterIndex));
	}

	@Override
	@Deprecated
	public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
		BigDecimal value = getBigDecimal(parameterIndex);
		return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
	}

	@Override
	public byte[] getBytes(int parameterIndex) throws SQLException {
		String value = text(parameterIndex);
		return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public Date getDate(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? null : outValues.getDate(outColumn(parameterIndex));
	}

	@Override
	public Time getTime(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? null : outValues.getTime(outColumn(parameterIndex));
	}

	@Override
	public Timestamp getTimestamp(int parameterIndex) throws SQLException {
		return isNull(parameterIndex) ? null : outValues.getTimestamp(outColumn(parameterIndex));
	}

	@Override
	public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
		return getDate(parameterIndex);
	}

	@Override
	public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
		return getTime(parameterIndex);
	}

	@Override
	public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
		return getTimestamp(parameterIndex);
	}

	/**
	 * The value as the Java type of the SQL type registered with <code>registerOutParameter</code>, or of the column
	 * type declared in the header (<code>String</code> when none).
	 */
	@Override
	public Object getObject(int parameterIndex) throws SQLException {
		Integer registered = outParameters.get(parameterIndex);
		int sqlType = registered != null ? registered : outParameter(parameterIndex).getSqlType();
		switch (sqlType) {
		case Types.CHAR:
		case Types.VARCHAR:
		case Types.LONGVARCHAR:
		case Types.NCHAR:
		case Types.NVARCHAR:
		case Types.LONGNVARCHAR:
		case Types.CLOB:
		case Types.NCLOB:
			return getString(parameterIndex);
		case Types.TINYINT:
		case Types.SMALLINT:
		case Types.INTEGER:
			return isNull(parameterIndex) ? null : Integer.valueOf(getInt(parameterIndex));
		case Types.BIGINT:
			return isNull(parameterIndex) ? null : Long.valueOf(getLong(parameterIndex));
		case Types.REAL:
		case Types.FLOAT:
		case Types.DOUBLE:
			return isNull(parameterIndex) ? null : Double.valueOf(getDouble(parameterIndex));
		case Types.NUMERIC:
		case Types.DECIMAL:
			return getBigDecimal(parameterIndex);
		case Types.BIT:
		case Types.BOOLEAN:
			return isNull(parameterIndex) ? null : Boolean.valueOf(getBoolean(parameterIndex));
		case Types.DATE:
			return getDate(parameterIndex);
		case Types.TIME:
			return getTime(parameterIndex);
		case Types.TIMESTAMP:
			return getTimestamp(parameterIndex);
		default:
			int column = outColumn(parameterIndex);
			Object value = column < 0 ? null : outValues.getObject(column);
			lastWasNull = value == null;
			return value;
		}
	}

	@Override
	public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
		return getObject(parameterIndex);
	}

	@Override
	public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
		if (type == String.class) {
			return type.cast(getString(parameterIndex));
		}
		int column = outColumn(parameterIndex);
		T value = column < 0 || isNull(parameterIndex) ? null : outValues.getObject(column, type);
		lastWasNull = value == null;
		return value;
	}

	@Override
	public String getNString(int parameterIndex) throws SQLException {
		return getString(parameterIndex);
	}

	@Override
	public Reader getCharacterStream(int parameterIndex) throws SQLException {
		String value = getString(parameterIndex);
		return value == null ? null : new StringReader(value);
	}

	@Override
	public Reader getNCharacterStream(int parameterIndex) throws SQLException {
		return getCharacterStream(parameterIndex);
	}

	@Override
	public Ref getRef(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getRef");
	}

	@Override
	public Blob getBlob(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getBlob");
	}

	@Override
	public Clob getClob(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getClob");
	}

	@Override
	public Array getArray(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getArray");
	}

	@Override
	public URL getURL(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getURL");
	}

	@Override
	public RowId getRowId(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getRowId");
	}

	@Override
	public NClob getNClob(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getNClob");
	}

	@Override
	public SQLXML getSQLXML(int parameterIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException("getSQLXML");
	}

	// ---- parameters by name: the index of the name in the signature

	/**
	 * @return the SQL index of a parameter name, from the signature of the last execution or else of the resource
	 *         named after the procedure
	 */
	private int indexOf(String parameterName) throws SQLException {
		ProcedureSignature declared = signature;
		if (declared == null && procedureName != null) {
			declared = ProcedureSignature.of(CsvStatement.loadTable(tableResources, procedureName));
		}
		if (declared == null) {
			throw new SQLException("Parameter '" + parameterName + "' unknown: declare the parameters of '"
					+ procedureName + "' with the in_, out_, inout_ (and ret_) columns of its resource");
		}
		int index = declared.indexOf(parameterName, returnPlaceholder);
		if (index < 0) {
			throw new SQLException("Parameter '" + parameterName + "' is not declared for '" + procedureName + "'");
		}
		return index;
	}

	@Override
	public String getString(String parameterName) throws SQLException {
		return getString(indexOf(parameterName));
	}

	@Override
	public boolean getBoolean(String parameterName) throws SQLException {
		return getBoolean(indexOf(parameterName));
	}

	@Override
	public byte getByte(String parameterName) throws SQLException {
		return getByte(indexOf(parameterName));
	}

	@Override
	public short getShort(String parameterName) throws SQLException {
		return getShort(indexOf(parameterName));
	}

	@Override
	public int getInt(String parameterName) throws SQLException {
		return getInt(indexOf(parameterName));
	}

	@Override
	public long getLong(String parameterName) throws SQLException {
		return getLong(indexOf(parameterName));
	}

	@Override
	public float getFloat(String parameterName) throws SQLException {
		return getFloat(indexOf(parameterName));
	}

	@Override
	public double getDouble(String parameterName) throws SQLException {
		return getDouble(indexOf(parameterName));
	}

	@Override
	public BigDecimal getBigDecimal(String parameterName) throws SQLException {
		return getBigDecimal(indexOf(parameterName));
	}

	@Override
	public byte[] getBytes(String parameterName) throws SQLException {
		return getBytes(indexOf(parameterName));
	}

	@Override
	public Date getDate(String parameterName) throws SQLException {
		return getDate(indexOf(parameterName));
	}

	@Override
	public Time getTime(String parameterName) throws SQLException {
		return getTime(indexOf(parameterName));
	}

	@Override
	public Timestamp getTimestamp(String parameterName) throws SQLException {
		return getTimestamp(indexOf(parameterName));
	}

	@Override
	public Date getDate(String parameterName, Calendar cal) throws SQLException {
		return getDate(indexOf(parameterName));
	}

	@Override
	public Time getTime(String parameterName, Calendar cal) throws SQLException {
		return getTime(indexOf(parameterName));
	}

	@Override
	public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
		return getTimestamp(indexOf(parameterName));
	}

	@Override
	public Object getObject(String parameterName) throws SQLException {
		return getObject(indexOf(parameterName));
	}

	@Override
	public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
		return getObject(indexOf(parameterName));
	}

	@Override
	public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
		return getObject(indexOf(parameterName), type);
	}

	@Override
	public String getNString(String parameterName) throws SQLException {
		return getNString(indexOf(parameterName));
	}

	@Override
	public Reader getCharacterStream(String parameterName) throws SQLException {
		return getCharacterStream(indexOf(parameterName));
	}

	@Override
	public Reader getNCharacterStream(String parameterName) throws SQLException {
		return getNCharacterStream(indexOf(parameterName));
	}

	@Override
	public Ref getRef(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getRef");
	}

	@Override
	public Blob getBlob(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getBlob");
	}

	@Override
	public Clob getClob(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getClob");
	}

	@Override
	public Array getArray(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getArray");
	}

	@Override
	public URL getURL(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getURL");
	}

	@Override
	public RowId getRowId(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getRowId");
	}

	@Override
	public NClob getNClob(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getNClob");
	}

	@Override
	public SQLXML getSQLXML(String parameterName) throws SQLException {
		throw new SQLFeatureNotSupportedException("getSQLXML");
	}

	@Override
	public void setNull(String parameterName, int sqlType) throws SQLException {
		setNull(indexOf(parameterName), sqlType);
	}

	@Override
	public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
		setNull(indexOf(parameterName), sqlType, typeName);
	}

	@Override
	public void setBoolean(String parameterName, boolean x) throws SQLException {
		setBoolean(indexOf(parameterName), x);
	}

	@Override
	public void setByte(String parameterName, byte x) throws SQLException {
		setByte(indexOf(parameterName), x);
	}

	@Override
	public void setShort(String parameterName, short x) throws SQLException {
		setShort(indexOf(parameterName), x);
	}

	@Override
	public void setInt(String parameterName, int x) throws SQLException {
		setInt(indexOf(parameterName), x);
	}

	@Override
	public void setLong(String parameterName, long x) throws SQLException {
		setLong(indexOf(parameterName), x);
	}

	@Override
	public void setFloat(String parameterName, float x) throws SQLException {
		setFloat(indexOf(parameterName), x);
	}

	@Override
	public void setDouble(String parameterName, double x) throws SQLException {
		setDouble(indexOf(parameterName), x);
	}

	@Override
	public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
		setBigDecimal(indexOf(parameterName), x);
	}

	@Override
	public void setString(String parameterName, String x) throws SQLException {
		setString(indexOf(parameterName), x);
	}

	@Override
	public void setNString(String parameterName, String value) throws SQLException {
		setString(indexOf(parameterName), value);
	}

	@Override
	public void setBytes(String parameterName, byte[] x) throws SQLException {
		setBytes(indexOf(parameterName), x);
	}

	@Override
	public void setDate(String parameterName, Date x) throws SQLException {
		setDate(indexOf(parameterName), x);
	}

	@Override
	public void setTime(String parameterName, Time x) throws SQLException {
		setTime(indexOf(parameterName), x);
	}

	@Override
	public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
		setTimestamp(indexOf(parameterName), x);
	}

	@Override
	public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
		setDate(indexOf(parameterName), x);
	}

	@Override
	public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
		setTime(indexOf(parameterName), x);
	}

	@Override
	public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
		setTimestamp(indexOf(parameterName), x);
	}

	@Override
	public void setObject(String parameterName, Object x) throws SQLException {
		setObject(indexOf(parameterName), x);
	}

	@Override
	public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
		setObject(indexOf(parameterName), x, targetSqlType);
	}

	@Override
	public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {
		setObject(indexOf(parameterName), x, targetSqlType, scale);
	}

	@Override
	public void setURL(String parameterName, URL val) throws SQLException {
		throw new SQLFeatureNotSupportedException("setURL");
	}

	@Override
	public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setAsciiStream");
	}

	@Override
	public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setBinaryStream");
	}

	@Override
	public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setCharacterStream");
	}

	@Override
	public void setRowId(String parameterName, RowId x) throws SQLException {
		throw new SQLFeatureNotSupportedException("setRowId");
	}

	@Override
	public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setNCharacterStream");
	}

	@Override
	public void setNClob(String parameterName, NClob value) throws SQLException {
		throw new SQLFeatureNotSupportedException("setNClob");
	}

	@Override
	public void setClob(String parameterName, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setClob");
	}

	@Override
	public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setBlob");
	}

	@Override
	public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setNClob");
	}

	@Override
	public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
		throw new SQLFeatureNotSupportedException("setSQLXML");
	}

	@Override
	public void setBlob(String parameterName, Blob x) throws SQLException {
		throw new SQLFeatureNotSupportedException("setBlob");
	}

	@Override
	public void setClob(String parameterName, Clob x) throws SQLException {
		throw new SQLFeatureNotSupportedException("setClob");
	}

	@Override
	public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setAsciiStream");
	}

	@Override
	public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setBinaryStream");
	}

	@Override
	public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException("setCharacterStream");
	}

	@Override
	public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
		throw new SQLFeatureNotSupportedException("setAsciiStream");
	}

	@Override
	public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
		throw new SQLFeatureNotSupportedException("setBinaryStream");
	}

	@Override
	public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException("setCharacterStream");
	}

	@Override
	public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
		throw new SQLFeatureNotSupportedException("setNCharacterStream");
	}

	@Override
	public void setClob(String parameterName, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException("setClob");
	}

	@Override
	public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
		throw new SQLFeatureNotSupportedException("setBlob");
	}

	@Override
	public void setNClob(String parameterName, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException("setNClob");
	}
}
