# Future ideas

Improvements that were identified while documenting the library (see [FEATURES.md](FEATURES.md)) but are **not
implemented yet**. Each item says what is missing today and what a change would involve.

## Result set getters

### 1. SQL `NULL` in varchar columns

* **Today:** an empty value is SQL `NULL` only in columns with a declared type other than `varchar`; in `varchar` (or
  untyped) columns it is the empty string, so a `NULL` string cannot be mocked. The CSV reader (opencsv 2.3) does not
  tell an unquoted empty value from a quoted one (`""`).
* **Idea:** an opt-in marker for NULL in varchar columns (e.g. a configurable token such as `\N`), or a CSV reader that
  distinguishes unquoted empty values.

## Date and time handling

### 2. ISO-style default formats

* **Today:** the defaults are `dd-MMM-yy`, `HH:mm`, `yyyyMMdd HHmmss.SSS`. Month names are always English
  (`Locale.ENGLISH`, whatever the JVM locale), but the time default drops seconds, and the timestamp default requires
  milliseconds. Users have to call `setDateFormat` / `setTimeFormat` / `setTimestampFormat` in every thread.
* **Idea:** default to ISO patterns (`yyyy-MM-dd`, `HH:mm:ss`, `yyyy-MM-dd HH:mm:ss[.SSS]`), possibly accepting several
  patterns per getter.
* **Compatibility:** breaking for existing CSVs and `?params` keys that use the current formats; consider a major version
  or a switch to select the old behaviour.

### 3. Format settings shared across threads and reset by `reset()`

* **Today:** the formats are `ThreadLocal`, so they apply only to the calling thread, and `reset()` does not restore
  the defaults.
* **Idea:** a process-wide setting (with thread-local override if needed) and restore the defaults in `reset()`.

## Encoding

### 4. Explicit charset for `InputStream` resources

* **Today:** `addInMemoryTableResource(String, InputStream)` and `addInMemoryTableResource(int, InputStream)` read the stream
  with the default charset of the VM, like CSV files.
* **Idea:** add overloads taking a `Charset` for streams whose encoding differs from the VM default.

## Resources and connections

### 5. Stored procedures: cursors, several result sets, functions

* **Today:** `prepareCall` supports OUT parameters (signature resources with `in_`/`out_`/`inout_`/`ret_` columns) and
  one result set per call (see [FEATURES.md, section 3 c](FEATURES.md#c-stored-procedures)). Not supported: REF CURSOR
  OUT parameters (`getObject` returning a `ResultSet`), several result sets (`getMoreResults`), a procedure returning
  both OUT parameters and a result set (also IN parameters with a result set: the Database V2 operation then
  calls it without parameters, so the IN values are not captured), update counts before the result set, and `getFunctionColumns` for functions.
* **Idea:** extra resources per procedure, e.g. `<proc>_<param>` for a cursor parameter and `<proc>_RS2`, `<proc>_RS3`
  for further result sets; a function signature served by `getFunctionColumns`.
* **Impact on Boomi unit tests:** processes using Oracle cursors, several result sets or the Database V2 *Execute
  Function* operation cannot be mocked yet.

### 6. Database directories loaded lazily

* **Today:** for `jdbc::mock::<dir>` URLs the CSV files are read when `connect()` is called. After `reset()`, a connection
  that is already open no longer sees those tables (new connections reload them).
* **Idea:** look up the directory on demand (or re-register it after `reset()`) so open connections keep working.

### 7. Table-name detection

The shared identifier rule (digits, `_`, quotes, schema-qualified names, first `FROM` outside parentheses, INSERT
without a column list) is done (`SqlNames`, see FEATURES.md section 3a). Still not recognised, so they need a
`-- QUERY_ID:` comment:

* **Today:** T-SQL forms with a word between the keyword and the table: `UPDATE TOP (10) t` (gives `TOP`),
  `DELETE TOP (5) FROM t`; `MERGE INTO t`;
  `UPDATE ... FROM` with an alias (`UPDATE o SET ... FROM orders o`, gives the alias `o`); temporary tables (`#tmp`) and
  quoted names with spaces (`[order details]`).
* **Idea:** skip `TOP (n)` / `TOP n [PERCENT]`, add MERGE; for `#tmp` and spaces, decide a resource-name mapping
  (`#` and spaces clash with the `#n` occurrence and the lookup separators).

### 8. Precedence between files and in-memory resources

* **Today:** if both a registered file and an in-memory resource exist for the same name, the **file wins**
  (step resources always win over both).
* **Idea:** decide whether in-memory should override files, since it is registered closer to the test; document the
  decision either way.

## Boomi runtime

### 9. Jar files of a removed library stay locked (unresolved)

* **Today:** on Windows, after a mockjdbc jar is removed from a Custom Library, the Boomi cleanup reports
  `Unable to remove un-deployed jar file` for it and for the other jars of the same library (also data-only jars), in
  the Legacy (`userlib\database`) and in the Database V2 folder. Stopping the Atom, deleting the jars and restarting is
  required (documented in FEATURES.md, section 11).
* **Done on the driver side:** no driver class in ThreadLocals (2.0.1), `/tables/` read from jars without caching,
  no registration in `DriverManager` inside Boomi. With these, after a forced GC one of the two driver class loaders
  was released (impossible with b12), but the runtime still keeps the removed jar open: the remaining reference is held
  by Boomi itself.
* **Legacy:** Boomi does not add a removed jar to `ignored_jars.txt` of `userlib\database`, so the jar stays on the
  classpath and the cleanup never tries to delete it.
* **Boomi cloud runtime:** the same happens there, where the runtime cannot be stopped or cleaned by the account. Seen in
  October 2026: after each library deploy the Database V2 connector used the new jar, while the Legacy connector kept
  running an old mockjdbc build (2.1.0 table-name detection and 2.2.0 stored procedures missing). Verify Legacy changes
  on a local Atom.
* **Idea:** investigate with Boomi support (class loader lifecycle of Custom Libraries on Windows); nothing else known
  to change in the driver.

## Build

### 10. Logging dependencies and the AspectJ tracing aspect

* **Today:** the build targets Java 8 and works on current JDKs (AspectJ weaving via `dev.aspectj:aspectj-maven-plugin`).
  `logback-classic`/`logback-core` are still declared as normal dependencies, so they end up in the classpath of every
  application using the driver, and the tracing aspect (`AspectLogger.aj`) requires the AspectJ runtime (`aspectjrt`), one more jar
  to install wherever the driver runs (e.g. Boomi).
* **Idea:** move logback to the `test` scope, and decide whether the TRACE logging of every public method is still worth
  the AspectJ build step and runtime.

## Documentation

### 11. Publication on Maven Central

* **Today:** no artifact is published on Maven: the jar comes from the GitHub releases or from `mvn package`, and the
  docs deliberately describe only that.
* **Idea:** when a release is published (coordinates `me.avogadro.mockjdbc:mockjdbc`, as in `pom.xml`), add the Maven /
  Gradle dependency snippet back to the README "Installation" section and to FEATURES.md section 1.
