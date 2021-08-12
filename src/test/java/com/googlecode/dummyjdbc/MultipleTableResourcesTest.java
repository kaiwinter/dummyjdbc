package com.googlecode.dummyjdbc;

import com.googlecode.dummyjdbc.statement.impl.CsvStatement;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class MultipleTableResourcesTest {

    public static final String INVOICE_POSITIONS_TABLE = "BOO.[dbo].[invoice_positions]";
    public static final String INVOICE_TABLE_NAME = "BOO.[dbo].[invoices]";

    private File testFileInvoices;
    private File testFileInvoiceAccounts;

    @Before
    public void setup() {
        testFileInvoices = new File("src/test/resources/invoices/invoices.csv");
        testFileInvoiceAccounts = new File("src/test/resources/invoices/invoice_positions.csv");
    }

    @Test
    public void testStatementIsCsvStatement() throws Exception {
        givenDummyDriverWithTables();

        Connection connection = DriverManager.getConnection("any");
        Statement stmt = connection.createStatement();

        assertTrue(stmt instanceof CsvStatement);
    }

    @Test
    public void testCsvStatementContainsMapWithTwoTableResources() throws Exception {
        givenDummyDriverWithTables();

        Connection connection = DriverManager.getConnection("any");
        Statement stmt = connection.createStatement();

        Field field = stmt.getClass().getDeclaredField("tableResources");
        field.setAccessible(true);
        Map<String, File> tableResources = (Map<String, File>) field.get(stmt);
        assertTrue(tableResources.containsKey(INVOICE_TABLE_NAME.toLowerCase()));
        assertTrue(tableResources.containsKey(INVOICE_POSITIONS_TABLE.toLowerCase()));
    }

    @Test
    public void testCsvStatementForTwoIndividualQueriesYieldsResults() throws Exception {
        givenDummyDriverWithTables();

        Connection connection = DriverManager.getConnection("any");
        Statement stmt = connection.createStatement();
        ResultSet rsOne = stmt.executeQuery("SELECT * FROM BOO.[dbo].[invoices] WHERE ID = '4711'");
        ResultSet rsTwo = stmt.executeQuery("SELECT * FROM BOO.[dbo].[invoice_positions] WHERE ID = '4711'");

        assertTrue(rsOne.next());
        assertTrue(rsTwo.next());
    }

    private void givenDummyDriverWithTables() throws ClassNotFoundException {
        Class.forName(DummyJdbcDriver.class.getCanonicalName());
        DummyJdbcDriver.addTableResource(INVOICE_TABLE_NAME, testFileInvoices);
        DummyJdbcDriver.addTableResource(INVOICE_POSITIONS_TABLE, testFileInvoiceAccounts);
    }

}
