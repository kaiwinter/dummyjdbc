package me.avogadro.mockjdbc.connection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.io.File;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import me.avogadro.mockjdbc.MockJdbcDriver;
import me.avogadro.mockjdbc.resultset.MockResultSetMetaData;
import me.avogadro.mockjdbc.resultset.impl.CSVResultSet;
import me.avogadro.mockjdbc.statement.impl.CsvStatement;
import me.avogadro.mockjdbc.statement.impl.ProcedureSignature;

/**
 * Metadata of the mock database. Descriptive values are never <code>null</code>. {@link #getColumns} describes the
 * columns of a table from the header of its CSV (resolved as for a query); every other method returning a
 * {@link ResultSet} returns an empty one, with the columns defined by the JDBC specification. Tools reading the
 * metadata before running a query (e.g. the Boomi Database V2 connector) can therefore go on.
 */
public class MockDatabaseMetaData implements DatabaseMetaData {

    /** Value of {@link #getDatabaseProductName()} and {@link #getDriverName()} */
    public static final String PRODUCT_NAME = "MockJDBC";

    /** Value of {@link #getDatabaseProductVersion()} and {@link #getDriverVersion()} */
    public static final String VERSION = MockJdbcDriver.VERSION_MAJOR + "." + MockJdbcDriver.VERSION_MINOR;

    private static final String[] KEY_COLUMNS = { "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME",
            "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "UPDATE_RULE", "DELETE_RULE",
            "FK_NAME", "PK_NAME", "DEFERRABILITY" };

    private static final String[] COLUMNS_COLUMNS = { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME",
            "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "NUM_PREC_RADIX", "NULLABLE",
            "REMARKS", "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION",
            "IS_NULLABLE", "SCOPE_CATALOG", "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT",
            "IS_GENERATEDCOLUMN" };

    private static final String[] PROCEDURE_COLUMNS_COLUMNS = { "PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME",
            "COLUMN_NAME", "COLUMN_TYPE", "DATA_TYPE", "TYPE_NAME", "PRECISION", "LENGTH", "SCALE", "RADIX", "NULLABLE",
            "REMARKS", "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION",
            "IS_NULLABLE", "SPECIFIC_NAME" };

    /** PRECISION, LENGTH and CHAR_OCTET_LENGTH reported for procedure parameters */
    private static final int PROCEDURE_COLUMN_PRECISION = 60;

    /** DECIMAL_DIGITS reported for |double and |decimal columns (the CSV does not declare a scale) */
    static final int DECIMAL_DIGITS = 10;

    private final Connection connection;
    private final String url;
    private final Map<String, File> tableResources;

    /**
     * @param connection the connection these metadata belong to
     * @param url the JDBC URL used to open the connection
     * @param tableResources the CSV files registered for the tables of the connection
     */
    public MockDatabaseMetaData(Connection connection, String url, Map<String, File> tableResources) {
        this.connection = connection;
        this.url = url == null ? "" : url;
        this.tableResources = tableResources == null ? Collections.<String, File>emptyMap() : tableResources;
    }

    /**
     * @param connection the connection these metadata belong to
     * @param url the JDBC URL used to open the connection
     */
    public MockDatabaseMetaData(Connection connection, String url) {
        this(connection, url, null);
    }

    public MockDatabaseMetaData() {
        this(null, "");
    }

    private static ResultSet emptyResultSet(String name, String... columns) {
        return CSVResultSet.empty(name, columns);
    }

    @Override public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    @Override public boolean allTablesAreSelectable() throws SQLException {
        return false;
    }

    @Override public String getURL() throws SQLException {
        return url;
    }

    @Override public String getUserName() throws SQLException {
        return "";
    }

    @Override public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override public boolean nullsAreSortedHigh() throws SQLException {
        return false;
    }

    @Override public boolean nullsAreSortedLow() throws SQLException {
        return false;
    }

    @Override public boolean nullsAreSortedAtStart() throws SQLException {
        return false;
    }

    @Override public boolean nullsAreSortedAtEnd() throws SQLException {
        return false;
    }

    @Override public String getDatabaseProductName() throws SQLException {
        return PRODUCT_NAME;
    }

    @Override public String getDatabaseProductVersion() throws SQLException {
        return VERSION;
    }

    @Override public String getDriverName() throws SQLException {
        return PRODUCT_NAME;
    }

    @Override public String getDriverVersion() throws SQLException {
        return VERSION;
    }

    @Override public int getDriverMajorVersion() {
        return MockJdbcDriver.VERSION_MAJOR;
    }

    @Override public int getDriverMinorVersion() {
        return MockJdbcDriver.VERSION_MINOR;
    }

    @Override public boolean usesLocalFiles() throws SQLException {
        return false;
    }

    @Override public boolean usesLocalFilePerTable() throws SQLException {
        return false;
    }

    @Override public boolean supportsMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override public boolean storesUpperCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override public boolean storesLowerCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override public boolean storesMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override public String getIdentifierQuoteString() throws SQLException {
        return " ";
    }

    @Override public String getSQLKeywords() throws SQLException {
        return "";
    }

    @Override public String getNumericFunctions() throws SQLException {
        return "";
    }

    @Override public String getStringFunctions() throws SQLException {
        return "";
    }

    @Override public String getSystemFunctions() throws SQLException {
        return "";
    }

    @Override public String getTimeDateFunctions() throws SQLException {
        return "";
    }

    @Override public String getSearchStringEscape() throws SQLException {
        return "\\";
    }

    @Override public String getExtraNameCharacters() throws SQLException {
        return "";
    }

    @Override public boolean supportsAlterTableWithAddColumn() throws SQLException {
        return false;
    }

    @Override public boolean supportsAlterTableWithDropColumn() throws SQLException {
        return false;
    }

    @Override public boolean supportsColumnAliasing() throws SQLException {
        return false;
    }

    @Override public boolean nullPlusNonNullIsNull() throws SQLException {
        return false;
    }

    @Override public boolean supportsConvert() throws SQLException {
        return false;
    }

    @Override public boolean supportsConvert(int i, int i2) throws SQLException {
        return false;
    }

    @Override public boolean supportsTableCorrelationNames() throws SQLException {
        return false;
    }

    @Override public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        return false;
    }

    @Override public boolean supportsExpressionsInOrderBy() throws SQLException {
        return false;
    }

    @Override public boolean supportsOrderByUnrelated() throws SQLException {
        return false;
    }

    @Override public boolean supportsGroupBy() throws SQLException {
        return false;
    }

    @Override public boolean supportsGroupByUnrelated() throws SQLException {
        return false;
    }

    @Override public boolean supportsGroupByBeyondSelect() throws SQLException {
        return false;
    }

    @Override public boolean supportsLikeEscapeClause() throws SQLException {
        return false;
    }

    @Override public boolean supportsMultipleResultSets() throws SQLException {
        return false;
    }

    @Override public boolean supportsMultipleTransactions() throws SQLException {
        return false;
    }

    @Override public boolean supportsNonNullableColumns() throws SQLException {
        return false;
    }

    @Override public boolean supportsMinimumSQLGrammar() throws SQLException {
        return false;
    }

    @Override public boolean supportsCoreSQLGrammar() throws SQLException {
        return false;
    }

    @Override public boolean supportsExtendedSQLGrammar() throws SQLException {
        return false;
    }

    @Override public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        return false;
    }

    @Override public boolean supportsANSI92IntermediateSQL() throws SQLException {
        return false;
    }

    @Override public boolean supportsANSI92FullSQL() throws SQLException {
        return false;
    }

    @Override public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        return false;
    }

    @Override public boolean supportsOuterJoins() throws SQLException {
        return false;
    }

    @Override public boolean supportsFullOuterJoins() throws SQLException {
        return false;
    }

    @Override public boolean supportsLimitedOuterJoins() throws SQLException {
        return false;
    }

    @Override public String getSchemaTerm() throws SQLException {
        return "schema";
    }

    @Override public String getProcedureTerm() throws SQLException {
        return "procedure";
    }

    @Override public String getCatalogTerm() throws SQLException {
        return "catalog";
    }

    @Override public boolean isCatalogAtStart() throws SQLException {
        return false;
    }

    @Override public String getCatalogSeparator() throws SQLException {
        return ".";
    }

    @Override public boolean supportsSchemasInDataManipulation() throws SQLException {
        return false;
    }

    @Override public boolean supportsSchemasInProcedureCalls() throws SQLException {
        return false;
    }

    @Override public boolean supportsSchemasInTableDefinitions() throws SQLException {
        return false;
    }

    @Override public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        return false;
    }

    @Override public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    @Override public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return false;
    }

    @Override public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        return false;
    }

    @Override public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        return false;
    }

    @Override public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        return false;
    }

    @Override public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    @Override public boolean supportsPositionedDelete() throws SQLException {
        return false;
    }

    @Override public boolean supportsPositionedUpdate() throws SQLException {
        return false;
    }

    @Override public boolean supportsSelectForUpdate() throws SQLException {
        return false;
    }

    @Override public boolean supportsStoredProcedures() throws SQLException {
        return false;
    }

    @Override public boolean supportsSubqueriesInComparisons() throws SQLException {
        return false;
    }

    @Override public boolean supportsSubqueriesInExists() throws SQLException {
        return false;
    }

    @Override public boolean supportsSubqueriesInIns() throws SQLException {
        return false;
    }

    @Override public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        return false;
    }

    @Override public boolean supportsCorrelatedSubqueries() throws SQLException {
        return false;
    }

    @Override public boolean supportsUnion() throws SQLException {
        return false;
    }

    @Override public boolean supportsUnionAll() throws SQLException {
        return false;
    }

    @Override public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        return false;
    }

    @Override public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        return false;
    }

    @Override public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        return false;
    }

    @Override public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        return false;
    }

    @Override public int getMaxBinaryLiteralLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxCharLiteralLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxColumnNameLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxColumnsInGroupBy() throws SQLException {
        return 0;
    }

    @Override public int getMaxColumnsInIndex() throws SQLException {
        return 0;
    }

    @Override public int getMaxColumnsInOrderBy() throws SQLException {
        return 0;
    }

    @Override public int getMaxColumnsInSelect() throws SQLException {
        return 0;
    }

    @Override public int getMaxColumnsInTable() throws SQLException {
        return 0;
    }

    @Override public int getMaxConnections() throws SQLException {
        return 0;
    }

    @Override public int getMaxCursorNameLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxIndexLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxSchemaNameLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxProcedureNameLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxCatalogNameLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxRowSize() throws SQLException {
        return 0;
    }

    @Override public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return false;
    }

    @Override public int getMaxStatementLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxStatements() throws SQLException {
        return 0;
    }

    @Override public int getMaxTableNameLength() throws SQLException {
        return 0;
    }

    @Override public int getMaxTablesInSelect() throws SQLException {
        return 0;
    }

    @Override public int getMaxUserNameLength() throws SQLException {
        return 0;
    }

    @Override public int getDefaultTransactionIsolation() throws SQLException {
        return 0;
    }

    @Override public boolean supportsTransactions() throws SQLException {
        return false;
    }

    @Override public boolean supportsTransactionIsolationLevel(int i) throws SQLException {
        return false;
    }

    @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        return false;
    }

    @Override public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        return false;
    }

    @Override public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        return false;
    }

    @Override public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return false;
    }

    @Override public ResultSet getProcedures(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getProcedures", "PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "RESERVED1", "RESERVED2", "RESERVED3", "REMARKS", "PROCEDURE_TYPE", "SPECIFIC_NAME");
    }

    /**
     * The parameters of a stored procedure whose resource declares a {@link ProcedureSignature} (header with
     * <code>in_</code>, <code>out_</code>, <code>inout_</code> and <code>ret_</code> columns), found as for a call:
     * <code>schema.procedure</code> first when a schema is given, then <code>procedure</code>; the plain resource,
     * else its first occurrence (<code>procedure#1</code>). The return value comes first, named
     * <code>@RETURN_VALUE</code>; the other parameters follow in the order of the header, named without the prefix.
     * Catalog is ignored, the procedure name must be exact (case insensitive), the column pattern supports
     * <code>%</code> and <code>_</code>. Empty when no signature is declared.
     */
    @Override public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
            String columnNamePattern) throws SQLException {
        List<LinkedHashMap<String, String>> rows = new ArrayList<LinkedHashMap<String, String>>();
        String procedure = procedureNamePattern == null ? "" : procedureNamePattern.trim();
        ProcedureSignature signature = procedure.isEmpty() || "%".equals(procedure) ? null
                : findSignature(schemaPattern, procedure);
        if (signature != null) {
            Pattern columnPattern = likePattern(columnNamePattern);
            List<ProcedureSignature.Parameter> parameters = new ArrayList<ProcedureSignature.Parameter>();
            if (signature.getReturnValue() != null) {
                parameters.add(signature.getReturnValue());
            }
            parameters.addAll(signature.getParameters());
            int position = 0;
            for (ProcedureSignature.Parameter parameter : parameters) {
                boolean returnValue = parameter.getMode() == ProcedureSignature.Mode.RETURN;
                String name = returnValue ? "@RETURN_VALUE" : parameter.getName();
                int ordinal = returnValue ? 0 : ++position;
                if (columnPattern != null && !columnPattern.matcher(name).matches()) {
                    continue;
                }
                int type = parameter.getSqlType();
                boolean decimal = type == Types.DOUBLE || type == Types.DECIMAL;
                LinkedHashMap<String, String> row = new LinkedHashMap<String, String>();
                row.put("PROCEDURE_CAT", null);
                row.put("PROCEDURE_SCHEM", null);
                row.put("PROCEDURE_NAME", procedure);
                row.put("COLUMN_NAME", name);
                row.put("COLUMN_TYPE", String.valueOf(parameter.getMode().getColumnType()));
                row.put("DATA_TYPE", String.valueOf(type));
                row.put("TYPE_NAME", parameter.getTypeName());
                row.put("PRECISION", String.valueOf(PROCEDURE_COLUMN_PRECISION));
                row.put("LENGTH", String.valueOf(PROCEDURE_COLUMN_PRECISION));
                row.put("SCALE", String.valueOf(decimal ? DECIMAL_DIGITS : 0));
                row.put("RADIX", "10");
                row.put("NULLABLE", String.valueOf(procedureNullable));
                row.put("REMARKS", null);
                row.put("COLUMN_DEF", null);
                row.put("SQL_DATA_TYPE", "0");
                row.put("SQL_DATETIME_SUB", "0");
                row.put("CHAR_OCTET_LENGTH", String.valueOf(PROCEDURE_COLUMN_PRECISION));
                row.put("ORDINAL_POSITION", String.valueOf(ordinal));
                row.put("IS_NULLABLE", "YES");
                row.put("SPECIFIC_NAME", procedure);
                rows.add(row);
            }
        }
        return new CSVResultSet("getProcedureColumns",
                new MockResultSetMetaData("getProcedureColumns", PROCEDURE_COLUMNS_COLUMNS), rows);
    }

    /** the signature declared for a procedure, or null */
    private ProcedureSignature findSignature(String schema, String procedure) throws SQLException {
        List<String> names = new ArrayList<String>();
        if (schema != null && !schema.trim().isEmpty() && !"%".equals(schema.trim())) {
            names.add(schema.trim() + "." + procedure);
        }
        names.add(procedure);
        for (String name : names) {
            for (String resource : new String[] { name, name + MockJdbcDriver.OCCURRENCE_SEPARATOR + 1 }) {
                ProcedureSignature signature = ProcedureSignature.of(CsvStatement.loadTable(tableResources, resource));
                if (signature != null) {
                    return signature;
                }
            }
        }
        return null;
    }

    @Override public ResultSet getTables(String s, String s2, String s3, String[] strings) throws SQLException {
        return emptyResultSet("getTables", "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS", "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION");
    }

    @Override public ResultSet getSchemas() throws SQLException {
        return emptyResultSet("getSchemas", "TABLE_SCHEM", "TABLE_CATALOG");
    }

    @Override public ResultSet getCatalogs() throws SQLException {
        return emptyResultSet("getCatalogs", "TABLE_CAT");
    }

    @Override public ResultSet getTableTypes() throws SQLException {
        return emptyResultSet("getTableTypes", "TABLE_TYPE");
    }

    /**
     * One row per column of the CSV header of the table, found as for a <code>SELECT * FROM table</code> (in-memory
     * resource, Boomi Dynamic Process Property, registered file or <code>/tables/</code>). Catalog and schema are
     * ignored; the table name must be exact (case insensitive), the column pattern supports <code>%</code> and
     * <code>_</code>.
     */
    @Override public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
            String columnNamePattern) throws SQLException {
        List<LinkedHashMap<String, String>> rows = new ArrayList<LinkedHashMap<String, String>>();
        String tableName = tableNamePattern == null ? "" : tableNamePattern.trim();
        if (!tableName.isEmpty() && !"%".equals(tableName)) {
            ResultSetMetaData table = CsvStatement.loadTable(tableResources, tableName).getMetaData();
            int columnCount = table == null ? 0 : table.getColumnCount();
            Pattern columnPattern = likePattern(columnNamePattern);
            for (int i = 1; i <= columnCount; i++) {
                String columnName = table.getColumnName(i);
                if (columnPattern != null && !columnPattern.matcher(columnName).matches()) {
                    continue;
                }
                int type = table.getColumnType(i);
                boolean decimal = type == Types.DOUBLE || type == Types.DECIMAL;
                LinkedHashMap<String, String> row = new LinkedHashMap<String, String>();
                row.put("TABLE_CAT", null);
                row.put("TABLE_SCHEM", null);
                row.put("TABLE_NAME", tableName);
                row.put("COLUMN_NAME", columnName);
                row.put("DATA_TYPE", String.valueOf(type));
                row.put("TYPE_NAME", table.getColumnTypeName(i));
                row.put("COLUMN_SIZE", String.valueOf(table.getPrecision(i)));
                row.put("BUFFER_LENGTH", "0");
                row.put("DECIMAL_DIGITS", String.valueOf(decimal ? DECIMAL_DIGITS : 0));
                row.put("NUM_PREC_RADIX", "10");
                row.put("NULLABLE", String.valueOf(columnNullable));
                row.put("REMARKS", null);
                row.put("COLUMN_DEF", null);
                row.put("SQL_DATA_TYPE", "0");
                row.put("SQL_DATETIME_SUB", "0");
                row.put("CHAR_OCTET_LENGTH", String.valueOf(table.getPrecision(i)));
                row.put("ORDINAL_POSITION", String.valueOf(i));
                row.put("IS_NULLABLE", "YES");
                row.put("SCOPE_CATALOG", null);
                row.put("SCOPE_SCHEMA", null);
                row.put("SCOPE_TABLE", null);
                row.put("SOURCE_DATA_TYPE", null);
                row.put("IS_AUTOINCREMENT", "NO");
                row.put("IS_GENERATEDCOLUMN", "NO");
                rows.add(row);
            }
        }
        return new CSVResultSet("getColumns", new MockResultSetMetaData("getColumns", COLUMNS_COLUMNS), rows);
    }

    /** JDBC LIKE pattern (% and _) to a case insensitive regex; null for "match everything" */
    private static Pattern likePattern(String pattern) {
        if (pattern == null || pattern.isEmpty() || "%".equals(pattern)) {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    @Override public ResultSet getColumnPrivileges(String s, String s2, String s3, String s4) throws SQLException {
        return emptyResultSet("getColumnPrivileges", "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "GRANTOR", "GRANTEE", "PRIVILEGE", "IS_GRANTABLE");
    }

    @Override public ResultSet getTablePrivileges(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getTablePrivileges", "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "GRANTOR", "GRANTEE", "PRIVILEGE", "IS_GRANTABLE");
    }

    @Override public ResultSet getBestRowIdentifier(String s, String s2, String s3, int i, boolean b) throws SQLException {
        return emptyResultSet("getBestRowIdentifier", "SCOPE", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "PSEUDO_COLUMN");
    }

    @Override public ResultSet getVersionColumns(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getVersionColumns", "SCOPE", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "PSEUDO_COLUMN");
    }

    @Override public ResultSet getPrimaryKeys(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getPrimaryKeys", "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME");
    }

    @Override public ResultSet getImportedKeys(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getImportedKeys", KEY_COLUMNS);
    }

    @Override public ResultSet getExportedKeys(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getExportedKeys", KEY_COLUMNS);
    }

    @Override public ResultSet getCrossReference(String s, String s2, String s3, String s4, String s5, String s6) throws SQLException {
        return emptyResultSet("getCrossReference", KEY_COLUMNS);
    }

    @Override public ResultSet getTypeInfo() throws SQLException {
        return emptyResultSet("getTypeInfo", "TYPE_NAME", "DATA_TYPE", "PRECISION", "LITERAL_PREFIX", "LITERAL_SUFFIX", "CREATE_PARAMS", "NULLABLE", "CASE_SENSITIVE", "SEARCHABLE", "UNSIGNED_ATTRIBUTE", "FIXED_PREC_SCALE", "AUTO_INCREMENT", "LOCAL_TYPE_NAME", "MINIMUM_SCALE", "MAXIMUM_SCALE", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "NUM_PREC_RADIX");
    }

    @Override public ResultSet getIndexInfo(String s, String s2, String s3, boolean b, boolean b2) throws SQLException {
        return emptyResultSet("getIndexInfo", "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE", "INDEX_QUALIFIER", "INDEX_NAME", "TYPE", "ORDINAL_POSITION", "COLUMN_NAME", "ASC_OR_DESC", "CARDINALITY", "PAGES", "FILTER_CONDITION");
    }

    @Override public boolean supportsResultSetType(int i) throws SQLException {
        return false;
    }

    @Override public boolean supportsResultSetConcurrency(int i, int i2) throws SQLException {
        return false;
    }

    @Override public boolean ownUpdatesAreVisible(int i) throws SQLException {
        return false;
    }

    @Override public boolean ownDeletesAreVisible(int i) throws SQLException {
        return false;
    }

    @Override public boolean ownInsertsAreVisible(int i) throws SQLException {
        return false;
    }

    @Override public boolean othersUpdatesAreVisible(int i) throws SQLException {
        return false;
    }

    @Override public boolean othersDeletesAreVisible(int i) throws SQLException {
        return false;
    }

    @Override public boolean othersInsertsAreVisible(int i) throws SQLException {
        return false;
    }

    @Override public boolean updatesAreDetected(int i) throws SQLException {
        return false;
    }

    @Override public boolean deletesAreDetected(int i) throws SQLException {
        return false;
    }

    @Override public boolean insertsAreDetected(int i) throws SQLException {
        return false;
    }

    @Override public boolean supportsBatchUpdates() throws SQLException {
        return false;
    }

    @Override public ResultSet getUDTs(String s, String s2, String s3, int[] ints) throws SQLException {
        return emptyResultSet("getUDTs", "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "CLASS_NAME", "DATA_TYPE", "REMARKS", "BASE_TYPE");
    }

    @Override public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override public boolean supportsSavepoints() throws SQLException {
        return false;
    }

    @Override public boolean supportsNamedParameters() throws SQLException {
        return false;
    }

    @Override public boolean supportsMultipleOpenResults() throws SQLException {
        return false;
    }

    @Override public boolean supportsGetGeneratedKeys() throws SQLException {
        return false;
    }

    @Override public ResultSet getSuperTypes(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getSuperTypes", "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SUPERTYPE_CAT", "SUPERTYPE_SCHEM", "SUPERTYPE_NAME");
    }

    @Override public ResultSet getSuperTables(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getSuperTables", "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "SUPERTABLE_NAME");
    }

    @Override public ResultSet getAttributes(String s, String s2, String s3, String s4) throws SQLException {
        return emptyResultSet("getAttributes", "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "ATTR_NAME", "DATA_TYPE", "ATTR_TYPE_NAME", "ATTR_SIZE", "DECIMAL_DIGITS", "NUM_PREC_RADIX", "NULLABLE", "REMARKS", "ATTR_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE", "SCOPE_CATALOG", "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE");
    }

    @Override public boolean supportsResultSetHoldability(int i) throws SQLException {
        return false;
    }

    @Override public int getResultSetHoldability() throws SQLException {
        return 0;
    }

    @Override public int getDatabaseMajorVersion() throws SQLException {
        return MockJdbcDriver.VERSION_MAJOR;
    }

    @Override public int getDatabaseMinorVersion() throws SQLException {
        return MockJdbcDriver.VERSION_MINOR;
    }

    @Override public int getJDBCMajorVersion() throws SQLException {
        return 4;
    }

    @Override public int getJDBCMinorVersion() throws SQLException {
        return 1;
    }

    @Override public int getSQLStateType() throws SQLException {
        return 0;
    }

    @Override public boolean locatorsUpdateCopy() throws SQLException {
        return false;
    }

    @Override public boolean supportsStatementPooling() throws SQLException {
        return false;
    }

    @Override public RowIdLifetime getRowIdLifetime() throws SQLException {
        return RowIdLifetime.ROWID_UNSUPPORTED;
    }

    @Override public ResultSet getSchemas(String s, String s2) throws SQLException {
        return emptyResultSet("getSchemas", "TABLE_SCHEM", "TABLE_CATALOG");
    }

    @Override public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return false;
    }

    @Override public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return false;
    }

    @Override public ResultSet getClientInfoProperties() throws SQLException {
        return emptyResultSet("getClientInfoProperties", "NAME", "MAX_LEN", "DEFAULT_VALUE", "DESCRIPTION");
    }

    @Override public ResultSet getFunctions(String s, String s2, String s3) throws SQLException {
        return emptyResultSet("getFunctions", "FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "REMARKS", "FUNCTION_TYPE", "SPECIFIC_NAME");
    }

    @Override public ResultSet getFunctionColumns(String s, String s2, String s3, String s4) throws SQLException {
        return emptyResultSet("getFunctionColumns", "FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "COLUMN_NAME", "COLUMN_TYPE", "DATA_TYPE", "TYPE_NAME", "PRECISION", "LENGTH", "SCALE", "RADIX", "NULLABLE", "REMARKS", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE", "SPECIFIC_NAME");
    }

    @Override public <T> T unwrap(Class<T> tClass) throws SQLException {
        if (tClass.isInstance(this)) {
            return tClass.cast(this);
        }
        throw new SQLException("Not a wrapper for " + tClass.getName());
    }

    @Override public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return false;
    }

	@Override
	public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
			throws SQLException {
		return emptyResultSet("getPseudoColumns", "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE",
				"COLUMN_SIZE", "DECIMAL_DIGITS", "NUM_PREC_RADIX", "COLUMN_USAGE", "REMARKS", "CHAR_OCTET_LENGTH", "IS_NULLABLE");
	}

	@Override
	public boolean generatedKeyAlwaysReturned() throws SQLException {
		return false;
	}
}
