Change Log
==========

Unreleased
----------------------------

 * Behaviour change: the month and day names of the date, time and timestamp formats (`dd-MMM-yy`, `HH:mm`,
   `yyyyMMdd HHmmss.SSS`, and the patterns set with `setDateFormat` / `setTimeFormat` / `setTimestampFormat`) are parsed
   and formatted with `Locale.ENGLISH`, whatever the locale of the machine or of the JVM. Before, `dd-MMM-yy` read
   `17-May-12` on an English machine but failed on an Italian one (`17-mag-12`). Numbers were already locale-independent.
   Unit tests no longer need `Locale.setDefault`
 * Fixed: names (column names, `|type` suffixes in CSV headers, table names, query IDs) are upper- and lower-cased with
   `Locale.ROOT`. In Turkish locales `i` became `İ` and `I` became `ı`, so `id|integer` headers and lookups by name
   failed on machines with a Turkish locale

Version 2.1.0 (2026-10-19)
----------------------------

 * `-- QUERY_ID: name` as the first line of a statement names the query: its dataset is `name`, its captured parameters
   `name_PARAMS` and its counter is its own, whatever the table. The ID is one word: an empty ID, or one with spaces,
   `?`, `#` or `,`, makes the statement fail with an `SQLException`. Recommended over `-- TESTCASE: name`, which still
   works as before
 * Documentation: best practice "an ID for every query" (`<project>.<service>.<step>`) in FEATURES.md

Version 2.0.1 (2026-10-09)
----------------------------

 * The per-thread counters keep only JDK classes in their ThreadLocal: a driver class stored there kept the driver's
   class loader (and its jar file) alive on long-lived pooled threads, preventing the runtime from replacing the jar
 * `MockJdbcDriver.deregister()` removes the driver from `DriverManager`, for containers that unload it (no effect when
   not registered); `MockJdbcDriver.isRegistered()` tells whether it is registered
 * Inside Boomi (class `com.boomi.execution.ExecutionManager` present) the driver no longer registers itself in
   `DriverManager`, which kept the Custom Library class loader and its jars in use; a startup log line
   (`java.util.logging`) tells whether the driver is registered
 * Documentation: step-by-step Boomi tutorial (`TUTORIAL.md`) and a Boomi unit test example in FEATURES.md
 * Fixed: a `/tables/<name>.csv` inside a jar on the classpath failed with "URI is not hierarchical" (also in the
   original dummyjdbc); it is now read from the jar, without caching so the jar is not kept open

 * Known issue (Boomi on Windows): jars of a removed Custom Library stay locked by the runtime and, for the Legacy
   connector, on the classpath; upgrading still requires stopping the Atom, deleting the old jars and restarting

Version 2.0.0 (2026-10-08)
----------------------------

 * Repeated use of a resource: `users#1`, `users#2`, ... are returned by the 1st, 2nd, ... query on `users` (falling back
   to `users`), and every write is also kept as `<name>_PARAMS#n`; one counter per name, per thread, restarted by
   `reset()` / `resetCounters()` and, inside Boomi, automatically on a new top level execution (Try/Catch continuations keep the counters) or with the
   property `mockjdbc#RESET`
 * The step counter is per thread
 * Project renamed from dummyjdbc to **mockjdbc**: package `me.avogadro.mockjdbc`, driver class
   `me.avogadro.mockjdbc.MockJdbcDriver` (all `Dummy*` classes are now `Mock*`), Maven coordinates
   `me.avogadro.mockjdbc:mockjdbc`, Boomi property prefix `mockjdbc_`, metadata product name `MockJDBC`
 * Build: Java 8 target and AspectJ weaving with `dev.aspectj:aspectj-maven-plugin` 1.14 / AspectJ 1.9.22.1 (works on
   current JDKs); the runtime dependency `aspectjrt` moves to 1.9.22.1
 * Fixed: UPDATE without a `-- TESTCASE` comment was never captured; INSERT/UPDATE spanning several lines were not
   recognised; the `-- TESTCASE` name kept a trailing `\r` with CRLF line endings and swallowed the following lines of a
   multi-line query
 * Fixed: `DatabaseMetaData` returned `null` from many methods (NullPointerException in the Boomi Database V2 connector);
   now descriptive values and empty result sets with the standard JDBC columns
 * Parameters are captured for every prepared statement except SELECTs: also DELETE and stored procedure calls
   (`EXEC`, `EXECUTE`, `CALL`, `{call ...}`); SELECTs with a TESTCASE comment or on a step no longer write `_PARAMS`
 * Stored procedure results are also found for `CALL my_proc(...)`, `{call my_proc(...)}` and calls on several lines
 * `getLong`, `getShort`, `getByte`, `getDouble`, `getFloat` and `getRow()` are implemented for CSV result sets
 * `ResultSetMetaData.getColumnName()` / `getColumnLabel()` return the column names as written in the CSV header
   (were upper case, e.g. Boomi Database V2 documents had keys `ID`, `NAME` instead of `id`, `name`)
 * `DatabaseMetaData.getColumns` describes the columns of a table from its CSV header (resolved as for a query), so the
   Boomi Database V2 connector can bind parameters by type; new header types `|bigint`, `|decimal`, `|boolean`
 * `getGeneratedKeys()` returns an empty result set (was null: every Boomi DB V2 insert failed), `addBatch()` /
   `executeBatch()` run the statement once per parameter set ("Commit By Rows"), `setMaxRows()` is honoured
 * `setLong`, `setShort`, `setByte`, `setFloat`, `setBoolean`, `setNull`, `setObject(..., type)` and `clearParameters()`
   are captured/honoured; `findColumn()` is implemented; `SELECT 1` and unknown tables return metadata (was null)
 * NULL convention: an empty value in a column with a declared type other than `varchar` is SQL NULL (`wasNull()`
   is implemented); `getObject()` returns the Java type of the declared column type
 * Driver version reported as 2.0 (was 1.0)
 * Boomi support through Dynamic Process Properties (detected at runtime via reflection, no dependency on Boomi):
   tables can be provided as `mockjdbc_<name>` properties (name as written, then lower case) and captured
   INSERT/UPDATE parameters are exposed as `mockjdbc_<table>_PARAMS` and `mockjdbc_<table in lower case>_params`
   (never persisted)

Version 1.5.1
----------------------------

 * Fixed issues with using step number to identify testing data

Version 1.5.0
----------------------------

 * Rebased to com.mindmercatis in order to continue development and provide separate CirceCI integration testing
 * Added methods to simplify testing by just saying which result will be given at which step

Version 1.4.0
----------------------------

 * Support for in-memory result sets
 * Support for insert/update queries
 * Support for capturing query parameters for test purposes
 * Support for providing different resultsets depending on the query parameters 

Version 1.3.0 (2018-03-14)
----------------------------

 * Support for table metadata
 * Support for multiple databases by using different jdbc urls
 
Version 1.2 (2015-03-14)
----------------------------

 * Switching to Java 7
 * Improved logging
 * AspectJ for automated tracing

Version 1.1.3 (2013-07-04)
----------------------------

 * Stable initial version
