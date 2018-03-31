# dummyjdbc 
[![CircleCI](https://circleci.com/gh/kaiwinter/dummyjdbc.svg?style=svg)](https://circleci.com/gh/kaiwinter/dummyjdbc)

This fork is intended to support:
- in-memory CSV
- capturing of input parameters

The main purposed of this fork is to simplify the development of UnitTests with [Boomi](https://boomi.com) iPaaS.
Using this fork you can create unit tests -without- access to the Atom's filesystem. All the test data will be stored inside the UnitTest processes.

dummyjdbc answers database requests of any application with dummy data to be independent of an existing database.

The library can either return dummy values, or values defined by you in a CSV file. The files are determined by the SQL query which makes this a very flexible tool. Also results of Stored Procedures can be mocked with data from CSV files.

For more details please see the [Wiki](https://github.com/kaiwinter/dummyjdbc/wiki)

## dummyjdbc at Maven Central (OLD: must redefine this section or merge into original repository)
```xml
<dependency>
   <groupId>com.googlecode.dummyjdbc</groupId>
   <artifactId>dummyjdbc</artifactId>
   <version>1.3.0</version>
</dependency>
```

## Overview

![Design](https://raw.githubusercontent.com/wiki/kaiwinter/dummyjdbc/images/dummyjdbc-design.png)
