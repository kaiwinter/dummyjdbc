# dummyjdbc 
[![CircleCI](https://circleci.com/gh/kaiwinter/dummyjdbc.svg?style=svg)](https://circleci.com/gh/kaiwinter/dummyjdbc)

dummyjdbc answers database requests of any application with dummy data to be independent of an existing database.

The library can either return dummy values, or values defined by you in a CSV file. The files are determined by the SQL query which makes this a very flexible tool. Also results of Stored Procedures can be mocked with data from CSV files.

For more details please see the [Wiki](https://github.com/kaiwinter/dummyjdbc/wiki)

## dummyjdbc at Maven Central
```xml
<dependency>
   <groupId>com.googlecode.dummyjdbc</groupId>
   <artifactId>dummyjdbc</artifactId>
   <version>1.3.1</version>
</dependency>
```

## Overview

![Design](https://raw.githubusercontent.com/wiki/kaiwinter/dummyjdbc/images/dummyjdbc-design.png)
