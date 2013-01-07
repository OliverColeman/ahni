package ojc.ahni.hyperneat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.FileAppender;

import ojc.ahni.hyperneat.HyperNEATEvolver;

import com.anji.util.Misc;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

/**
 * <p>This is the main class from which experiment runs are performed.
 * The main purpose of this class is to allow performing multiple evolutionary runs and aggregating the result.</p>
 * <p>For each run a new {@link Properties} object is generated from the properties file specified on the command line. The Properties
 * object encapsulates all the configuration parameters for a run, and can be used to retrieve these properties as well as generate and retrieve 
 * singletons of most components used in a run (e.g. the {@link HyperNEATEvolver}, {@link org.jgapcustomised.BulkFitnessFunction} and 
 * {@link com.anji.integration.Transcriber}).</p> 
 */
public class Run {
	private static Logger logger = Logger.getLogger(Run.class);
	private static final DecimalFormat nf = new DecimalFormat("0.0000");
	
	public boolean nolog = false;

	@Parameter(names = { "-output" }, description = "Directory to write output files to (overrides output.dir in properties file).")
	public String outputDir = null;
	
	@Parameter(names = { "-aggresult" }, description = "Suffix of names of files to write aggregate results to.")
	public String resultFileNameBase = "results";
	
	@Parameter(description = "Properties file to read experiment parameters from.", arity=1)
	public List<String> parameters = new ArrayList<String>();

	public Properties properties = null;
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Run runner = new Run();
			JCommander jcom = new JCommander(runner, args);
			
			if (runner.properties == null) {
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
	 * @return The final (average) fitness.
	 */
	public double run() throws Exception {
		if (properties == null) properties = new Properties(parameters.get(0));
		
		if (nolog) {
			outputDir = null;
			properties.setProperty("log4j.rootLogger", "OFF");
		}
		
		long experimentID = System.currentTimeMillis();
		String runLogFile = null;
		if (!nolog) {
			if (outputDir == null) {
				outputDir = properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + File.separator + experimentID + File.separator;
			}
			if ((new File(outputDir)).exists()) {
				throw new IllegalArgumentException("Output directory " + outputDir + " already exists.");
			}
			outputDir = (new File(outputDir)).getCanonicalPath() + File.separator;
			resultFileNameBase = outputDir + resultFileNameBase;
			
			runLogFile = properties.getProperty("log4j.appender.RunLog.File", null);

			logger.info("Output directory is " + outputDir + ".");
			logger.info("Performance results will be written to " + resultFileNameBase + "-[performance|fitness].");
		}
		else {
			properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
		}
		
		int numRuns = properties.getIntProperty(HyperNEATConfiguration.NUM_RUNS_KEY);
		int numGens = properties.getIntProperty(HyperNEATEvolver.NUM_GENERATIONS_KEY);

		double[][] performance;
		double[][] fitness;
		
		performance = new double[numRuns][];
		fitness = new double[numRuns][];

		long start = System.currentTimeMillis();
		double avgRunTime = 0;
		for (int run = 0; run < numRuns; run++) {
			long startRun = System.currentTimeMillis();

			Properties runProps = new Properties(properties);
			String runID = properties.getProperty("run.name") + "-" + experimentID + (numRuns > 1 ? "-" + run : "");
			runProps.setProperty("run.id", runID);
			if (!nolog) {
				String runOutputDir = outputDir + (numRuns > 1 ? run + File.separator : "");
				runProps.setProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY, runOutputDir);
			
				// If there is a file logger for each run.
				if (runLogFile != null) {
					FileAppender fileAppender = (FileAppender) Logger.getRootLogger().getAppender("RunLog");
					fileAppender.setFile(runOutputDir + runLogFile);
					fileAppender.activateOptions();
				}
			}
			else {
				runProps.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
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
		logger.info(numRuns + " runs completed in " + Misc.formatTimeInterval((end - start) / 1000));


		// Print average results for each run.
		double[] avgPerf = new double[numGens];
		double[] avgFit = new double[numGens];
		double p, f;
		for (int gen = 0; gen < numGens; gen++) {
			avgPerf[gen] = 0;
			avgFit[gen] = 0;
			for (int run = 0; run < numRuns; run++) {
				p = performance[run][gen];
				f = fitness[run][gen];
				avgPerf[gen] += p;
				avgFit[gen] += f;
			}
			avgPerf[gen] /= numRuns;
			avgFit[gen] /= numRuns;
		}
		
		if (!nolog) {
			BufferedWriter resultFilePerf = new BufferedWriter(new FileWriter(resultFileNameBase + "-avg_performance_in_each_gen_over_all_runs.txt"));
			String results = "";
			for (int gen = 0; gen < numGens; gen++)
				results += nf.format(avgPerf[gen]) + ", ";
			resultFilePerf.write(results + "\n");
			resultFilePerf.close();
			logger.info("Wrote best performance for each generation to " + resultFileNameBase + "-avg_performance_in_each_gen_over_all_runs.txt");
	
			BufferedWriter resultFileFit = new BufferedWriter(new FileWriter(resultFileNameBase + "-avg_fitness_in_each_gen_over_all_runs.txt"));
			results = "";
			for (int gen = 0; gen < numGens; gen++)
				results += nf.format(avgFit[gen]) + ", ";
			resultFileFit.write(results + "\n");
			resultFileFit.close();			
			logger.info("Wrote best fitness for each generation to " + resultFileNameBase + "-avg_fitness_in_each_gen_over_all_runs.txt");
		}
		
		return avgFit[numGens-1];
	}

	/**
	 * command line usage
	 */
	private static void usage() {
		System.out.println("Usage:\n" + 
				"Experiments can be run with:\n" +
				"  <cmd> <properties-file> [result file name]\n\n" +
				"Post processing can be run with:\n" + 
				"  <cmd> pp [compressAverage|ca] <input file/dir> <output file/dir> [options]");
	}
}
