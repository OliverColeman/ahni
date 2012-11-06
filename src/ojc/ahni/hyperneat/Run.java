package ojc.ahni.hyperneat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.*;
import java.util.Random;

import org.apache.log4j.Logger;

import ojc.ahni.hyperneat.HyperNEATEvolver;
import com.anji.util.Misc;
import com.anji.util.Properties;

/**
 * This is the main class from which experiment runs are performed.
 */
public class Run {
	private static Logger logger = Logger.getLogger(Run.class.getName());
	private static final DecimalFormat nf = new DecimalFormat("0.0000");

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if (args.length == 0) {
				usage();
				System.exit(-1);
			}

			Properties props = new Properties(args[0]);

			long experimentID = System.currentTimeMillis();
			String outputDir = props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + File.separatorChar + experimentID + File.separatorChar;
			String resultFileNameBase = outputDir;
			if (args.length > 1)
				resultFileNameBase += args[1];
			else
				resultFileNameBase += "results";

			logger.info("Output directory is " + outputDir + ".");
			logger.info("Performance results will be written to " + resultFileNameBase + "-[performance|fitness].");

			int numRuns = props.getIntProperty(HyperNEATConfiguration.NUM_RUNS_KEY);
			int numGens = props.getIntProperty(HyperNEATEvolver.NUM_GENERATIONS_KEY);

			double[][] performance;
			double[][] fitness;
			
			performance = new double[numRuns][];
			fitness = new double[numRuns][];

			long start = System.currentTimeMillis();
			double avgRunTime = 0;
			for (int run = 0; run < numRuns; run++) {
				long startRun = System.currentTimeMillis();

				props = new Properties(args[0]);
				props.setProperty("run.id", props.getProperty("run.name") + "-" + experimentID);
				props.setProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY, outputDir);

				// System.out.print("run: " + run + "\t");
				logger.info("\n\n--- START RUN: " + (run + 1) + " of " + numRuns + " (" + ((run * 100) / (numRuns)) + "%) ---------------------------------------\n\n");
				HyperNEATEvolver evolver = new HyperNEATEvolver();
				evolver.init(props);
				// champFitnesses[run] = evolver.run();
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


			// print results
			BufferedWriter resultFilePerf = new BufferedWriter(new FileWriter(resultFileNameBase + "-avg_performance_in_each_gen_over_all_runs.txt"));
			BufferedWriter resultFileFit = new BufferedWriter(new FileWriter(resultFileNameBase + "-avg_fitness_in_each_gen_over_all_runs.txt"));
			double[] avgPerf = new double[numGens];
			double[] avgFit = new double[numGens];
			double p, f, pc;
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

			logger.info("Average performance for each gen over " + numRuns + " runs:");
			String results = "";
			for (int gen = 0; gen < numGens; gen++)
				results += nf.format(avgPerf[gen]) + ", ";
			logger.info(results);
			resultFilePerf.write(results + "\n");
			resultFilePerf.close();

			logger.info("Average fitness for each gen over " + numRuns + " runs:");
			results = "";
			for (int gen = 0; gen < numGens; gen++)
				results += nf.format(avgFit[gen]) + ", ";
			logger.info(results);
			resultFileFit.write(results + "\n");
			resultFileFit.close();

			System.exit(0);
		} catch (Throwable th) {
			logger.error(th);
			th.printStackTrace();
		}
	}

	/**
	 * command line usage
	 */
	private static void usage() {
		System.err.println("usage: <cmd> <properties-file> [result file name]");
	}
}
