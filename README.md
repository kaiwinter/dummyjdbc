# dummyjdbc 
[![CircleCI](https://circleci.com/gh/SimoneAvogadro/dummyjdbc.svg?style=svg)](https://circleci.com/gh/SimoneAvogadro/dummyjdbc)

dummyjdbc answers database requests of any application with dummy data to be independent of an existing database.

The library can either return dummy values, or values defined by you in a CSV file. The files are determined by the SQL query which makes this a very flexible tool. Also results of Stored Procedures can be mocked with data from CSV files.

For more details please see the [Wiki](https://github.com/kaiwinter/dummyjdbc/wiki)

## New Methods in 1.4.0
Three new methods have been added to `com.googlecode.dummyjdbc.DummyJdbcDriver` in order to support:
* InMemory resources for resultsets
* differentiated resultsers depending on query parameters
* capturing INSERT/UPDATE parameters

```Java
/**
 * Add the CSV contained the string 'value' to the list of available resultsets
 */
public static void addInMemoryTableResource(String testID, String value);

/**
 * Add the CSV contained the InputStream 'valueStream' to the list of available resultsets
 */
public static void addInMemoryTableResource(String testID, InputStream valueStream);

/**
 * Get the current value of the resource, used mainly to examine the parameters used for INSERT/UPDATE queries
 */
public static String getInMemoryTableResource(String testID);
```

## Sample Usage
```java
@Test
public void testInMemoryCSVFromString() throws ClassNotFoundException, URISyntaxException, SQLException {
   Class.forName(DummyJdbcDriver.class.getCanonicalName());

   DummyJdbcDriver.addInMemoryTableResource("TEST1", 
                     "\n"+
                     "name, age\n"+
                     "John, 20"+
                     "\n"
   );

   Connection connection = DriverManager.getConnection("any");
   PreparedStatement statement = connection.prepareStatement(
         "-- TESTCASE:test1\n"+
         "SELECT * FROM test_table");

   Assert.assertTrue(statement instanceof CsvPreparedStatement);
   resultSet = statement.executeQuery();

   Assert.assertTrue(resultSet.next());
   Assert.assertEquals("John", resultSet.getString(1));
   Assert.assertEquals(20, resultSet.getInt(2));
   Assert.assertEquals("John", resultSet.getString("name"));
   Assert.assertEquals(20, resultSet.getInt("age"));
}
```

## How in memory resources are selected

### Explicit comment in SQL
InMemory resource 'name' will be inferred by using some logic

```SQL
-- TESTCASE: Hello1
SELECT *
FROM TableUsedEverywhere
```
will search for resource: `Hello1` (case insensitive)

### Table name deduction
This is derived from the original dumymjdbc design, with some added REGEX for INSERT/UPDATE queries

```SQL
SELECT name
FROM mytable
WHERE surname='Happy'
```
will search for resource: `mytable` (case insensitive)

### Parameters
In order to make possible more sophisticated test cases the driver now will try to match also parameters
So the following query:
```SQL
SELECT name
FROM mytable
WHERE surname=? AND age=?
```
with parameters "Smith" and "34" will search for resources in the following order (always case-insensitive):
* `mytable?Smith,34`
* `mytable`

## Testing INSERT/DELETE queries
One key part of testing how the application interacts with the DB is to capture if it performed the right INSERT/UPDATE queries, this is now possible.

When updating a table now the parameters are captured and stored into a String which will be accessible for testing purposes

### How to know which parameters have been used for a query
A new InMemory resource will be created with name equals to the name of the table + `_PARAMS`
E.g: when updating table `users` a new key will be added with name `users_PARAMS`

### Sample code

```java
        Class.forName(DummyJdbcDriver.class.getCanonicalName());

        Connection connection = DriverManager.getConnection("any");
        PreparedStatement statement = connection.prepareStatement("INSERT INTO users (name,age) VALUES (?,?) ");

        statement.setString(1, "hello");
        statement.setInt(2, 30);
        status = statement.execute();
        params = DummyJdbcDriver.getInMemoryTableResource("users_PARAMS");
        Assert.assertEquals("hello,30", params);
```


## dummyjdbc at Maven Central [OUTDATED]

In order to use the official 1.3 version you can use Maven
```xml
<dependency>
   <groupId>com.googlecode.dummyjdbc</groupId>
   <artifactId>dummyjdbc</artifactId>
   <version>1.3.0</version>
</dependency>
```

In order to use v 1.4.0 at present you must download it directly from GitHub


## Overview

![Design](https://raw.githubusercontent.com/wiki/kaiwinter/dummyjdbc/images/dummyjdbc-design.png)
