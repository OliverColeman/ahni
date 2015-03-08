package com.ojcoleman.ahni.hyperneat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgapcustomised.BulkFitnessFunction;

import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.Transcriber;

/**
 * Extension of {@link com.anji.util.Properties} that provides some convenience methods for retrieving commonly required objects and
 * event communication functionality via {@link com.ojcoleman.ahni.event.AHNIEventListener}s. 
 */
public class Properties extends com.anji.util.Properties {
	private static final long serialVersionUID = 1L;
	
	private HyperNEATEvolver evolver;
	
	/**
	 * Creates a new empty Properties.
	 */
	public Properties() {
		super();
	}

	/**
	 * Creates a new Properties initialised with the given properties.
	 */
	public Properties(com.anji.util.Properties values) {
		super(values);
	}

	/**
	 * Creates a new Properties initialised with properties read from the given resource.
	 * @throws IOException
	 */
	public Properties(String resource) throws IOException {
		super();
		loadFromFileWithoutLogging(resource);
	}
	
	/**
	 * Get the HyperNEATConfiguration for this run.
	 */
	public HyperNEATConfiguration getConfig() {
		return (HyperNEATConfiguration) singletonObjectProperty(HyperNEATConfiguration.class);
	}
	
	/**
	 * Get the HyperNEATEvolver that is handling this run.
	 */
	public HyperNEATEvolver getEvolver() {
		// Typically the HyperNEATEvolver is created via Run, and it will call setEvolver.
		if (evolver == null) {
			evolver = (HyperNEATEvolver) singletonObjectProperty(HyperNEATEvolver.class);
		}
		return evolver;
	}
	
	/**
	 * Get the Transcriber for this run.
	 */
	public Transcriber getTranscriber() {
		return (Transcriber) singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
	}
	
	/**
	 * Get the BulkFitnessFunction for this run.
	 */
	public BulkFitnessFunction getFitnessFunction() {
		return (BulkFitnessFunction) singletonObjectProperty(HyperNEATEvolver.FITNESS_FUNCTION_CLASS_KEY);
	}
	
	/**
	 * Returns true if log files may be created.
	 * @see #getOutputDirPath()
	 */
	public boolean logFilesEnabled() {
		return containsKey(HyperNEATConfiguration.OUTPUT_DIR_KEY);
	}
	
	/**
	 * Modifies the Log4J settings to configure the file loggers.
	 * @param outputDir The directory that file loggers should create files in.
	 * @param disableFiles If true then all file logging is disabled. 
	 */
	public void configureLog4JSettings(String outputDir, boolean disableFiles) {
		// If logging is not disabled.
		if (!getProperty("log4j.rootLogger", "OFF").trim().startsWith("OFF")) {
			Set<String> propKeys = stringPropertyNames();
			// Find all logger labels that correspond to file loggers.
			ArrayList<String> fileLogLabels = new ArrayList<String>();
			ArrayList<String> fileLogProps = new ArrayList<String>();
			Pattern p = Pattern.compile("log4j\\.appender\\.(\\w*)\\.file", Pattern.CASE_INSENSITIVE);
			for (String k : propKeys) {
				Matcher m = p.matcher(k);
				if (m.matches()) {
					fileLogProps.add(m.group());
					fileLogLabels.add(m.group(1));
				}
			}
			if (!fileLogLabels.isEmpty()) {
				if (disableFiles) {
					// Construct a new root logger without the logger labels that corresponding to file loggers.
					String[] rootLoggerProp = getProperty("log4j.rootLogger").split(",");
					String newRootLoggerProp = rootLoggerProp[0];
					for (int i = 1; i < rootLoggerProp.length; i++) {
						if (!fileLogLabels.contains(rootLoggerProp[i].trim())) {
							newRootLoggerProp += ", " + rootLoggerProp[i].trim();
						}
					}
					setProperty("log4j.rootLogger", newRootLoggerProp);
				} else {
					// Make sure all file loggers are configured to output to the output dir.
					for (String prop : fileLogProps) {
						String val = getProperty(prop);
						if (!val.contains(outputDir)) {
							val = outputDir + val;
							setProperty(prop, val);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Returns the path of the directory where files for this experiment should be written to. Includes a trailing slash.
	 * @see #logFilesEnabled()
	 */
	public String getOutputDirPath() {
		return getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY, null);
	}
	
	/**
	 * Set the HyperNEATEvolver that is handling this run. This method should generally only be called by {@link HyperNEATEvolver}.
	 */
	public void setEvolver(HyperNEATEvolver evolver) {
		this.evolver = evolver;
	}
	
	
	// Below copied from com.anji.util.Properties to make use of our own Configurable interface.
	
	
	/**
	 * @param key <code>key</code>+<code>CLASS_SUFFIX</code> references property with fully qualified class name
	 * @return Object value corresponding to <code>key</code>; throws runtime exception if key not found
	 */
	public Object newObjectProperty(String key) {
		try {
			Class cl = getClassProperty(key + CLASS_SUFFIX);
			Object result = cl.newInstance();
			if (result instanceof Configurable) {
				((Configurable) result).init(this);
			}
			else if (result instanceof com.anji.util.Configurable) {
				((com.anji.util.Configurable) result).init(this);
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
	public<T> T newObjectProperty(Class<T> aClass) {
		try {
			T result = aClass.newInstance();
			if (result instanceof Configurable) {
				((Configurable) result).init(this);
			}
			else if (result instanceof com.anji.util.Configurable) {
				((com.anji.util.Configurable) result).init(this);
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("can't create object for class " + aClass + ":\n" + e);
		}
	}
}
