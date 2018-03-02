package com.googlecode.dummyjdbc.statement.impl;

import java.io.File;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.googlecode.dummyjdbc.DummyJdbcDriver;

public class ResultSetMetaDataTest {

    private ResultSetMetaData resultSetMetaData;

    @Before
    public void setup() throws ClassNotFoundException, SQLException, URISyntaxException {
        Class.forName(DummyJdbcDriver.class.getCanonicalName());

        DummyJdbcDriver.addTableResource("metadata",
            new File(CsvGenericStatementTest.class.getResource("metadata.csv").toURI()));
        Connection connection = DriverManager.getConnection("any");
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT * FROM metadata");
        resultSetMetaData = resultSet.getMetaData();

        Assert.assertNotNull(resultSetMetaData);
    }

    @Test
    public void testMetaData() throws SQLException {
        Assert.assertEquals(resultSetMetaData.getColumnCount(), 6);
        Assert.assertEquals(resultSetMetaData.getColumnName(1), "string_column".toUpperCase());
        Assert.assertEquals(resultSetMetaData.getColumnName(6), "date_column_invalid".toUpperCase());
    }

    @Test
    public void testIsSigned() throws SQLException {
        Assert.assertEquals(resultSetMetaData.isSigned(1), false);
        Assert.assertEquals(resultSetMetaData.isSigned(2), true);
    }

    @Test
    public void testIsNullable() throws SQLException {
        Assert.assertEquals(resultSetMetaData.isNullable(1), 3);
        Assert.assertEquals(resultSetMetaData.isNullable(2), 3);
    }

}
