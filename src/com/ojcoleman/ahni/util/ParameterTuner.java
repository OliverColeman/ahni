package com.ojcoleman.ahni.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.esotericsoftware.wildcard.Paths;
import com.ojcoleman.ahni.evaluation.TargetFitnessCalculator;
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
	public static final double significanceFactor = 1.01;
	private static final DecimalFormat nf = new DecimalFormat("0.00000");
	
	private ArrayList<String> runningCondorClusterIDs = new ArrayList<String>();
	private Properties props;

	private String[] propsToTune;
	private int propCount;
	private double[] currentBestVals;
	private String[] types;
	private double[] minValues;
	private double[] maxValues;
	private int numGens;
	private int popSize;
	private int totalEvaluations;
	private int maxIterations;
	private double valAdjustFactor;
	private int numRuns;
	private double solvedPerformance;
	private String htCondorTpl;
	private int[] adjustIneffectiveCount;
	private int[] adjustCountDown;
	private int runLotID;
	private Result bestResult;
	private BufferedWriter outputFile;
	
	public static void main(String[] args) {
		if (args.length == 0) {
			usage();
			System.exit(-1);
		}
		
		ParameterTuner pt = new ParameterTuner();
		pt.go(args);
	}
	
	public ParameterTuner() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	if (outputFile != null) {
            		try {
						outputFile.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
            	}
            	System.out.println("\n\nExiting. Cancelling currently running condor jobs (if any).");
            	for (String id : runningCondorClusterIDs) {
            		try {
            			System.out.println("  condor_rm " + id);
						Runtime.getRuntime().exec("condor_rm " + id);
					} catch (IOException e) {
						e.printStackTrace();
					}
            	}
            }
        });
	}
	
	public void go(String[] args) {
		try {
			props = new Properties(args[0]);
			propsToTune = props.getProperty("parametertuner.totune").replaceAll("\\s", "").split(",");
			propCount = propsToTune.length;
			currentBestVals = props.getDoubleArrayProperty("parametertuner.initialvalues");
			types = props.getStringArrayProperty("parametertuner.types", ArrayUtil.newArray(propCount, "f"));
			minValues = props.getDoubleArrayProperty("parametertuner.minvalues", ArrayUtil.newArray(propCount, -Double.MAX_VALUE));
			maxValues = props.getDoubleArrayProperty("parametertuner.maxvalues", ArrayUtil.newArray(propCount, Double.MAX_VALUE));
			numGens = props.getIntProperty("parametertuner.numgens", 500);
			popSize = props.getIntProperty("popul.size");
			
			for (int i = 0; i < propCount; i++) {
				if (types[i].equals("b")) {
					if (minValues[i] < 0) minValues[i] = 0;
					if (maxValues[i] > 1) maxValues[i] = 1;
				}
				
				if (propsToTune[i].equals("popul.size")) {
					popSize = (int) Math.round(currentBestVals[i]);
				}
			}
			
			// Store the total evaluations that will be performed for a run, so that if we're tuning popul.size
			// then we can adjust the number of generations accordingly.
			totalEvaluations = numGens * popSize;
			
			maxIterations = props.getIntProperty("parametertuner.maxiterations", 100);
			valAdjustFactor = props.getDoubleProperty("parametertuner.initialvalueadjustfactor", 2);
			numRuns = props.getIntProperty("parametertuner.numruns", 50);
			solvedPerformance = props.getDoubleProperty("parametertuner.solvedperformance", 1);
			htCondorTpl = props.getProperty("parametertuner.htcondor", null);
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

			adjustIneffectiveCount = new int[propCount];
			adjustCountDown = new int[propCount];
			double adjustDownVal = 0, adjustUpVal = 0;
			runLotID = 0;
			
			outputFile = new BufferedWriter(new FileWriter("results.csv"));
			outputFile.append(props.getProperty("parametertuner.totune") + ",gens, mean perf\n");

			System.out.println("Determining initial fitness with values:");
			for (int p = 0; p < propCount; p++) {
				String propKey = propsToTune[p];
				props.setProperty(propKey, "" + typeAdjustValueStr(types[p], currentBestVals[p]));
				System.out.println("  " + propsToTune[p] + "=" + props.getProperty(propKey));
			}
			System.out.println("  num.generations=" + numGens);
			bestResult = doRuns((Properties) props.clone(), runLotID++, "i");
			System.out.println();
			if (bestResult.solvedByGeneration() != -1) {
				numGens = bestResult.solvedByGeneration();
				totalEvaluations = numGens * popSize;
				props.setProperty("num.generations", "" + numGens);
				System.out.println("Set generations to " + numGens);
			}
			System.out.println("Initial performance: " + nf.format(bestResult.performance()));
			
			addResult(bestResult);
			
			ExecutorService runExecutor = Executors.newFixedThreadPool(htCondorTpl != null ? 2 : 1);
			
			int stagnantCount = 0;
			
			for (int i = 0; i < maxIterations; i++) {
				System.out.println("Start iteration " + i);
				
				boolean adjustedAnyParams = false;
				boolean adjustCountDownZeroExists = false; // Indicates if any parameters are due to be adjusted this iteration.
				boolean noImprovement = true;
				boolean triedAtLeastOneParamAdjustment = false;

				// Adjust each parameter in turn.
				for (int p = 0; p < propCount; p++) {
					String propKey = propsToTune[p];
					boolean tuningPopSize = propKey.equals("popul.size");

					// Sample fitness either side of current parameter value.
					if (adjustCountDown[p] == 0) {
						Future<Result> downFuture = null;
						Future<Result> upFuture = null;
						
						
						System.out.print("\tTuning " + propKey + " (current value is " + nf.format(currentBestVals[p]) + "). ");
						double newVal = currentBestVals[p];
						
						if (types[p].equals("b")) {
							adjustDownVal = 0;
						} else {
							adjustDownVal = typeAdjustValue(types[p], Math.min(maxValues[p], Math.max(minValues[p], currentBestVals[p] / valAdjustFactor)));
						}
						if (adjustDownVal != currentBestVals[p]) {
							props.setProperty(propKey, "" + typeAdjustValueStr(types[p], adjustDownVal));
							if (tuningPopSize) {
								// Adjust gens so that total evaluations per run is maintained.
								props.setProperty("num.generations", "" + (int) Math.round(totalEvaluations / adjustDownVal));
							}
							downFuture = runExecutor.submit(new DoRuns(props, runLotID++, "d"));
							triedAtLeastOneParamAdjustment = true;
						}

						if (types[p].equals("b")) {
							adjustUpVal = 1;
						} else {
							adjustUpVal = typeAdjustValue(types[p], Math.min(maxValues[p], Math.max(minValues[p], currentBestVals[p] * valAdjustFactor)));
						}
						if (adjustUpVal != currentBestVals[p]) {
							props.setProperty(propKey, "" + typeAdjustValueStr(types[p], adjustUpVal));
							if (tuningPopSize) {
								// Adjust gens so that total evaluations per run is maintained.
								props.setProperty("num.generations", "" + (int) Math.round(totalEvaluations / adjustUpVal));
							}
							upFuture = runExecutor.submit(new DoRuns(props, runLotID++, "u"));
							triedAtLeastOneParamAdjustment = true;
						}
						
						// Wait for tasks to complete.
						if (downFuture != null) downFuture.get();
						if (upFuture != null) upFuture.get();
						System.out.println();
						
						if (downFuture != null) {
							Result adjustDownResult = downFuture.get();
							boolean better = adjustDownResult.betterThan(bestResult);
							System.out.println("\t\tValue " + nf.format(adjustDownVal) + (tuningPopSize ? " (" + adjustDownResult.maxGens() + " max gens)" : "") +  " gave " + adjustDownResult + "." + (better ? " BETTER THAN CURRENT BEST." : ""));
							if (better) {
								bestResult = adjustDownResult;
								newVal = adjustDownVal;
							}
							addResult(adjustDownResult);
						}
						
						if (upFuture != null) {
							Result adjustUpResult = upFuture.get();
							boolean better = adjustUpResult.betterThan(bestResult);
							System.out.println("\t\tValue " + nf.format(adjustUpVal) + (tuningPopSize ? " (" + adjustUpResult.maxGens() + " max gens)" : "") + " gave " + adjustUpResult + "." + (better ? " BETTER THAN CURRENT BEST." : ""));
							if (better) {
								bestResult = adjustUpResult;
								newVal = adjustUpVal;
							}
							addResult(adjustUpResult);
						}
						
						outputFile.append("\n");
						
						// If the fitness was increased by an adjustment.
						if (currentBestVals[p] != newVal) {
							adjustIneffectiveCount[p] = 0;
							currentBestVals[p] = newVal;
							adjustedAnyParams = true;
							
							if (tuningPopSize) {
								popSize = (int) Math.round(newVal);
								numGens = (int) Math.round(totalEvaluations / newVal);
								props.setProperty("num.generations", "" + numGens);
							}
							
							if (bestResult.solvedByGeneration() != -1) {
								numGens = bestResult.solvedByGeneration();
								totalEvaluations = numGens * popSize;
								props.setProperty("num.generations", "" + numGens);
								System.out.println("  Reduced number of generations to " + numGens);
							}
							
							noImprovement = false;
						} else {
							// If the fitness did not improve by adjusting this parameter then hold off adjusting it
							// for a few iterations (dependent on how many times adjusting it has been ineffective in
							// previous generations).
							adjustIneffectiveCount[p]++;
							adjustCountDown[p] = adjustIneffectiveCount[p];

						}

						// Set parameter to current best value.
						props.setProperty(propKey, "" + typeAdjustValueStr(types[p], currentBestVals[p]));
					} else {
						adjustCountDown[p]--;
						System.out.println("\tSkipping " + propKey + " for " + adjustCountDown[p] + " more iterations.");
					}
					
					adjustCountDownZeroExists |= adjustCountDown[p] == 0;
				}
				
				outputFile.append("\n");

				System.out.println("\nFinished iteration. Best result is " + bestResult + ". Current best values are:");
				for (int p = 0; p < propCount; p++) {
					System.out.println("  " + propsToTune[p] + "=" + typeAdjustValueStr(types[p], currentBestVals[p]));
				}
				System.out.println("  num.generations=" + numGens);

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
				
				if (triedAtLeastOneParamAdjustment) {
					if (noImprovement) {
						stagnantCount++;
						// Finish if no improvement after 5 iterations.
						if (stagnantCount > 5) {
							break;
						}
					}
					else {
						stagnantCount = 0;
					}
				}
			}
			System.out.println("Finished adjusting parameters.");
			System.out.println("Final best parameter values, giving result " + bestResult + ", were:");
			for (int p = 0; p < propCount; p++) {
				System.out.println(propsToTune[p] + "=" + typeAdjustValueStr(types[p], currentBestVals[p]));
			}
			System.out.println("num.generations=" + numGens);
			
			outputFile.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static double typeAdjustValue(String type, double v) {
		if (type.equals("d")) return v;
		int vi = (int) Math.round(v);
		if (type.equals("i")) return vi;
		if (type.equals("b")) return Math.max(0, Math.min(1, vi));
		throw new IllegalArgumentException("Invalid parameter type specified: " + type);
	}
	
	private static String typeAdjustValueStr(String type, double v) {
		if (type.equals("d")) return ""+v;
		int vi = (int) Math.round(v);
		if (type.equals("i")) return ""+vi;
		if (type.equals("b")) return vi >= 1 ? "true" : "false";
		throw new IllegalArgumentException("Invalid parameter type specified: " + type);
	}
	
	private void addResult(Result r) {
		try {
			for (int p = 0; p < propCount; p++) {
				outputFile.append(r.getProps().get(this.propsToTune[p]) + ", ");
			}
			outputFile.append(numGens + ", " + r.performance() + "\n");
			outputFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * command line usage
	 */
	private static void usage() {
		System.out.println("Usage:\n" + "Parameter tuning can be run with:\n  <cmd> <properties-file>");
	}
	
	

	private Result doRuns(Properties props, int id, String label) throws Exception {
		if (htCondorTpl == null) {
			return new Result(props, doRunsSerial(props, label), props.getIntProperty("popul.size"), props.getIntProperty("num.generations"));
		} else {
			return new Result(props, doRunsHTCondor(props, id, label), props.getIntProperty("popul.size"), props.getIntProperty("num.generations"));
		}
	}
	
	private Results doRunsSerial(Properties props, String label) throws Exception {
		System.out.print("Starting " + numRuns + " runs (" + label + ") ");
		double[][] performances = new double[numRuns][];
		for (int r = 0; r < numRuns; r++) {
			Run runner = new Run(props);
			runner.noOutput = true;
			runner.run();
			performances[r] = runner.performance[0];
			System.out.print(label);
		}
		System.out.print(" (" + label + " finished) ");
		return new Results(performances, null);
	}
	
	private Results doRunsHTCondor(Properties props, int id, String label) throws Exception {
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
		
		String mainJAR = this.getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
		mainJAR = URLDecoder.decode(mainJAR, "UTF-8");
		String jarFiles = condorSubmit.containsKey("jar_files") ? condorSubmit.get("jar_files") + "," : "";
		jarFiles += mainJAR;
		
		condorSubmit.put("universe", "java");
		condorSubmit.put("executable", mainJAR);
		condorSubmit.put("jar_files", jarFiles);
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
		String condorClusterID = null;
		if (m.find()) {
			condorClusterID = m.group(1);
			System.out.print("Started " + numRuns + " runs (" + label + ", cluster " + condorClusterID + ") ");
			runningCondorClusterIDs.add("" + condorClusterID);
		}
		else {
			System.out.println("Unable to determine cluster ID from condor_submit output:\n " + condorProcessOut);
		}
		
		// Wait for condor jobs to finish.
		int currentDoneCount = 0;
		while (currentDoneCount != numRuns) {
			Thread.sleep(1000); // Wait for 1 second.
			Paths paths = new Paths(condorOutDir, "result-*-performance.csv");
			if (currentDoneCount != paths.count()) {
				for (int i = 0; i < paths.count() - currentDoneCount; i++) System.out.print(label);
			}
			currentDoneCount = paths.count();
		}
		System.out.print(" (" + label + " finished) ");
		
		if (condorClusterID != null) {
			runningCondorClusterIDs.remove(condorClusterID);
		}
		
		// Collate results.
		Results results = PostProcess.combineResults(condorOutDirSep + "result-*-performance.csv", false);
		
		// Clean up generated files.
		//Paths paths = new Paths("./", condorOutDir);
		//paths.delete();

		return results;
	}
	
	
	private class DoRuns implements Callable<Result> {
		int id;
		Properties props;
		String label;
		public DoRuns(Properties props, int id, String label) {
			this.id = id;
			this.props = (Properties) props.clone();;
			this.label = label;
		}
		@Override
		public Result call() throws Exception {
			return doRuns(props, id, label);
		}
	}
	
	
	private class Result {
		// Use median as it's better for distributions with outliers, and we're likely to have outliers.
		private double performance = -1;
		private int solvedByGeneration = -1;
		private int popSize;
		private int maxGens;
		private Properties props;
		
		public Result(Properties props, Results r, int popSize, int maxGens) {
			this.props = props;
			this.popSize = popSize;
			this.maxGens = maxGens;
			Statistics s = new Statistics(r);
			for (int g = 0; g < r.getItemCount(); g++) {
				double m = getCroppedMean(s, g, 0.05);
				if (m >= solvedPerformance) {
					solvedByGeneration = g;
					performance = m;
					return;
				}
			}
			performance = getCroppedMean(s, r.getItemCount()-1, 0.05);
		}

		public double performance() {
			return performance;
		}
		public int solvedByGeneration() {
			return solvedByGeneration;
		}
		public int popSize() {
			return popSize;
		}
		public int maxGens() {
			return maxGens;
		}
		
		public Properties getProps() {
			return props;
		}
		
		// Get the mean of the values, with some of the bottom and top values removed.
		private double getCroppedMean(Statistics s, int gen, double crop) {
			double[] perf = s.getData(gen).getSortedValues();
			double sum = 0;
			int cropIdx = (int) Math.round(perf.length * crop);
			for (int i = cropIdx; i < perf.length-cropIdx; i++) {
				sum += perf[i];
			}
			return sum / (perf.length - cropIdx * 2);
		}
		
		
		public boolean betterThan(Result r) {
			int solvedByEval = solvedByGeneration == -1 ? Integer.MAX_VALUE : solvedByGeneration * popSize;
			int rSolvedByEval = r.solvedByGeneration == -1 ? Integer.MAX_VALUE : r.solvedByGeneration * r.popSize;
			if (solvedByEval < rSolvedByEval) return true;
			if (solvedByEval > rSolvedByEval) return false;
			return performance > r.performance;
		}
		
		@Override
		public String toString() {
			return "(" + nf.format(performance) + " : " + (solvedByGeneration == -1 ? "not solved" : "solved in " + solvedByGeneration + " gens") + ")";
		}
	}
}
