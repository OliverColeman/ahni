package ojc.ahni.hyperneat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.log4j.Logger;
import org.apache.log4j.FileAppender;
import org.apache.log4j.PropertyConfigurator;

import ojc.ahni.hyperneat.HyperNEATEvolver;
import ojc.ahni.util.ArrayUtil;
import ojc.ahni.util.PostProcess;
import ojc.ahni.util.PropertiesConverter;

import com.anji.util.Misc;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

/**
 * <p>
 * This is the main class from which experiment runs are performed. The main purpose of this class is to allow
 * performing multiple evolutionary runs and aggregating the result.
 * </p>
 * <p>
 * For each run a new {@link Properties} object is generated from the properties file specified on the command line. The
 * Properties object encapsulates all the configuration parameters for a run, and can be used to retrieve these
 * properties as well as generate and retrieve singletons of most components used in a run (e.g. the
 * {@link HyperNEATEvolver}, {@link org.jgapcustomised.BulkFitnessFunction} and {@link com.anji.integration.Transcriber}
 * ).
 * </p>
 */
public class Run {
	private static Logger logger = Logger.getLogger(Run.class);
	private static final DecimalFormat nf = new DecimalFormat("0.0000");

	/**
	 * Disable all output to files and terminal.
	 */
	public boolean noOutput = false;

	@Parameter(names = { "-aggresult", "-ar" }, description = "Suffix of names of files to write aggregate results to (CSV files containing raw results and stats over all runs).")
	public String resultFileNameBase = "results";

	@Parameter(names = { "-nofiles", "-nf" }, description = "Do not generate any files (only output will be to terminal).")
	public boolean noFiles = false;

	@Parameter(names = { "-aggonly", "-ao" }, description = "Only generate files for aggregate results.")
	public boolean aggFilesOnly = false;

	@Parameter(names = { "-outputdir", "-od" }, description = "Directory to write output files to (overrides output.dir in properties file).")
	public String outputDir = null;

	@Parameter(names = { "-force", "-f" }, description = "Force using the specified output directory even if it exists.")
	public boolean forceOutputDir = false;

	@Parameter(converter = PropertiesConverter.class, arity = 1, description = "<Properties file to read experiment parameters from>")
	public List<Properties> propertiesFiles = new ArrayList<Properties>(1);

	Properties properties;
	
	/**
	 * The best performance for each generation for each run, in the format [run][generation].
	 * This will only be populated after {@link #run()} has completed.
	 */
	public double[][] performance;
	
	/**
	 * The best fitness for each generation for each run, in the format [run][generation].
	 * This will only be populated after {@link #run()} has completed.
	 */
	public double[][] fitness;


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Run runner = new Run();
			JCommander jcom = new JCommander(runner, args);
			if (runner.propertiesFiles.isEmpty()) {
				jcom.usage();
				System.exit(-1);
			}
			runner.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Run() throws IOException {
	}

	public Run(Properties props) {
		properties = props;
	}

	/**
	 * Performs one or more runs.
	 * 
	 * @return The final (average) fitness.
	 */
	public void run() throws Exception {
		if (properties == null) {
			properties = propertiesFiles.get(0);
		}

		long experimentID = System.currentTimeMillis();
		String runLogFile = properties.getProperty("log4j.appender.RunLog.File", null);

		// If there should be no output whatsoever.
		if (noOutput) {
			properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
			outputDir = null;
			resultFileNameBase = null;
			runLogFile = null;
		}
		// If no files should be generated (but output to terminal is allowed).
		else if (noFiles) {
			properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
			outputDir = null;
			resultFileNameBase = null;
			runLogFile = null;
			configureLog4J(true);
		}
		// If all or aggregate output is allowed.
		else {
			if (outputDir == null) {
				outputDir = properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + File.separator + experimentID;
			}
			if (!outputDir.endsWith("\\") && !outputDir.endsWith("/")) {
				outputDir += File.separator;
			}
			// NOTE: outputDir is kept as a relative path to allow the program to be relocated to another machine, for
			// example by HTCondor.

			if (!forceOutputDir && (new File(outputDir)).exists()) {
				throw new IllegalArgumentException("Output directory " + outputDir + " already exists.");
			}

			configureLog4J(aggFilesOnly);
			resultFileNameBase = outputDir + resultFileNameBase;

			// If only aggregate result files should be produced.
			if (aggFilesOnly) {
				// Disable file output for everything else (now that we've set resultFileNameBase).
				outputDir = null;
				runLogFile = null;
				properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
			} else {
				logger.info("Output directory is " + outputDir + ".");
			}

			logger.info("Performance results will be written to " + resultFileNameBase + "-[performance|fitness].");
		}

		int numRuns = properties.getIntProperty(HyperNEATConfiguration.NUM_RUNS_KEY);

		performance = new double[numRuns][];
		fitness = new double[numRuns][];

		long start = System.currentTimeMillis();
		double avgRunTime = 0;
		for (int run = 0; run < numRuns; run++) {
			long startRun = System.currentTimeMillis();

			Properties runProps = new Properties(properties);
			String runID = properties.getProperty("run.name") + "-" + experimentID + (numRuns > 1 ? "-" + run : "");
			runProps.setProperty("run.id", runID);

			String runOutputDir = outputDir + (numRuns > 1 ? run + File.separator : "");
			if (outputDir != null) {
				runProps.setProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY, runOutputDir);

				// If there is a file logger for each run.
				if (runLogFile != null) {
					FileAppender fileAppender = (FileAppender) Logger.getRootLogger().getAppender("RunLog");
					fileAppender.setFile(runOutputDir + runLogFile);
					fileAppender.activateOptions();

				}
			}

			logger.info("\n\n--- START RUN: " + (run + 1) + " of " + numRuns + " (" + ((run * 100) / (numRuns)) + "%) ---------------------------------------\n\n");
			HyperNEATEvolver evolver = (HyperNEATEvolver) runProps.singletonObjectProperty(HyperNEATEvolver.class);

			evolver.run();

			performance[run] = evolver.getBestPerformance();
			fitness[run] = evolver.getBestFitness();

			evolver.dispose();

			long duration = (System.currentTimeMillis() - startRun) / 1000;
			if (avgRunTime == 0)
				avgRunTime = duration;
			else
				avgRunTime = avgRunTime * 0.9 + duration * 0.1;
			int eta = (int) Math.round(avgRunTime * (numRuns - (run + 1)));
			logger.info("\n--- Run finished in " + Misc.formatTimeInterval(duration) + ".  ETA to complete all runs:" + Misc.formatTimeInterval(eta) + ". ------------------\n");
		}
		long end = System.currentTimeMillis();

		// If there is a file logger for each run, set log file back to root output dir.
		if (runLogFile != null) {
			FileAppender fileAppender = (FileAppender) Logger.getRootLogger().getAppender("RunLog");
			fileAppender.setFile(outputDir + runLogFile);
			fileAppender.activateOptions();

		}
		logger.info(numRuns + " runs completed in " + Misc.formatTimeInterval((end - start) / 1000));

		if (resultFileNameBase != null) {
			String resultPerfFile = resultFileNameBase + "-performance.csv";
			String resultFitFile = resultFileNameBase + "-fitness.csv";

			BufferedWriter resultFilePerf = new BufferedWriter(new FileWriter(resultPerfFile));
			BufferedWriter resultFileFit = new BufferedWriter(new FileWriter(resultFitFile));
			for (int run = 0; run < numRuns; run++) {
				resultFilePerf.append(ArrayUtil.toString(performance[run], ", ", nf));
				resultFilePerf.append("\n");
				resultFileFit.append(ArrayUtil.toString(fitness[run], ", ", nf));
				resultFileFit.append("\n");
			}
			resultFilePerf.close();
			resultFileFit.close();
			logger.info("Wrote best performance for each generation in each run to " + resultPerfFile);
			logger.info("Wrote best fitness for each generation in each run to " + resultFitFile);

			// Only do stats if there's more than one run.
			if (numRuns > 1) {
				// Get results in [generation][run] format.
				double[][] performanceT = ((Array2DRowRealMatrix) (new Array2DRowRealMatrix(performance, false)).transpose()).getDataRef();
				double[][] fitnessT = ((Array2DRowRealMatrix) (new Array2DRowRealMatrix(fitness, false)).transpose()).getDataRef();
				String statsPerfFile = resultFileNameBase + "-performance-stats.csv";
				String statsFitFile = resultFileNameBase + "-fitness-stats.csv";
				BufferedWriter statsFilePerf = new BufferedWriter(new FileWriter(resultFileNameBase + "-performance-stats.csv"));
				BufferedWriter statsFileFit = new BufferedWriter(new FileWriter(resultFileNameBase + "-fitness-stats.csv"));
				statsFilePerf.write(PostProcess.generateStats(performanceT));
				statsFileFit.write(PostProcess.generateStats(fitnessT));
				statsFilePerf.close();
				statsFileFit.close();
				logger.info("Wrote statistics for best performance over each run for each generation to " + statsPerfFile);
				logger.info("Wrote statistics for best fitness over each run for each generation to " + statsFitFile);
			}
		}
	}

	private void configureLog4J(boolean disableFiles) {
		// If logging is not disabled.
		if (!properties.getProperty("log4j.rootLogger", "OFF").trim().startsWith("OFF")) {
			Set<String> propKeys = properties.stringPropertyNames();
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
					String[] rootLoggerProp = properties.getProperty("log4j.rootLogger").split(",");
					String newRootLoggerProp = rootLoggerProp[0];
					for (int i = 1; i < rootLoggerProp.length; i++) {
						if (!fileLogLabels.contains(rootLoggerProp[i].trim())) {
							newRootLoggerProp += ", " + rootLoggerProp[i].trim();
						}
					}
					properties.setProperty("log4j.rootLogger", newRootLoggerProp);
				} else {
					// Make sure all file loggers are configured to output to the output dir.
					for (String prop : fileLogProps) {
						String val = properties.getProperty(prop);
						if (!val.contains(outputDir)) {
							val = outputDir + val;
							properties.setProperty(prop, val);
						}
					}
				}
			}

			java.util.Properties log4jProps = new java.util.Properties();
			log4jProps.putAll(properties);
			PropertyConfigurator.configure(log4jProps);

			properties.configureLogger();
		}
	}
}
