# Introduction #
## Features ##
  * Ignoring JDBC queries and returning an empty `ResultSet` for any query
  * Returning values which are parsed from CSV files as `ResultSet`
  * SQL queries and stored procedures are supported


## Usage ##
dummyjdbc can be used for different scenarios.


---


## Integration mode ##
In this scenario you want to test (or just run) an application on a test system where you don't have access to a database. You can use dummyjdbc to either ignore JDBC queries and just return empty `ResultSet`s, or you create CSV files for the tables you want to access and let dummyjdbc parse them.

### Requirement ###
The database driver have to be configureable to let the application use dummyjdbc.

### How to ###
Just add the dummyjdbc.jar to your path and switch your driver to `com.googlecode.dummyjdbc.DummyJdbcDriver`. If you don't want dummyjdbc to return any data that's all. To let dummyjdbc return results for queries create a folder named `tables` in the directory of dummyjdbc. In this directory you have to put one file per database table. <br />
**Example:** For the query: `SELECT id, name FROM users` you have to put the file `users.csv` in the `tables` folder. The CSV file have to contain at least the following data:
```
id, name
1, joe
```



---


## JUnit mode ##
Using dummyjdbc in unit tests is useful if you don't want to create mock objects which are returned by your DAO or if you actually can't mock them. That way you can even test logic in your DAO (which you might have in legacy applications without real architecture).
### Requirement ###
The database `Connection` have to be mockable e.g. by Mockito.

### How to ###
Add dummyjdbc to your build path. In your JUnit test register the tables for which data should be returned.
<br />
**Example:**
```
   @Test
   public void testDummyJdbc() throws SQLException {
      // Register CSV file for table name
      DummyJdbcDriver.addTableResource("users", new File("users.csv"));
      Connection connection = DriverManager.getConnection("any");

      // Get class to test and inject connection
      ApplicationDao applicationDao = new ApplicationDao(connection);

      // Test DAO, dummyjdbc will deliver the data from the CSV file
      Collection<UserBo> users = applicationDao.getUsers();
      Assert.assertEquals(1, users.size());
   }
```