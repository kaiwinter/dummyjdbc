# dummyjdbc 
[![CircleCI](https://circleci.com/gh/SimoneAvogadro/dummyjdbc.svg?style=svg)](https://circleci.com/gh/SimoneAvogadro/dummyjdbc)

dummyjdbc answers database requests of any application with dummy data to be independent of an existing database.

The library can either return dummy values, or values defined by you in a CSV file. The files are determined by the SQL query which makes this a very flexible tool. Also results of Stored Procedures can be mocked with data from CSV files.

For more details please see the [Wiki](https://github.com/kaiwinter/dummyjdbc/wiki)

## Sample Usage

## How in memory resources are selected

### Explicit comment in SQL
InMemory resource 'name' will be inferred by using some logic

```SQL
-- TESTCASE: Hello1
SELECT *
FROM TableUsedEverywhere
```
will search for resource: "Hello1" (case insensitive)

### Table name deduction
This is derived from the original dumymjdbc design, with some added REGEX for INSERT/UPDATE queries

```SQL
SELECT name
FROM mytable
WHERE surname='Happy'
```
will search for resource: "mytable" (case insensitive)

### Parameters
In order to make possible more sophisticated test cases the driver now will try to match also parameters
So the following query:
```SQL
SELECT name
FROM mytable
WHERE surname=? AND age=?
```
with parameters "Smith" and "34" will search for resources in the following order (always case-insensitive):
* mytable
* mytable?Smith,34

## Testing INSERT/DELETE queries
One key part of testing how the application interacts with the DB is to capture if it performed the right INSERT/UPDATE queries, this is not possible.

### How to know which parameters have been used for a query
### Sample code


## dummyjdbc at Maven Central
```xml
<dependency>
   <groupId>com.googlecode.dummyjdbc</groupId>
   <artifactId>dummyjdbc</artifactId>
   <version>1.3.0</version>
</dependency>
```

## Overview

![Design](https://raw.githubusercontent.com/wiki/kaiwinter/dummyjdbc/images/dummyjdbc-design.png)
