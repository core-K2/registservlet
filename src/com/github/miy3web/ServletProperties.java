package com.github.miy3web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletContext;

public class ServletProperties {
	public static final String[] PROPERTY_DIR_LIST = new String[] { "${user.dir}", "${catalina.base}/conf",
			"${catalina.base}", "${catalina.home}", "${java.home}", "" };

	private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{[_a-zA-Z0-9\\.]*\\}");

	private static final int VARIABLE_START = 2;

	private static final int VARIABLE_END = 1;

	public static Properties loadProperties(String asDir, String asFileName) {
		try {
			InputStream stream = null;
			try {
				if (asDir == null || "".equals(asDir)) {
					stream = ServletProperties.class.getClassLoader().getResourceAsStream(asFileName);
				} else {

					File lsFile = new File(asDir, asFileName);
					stream = new FileInputStream(lsFile.getAbsolutePath());
				}
			} finally {
				if (stream != null) {
					Properties lProp = new Properties();
					lProp.load(stream);
					stream.close();
					if (lProp != null) {

						replaceValues(lProp, System.getenv());

						replaceValues(lProp, System.getProperties());

						replaceValues(lProp, lProp);
					}
					return lProp;
				}
			}
		} catch (IOException iOException) {
		}

		return null;
	}

	public static Properties loadProperties(ServletContext aContext, String asFileName) {
		Properties lRet = loadProperties(aContext.getRealPath("/WEB-INF"), asFileName);
		if (lRet == null) {
			lRet = loadProperties(asFileName);
		}
		return lRet;
	}

	public static Properties loadProperties(String asFileName) {
		return loadProperties(PROPERTY_DIR_LIST, asFileName);
	}

	public static Properties loadProperties(String[] asProp, String asFileName) {
		Properties lSysProp = System.getProperties();
		for (int i = 0; i < asProp.length; i++) {
			String lsDir = replaceSystemValue(asProp[i], lSysProp);
			if (lsDir != null) {
				return loadProperties(lsDir, asFileName);
			}
		}
		return null;
	}

	public static String replaceSystemValue(String asValue) {
		return replaceSystemValue(asValue, System.getProperties());
	}

	public static String replaceSystemValue(String asValue, Map<?, ?> aMap) {
		return replaceValue(asValue, aMap, VARIABLE_PATTERN, VARIABLE_START, VARIABLE_END);
	}

	public static void replaceValues(Properties aProp, List<Map<?, ?>> aList) {
		for (Map<?, ?> lMap : aList) {
			replaceValues(aProp, lMap, VARIABLE_PATTERN, VARIABLE_START, VARIABLE_END);
		}
	}

	public static void replaceValues(Properties aProp, Map<?, ?> aMap) {
		replaceValues(aProp, aMap, VARIABLE_PATTERN, VARIABLE_START, VARIABLE_END);
	}

	private static void replaceValues(Properties aProp, Map<?, ?> aMap, Pattern aPattern, int aiStart, int aiEnd) {
		for (Object lKey : aProp.keySet()) {
			String lsKey = lKey.toString();
			String lsValue = aProp.getProperty(lsKey);
			if (aPattern.matcher(lsValue).find()) {
				aProp.setProperty(lsKey, replaceValue(lsValue, aMap, aPattern, aiStart, aiEnd));
			}
		}
	}

	private static String replaceValue(String asValue, Map<?, ?> aMap, Pattern aPattern, int aiStart, int aiEnd) {
		Matcher lMatcher = aPattern.matcher(asValue);
		while (lMatcher.find()) {

			String lsKey = lMatcher.group();
			Object lValue = aMap.get(lsKey.substring(aiStart, lsKey.length() - aiEnd));
			if (lValue != null) {
				try {
					String lsRep = replaceValue(lValue.toString(), aMap, aPattern, aiStart, aiEnd);
					int liLen = lsKey.length();
					int i;
					while ((i = asValue.indexOf(lsKey)) >= 0) {
						asValue = String.valueOf(asValue.substring(0, i)) + lsRep + asValue.substring(i + liLen);
					}
					lMatcher = aPattern.matcher(asValue);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return asValue;
	}
}
