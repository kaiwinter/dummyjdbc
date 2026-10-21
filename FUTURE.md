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

### 5. CallableStatement (`Connection.prepareCall`)

* **Today:** `prepareCall(...)` returns `null`, so code (or a Boomi "Stored Procedure" operation) that calls a
  procedure through a `CallableStatement` fails. Stored procedures work only through `prepareStatement` /
  `createStatement` with `EXEC`, `EXECUTE`, `CALL` or `{call ...}`.
* **Idea:** return a `CallableStatement` built on `CsvPreparedStatement` (same lookup and parameter capture), with OUT
  parameters registered and read back from the CSV result or from dedicated resources.

### 6. Database directories loaded lazily

* **Today:** for `jdbc::mock::<dir>` URLs the CSV files are read when `connect()` is called. After `reset()`, a connection
  that is already open no longer sees those tables (new connections reload them).
* **Idea:** look up the directory on demand (or re-register it after `reset()`) so open connections keep working.

### 7. Table-name detection

* **Today (INSERT/UPDATE/DELETE):** the table of an `INSERT INTO <table> (` / `UPDATE <table>` / `DELETE [FROM] <table>`
  is matched with letters only (`[a-zA-Z]`), so these are not captured under `<table>_PARAMS` and need a
  `-- QUERY_ID:` comment (or a step resource):
  * names containing digits or underscores (`user_roles`, `table2`);
  * schema-qualified names (`dbo.orders`);
  * quoted identifiers (`[orders]`, `"orders"`, `` `orders` ``);
  * `INSERT INTO orders VALUES (...)` without a column list (the pattern wants `(` right after the name).
* **Today (SELECT):** `TABLENAME_PATTERN` (`.*from\s*(\S*)\s?.*`) is greedy, so the name is the word after the **last**
  `from` of the whole SQL: `... WHERE id IN (SELECT id FROM blocked)` gives `blocked)`, and a column such as
  `created_from = ?` after the real `FROM` probably gives `=` (to confirm with a test). `FROM dbo.customers` gives
  `dbo.customers`, `FROM [customers]` gives `[customers]`.
* **Idea:** one shared identifier rule for reads and writes: `[A-Za-z0-9_$]` parts, optionally quoted with `[]`, `""`
  or backticks, optionally schema-qualified (resource name = the last part, lower case, or the full name as a fallback);
  for SELECT, the first `FROM` at nesting level 0 and a word boundary before `from`; INSERT with or without the column
  list. Tests for each form. Seen while writing the `boomi-unit-testing` skill (2026-10-21): with mockjdbc as the
  driver, the Boomi Database V2 Dynamic Insert generates `Insert into payments(pay_id,amount) values (?,?)` (no schema
  even with `schemaName` set, no quotes), so the generated SQL is already captured; the limits above hit the SQL
  written by hand in Legacy and V2 operations.

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
