package com.googlecode.dummyjdbc.resultset;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Created with IntelliJ IDEA.
 *
 * @author xuqiang
 * @date 2018/02/28
 * @description
 **/
public class DummyResultSetMetaData implements ResultSetMetaData {

    private enum DataType {
        VARCHAR(Types.VARCHAR),
        INTEGER(Types.INTEGER),
        DOUBLE(Types.DOUBLE),
        DATE(Types.DATE),
        TIME(Types.TIME),
        TIMESTAMP(Types.TIMESTAMP);

        private final int sqlType;

        DataType(int sqlType) {
            this.sqlType = sqlType;
        }

        public static DataType fromString(String spec) {
            return valueOf(spec.toUpperCase());
        }
    }

    private static final int DEFAULT_COLUMN_PRECISION = 60;
    private static final int DEFAULT_COLUMN_SCALE = 0;
    private final String tableName;
    private final String[] columnNames;
    private final LinkedHashMap<String, DataType> columnTypes = new LinkedHashMap<>();

    public DummyResultSetMetaData(String tableName, String[] columnSpecs) {
        for (String columnSpec : columnSpecs) {
            String[] specElems = Objects.requireNonNull(columnSpec.trim().toUpperCase(), "Header must be specified").split("\\s*\\|\\s*");
            DataType dt;
            switch (specElems.length) {
                case 1:
                    dt = DataType.VARCHAR;
                    break;
                case 2:
                    dt = DataType.fromString(specElems[1]);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown column spec: " + columnSpec);
            }
            columnTypes.put(specElems[0], dt);
        }
        columnNames = columnTypes.keySet().toArray(new String[0]);
        this.tableName = tableName;
    }

    @Override
    public int getColumnCount() throws SQLException {
        return columnTypes.size();
    }

    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        throw new UnsupportedOperationException("isAutoIncrement");

    }

    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        throw new UnsupportedOperationException("isCaseSensitive");

    }

    @Override
    public boolean isSearchable(int column) throws SQLException {
        throw new UnsupportedOperationException("isSearchable");

    }

    @Override
    public boolean isCurrency(int column) throws SQLException {
        throw new UnsupportedOperationException("isCurrency");

    }

    @Override
    public int isNullable(int column) throws SQLException {
        throw new UnsupportedOperationException("isNullable");

    }

    @Override
    public boolean isSigned(int column) throws SQLException {
        throw new UnsupportedOperationException("isSigned");

    }

    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        throw new UnsupportedOperationException("getColumnDisplaySize");

    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
        return getColumnName(column);
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        return columnNames[column - 1];
    }

    @Override
    public String getSchemaName(int column) throws SQLException {
        return "";
    }

    @Override
    public int getPrecision(int column) throws SQLException {
        return DEFAULT_COLUMN_PRECISION;
    }

    @Override
    public int getScale(int column) throws SQLException {
        return DEFAULT_COLUMN_SCALE;
    }

    @Override
    public String getTableName(int column) throws SQLException {
        return tableName;
    }

    @Override
    public String getCatalogName(int column) throws SQLException {
        throw new UnsupportedOperationException("getCatalogName");

    }

    @Override
    public int getColumnType(int column) throws SQLException {
        return columnTypes.get(columnNames[column - 1]).sqlType;
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
        return columnTypes.get(columnNames[column - 1]).name();
    }

    @Override
    public boolean isReadOnly(int column) throws SQLException {
        throw new UnsupportedOperationException("isReadOnly");

    }

    @Override
    public boolean isWritable(int column) throws SQLException {
        throw new UnsupportedOperationException("isWritable");

    }

    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        throw new UnsupportedOperationException("isDefinitelyWritable");

    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
        throw new UnsupportedOperationException("getColumnClassName");

    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new UnsupportedOperationException("unwrap");

    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new UnsupportedOperationException("isWrapperFor");

    }

    public String[] getColumnNames() {
        return columnNames;
    }
}
