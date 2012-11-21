/*
 * Copyright (C) 2004 Derek James and Philip Tucker This file is part of ANJI (Another NEAT Java
 * Implementation). ANJI is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA created by Philip Tucker on May 12, 2003
 */
package com.anji.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import ojc.ahni.hyperneat.HyperNEATEvolver;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.regexp.RE;

/**
 * Beefed up version of java.util.Properties used to manage configuration parameters. Adds convenience functions for
 * converting String properties to various data types and logs all default and configured property settings.
 * 
 * @author Philip Tucker
 */
public class Properties extends java.util.Properties {

	/**
	 * made public for unit tests
	 */
	public final static String CLASS_SUFFIX = ".class";

	private static Logger logger = Logger.getLogger(Properties.class.getName());

	private HashSet loggedProps = new HashSet();

	private HashMap keyToSingletonsMap = new HashMap();

	private HashMap classToSingletonsMap = new HashMap();

	private String name = "default";

	/**
	 * default constructor
	 */
	public Properties() {
		super();
	}

	/**
	 * Initialize with <code>values</code>.
	 * 
	 * @param values
	 */
	public Properties(java.util.Properties values) {
		super();
		putAll(values);
	}

	/**
	 * initialize properties from file; also, initializes <code>Logger</code>
	 * 
	 * @param resource a file in the application classpath
	 * @throws IOException
	 */
	public Properties(String resource) throws IOException {
		super();
		loadFromResource(resource);
		logger.info("loaded properties from " + resource);
	}

	/**
	 * loads properties from file; also, initializes <code>Logger</code>
	 * 
	 * @param resource a file in the application classpath
	 * @throws IOException
	 */
	public void loadFromResource(String resource) throws IOException {
		loadFromResourceWithoutLogging(resource);
		java.util.Properties log4jProps = new java.util.Properties();
		log4jProps.putAll(this);
		PropertyConfigurator.configure(log4jProps);
	}

	/**
	 * loads properties from file
	 * 
	 * @param resource a file in the application classpath
	 * @throws IOException
	 */
	public void loadFromResourceWithoutLogging(String resource) throws IOException {
		InputStream in = ClassLoader.getSystemResourceAsStream(resource);
		load(in);
		setName(resource);
	}

	/**
	 * Log each key/value pair requested of this object exactly once. If value is not specified in properties, log
	 * default value.
	 * 
	 * @param key
	 * @param value
	 * @param defaultValue
	 */
	private void log(String key, String value, String defaultValue) {

		synchronized (loggedProps) {
			if (loggedProps.contains(key) == false) {
				StringBuffer log = new StringBuffer("Properties: ");
				log.append(key).append(" == ").append(value);
				if (value == null)
					log.append(" [").append(defaultValue).append("]");
				logger.info(log);
				loggedProps.add(key);
			}
		}

	}

	/**
	 * @param key
	 * @return String property value corresponding to <code>key</code>; throws runtime exception if key not found
	 */
	public String getProperty(String key) {
		String value = super.getProperty(key);
		if (value == null)
			throw new IllegalArgumentException("no value for " + key);
		log(key, value, null);
		return value;
	}

	/**
	 * @param key
	 * @return boolean property value corresponding to <code>key</code>; throws runtime exception if key not found
	 */
	public boolean getBooleanProperty(String key) {
		String value = super.getProperty(key);
		if (value == null)
			throw new IllegalArgumentException("no value for " + key);
		log(key, value, null);
		return Boolean.valueOf(value).booleanValue();
	}

	/**
	 * @param key
	 * @return long property value corresponding to <code>key</code>; throws runtime exception if key not found or
	 *         invalid long
	 */
	public long getLongProperty(String key) {
		try {
			String value = super.getProperty(key);
			if (value == null)
				throw new IllegalArgumentException("no value for " + key);
			log(key, value, null);
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("bad value for property " + key + ": " + e);
		}
	}

	/**
	 * @param key
	 * @return int property value corresponding to <code>key</code; throws runtime exception if key not found or invalid
	 *         integer
	 */
	public int getIntProperty(String key) {
		try {
			String value = super.getProperty(key);
			if (value == null)
				throw new IllegalArgumentException("no value for " + key);
			log(key, value, null);
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("bad value for property " + key + ": " + e);
		}
	}

	/**
	 * @param key
	 * @return short property value corresponding to <code>key</code; throws runtime exception if key not found or
	 *         invalid short
	 */
	public short getShortProperty(String key) {
		try {
			String value = super.getProperty(key);
			if (value == null)
				throw new IllegalArgumentException("no value for " + key);
			log(key, value, null);
			return Short.parseShort(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("bad value for property " + key + ": " + e);
		}
	}

	/**
	 * @param key
	 * @return double property value corresponding to <code>key</code>; throws runtime exception if key not found or
	 *         invalid double
	 */
	public double getFloatProperty(String key) {
		try {
			String value = super.getProperty(key);
			if (value == null)
				throw new IllegalArgumentException("no value for " + key);
			log(key, value, null);
			return Float.parseFloat(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("bad value for property " + key + ": " + e);
		}
	}

	/**
	 * @param key
	 * @return double property value corresponding to <code>key</code>; throws runtime exception if key not found or
	 *         invalid double
	 */
	public double getDoubleProperty(String key) {
		try {
			String value = super.getProperty(key);
			if (value == null)
				throw new IllegalArgumentException("no value for " + key);
			log(key, value, null);
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("bad value for property " + key + ": " + e);
		}
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return String property value corresponding to <code>key</code>., or <code>defaultVal</code> if key not found
	 */
	public String getProperty(String key, String defaultVal) {
		String value = super.getProperty(key);
		log(key, value, defaultVal);
		return (value == null) ? defaultVal : value;
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return boolean property value corresponding to <code>key</code>., or <code>defaultVal</code> if key not found
	 */
	public boolean getBooleanProperty(String key, boolean defaultVal) {
		String value = super.getProperty(key);
		log(key, value, Boolean.toString(defaultVal));
		return (value == null) ? defaultVal : Boolean.valueOf(value).booleanValue();
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return long property value corresponding to <code>key</code>., or <code>defaultVal</code> if key not found
	 */
	public long getLongProperty(String key, long defaultVal) {
		String value = super.getProperty(key);
		log(key, value, Long.toString(defaultVal));
		return (value == null) ? defaultVal : Long.parseLong(value);
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return int property value corresponding to <code>key</code>., or <code>defaultVal</code> if key not found
	 */
	public int getIntProperty(String key, int defaultVal) {
		String value = super.getProperty(key);
		log(key, value, Integer.toString(defaultVal));
		return (value == null) ? defaultVal : Integer.parseInt(value);
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return short property value corresponding to <code>key</code>., or <code>defaultVal</code> if key not found
	 */
	public short getShortProperty(String key, short defaultVal) {
		String value = super.getProperty(key);
		log(key, value, Short.toString(defaultVal));
		return (value == null) ? defaultVal : Short.parseShort(value);
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return double property value corresponding to <code>key</code>., or <code>defaultVal</code> if key not found
	 */
	public float getFloatProperty(String key, float defaultVal) {
		String value = super.getProperty(key);
		log(key, value, Float.toString(defaultVal));
		return (value == null) ? defaultVal : Float.parseFloat(value);
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return double property value corresponding to <code>key</code>., or <code>defaultVal</code> if key not found
	 */
	public double getDoubleProperty(String key, double defaultVal) {
		String value = super.getProperty(key);
		log(key, value, Double.toString(defaultVal));
		return (value == null) ? defaultVal : Double.parseDouble(value);
	}

	/**
	 * Returns property keys matching regular expression pattern.
	 * 
	 * @param pattern interpreted as regular expression
	 * @return Set contains String objects
	 */
	public Set getKeysForPattern(String pattern) {
		RE regex = new RE(pattern);
		Set result = new HashSet();
		Iterator it = super.keySet().iterator();
		while (it.hasNext()) {
			String key = (String) it.next();
			if (regex.match(key))
				result.add(key);
		}
		return result;
	}

	/**
	 * Returns property values for keys matching regular expression pattern.
	 * 
	 * @param pattern interpreted as regular expression
	 * @return Set contains String objects
	 */
	public Set getPropertiesForPattern(String pattern) {
		RE regex = new RE(pattern);
		Set result = new HashSet();
		Iterator it = super.keySet().iterator();
		while (it.hasNext()) {
			String key = (String) it.next();
			if (regex.match(key))
				result.add(getProperty(key));
		}
		return result;
	}

	/**
	 * Return properties filtered for a particular sub-component, based on <code>prefix</code>.
	 * 
	 * @param prefix
	 * @return <code>Properties</code> object properties contained in this object, but for those cases where the
	 *         property starts with <code>prefix</code>, the property overrides another property of the same key; e.g.,
	 *         if <code>prefix</code> is "abc", and there is an "abc.foo=bar" property and a "foo=bat" property, this
	 *         method returns a properties object containing "foo=bar"
	 */
	public Properties getSubProperties(String prefix) {
		Properties result = new Properties(this);
		Properties newProps = new Properties();
		Iterator it = keySet().iterator();
		while (it.hasNext()) {
			String key = (String) it.next();
			if (key.startsWith(prefix)) {
				result.remove(key);
				newProps.put(key.substring(prefix.length()), super.getProperty(key));
			}
		}
		result.putAll(newProps);
		result.setName(prefix);
		return result;
	}

	/**
	 * @param key
	 * @return File directory property value corresponding to <code>key</code>; throws runtime exception if key not
	 *         found
	 */
	public File getDirProperty(String key) {
		String value = super.getProperty(key);
		if (value == null)
			throw new IllegalArgumentException("no value for " + key);
		log(key, value, null);
		File result = new File(value);
		if (result.exists()) {
			if (result.isDirectory() == false)
				throw new IllegalArgumentException("property " + key + "=" + value + " is not a directory");
		} else {
			if (result.mkdir() == false)
				throw new IllegalStateException("failed creating directory " + key + "=" + value);
		}
		return result;
	}

	/**
	 * @param key
	 * @return FileInputStream property value corresponding to <code>key</code>; throws runtime exception if key not
	 *         found
	 * @throws FileNotFoundException
	 */
	public FileInputStream getFileInputProperty(String key) throws FileNotFoundException {
		String value = super.getProperty(key);
		if (value == null)
			throw new IllegalArgumentException("no value for " + key);
		log(key, value, null);
		return new FileInputStream(value);
	}

	/**
	 * @param key
	 * @return FileOutputStream property value corresponding to <code>key</code>; throws runtime exception if key not
	 *         found
	 * @throws FileNotFoundException
	 */
	public FileOutputStream getFileOutputProperty(String key) throws FileNotFoundException {
		String value = super.getProperty(key);
		if (value == null)
			throw new IllegalArgumentException("no value for " + key);
		log(key, value, null);
		return new FileOutputStream(value);
	}

	/**
	 * @param key
	 * @return InputStream resource (i.e., searches classpath for it) property value corresponding to <code>key</code>;
	 *         throws runtime exception if key not found
	 */
	public InputStream getResourceProperty(String key) {
		String value = super.getProperty(key);
		if (value == null)
			throw new IllegalArgumentException("no value for " + key);
		log(key, value, null);
		return ClassLoader.getSystemResourceAsStream(value);
	}

	/**
	 * @param key
	 * @return Object value corresponding to <code>key</code>; throws runtime exception if key not found
	 */
	public Object singletonObjectProperty(String key) {
		synchronized (keyToSingletonsMap) {
			Object result = keyToSingletonsMap.get(key);
			if (result == null) {
				result = newObjectProperty(key);
				keyToSingletonsMap.put(key, result);
			}
			return result;
		}
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return name + ": " + super.toString();
	}

	/**
	 * @param aClass
	 * @return Object singleton instance of <code>cl</code> initialized with properties if it is
	 *         <code>Configurable</code>
	 */
	public Object singletonObjectProperty(Class aClass) {
		//System.err.println(System.identityHashCode(this) + "  " + name + " singletonObjectProperty(Class aClass)  ENTER");
		synchronized (classToSingletonsMap) {
			///System.err.println(System.identityHashCode(this) + "  " + name + " singletonObjectProperty(Class aClass)  IN synchronized");
			Object result = classToSingletonsMap.get(aClass);
			if (result == null) {
				result = newObjectProperty(aClass);
				classToSingletonsMap.put(aClass, result);
			}
			//System.err.println(System.identityHashCode(this) + "  " + name + " singletonObjectProperty(Class aClass)  EXIT");
			return result;
		}
	}

	/**
	 * @param key <code>key</code>+<code>CLASS_SUFFIX</code> references property with fully qualified class name
	 * @return Object value corresponding to <code>key</code>; throws runtime exception if key not found
	 */
	public Object newObjectProperty(String key) {
		try {
			Class cl = getClassProperty(key + CLASS_SUFFIX);
			Object result = cl.newInstance();
			if (result instanceof Configurable) {
				Configurable conf = (Configurable) result;
				//conf.init(getSubProperties(key + "."));
				conf.init(this);
			}
			return result;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("can't create object for key " + key + ": " + e);
		}
	}

	/**
	 * @param aClass
	 * @return Object new instance of class <code>cl</code>, initialized with properties if <code>Configurable</code>
	 *         not found
	 */
	public Object newObjectProperty(Class aClass) {
		try {
			Object result = aClass.newInstance();
			if (result instanceof Configurable) {
				Configurable c = (Configurable) result;
				c.init(this);
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("can't create object for class " + aClass + ":\n" + e);
		}
	}

	/**
	 * @param key
	 * @return <code>Class</code> corresponding to full package specification in property value associated with
	 *         <code>key</code>
	 * @throws IllegalArgumentException if key not found
	 */
	public Class getClassProperty(String key) {
		try {
			String value = super.getProperty(key);
			if (value == null)
				throw new IllegalArgumentException("no value for " + key);
			log(key, value, null);
			Class myClass = Class.forName(value);
			return myClass;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("can't get class for key " + key + ": " + e);
		}
	}

	/**
	 * @param key
	 * @param defaultVal
	 * @return <code>Class</code> corresponding to full package specification in property value associated with
	 *         <code>key</code>; returns <code>defaultVal</code> if key not found
	 */
	public Class getClassProperty(String key, Class defaultVal) {
		String val = super.getProperty(key);
		log(key, val, defaultVal.toString());
		if (val == null)
			return defaultVal;

		try {
			Class myClass = Class.forName(val);
			return myClass;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("can't get class for key " + key + ": " + e);
		}
	}

	/**
	 * @param in
	 * @return <code>double[][]</code> matrix generated by interpreting contents of <code>in</code> as a 2-dimensional
	 *         array of values; columns separated by semi-colons, rows separated by "\n"
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static double[][] loadArrayFromFile(InputStream in) throws FileNotFoundException, IOException {
		// better class for this? combine w/ getProperty
		List rows = new ArrayList();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			double[] row = loadRowFromString(line);
			rows.add(row);
		}

		double[][] result = new double[rows.size()][];
		Iterator it = rows.iterator();
		int i = 0;
		while (it.hasNext())
			result[i++] = (double[]) it.next();
		return result;
	}

	/**
	 * TODO - better class for this? combine w/ getProperty
	 * 
	 * @param in
	 * @return <code>boolean[][]</code> matrix generated by interpreting contents of <code>in</code> as a 2-dimensional
	 *         array of values; columns separated by semi-colons, rows separated by "\n"
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static boolean[][] loadBooleanArrayFromFile(InputStream in) throws FileNotFoundException, IOException {
		List rows = new ArrayList();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			boolean[] row = loadBooleanRowFromString(line);
			rows.add(row);
		}

		boolean[][] result = new boolean[rows.size()][];
		Iterator it = rows.iterator();
		int i = 0;
		while (it.hasNext())
			result[i++] = (boolean[]) it.next();
		return result;
	}

	/**
	 * TODO - better class for this? combine w/ getProperty
	 * 
	 * @param line
	 * @return <code>double[]</code> generated by interpreting contents of <code>line</code> as a list of values
	 *         separated by semi-colons
	 */
	protected static double[] loadRowFromString(String line) {
		List row = new ArrayList();
		StringTokenizer tok = new StringTokenizer(line, "; ");
		while (tok.hasMoreTokens()) {
			String element = tok.nextToken();
			Double value = Double.valueOf(element);
			row.add(value);
		}

		double[] result = new double[row.size()];
		Iterator it = row.iterator();
		int i = 0;
		while (it.hasNext())
			result[i++] = ((Double) it.next()).doubleValue();
		return result;
	}

	/**
	 * TODO - better class for this? combine w/ getProperty
	 * 
	 * @param line
	 * @return <code>boolean[]</code> generated by interpreting contents of <code>line</code> as a list of values
	 *         separated by semi-colons
	 */
	public static boolean[] loadBooleanRowFromString(String line) {
		List row = new ArrayList();
		StringTokenizer tok = new StringTokenizer(line, "; ");
		while (tok.hasMoreTokens()) {
			String element = tok.nextToken();
			Boolean value = Boolean.valueOf(element);
			row.add(value);
		}

		boolean[] result = new boolean[row.size()];
		Iterator it = row.iterator();
		int i = 0;
		while (it.hasNext())
			result[i++] = ((Boolean) it.next()).booleanValue();
		return result;
	}

	/**
	 * Return list of objects initialized from properties. Each object has a name given in a comma-separated list in
	 * property value for <code>key</code>. Each object, if it is <code>Configurable</code>, is initialized with
	 * properties prefixed with its name, plus remainder of properties. The class of each object is determined by a
	 * property named object name +<code>CLASS_SUFFIX</code>
	 * 
	 * @param key
	 * @param defaultList returns this if no property <code>key</code> exists
	 * @return <code>List</code> contains initialized objects
	 */
	public List newObjectListProperty(String key, List defaultList) {
		String val = super.getProperty(key);
		if (val == null) {
			log(key, val, defaultList == null ? "null" : defaultList.toString());
			return defaultList;
		}

		StringTokenizer tok = new StringTokenizer(val, ",");
		List results = new ArrayList();
		while (tok.hasMoreTokens()) {
			String objectName = tok.nextToken().trim();
			Object o = newObjectProperty(objectName);
			results.add(o);
		}
		log(key, val, null);
		return results;
	}

	/**
	 * Throws <code>IllegalArgumentException</code> exception if key not present.
	 * 
	 * @param key
	 * @return <code>List</code> contains initialized objects
	 * @see Properties#newObjectListProperty(String, List)
	 */
	public List newObjectListProperty(String key) {
		List result = newObjectListProperty(key, null);
		if (result == null)
			throw new IllegalArgumentException("no value for " + key);
		return result;
	}

	/**
	 * @return name of this property set
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param aName name of this property set
	 */
	protected void setName(String aName) {
		name = aName;
	}
}
