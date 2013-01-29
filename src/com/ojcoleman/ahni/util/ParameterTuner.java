package com.ojcoleman.ahni.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.esotericsoftware.wildcard.Paths;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.hyperneat.Run;


/**
 * <p>
 * Attempts to fine tune one or more parameters for an experiment described by a Properties file. Parameters are
 * adjusted iteratively, with each parameter adjusted in turn each iteration. The underlying assumption is that the
 * fitness landscape represented by the parameters to adjust is unimodal. It is currently assumed that all parameters
 * are represented by floating point numbers.
 * </p>
 * <p>
 * For each parameter for each iteration the fitness value is calculated by adjusting the parameter up or down by a
 * multiplicative factor and running the experiment. If either of these adjustments results in a higher fitness then the
 * adjusted value will be adopted (at least until the next iteration when a new value may be adopted).
 * </p>
 * <p>
 * The default initial multiplicative factor is 2 (thus the initial downward adjustment of a parameter p is p/2 and the
 * upward adjustment is p*2). A different initial multiplicative factor may be specified in the Properties file. For each
 * iteration in which no parameter adjustments are adopted the multiplicative factor is halved.
 * </p>
 * <p>
 * If fitness was not increased by adjusting a parameter in the previous iteration then it will not be adjusted in the
 * current iteration to save time. If adjusting the parameter in the next iteration still doesn't yield an increase in
 * fitness then the parameter will be ignored for the next two iterations, and so on.
 * </p>
 * <p>
 * The following properties may be specified in the Properties file:
 * <dl>
 * <dt>parametertuner.totune</dt>
 * <dd>A comma separated list of property keys corresponding to the parameters to tune</dd>
 * <dt>parametertuner.initialvalues</dt>
 * <dd>A comma separated list of the initial values to use for the parameters, in the same order as that given in
 * <em>parametertuner.totune</em>.</dd>
 * <dt>parametertuner.minvalues</dt>
 * <dd>A comma separated list of the minimum values to use for the parameters, in the same order as that given in
 * <em>parametertuner.totune</em>. Optional.</dd>
 * <dt>parametertuner.maxvalues</dt>
 * <dd>A comma separated list of the maximum values to use for the parameters, in the same order as that given in
 * <em>parametertuner.totune</em>. Optional.</dd>
 * <dt>parametertuner.maxiterations</dt>
 * <dd>The maximum number of iterations to perform. Default is 100.</dd>
 * <dt>parametertuner.initialvalueadjustfactor</dt>
 * <dd>The initial multiplicative factor</dd>
 * <dt>parametertuner.numruns</dt>
 * <dd>The number of runs to perform when determining fitness for a set of parameter values. Default is 50, which is probably about the safest minimum.</dd>
 * </dl>
 * </p>
 */
public class ParameterTuner {
	public static double significanceFactor = 1.01;
	private static final DecimalFormat nf = new DecimalFormat("0.00000");
	
	public static void main(String[] args) {
		if (args.length == 0) {
			usage();
			System.exit(-1);
		}

		try {
			Properties props = new Properties(args[0]);

			String[] propsToTune = props.getProperty("parametertuner.totune").replaceAll("\\s", "").split(",");
			int propCount = propsToTune.length;
			double[] currentBestVals = props.getDoubleArrayProperty("parametertuner.initialvalues");
			double[] minValues = props.getDoubleArrayProperty("parametertuner.minvalues", ArrayUtil.newArray(propCount, -Double.MAX_VALUE));
			double[] maxValues = props.getDoubleArrayProperty("parametertuner.maxvalues", ArrayUtil.newArray(propCount, Double.MAX_VALUE));
			int maxIterations = props.getIntProperty("parametertuner.maxiterations", 100);
			double valAdjustFactor = props.getDoubleProperty("parametertuner.initialvalueadjustfactor", 2);
			int numRuns = props.getIntProperty("parametertuner.numruns", 50);
			int numGens = props.getIntProperty("parametertuner.numgens", 500);
			double solvedPerformance = props.getDoubleProperty("parametertuner.solvedperformance", 1);
			String htCondorTpl = props.getProperty("parametertuner.htcondor", null);
			
			if (htCondorTpl != null) {
				// Clean up generated files from abortive runs.
				Paths paths = new Paths("./", "pt-condor-*");
				paths.delete();
			}
			
			props.setProperty("num.runs", "1"); // We'll calculate our own average so we can report progress as we go.
			props.setProperty("num.generations", "" + numGens);
			props.setProperty("log4j.rootLogger", "OFF"); // Suppress logging.
			Logger.getRootLogger().setLevel(Level.OFF);
			props.remove("random.seed"); // Make sure the runs are randomised.

			int[] adjustIneffectiveCount = new int[propCount];
			int[] adjustCountDown = new int[propCount];
			double adjustDownFitness = 0, adjustUpFitness = 0;
			double adjustDownVal = 0, adjustUpVal = 0;
			int runLotID = 0;

			System.out.println("Determining initial fitness.");
			double bestFitness = doRuns(props, numRuns, runLotID++, htCondorTpl);
			System.out.println("Initial fitness: " + nf.format(bestFitness));
			
			for (int i = 0; i < maxIterations; i++) {
				System.out.println("Start iteration " + i);
				
				boolean adjustedAnyParams = false;
				boolean adjustCountDownZeroExists = false; // Indicates if any parameters are due to be adjusted this iteration.

				// Adjust each parameter in turn.
				for (int p = 0; p < propCount; p++) {
					String propKey = propsToTune[p];
					boolean solvedInCurrentGens = false;

					// Sample fitness either side of current parameter value.
					if (adjustCountDown[p] == 0) {
						System.out.println("\tTuning " + propKey + " (current value is " + nf.format(currentBestVals[p]) + ").");
						double newVal = currentBestVals[p];
						double newBestFitness = bestFitness;

						adjustDownVal = Math.min(maxValues[p], Math.max(minValues[p], currentBestVals[p] / valAdjustFactor));
						if (adjustDownVal != currentBestVals[p]) {
							props.setProperty(propKey, "" + adjustDownVal);
							adjustDownFitness = doRuns(props, numRuns, runLotID++, htCondorTpl);
							boolean better = adjustDownFitness > bestFitness * significanceFactor;
							System.out.println("\t\tValue " + nf.format(adjustDownVal) + " gave fitness of " + nf.format(adjustDownFitness) + " (" + (!better ? "not significantly " : "") + "greater than current best).");

							if (better) {
								newBestFitness = adjustDownFitness;
								newVal = adjustDownVal;
							}
							
							solvedInCurrentGens |= adjustDownFitness >= solvedPerformance;
						}

						adjustUpVal = Math.min(maxValues[p], Math.max(minValues[p], currentBestVals[p] * valAdjustFactor));
						if (adjustUpVal != currentBestVals[p]) {
							props.setProperty(propKey, "" + adjustUpVal);
							adjustUpFitness = doRuns(props, numRuns, runLotID++, htCondorTpl);
							boolean better = adjustUpFitness > bestFitness * significanceFactor && adjustUpFitness > adjustDownFitness;
							System.out.println("\t\tValue " + nf.format(adjustUpVal) + " gave fitness of " + nf.format(adjustUpFitness) + " (" + (!better ? "not significantly " : "") + "greater than current best and/or downward adjustment).");

							if (better) {
								newBestFitness = adjustUpFitness;
								newVal = adjustUpVal;
							}
							
							solvedInCurrentGens |= adjustUpFitness >= solvedPerformance;
						}

						// If the fitness was increased by an adjustment.
						if (currentBestVals[p] != newVal) {
							adjustIneffectiveCount[p] = 0;
							currentBestVals[p] = newVal;
							adjustedAnyParams = true;
							bestFitness = newBestFitness;
						} else {
							// If the fitness did not improve by adjusting this parameter then hold off adjusting it
							// for a few iterations (dependent on how many times adjusting it has been ineffective in
							// previous generations).
							adjustIneffectiveCount[p]++;
							adjustCountDown[p] = adjustIneffectiveCount[p];

						}

						// Set parameter to current best value.
						props.setProperty(propKey, "" + currentBestVals[p]);
						
						// Reduce number of generations if solutions are being consistently found in the current number of generations.
						if (solvedInCurrentGens) {
							numGens = (int) (numGens * 0.75);
							props.setProperty("num.generations", "" + numGens);
							System.out.println("  Reduced number of generations to " + numGens); 
						}
					} else {
						adjustCountDown[p]--;
						System.out.println("\tSkipping " + propKey + " for " + adjustCountDown[p] + " more iterations.");
					}
					
					adjustCountDownZeroExists |= adjustCountDown[p] == 0;
				}

				System.out.println("\nFinished iteration. Current best values are:");
				for (int p = 0; p < propCount; p++) {
					System.out.println("  " + propsToTune[p] + " = " + nf.format(currentBestVals[p]));
				}

				if (!adjustedAnyParams) { 
					valAdjustFactor = (valAdjustFactor-1)/2+1;
					System.out.println("\nValue adjust factor is now " + nf.format(valAdjustFactor));
				}
				System.out.println("\n");

				// Make sure that at least one parameter is due to be adjusted. 
				while (!adjustCountDownZeroExists) {
					for (int p = 0; p < propCount; p++) {
						if (adjustCountDown[p] > 0) adjustCountDown[p]--;
						adjustCountDownZeroExists |= adjustCountDown[p] == 0;
					}
				}
			}
			System.out.println("Finished adjusting parameters.");
			System.out.println("Final best parameter values were:");
			for (int p = 0; p < propCount; p++) {
				System.out.println(propsToTune[p] + " = " + currentBestVals[p]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private static double doRuns(Properties props, int numRuns, int id, String htCondorTpl) throws Exception {
		if (htCondorTpl == null) {
			return doRunsSerial(props, numRuns);
		}
		return doRunsHTCondor(props, numRuns, id, htCondorTpl);
	}
	
	private static double doRunsSerial(Properties props, int numRuns) throws Exception {
		double sumFitness = 0;
		System.out.print("\t\tStarting " + numRuns + " runs: ");
		for (int r = 0; r < numRuns; r++) {
			System.out.print(".");
			Run runner = new Run(props);
			runner.noOutput = true;
			runner.run();
			double[] fitness = runner.fitness[0];
			double fs = 0;
			// Get average of last 10 fitness evaluations, in case fitness function is very stochastic.
			// (Generally speaking, the fitness function should not be very stochastic, but in some cases can be).
			for (int g = fitness.length-10; g < fitness.length; g++) {
				fs += fitness[g];
			}
			sumFitness += fs / 10;
		}
		float memHeap = (float) (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1048576d);
		float memNonHeap = (float) (ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / 1048576d);
		System.out.println(" Finished runs, memory usage (heap / non-heap / total MB): " + memHeap + " / " + memNonHeap + " / " + (memHeap + memNonHeap));
		return sumFitness / numRuns;
	}
	
	private static double doRunsHTCondor(Properties props, int numRuns, int id, String htCondorTpl) throws Exception {
		// Create dir to store temp files.
		String condorOutDir = "pt-condor-" + id;
		String condorOutDirSep = condorOutDir + File.separator;
		(new File(condorOutDir)).mkdir();
		// Save properties file.
		props.store(new FileOutputStream(condorOutDirSep + "pt.properties"), "" + id);
		
		// Generate condor submit file from template.
		String[] lines = htCondorTpl.split("\n");
		Map<String, String> condorSubmit = new HashMap<String, String>();
		for (String line : lines) {
			if (!line.trim().isEmpty()) {
				String[] keyVal = line.split("=", 2);
				condorSubmit.put(keyVal[0].trim(), keyVal[1].trim());
			}
		}
		condorSubmit.put("universe", "java");
		condorSubmit.put("arguments", "com.ojcoleman.ahni.hyperneat.Run -ao -od ./ -f -ar result-$(Process) ./pt.properties");
		condorSubmit.put("universe", "java");
		String tif = (condorSubmit.containsKey("transfer_input_files") ? condorSubmit.get("transfer_input_files") + ", " : "") + "pt.properties";
		condorSubmit.put("transfer_input_files", tif);
		condorSubmit.put("output", "out-$(Process).txt");
		condorSubmit.put("error", "err-$(Process).txt");
		condorSubmit.put("when_to_transfer_output", "ON_EXIT");
		condorSubmit.put("should_transfer_files", "YES");
		condorSubmit.remove("queue");
		BufferedWriter fileWriter = new BufferedWriter(new FileWriter(new File(condorOutDirSep + "submit.txt")));
		for (Map.Entry<String, String> entry : condorSubmit.entrySet()) {
			fileWriter.write(entry.getKey() + "=" + entry.getValue() + "\n");
		}
		fileWriter.write("queue " + numRuns + "\n");
		fileWriter.close();
		
		// Submit jobs to condor.
		System.out.print("\t\tStarting " + numRuns + " runs ");
		Process condorProcess = Runtime.getRuntime().exec("condor_submit submit.txt", null, new File(condorOutDir));
		boolean finished = false;
		int condorProcessReturn = -1;
		InputStreamReader condorProcessOutStream = new InputStreamReader(condorProcess.getInputStream());
		StringBuilder condorProcessOut = new StringBuilder();
		int c;
		do {
			Thread.sleep(100);
			
			// Catch the output.
			while ((c = condorProcessOutStream.read()) != -1)
				condorProcessOut.append((char) c);

			// See if process has finished (waitFor() doesn't seem to work).
			try {
				condorProcessReturn = condorProcess.exitValue();
				finished = true;
			}
			catch (IllegalThreadStateException e) {}
		} while (!finished);
		
		// Catch any last bits of output.
		while ((c = condorProcessOutStream.read()) != -1)
			condorProcessOut.append((char) c);
		
		if (condorProcessReturn != 0) {
			throw new Exception("Error submitting HTCondor jobs:\n" + condorProcessOut);
		}
		
		Pattern p = Pattern.compile("\\d+ job\\(s\\) submitted to cluster (\\d+)\\.");
		Matcher m = p.matcher(condorProcessOut.toString());
		if (m.find()) {
			String condorClusterID = m.group(1);
			System.out.print(" (cluster " + condorClusterID + "): ");
		}
		else {
			System.out.println("Unable to determine cluster ID from condor_submit output:\n " + condorProcessOut);
		}
		
		// Wait for condor jobs to finish.
		int currentDoneCount = 0;
		while (currentDoneCount != numRuns) {
			Thread.sleep(10000); // Wait for 10 seconds.
			Paths paths = new Paths(condorOutDir, "result-*-fitness.csv");
			if (currentDoneCount != paths.count()) {
				for (int i = 0; i < paths.count() - currentDoneCount; i++) System.out.print(".");
			}
			currentDoneCount = paths.count();
		}
		System.out.println(" Finished.");
		
		// Collate results.
		Results results = PostProcess.combineResults(condorOutDirSep + "result-*-fitness.csv", false);
		// Average over last 10 generations from all runs. The last 10 generations are 
		// used to help compensate for noisy evaluation functions.
		double result = 0;
		for (int r = 0; r < numRuns; r++) {
			for (int g = results.getItemCount()-10; g < results.getItemCount(); g++) {
				result += results.getData(r, g);
			}
		}
		result /= 10 * numRuns;
		
		// Clean up generated files.
		Paths paths = new Paths("./", condorOutDir);
		paths.delete();

		return result;
	}
	
	
	/**
	 * command line usage
	 */
	private static void usage() {
		System.out.println("Usage:\n" + "Parameter tuning can be run with:\n  <cmd> <properties-file>");
	}
}
