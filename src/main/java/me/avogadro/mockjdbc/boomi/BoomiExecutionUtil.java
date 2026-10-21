package me.avogadro.mockjdbc.boomi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to the Boomi runtime ({@value #EXECUTION_UTIL_CLASS}) through reflection.
 * <p>
 * The driver has no compile time dependency on Boomi: when the Boomi classes are not on the classpath
 * {@link #isBoomi()} is <code>false</code> and every method of this class does nothing.
 *
 * @author Simone Avogadro
 */
public final class BoomiExecutionUtil {

	private static final Logger LOGGER = LoggerFactory.getLogger(BoomiExecutionUtil.class);

	/** Boomi class exposing the static methods for Dynamic Process Properties */
	public static final String EXECUTION_UTIL_CLASS = "com.boomi.execution.ExecutionUtil";

	/** Prefix of the Dynamic Process Properties read and written by the driver */
	public static final String PROPERTY_PREFIX = "mockjdbc_";

	/** Dynamic Process Property requesting a reset of the counters (also read in lower case) */
	public static final String RESET_PROPERTY = "mockjdbc#RESET";

	/** Runtime execution property identifying the current execution (fallback for the top level execution ID) */
	public static final String EXECUTION_ID_PROPERTY = "EXECUTION_ID";

	/**
	 * Boomi class whose static <code>getCurrent()</code> returns the current execution task, exposing
	 * <code>getTopLevelExecutionId()</code>: unlike EXECUTION_ID it does not change inside Try/Catch and other
	 * continuations of the same execution
	 */
	public static final String EXECUTION_MANAGER_CLASS = "com.boomi.execution.ExecutionManager";

	private static boolean initialized = false;
	private static Method getDynamicProcessPropertyMethod;
	private static Method setDynamicProcessPropertyMethod;
	/** optional: ExecutionUtil.getRuntimeExecutionProperty(String) */
	private static Method getRuntimeExecutionPropertyMethod;
	/** optional: ExecutionManager.getCurrent() */
	private static Method getCurrentExecutionMethod;
	/** getTopLevelExecutionId() of the class last returned by getCurrent(), looked up once */
	private static Method getTopLevelExecutionIdMethod;
	private static boolean topLevelExecutionIdUnavailable;

	private BoomiExecutionUtil() {
		// static utility
	}

	/**
	 * @return <code>true</code> if the Boomi runtime classes have been found, i.e. the driver is running inside Boomi
	 */
	public static boolean isBoomi() {
		init();
		return getDynamicProcessPropertyMethod != null && setDynamicProcessPropertyMethod != null;
	}

	/**
	 * Name of the Dynamic Process Property used for a driver resource, keeping the case of the resource name, e.g.
	 * <code>users_PARAMS</code> becomes <code>mockjdbc_users_PARAMS</code>
	 *
	 * @param resourceName name of a table, test case, step or captured parameters
	 * @return the property name (prefix + resource name as written)
	 */
	public static String propertyName(String resourceName) {
		return PROPERTY_PREFIX + resourceName.trim();
	}

	/**
	 * Reads the Dynamic Process Property of a driver resource. Boomi property names are case sensitive while the
	 * driver resources are not, so the name is tried as written first (e.g. <code>mockjdbc_T_014a</code>) and then in
	 * lower case (<code>mockjdbc_t_014a</code>). Empty or blank values count as not defined.
	 *
	 * @param resourceName name of a table, test case, step or captured parameters
	 * @return the trimmed property value, or <code>null</code> if not defined or not running inside Boomi
	 */
	public static String getResourceProperty(String resourceName) {
		if (!isBoomi()) {
			return null;
		}
		String exactName = propertyName(resourceName);
		String value = trimToNull(getDynamicProcessProperty(exactName));
		if (value == null) {
			String lowerCaseName = propertyName(resourceName.toLowerCase(Locale.ROOT));
			if (!lowerCaseName.equals(exactName)) {
				value = trimToNull(getDynamicProcessProperty(lowerCaseName));
			}
		}
		return value;
	}

	/**
	 * Writes the Dynamic Process Property of a driver resource (never persisted) both with the name as written (e.g.
	 * <code>mockjdbc_Users_PARAMS</code>) and in lower case (<code>mockjdbc_users_params</code>).
	 *
	 * @param resourceName name of a table, test case or captured parameters
	 * @param value the value to set
	 * @return <code>true</code> if the properties have been set, <code>false</code> when not running inside Boomi or in
	 *         case of errors
	 */
	public static boolean setResourceProperty(String resourceName, String value) {
		if (!isBoomi()) {
			return false;
		}
		String exactName = propertyName(resourceName);
		boolean ok = setDynamicProcessProperty(exactName, value);
		String lowerCaseName = propertyName(resourceName.toLowerCase(Locale.ROOT));
		if (!lowerCaseName.equals(exactName)) {
			ok &= setDynamicProcessProperty(lowerCaseName, value);
		}
		return ok;
	}

	/**
	 * ID of the current Boomi execution, the same for the whole execution: the top level execution ID
	 * (<code>ExecutionManager.getCurrent().getTopLevelExecutionId()</code>), which stays the same inside Try/Catch and
	 * other continuations; when not available, <code>ExecutionUtil.getRuntimeExecutionProperty("EXECUTION_ID")</code>.
	 *
	 * @return the execution ID, or <code>null</code> when not running inside Boomi or not available
	 */
	public static String getExecutionId() {
		if (!isBoomi()) {
			return null;
		}
		String topLevelExecutionId = getTopLevelExecutionId();
		if (topLevelExecutionId != null) {
			return topLevelExecutionId;
		}
		if (getRuntimeExecutionPropertyMethod == null) {
			return null;
		}
		try {
			Object value = getRuntimeExecutionPropertyMethod.invoke(null, EXECUTION_ID_PROPERTY);
			return trimToNull(value == null ? null : value.toString());
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.debug("Unable to read the execution ID", unwrap(e));
			return null;
		}
	}

	/** ExecutionManager.getCurrent().getTopLevelExecutionId() through reflection, or null */
	private static synchronized String getTopLevelExecutionId() {
		if (getCurrentExecutionMethod == null || topLevelExecutionIdUnavailable) {
			return null;
		}
		try {
			Object task = getCurrentExecutionMethod.invoke(null);
			if (task == null) {
				return null;
			}
			if (getTopLevelExecutionIdMethod == null
					|| !getTopLevelExecutionIdMethod.getDeclaringClass().isInstance(task)) {
				getTopLevelExecutionIdMethod = task.getClass().getMethod("getTopLevelExecutionId");
				getTopLevelExecutionIdMethod.setAccessible(true);
			}
			Object value = getTopLevelExecutionIdMethod.invoke(task);
			return trimToNull(value == null ? null : value.toString());
		} catch (NoSuchMethodException e) {
			LOGGER.info("Top level execution ID not available, using {}", EXECUTION_ID_PROPERTY);
			topLevelExecutionIdUnavailable = true;
			return null;
		} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
			LOGGER.debug("Unable to read the top level execution ID", e);
			return null;
		}
	}

	/**
	 * Checks the Dynamic Process Property {@value #RESET_PROPERTY} (then in lower case): when set and not blank it is
	 * emptied (Boomi has no method to remove a property) and <code>true</code> is returned.
	 *
	 * @return <code>true</code> if a reset of the counters was requested
	 */
	public static boolean consumeResetRequest() {
		if (!isBoomi()) {
			return false;
		}
		for (String name : new String[] { RESET_PROPERTY, RESET_PROPERTY.toLowerCase(Locale.ROOT) }) {
			if (trimToNull(getDynamicProcessProperty(name)) != null) {
				setDynamicProcessProperty(name, "");
				return true;
			}
		}
		return false;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Calls <code>ExecutionUtil.getDynamicProcessProperty(name)</code>
	 *
	 * @param name property name (case sensitive)
	 * @return the property value, or <code>null</code> when not running inside Boomi or in case of errors
	 */
	public static String getDynamicProcessProperty(String name) {
		if (!isBoomi()) {
			return null;
		}
		try {
			Object value = getDynamicProcessPropertyMethod.invoke(null, name);
			return value == null ? null : value.toString();
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.warn("Unable to read Dynamic Process Property '{}'", name, unwrap(e));
			return null;
		}
	}

	/**
	 * Calls <code>ExecutionUtil.setDynamicProcessProperty(name, value, false)</code>: the value is never persisted
	 * across executions.
	 *
	 * @param name property name (case sensitive)
	 * @param value property value
	 * @return <code>true</code> if the property has been set, <code>false</code> when not running inside Boomi or in
	 *         case of errors
	 */
	public static boolean setDynamicProcessProperty(String name, String value) {
		if (!isBoomi()) {
			return false;
		}
		try {
			setDynamicProcessPropertyMethod.invoke(null, name, value, Boolean.FALSE);
			return true;
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.warn("Unable to set Dynamic Process Property '{}'", name, unwrap(e));
			return false;
		}
	}

	private static synchronized void init() {
		if (!initialized) {
			lookup(EXECUTION_UTIL_CLASS, EXECUTION_MANAGER_CLASS);
		}
	}

	/**
	 * Looks up the Boomi methods (real ExecutionManager class). Package visible so that tests can provide a fake class.
	 *
	 * @param className fully qualified name of the class exposing the static property methods
	 */
	static synchronized void lookup(String className) {
		lookup(className, EXECUTION_MANAGER_CLASS);
	}

	/**
	 * Looks up the Boomi methods on the given classes. Package visible so that tests can provide fake Boomi classes.
	 *
	 * @param className fully qualified name of the class exposing the static property methods
	 * @param executionManagerClassName fully qualified name of the class exposing the static getCurrent()
	 */
	static synchronized void lookup(String className, String executionManagerClassName) {
		initialized = true;
		getDynamicProcessPropertyMethod = null;
		setDynamicProcessPropertyMethod = null;
		getRuntimeExecutionPropertyMethod = null;
		getCurrentExecutionMethod = null;
		getTopLevelExecutionIdMethod = null;
		topLevelExecutionIdUnavailable = false;

		Class<?> executionManager = findClass(executionManagerClassName);
		if (executionManager != null) {
			try {
				Method getCurrent = executionManager.getMethod("getCurrent");
				if (Modifier.isStatic(getCurrent.getModifiers()) && getCurrent.getParameterTypes().length == 0) {
					getCurrentExecutionMethod = getCurrent;
				}
			} catch (NoSuchMethodException | RuntimeException e) {
				LOGGER.debug("{}.getCurrent() not available", executionManagerClassName, e);
			}
		}

		Class<?> executionUtil = findClass(className);
		if (executionUtil == null) {
			LOGGER.debug("{} not found: not running inside Boomi", className);
			return;
		}

		for (Method method : executionUtil.getMethods()) {
			if (!Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			Class<?>[] params = method.getParameterTypes();
			if ("getDynamicProcessProperty".equals(method.getName()) && params.length == 1
					&& params[0].isAssignableFrom(String.class)) {
				getDynamicProcessPropertyMethod = method;
			} else if ("setDynamicProcessProperty".equals(method.getName()) && params.length == 3
					&& params[0].isAssignableFrom(String.class) && params[1].isAssignableFrom(String.class)
					&& (params[2] == boolean.class || params[2] == Boolean.class)) {
				setDynamicProcessPropertyMethod = method;
			} else if ("getRuntimeExecutionProperty".equals(method.getName()) && params.length == 1
					&& params[0].isAssignableFrom(String.class)) {
				getRuntimeExecutionPropertyMethod = method;
			}
		}

		if (getDynamicProcessPropertyMethod == null || setDynamicProcessPropertyMethod == null) {
			LOGGER.warn("{} found but its Dynamic Process Property methods are missing: Boomi support disabled",
					className);
			getDynamicProcessPropertyMethod = null;
			setDynamicProcessPropertyMethod = null;
		} else {
			LOGGER.info("Boomi runtime detected: Dynamic Process Properties enabled");
		}
	}

	/**
	 * Tells whether the Boomi runtime class {@value #EXECUTION_MANAGER_CLASS} can be loaded, without initializing it.
	 * Used when the driver class is initialized, before any Boomi method is needed.
	 *
	 * @return <code>true</code> when running inside Boomi
	 */
	public static boolean isExecutionManagerAvailable() {
		return isClassAvailable(EXECUTION_MANAGER_CLASS);
	}

	static boolean isClassAvailable(String className) {
		ClassLoader[] loaders = { Thread.currentThread().getContextClassLoader(),
				BoomiExecutionUtil.class.getClassLoader() };
		for (ClassLoader loader : loaders) {
			if (loader == null) {
				continue;
			}
			try {
				Class.forName(className, false, loader);
				return true;
			} catch (ClassNotFoundException e) {
				// try the next class loader
			} catch (LinkageError e) {
				LOGGER.debug("Unable to load {}", className, e);
			}
		}
		return false;
	}

	private static Class<?> findClass(String className) {
		ClassLoader[] loaders = { Thread.currentThread().getContextClassLoader(),
				BoomiExecutionUtil.class.getClassLoader() };
		for (ClassLoader loader : loaders) {
			if (loader == null) {
				continue;
			}
			try {
				return Class.forName(className, true, loader);
			} catch (ClassNotFoundException e) {
				// try the next class loader
			} catch (LinkageError e) {
				LOGGER.debug("Unable to load {}", className, e);
			}
		}
		return null;
	}

	private static Throwable unwrap(Exception e) {
		return e instanceof InvocationTargetException ? ((InvocationTargetException) e).getCause() : e;
	}
}
