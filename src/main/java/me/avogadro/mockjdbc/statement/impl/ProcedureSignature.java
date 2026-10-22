package me.avogadro.mockjdbc.statement.impl;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.avogadro.mockjdbc.resultset.impl.CSVResultSet;

/**
 * Signature of a stored procedure, declared by the CSV header of its resource when <b>every</b> column starts with
 * <code>in_</code>, <code>out_</code>, <code>inout_</code> or <code>ret_</code> (case insensitive), e.g.
 * <code>in_name,in_value|integer,out_size|integer,out_status,out_message</code>.
 * <ul>
 * <li>the order of the <code>in_</code>, <code>out_</code> and <code>inout_</code> columns is the order of the
 * <code>?</code> of the call;</li>
 * <li>the optional <code>ret_</code> column is the return value of <code>{? = call ...}</code> (parameter 1);</li>
 * <li>the name after the prefix is the parameter name, the optional <code>|type</code> its SQL type;</li>
 * <li>the first data row holds the values of the OUT parameters; the values of the IN columns are ignored.</li>
 * </ul>
 * A header with any column without a prefix is a result set, as for a table.
 */
public final class ProcedureSignature {

	/** Direction of a parameter, with its {@link DatabaseMetaData#getProcedureColumns} COLUMN_TYPE */
	public enum Mode {
		IN("in_", DatabaseMetaData.procedureColumnIn),
		INOUT("inout_", DatabaseMetaData.procedureColumnInOut),
		OUT("out_", DatabaseMetaData.procedureColumnOut),
		RETURN("ret_", DatabaseMetaData.procedureColumnReturn);

		private final String prefix;
		private final int columnType;

		Mode(String prefix, int columnType) {
			this.prefix = prefix;
			this.columnType = columnType;
		}

		public int getColumnType() {
			return columnType;
		}

		/** IN and INOUT parameters carry a value into the procedure */
		public boolean isInput() {
			return this == IN || this == INOUT;
		}

		/** INOUT, OUT and RETURN parameters carry a value back from the procedure */
		public boolean isOutput() {
			return this != IN;
		}

		static Mode of(String columnName) {
			String lower = columnName.toLowerCase(Locale.ROOT);
			for (Mode mode : values()) {
				if (lower.startsWith(mode.prefix) && lower.length() > mode.prefix.length()) {
					return mode;
				}
			}
			return null;
		}
	}

	/** One parameter: name without prefix, direction, SQL type and 1-based column of the CSV */
	public static final class Parameter {
		private final String name;
		private final Mode mode;
		private final int sqlType;
		private final String typeName;
		private final int column;

		Parameter(String name, Mode mode, int sqlType, String typeName, int column) {
			this.name = name;
			this.mode = mode;
			this.sqlType = sqlType;
			this.typeName = typeName;
			this.column = column;
		}

		public String getName() {
			return name;
		}

		public Mode getMode() {
			return mode;
		}

		public int getSqlType() {
			return sqlType;
		}

		public String getTypeName() {
			return typeName;
		}

		/** 1-based column of the CSV holding the value */
		public int getColumn() {
			return column;
		}
	}

	/** the <code>?</code> of the call, in order (no return value) */
	private final List<Parameter> parameters;
	/** the <code>ret_</code> column, or <code>null</code> */
	private final Parameter returnValue;

	private ProcedureSignature(List<Parameter> parameters, Parameter returnValue) {
		this.parameters = Collections.unmodifiableList(parameters);
		this.returnValue = returnValue;
	}

	/**
	 * @param resultSet the result set loaded for the procedure name
	 * @return the signature declared by its header, or <code>null</code> if it is a plain result set (or not a CSV)
	 * @throws SQLException if the header declares more than one <code>ret_</code> column
	 */
	public static ProcedureSignature of(ResultSet resultSet) throws SQLException {
		if (!(resultSet instanceof CSVResultSet)) {
			return null;
		}
		ResultSetMetaData metaData = resultSet.getMetaData();
		int columnCount = metaData == null ? 0 : metaData.getColumnCount();
		if (columnCount == 0) {
			return null;
		}
		List<Parameter> parameters = new ArrayList<Parameter>();
		Parameter returnValue = null;
		for (int i = 1; i <= columnCount; i++) {
			String columnName = metaData.getColumnName(i).trim();
			Mode mode = Mode.of(columnName);
			if (mode == null) {
				return null;
			}
			Parameter parameter = new Parameter(columnName.substring(mode.prefix.length()), mode,
					metaData.getColumnType(i), metaData.getColumnTypeName(i), i);
			if (mode == Mode.RETURN) {
				if (returnValue != null) {
					throw new SQLException("Only one ret_ column is allowed: " + returnValue.getName() + ", "
							+ parameter.getName());
				}
				returnValue = parameter;
			} else {
				parameters.add(parameter);
			}
		}
		return new ProcedureSignature(parameters, returnValue);
	}

	/** the parameters marked by the <code>?</code> of the call, in order (the return value excluded) */
	public List<Parameter> getParameters() {
		return parameters;
	}

	/** the <code>ret_</code> column, or <code>null</code> when not declared */
	public Parameter getReturnValue() {
		return returnValue;
	}

	/**
	 * @param index 1-based index of the <code>?</code> in the SQL
	 * @param returnPlaceholder <code>true</code> when the SQL starts with <code>{? = call</code>: index 1 is the return
	 *            value
	 * @return the parameter at that index, or <code>null</code> if the signature has none there
	 */
	public Parameter atIndex(int index, boolean returnPlaceholder) {
		if (returnPlaceholder) {
			if (index == 1) {
				return returnValue;
			}
			index--;
		}
		return index >= 1 && index <= parameters.size() ? parameters.get(index - 1) : null;
	}

	/**
	 * @param name a parameter name, also with the prefix (<code>out_size</code>) or a leading <code>@</code> or
	 *            <code>:</code> (case insensitive)
	 * @param returnPlaceholder see {@link #atIndex(int, boolean)}
	 * @return the 1-based index of the <code>?</code> for that name, or -1
	 */
	public int indexOf(String name, boolean returnPlaceholder) {
		String bare = bareName(name);
		if (returnValue != null && returnPlaceholder && matches(returnValue, bare)) {
			return 1;
		}
		for (int i = 0; i < parameters.size(); i++) {
			if (matches(parameters.get(i), bare)) {
				return returnPlaceholder ? i + 2 : i + 1;
			}
		}
		return -1;
	}

	private static boolean matches(Parameter parameter, String bareName) {
		return parameter.getName().equalsIgnoreCase(bareName)
				|| (parameter.getMode().prefix + parameter.getName()).equalsIgnoreCase(bareName);
	}

	private static String bareName(String name) {
		String trimmed = name == null ? "" : name.trim();
		while (trimmed.startsWith("@") || trimmed.startsWith(":")) {
			trimmed = trimmed.substring(1);
		}
		return trimmed;
	}
}
