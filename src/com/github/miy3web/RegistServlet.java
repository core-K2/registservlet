package com.github.miy3web;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebListener;

@WebListener
public class RegistServlet implements ServletContextListener {
	public static final String PROPERTY_FILE = "servlet.properties";
	private static final String PACKAGE_DELIMITER = "/";
	private static final String CLASS_DELIMITER = ".";
	private LogLevel _logLevel;

	public RegistServlet() {
		this._logLevel = LogLevel.ERROR;
	}

	private enum LogLevel {
		FATAL, ERROR, WARN, INFO, DEBUG, TRACE;
	}

	private void setLogLevel(String asLevel) {
		if (asLevel == null || "".equals(asLevel))
			return;
		byte b;
		int i;
		LogLevel[] arrayOfLogLevel;
		for (i = (arrayOfLogLevel = LogLevel.values()).length, b = 0; b < i;) {
			LogLevel lLevel = arrayOfLogLevel[b];
			if (asLevel.equals(lLevel.name()) || asLevel.equals(String.valueOf(lLevel.ordinal()))) {
				this._logLevel = lLevel;
				break;
			}
			b++;
		}

	}

	private static final SimpleDateFormat _sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");

	private static final String CLASS_EXT = ".class";

	private void outputLog(LogLevel aLevel, String asMsg) {
		if (aLevel.ordinal() <= this._logLevel.ordinal()) {
			((aLevel.compareTo(LogLevel.ERROR) <= 0) ? System.err : System.out)
					.println(String.valueOf(_sdf.format(new Date())) + " " + aLevel.name() + " > " + asMsg);
		}
	}

	public void contextDestroyed(ServletContextEvent event) {
		outputLog(LogLevel.TRACE, "RegistServlet contextDestroyed " + event.toString());
	}

	public void contextInitialized(ServletContextEvent event) {
		outputLog(LogLevel.TRACE, "RegistServlet contextInitialized " + event.toString());
		Properties lProps = ServletProperties.loadProperties(event.getServletContext(), "servlet.properties");
		ClassLoader lLoader = Thread.currentThread().getContextClassLoader();
		if (lProps == null || lLoader == null) {
			return;
		}
		String lsResources = lProps.getProperty("servlet.resources");
		if (lsResources == null) {
			return;
		}
		setLogLevel(lProps.getProperty("log.level"));
		String lsEncoding = lProps.getProperty("resource.encoding", "utf-8");
		String[] lsResource = lsResources.split(";");
		String lsMatch = lProps.getProperty("resource.match", "");
		String lsAvoid = lProps.getProperty("resource.avoid", "");
		String lsCMatch = lProps.getProperty("class.match", "");
		String lsCAvoid = lProps.getProperty("class.avoid", "");
		String lsRepPat = lProps.getProperty("name.replace.pattern", "");
		String lsRepStr = lProps.getProperty("name.replace.string", "");
		Pattern lMatch = "".equals(lsMatch) ? null : Pattern.compile(lsMatch);
		Pattern lAvoid = "".equals(lsAvoid) ? null : Pattern.compile(lsAvoid);
		Pattern lCMatch = "".equals(lsCMatch) ? null : Pattern.compile(lsCMatch);
		Pattern lCAvoid = "".equals(lsCAvoid) ? null : Pattern.compile(lsCAvoid);
		Pattern lRepPat = "".equals(lsRepPat) ? null : Pattern.compile(lsRepPat);
		List<Class<?>> lClasses = new ArrayList<>();
		for (int i = 0; i < lsResource.length; i++) {
			lClasses.addAll(getResourceClasses(lLoader, lsEncoding, lsResource[i], lMatch, lAvoid, lCMatch, lCAvoid));
		}

		String lsPrefix = lProps.getProperty("servlet.urlPrefix", "");
		Pattern lPattern = Pattern.compile(lProps.getProperty("servlet.superclass", "Servlet"));
		ServletContext lContext = event.getServletContext();
		for (Class<?> lClass : lClasses) {
			if (lClass == null) {
				continue;
			}
			try {
				int liMod = lClass.getModifiers();

				if (!Modifier.isPublic(liMod) || Modifier.isAbstract(liMod)) {
					continue;
				}
				if (isSuperClassOf(lClass, lPattern)) {
					registServletClass(lContext, lClass, lsPrefix, lRepPat, lsRepStr);
				}
			} catch (Exception ex) {
				outputLog(LogLevel.FATAL, "Class:" + lClass.getName() + "\t" + ex.toString());
			}
		}
	}

	private boolean isSuperClassOf(Class<?> aClass, Pattern aPattern) {
		for (Class<?> lClass = aClass; !lClass.isPrimitive();) {
			String lsName = lClass.getName();
			if (lsName.endsWith(".Object")) {
				break;
			}
			if (aPattern.matcher(lsName).find()) {
				return true;
			}
			lClass = lClass.getSuperclass();
		}
		return false;
	}

	private boolean registServletClass(ServletContext context, Class<?> aClass, String asPrefix, Pattern aRepPat,
			String asRepStr) {
		try {
			String lsName = aClass.getName();
			Servlet servlet = createServlet(context, aClass);
			ServletRegistration.Dynamic dynamic = context.addServlet(lsName, servlet);
			if (dynamic != null) {
				if (aRepPat != null) {
					lsName = aRepPat.matcher(lsName).replaceAll(asRepStr);
				}
				String lsUrl = String.valueOf(asPrefix) + lsName;
				Set<?> conflicts = dynamic.addMapping(new String[] { lsUrl });
				if (conflicts.isEmpty()) {
					outputLog(LogLevel.INFO, "Mapping : " + lsUrl);
					return true;
				}
				outputLog(LogLevel.ERROR, "!!! Duplicate !!! : " + lsUrl);
			}

		} catch (Exception e) {
			outputLog(LogLevel.FATAL, e.getMessage());
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private Servlet createServlet(ServletContext context, Class<?> aClass) {
		Servlet servlet = null;
		try {
			servlet = context.createServlet((Class<Servlet>) aClass);
		} catch (ServletException e) {
			outputLog(LogLevel.ERROR, String.valueOf(aClass.getName()) + " ERROR:" + e.getMessage());
		}
		return servlet;
	}

	private List<Class<?>> getResourceClasses(ClassLoader aLoader, String asEncoding, String asUrl, Pattern aMatch,
			Pattern aAvoid, Pattern aCMatch, Pattern aCAvoid) {
		List<Class<?>> lClasses = new ArrayList<>();
		try {
			outputLog(LogLevel.DEBUG, "Enumeration " + asUrl);
			Enumeration<URL> resources = aLoader.getResources(asUrl);
			while (resources.hasMoreElements()) {
				URL lUrl = resources.nextElement();
				String lsUrl = URLDecoder.decode(lUrl.getPath(), asEncoding);
				String lsProt = lUrl.getProtocol();
				boolean lbOK = isMatchPattern(lsUrl, aMatch, aAvoid);
				outputLog(LogLevel.DEBUG, String.valueOf(lsUrl) + " prot:" + lsProt + (!lbOK ? " AVOID" : ""));
				if (lbOK) {
					if ("file".equals(lsProt)) {
						lClasses.addAll(findClassesWithFile(aLoader, asUrl, new File(lsUrl), aCMatch, aCAvoid));
						continue;
					}
					if ("jar".equals(lsProt)) {
						lClasses.addAll(findClassesWithJarFile(aLoader, asUrl, lUrl, aCMatch, aCAvoid));
					}
				}

			}
		} catch (Exception e) {
			outputLog(LogLevel.FATAL, e.getMessage());
		}
		return lClasses;
	}

	private boolean isMatchPattern(String asName, Pattern aMatch, Pattern aAvoid) {
		return (aMatch != null && aMatch.matcher(asName).find()) ? true
				: (!(aAvoid != null && aAvoid.matcher(asName).find()));
	}

	private static final int CLASS_EXT_LEN = CLASS_EXT.length();

	private boolean isClassFile(String asFileName) {
		return asFileName.endsWith(CLASS_EXT);
	}

	private String fileNameToClassName(String asName) {
		return isClassFile(asName) ? asName.substring(0, asName.length() - CLASS_EXT_LEN) : asName;
	}

	private String resourceNameToClassName(String asResourceName) {
		return fileNameToClassName(asResourceName).replace(PACKAGE_DELIMITER, CLASS_DELIMITER);
	}

	private Class<?> loadClass(ClassLoader aLoader, String asName) {
		Class<?> lClass = null;
		String lsClassName = resourceNameToClassName(asName);
		try {
			while (lsClassName.startsWith(CLASS_DELIMITER)) {
				lsClassName = lsClassName.substring(1);
			}
			lClass = aLoader.loadClass(lsClassName);
		} catch (NoClassDefFoundError e) {
			outputLog(LogLevel.ERROR, String.valueOf(lsClassName) + " not found");
		} catch (Exception e) {
			outputLog(LogLevel.FATAL, String.valueOf(lsClassName) + " Error\r\n" + e.getMessage());
		}
		return lClass;
	}

	private List<Class<?>> findClassesWithFile(ClassLoader aLoader, String asPackageName, File aDir, Pattern aMatch,
			Pattern aAvoid) throws Exception {
		List<Class<?>> lClasses = new ArrayList<>();
		byte b;
		int i;
		String[] arrayOfString;
		for (i = (arrayOfString = aDir.list()).length, b = 0; b < i;) {
			String lsPath = arrayOfString[b];
			File lEntry = new File(aDir, lsPath);
			String lsName = lEntry.getName();
			String lsFile = String.valueOf(asPackageName) + PACKAGE_DELIMITER + lsName;
			if (lEntry.isFile()) {
				if (isClassFile(lsName)) {
					if (isMatchPattern(lsName, aMatch, aAvoid)) {
						lClasses.add(loadClass(aLoader, lsFile));
					} else {
						outputLog(LogLevel.TRACE, String.valueOf(lsFile) + " AVOID");
					}
				} else if (lsPath.endsWith(".jar")) {
					File lFile = new File(lEntry.getCanonicalPath());
					findClassesWithJarFile(aLoader, asPackageName, lFile.toURI().toURL(), aMatch, aAvoid);
				} else {
					outputLog(LogLevel.TRACE, String.valueOf(lsFile) + " UnKnown");
				}
			} else if (lEntry.isDirectory()) {
				lClasses.addAll(findClassesWithFile(aLoader, lsFile, lEntry, aMatch, aAvoid));
			} else {
				outputLog(LogLevel.WARN, String.valueOf(lsFile) + " Unexpected!!!");
			}
			b++;
		}

		return lClasses;
	}

	private List<Class<?>> findClassesWithJarFile(ClassLoader aLoader, String asPackageName, URL aJarUrl,
			Pattern aMatch, Pattern aAvoid) throws Exception {
		List<Class<?>> lClasses = new ArrayList<>();
		JarFile lJarFile = null;
		try {
			if ("file".equals(aJarUrl.getProtocol())) {
				lJarFile = new JarFile(aJarUrl.getPath());
			} else {
				JarURLConnection lConnection = (JarURLConnection) aJarUrl.openConnection();
				lJarFile = lConnection.getJarFile();
			}
			Enumeration<JarEntry> lJarEnum = lJarFile.entries();
			while (lJarEnum.hasMoreElements()) {
				JarEntry lEntry = lJarEnum.nextElement();
				String lsName = lEntry.getName();
				if (isClassFile(lsName)) {
					if (isMatchPattern(lsName, aMatch, aAvoid)) {
						lClasses.add(loadClass(aLoader, lsName));
						continue;
					}
					outputLog(LogLevel.TRACE, String.valueOf(lsName) + " AVOID");
				}

			}
		} catch (Exception e) {
			outputLog(LogLevel.ERROR, String.valueOf(aJarUrl.toString()) + " ERROR!!!\r\n" + e.getMessage());
		} finally {
			if (lJarFile != null) {
				lJarFile.close();
			}
		}
		return lClasses;
	}
}
