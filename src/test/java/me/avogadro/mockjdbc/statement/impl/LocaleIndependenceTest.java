package me.avogadro.mockjdbc.statement.impl;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import me.avogadro.mockjdbc.MockJdbcDriver;

/**
 * The driver gives the same results whatever the default locale of the JVM: dates use English month names and names
 * are upper- and lower-cased without the Turkish dotted / dotless i.
 * <p>
 * Each check runs on a new thread, so that the per-thread date formats are created while the other locale is the
 * default, and the default locale is restored afterwards.
 */
public final class LocaleIndependenceTest {

	private static final Locale ITALIAN = Locale.forLanguageTag("it-IT");
	private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

	private interface Check {
		void run() throws Exception;
	}

	@Before
	public void setup() throws ClassNotFoundException {
		Class.forName(MockJdbcDriver.class.getCanonicalName());
		MockJdbcDriver.reset();
	}

	private static void inLocale(Locale locale, final Check check) throws Throwable {
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		Locale previous = Locale.getDefault();
		Locale.setDefault(locale);
		try {
			Thread thread = new Thread(() -> {
				try {
					check.run();
				} catch (Throwable t) {
					failure.set(t);
				}
			});
			thread.start();
			thread.join();
		} finally {
			Locale.setDefault(previous);
		}
		if (failure.get() != null) {
			throw failure.get();
		}
	}

	private static ResultSet query(String csv) throws SQLException {
		MockJdbcDriver.addInMemoryTableResource("t", csv);
		ResultSet resultSet = DriverManager.getConnection("any").createStatement().executeQuery("SELECT * FROM t");
		Assert.assertTrue(resultSet.next());
		return resultSet;
	}

	private static void assertDay(java.util.Date date, int year, int month, int day) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		Assert.assertEquals(year, calendar.get(Calendar.YEAR));
		Assert.assertEquals(month, calendar.get(Calendar.MONTH));
		Assert.assertEquals(day, calendar.get(Calendar.DAY_OF_MONTH));
	}

	@Test
	public void testDefaultDateFormatReadsEnglishMonthsWithItalianLocale() throws Throwable {
		inLocale(ITALIAN, () -> assertDay(query("day|date\n17-MAY-12").getDate("day"), 2012, Calendar.MAY, 17));
	}

	@Test(expected = SQLException.class)
	public void testDefaultDateFormatRejectsItalianMonthsWithItalianLocale() throws Throwable {
		inLocale(ITALIAN, () -> query("day|date\n17-MAG-12").getDate("day"));
	}

	@Test
	public void testCustomDateFormatUsesEnglishWithItalianLocale() throws Throwable {
		inLocale(ITALIAN, () -> {
			MockJdbcDriver.setDateFormat("dd MMMM yyyy");
			assertDay(query("day\n17 May 2012").getDate("day"), 2012, Calendar.MAY, 17);
		});
	}

	@Test
	public void testDateParameterIsWrittenInEnglishWithItalianLocale() throws Throwable {
		inLocale(ITALIAN, () -> {
			Calendar calendar = Calendar.getInstance();
			calendar.clear();
			calendar.set(2012, Calendar.MAY, 17);
			PreparedStatement statement = DriverManager.getConnection("any")
					.prepareStatement("INSERT INTO orders (day) VALUES (?)");
			statement.setDate(1, new java.sql.Date(calendar.getTimeInMillis()));
			statement.executeUpdate();

			Assert.assertEquals("17-May-12", MockJdbcDriver.getInMemoryTableResource("orders_PARAMS"));
		});
	}

	@Test
	public void testTypedHeaderAndLookupByNameWithTurkishLocale() throws Throwable {
		inLocale(TURKISH, () -> {
			ResultSet resultSet = query("id|integer, city\n7, Milano");

			Assert.assertEquals(Types.INTEGER, resultSet.getMetaData().getColumnType(1));
			Assert.assertEquals(7, resultSet.getInt("id"));
			Assert.assertEquals(7, resultSet.getInt("ID"));
			Assert.assertEquals("Milano", resultSet.getString("CITY"));
		});
	}

	@Test
	public void testTableNameWithTurkishLocale() throws Throwable {
		inLocale(TURKISH, () -> {
			MockJdbcDriver.addInMemoryTableResource("ITEMS", "name\nwidget");
			ResultSet resultSet = DriverManager.getConnection("any").createStatement()
					.executeQuery("SELECT * FROM items");

			Assert.assertTrue(resultSet.next());
			Assert.assertEquals("widget", resultSet.getString("name"));
		});
	}
}
