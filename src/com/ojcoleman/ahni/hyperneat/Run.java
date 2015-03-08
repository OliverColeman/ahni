package com.ojcoleman.ahni.hyperneat;

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

import org.apache.log4j.Logger;
import org.apache.log4j.FileAppender;
import org.apache.log4j.PropertyConfigurator;


import com.anji.util.Misc;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.util.PropertiesConverter;
import com.ojcoleman.ahni.util.Results;
import com.ojcoleman.ahni.util.Statistics;

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
	private static Logger logger;
	private static final DecimalFormat nf = new DecimalFormat("0.0000");
	
	/**
	 * Optional property key. ID of current experiment (constant over all runs). Must be an integer value (Long is acceptable).
	 * If not given then current system time in milliseconds is used.
	 */
	private static final String EXPERIMENT_ID_KEY = "experiment.id";
	

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

	@Parameter(names = { "-outputprefix", "-op" }, description = "Prefix to apply to output files (overrides output.prefix in properties file).")
	public String outputPrefix = null;

	@Parameter(names = { "-force", "-f" }, description = "Force using the specified output directory even if it exists.")
	public boolean forceOutputDir = false;

	@Parameter(converter = PropertiesConverter.class, arity = 1, description = "<Properties file to read experiment parameters from>")
	public List<Properties> propertiesFiles = new ArrayList<Properties>(1);

	Properties properties;

	/**
	 * The best performance for each generation for each run, in the format [run][generation]. This will only be
	 * populated after {@link #run()} has completed.
	 */
	public double[][] performance;

	/**
	 * The best fitness for each generation for each run, in the format [run][generation]. This will only be populated
	 * after {@link #run()} has completed.
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
	 */
	public void run() throws Exception {
		if (properties == null) {
			properties = propertiesFiles.get(0);
		}
		
		if (outputPrefix != null) {
			properties.setProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, outputPrefix);
		}

		String runLogFile = properties.getProperty("log4j.appender.RunLog.File", null);
		
		if (!properties.containsKey(EXPERIMENT_ID_KEY)) {
			properties.setProperty(EXPERIMENT_ID_KEY, "" + System.currentTimeMillis());
		}
		long experimentID = properties.getLongProperty(EXPERIMENT_ID_KEY);
		
		if (!properties.containsKey("run.id")) {
			properties.setProperty("run.id", "0");
		}
		
		// If there should be no output whatsoever.
		if (noOutput) {
			properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
			outputDir = null;
			resultFileNameBase = null;
			runLogFile = null;
			properties.setProperty("log4j.rootLogger", "OFF");
			configureLog4J(true);
		}
		// If no files should be generated (but output to terminal is allowed).
		else if (noFiles || outputDir == null && !properties.containsKey(HyperNEATConfiguration.OUTPUT_DIR_KEY)) {
			properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
			outputDir = null;
			resultFileNameBase = null;
			runLogFile = null;
			configureLog4J(true);			
			//logEnv();
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
			//logEnv();

			resultFileNameBase = outputDir + properties.getProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, "") + resultFileNameBase;

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
		double avgGenerations = 0;
		int solvedCount = 0;
		for (int run = 0; run < numRuns; run++) {
			long startRun = System.currentTimeMillis();
			
			Properties runProps = new Properties(properties);
			
			// Look for AHNI_RUN_ID env variable set by ParameterTuner for HTCondor runs.
			String runID = System.getenv("AHNI_RUN_ID") == null ? ""+run : System.getenv("AHNI_RUN_ID");
			runProps.setProperty("run.id", runID);
			
			String runOutputDir = outputDir + (numRuns > 1 ? run + File.separator : "");
			if (outputDir != null) {
				runProps.setProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY, runOutputDir);

				// If there is a file logger for each run.
				if (runLogFile != null) {
					FileAppender fileAppender = (FileAppender) Logger.getRootLogger().getAppender("RunLog");
					if (fileAppender != null) {
						fileAppender.setFile(runOutputDir + runLogFile);
						fileAppender.activateOptions();
					}
				}
			}
			
			logger.info("\n\n--- START RUN: " + (run + 1) + " of " + numRuns + " (" + ((run * 100) / (numRuns)) + "%) ---------------------------------------\n\n");
			HyperNEATEvolver evolver = (HyperNEATEvolver) runProps.singletonObjectProperty(HyperNEATEvolver.class);
			
			evolver.run();
			
			performance[run] = evolver.getBestPerformance();
			fitness[run] = evolver.getBestFitness();
			avgGenerations += evolver.getGeneration();
			if (evolver.getGeneration() < performance[run].length) {
				solvedCount++;
			}

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
			if (fileAppender != null) {
				fileAppender.setFile(outputDir + runLogFile);
				fileAppender.activateOptions();
			}
		}
		logger.info(numRuns + " runs completed in " + Misc.formatTimeInterval((end - start) / 1000));
		
		avgGenerations /= numRuns;
		logger.info("Average number of generations: " + avgGenerations);
		logger.info("Number of runs in which solution was found: " + solvedCount);

		if (resultFileNameBase != null) {
			String resultPerfFileName = resultFileNameBase + "-performance.csv";
			Results resultsPerf = new Results(performance, null);
			BufferedWriter resultFilePerf = new BufferedWriter(new FileWriter(resultPerfFileName));
			resultFilePerf.append(resultsPerf.toString());
			resultFilePerf.close();
			logger.info("Wrote best performance for each generation in each run to " + resultPerfFileName);

			String resultFitFileName = resultFileNameBase + "-fitness.csv";
			Results resultsFitness = new Results(fitness, null);
			BufferedWriter resultFileFit = new BufferedWriter(new FileWriter(resultFitFileName));
			resultFileFit.append(resultsFitness.toString());
			resultFileFit.close();
			logger.info("Wrote best fitness for each generation in each run to " + resultFitFileName);

			// Only do stats if there's more than one run.
			if (numRuns > 1) {
				String statsPerfFileName = resultFileNameBase + "-performance-stats.csv";
				Statistics statsPerf = new Statistics(resultsPerf);
				BufferedWriter statsFilePerf = new BufferedWriter(new FileWriter(resultFileNameBase + "-performance-stats.csv"));
				statsFilePerf.write(statsPerf.getBasicStats().toString());
				statsFilePerf.close();
				logger.info("Wrote statistics for best performance over each run for each generation to " + statsPerfFileName);

				String statsFitnessFileName = resultFileNameBase + "-fitness-stats.csv";
				Statistics statsFitness = new Statistics(resultsFitness);
				BufferedWriter statsFileFitness = new BufferedWriter(new FileWriter(resultFileNameBase + "-fitness-stats.csv"));
				statsFileFitness.write(statsFitness.getBasicStats().toString());
				statsFileFitness.close();
				logger.info("Wrote statistics for best fitness over each run for each generation to " + statsFitnessFileName);
			}
		}
	}
	
	private void logEnv() {
		StringBuffer out = new StringBuffer();
		out.append("\nEnvironment vars:");
		for(java.util.Map.Entry<String, String> e : System.getenv().entrySet()) {
			out.append("\n\t" + e.toString());
		}
		out.append("\nJRE system props:");
		for(java.util.Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
			out.append("\n\t" + e.toString());
		}
		logger.info(out);
	}

	private void configureLog4J(boolean disableFiles) {
		properties.configureLog4JSettings(outputDir, disableFiles);
		java.util.Properties log4jProps = new java.util.Properties();
		log4jProps.putAll(properties);
		PropertyConfigurator.configure(log4jProps);
		properties.configureLogger();
		logger = Logger.getLogger(Run.class);
	}
	
	
	
	
	
	
	
	
	
	
	

	/*
	static class EnvRunSet implements Callable<Boolean> {
		private final Properties properties;
		private final long seed;

		EnvRunSet(Properties properties, long seed) {
			this.properties = properties;
			this.seed = seed;
		}

		@Override
		public Boolean call() {
			properties.setProperty("fitness.function.rlrnn.seed", "" + seed);

			int numRuns = properties.getIntProperty(HyperNEATConfiguration.NUM_RUNS_KEY);

			boolean envSolved = false;
			int runCount = -1, finalGen = -1;
			long start = System.currentTimeMillis();
			for (int run = 0; run < numRuns; run++) {
				Properties runProps = new Properties(properties);
				HyperNEATEvolver evolver = (HyperNEATEvolver) runProps.singletonObjectProperty(HyperNEATEvolver.class);
				double[] performance;
				try {
					performance = evolver.run();
				} catch (Exception e) {
					e.printStackTrace();
					return false;
				}
				finalGen = evolver.getGeneration();
				evolver.dispose();

				envSolved = performance[performance.length - 1] >= 0.9;
				if (envSolved) {
					runCount = run + 1;
					break;
				}
			}
			long finish = System.currentTimeMillis();
			
			System.out.println((envSolved ? ("  solved after " + runCount + " runs at generation " + finalGen + ".") : "  not solved.") + " Took " + ((finish-start)/60000f) + " minutes.");

			return envSolved;
		}
	}

	public void modifiedRun() throws Exception {
		if (properties == null) {
			properties = propertiesFiles.get(0);
		}

		properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
		properties.setProperty("log4j.rootLogger", "OFF");
		configureLog4J(true);

		int envCount = 50;
		ArrayList<EnvRunSet> envTasks = new ArrayList<EnvRunSet>(envCount);
		Random r = new Random();
		for (int e = 0; e < envCount; e++) {
			envTasks.add(new EnvRunSet(new Properties(properties), r.nextLong()));
		}

		ExecutorService executor = Executors.newFixedThreadPool(8);
		List<Future<Boolean>> results = executor.invokeAll(envTasks);
		executor.shutdown();

		int envSolvedCount = 0;
		for (Future<Boolean> result : results) {
			if (result.get())
				envSolvedCount++;
		}

		System.out.println("\nSOLVED " + envSolvedCount + " ENVIRONMENTS OF SIZE " + properties.getIntProperty(RLRecurrentNetworkBased.SIZE));
	}
	 */
}
