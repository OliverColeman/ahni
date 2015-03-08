package com.ojcoleman.ahni.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
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
 * adjusted iteratively, with each property adjusted in turn each iteration. The underlying assumption is that the
 * fitness landscape represented by the parameters to adjust is unimodal. It is currently assumed that all parameters
 * are represented by floating point numbers.
 * </p>
 * <p>
 * For each property for each iteration the fitness value is calculated by adjusting the property up or down by a
 * multiplicative factor and running the experiment. If either of these adjustments results in a higher fitness then the
 * adjusted value will be adopted (at least until the next iteration when a new value may be adopted).
 * </p>
 * <p>
 * The default initial multiplicative factor is 2 (thus the initial downward adjustment of a property p is p/2 and the
 * upward adjustment is p*2). A different initial multiplicative factor may be specified in the Properties file. For each
 * iteration in which no property adjustments are adopted the multiplicative factor is halved.
 * </p>
 * <p>
 * If fitness was not increased by adjusting a property in the previous iteration then it will not be adjusted in the
 * current iteration to save time. If adjusting the property in the next iteration still doesn't yield an increase in
 * fitness then the property will be ignored for the next two iterations, and so on.
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
 * <dd>The number of runs to perform when determining fitness for a set of property values. Default is 50, which is probably about the safest minimum.</dd>
 * </dl>
 * </p>
 */
public class ParameterTuner implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final DecimalFormat nf = new DecimalFormat("0.00000");
	private static final DateFormat df = new SimpleDateFormat("HH:mm:ss");
	
	
	private ArrayList<String> runningCondorClusterIDs = new ArrayList<String>();
	private Properties props;

	private Param[] propsToTune;
	private Param.Value[] currentBestValues;
	private int propCount;
	private int numGens;
	private int popSize;
	private int totalEvaluations;
	private int maxIterations;
	private int numRuns;
	private double solvedPerformance;
	private String htCondorTpl;
	private int[] adjustIneffectiveCount;
	private int[] adjustCountDown;
	private Result bestResult;
	private int iteration;
	private int property;
	private int stagnantCount = 0;
	private boolean suppressLogging;
	
	public static void main(String[] args) {
		File checkPoint = new File("checkpoint");
		if (checkPoint.exists() && checkPoint.isFile()) {
			System.out.println("Resume previous session?");
			if (System.console().readLine().toLowerCase().matches("yes|y")) {
				try {
					ObjectInputStream ois = new ObjectInputStream(new FileInputStream(checkPoint));
					ParameterTuner pt = (ParameterTuner) ois.readObject();
					ois.close();
					pt.go(null, true);
				} catch (Exception e) {
					System.err.println("Unable to resume from check point.");
					e.printStackTrace();
					System.exit(1);
				}
				System.exit(0);
			}
		}
		if (args.length == 0) {
			usage();
			System.exit(-1);
		}
		
		ParameterTuner pt = new ParameterTuner();
		pt.go(args, false);
	}
	
	public ParameterTuner() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
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
	
	public void go(String[] args, boolean resume) {
		try {
			ExecutorService runExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory(ParameterTuner.class.getName()));
			BufferedWriter resultFile;

			if (!resume) {
				props = new Properties(args[0]);
				
				Properties toTune = new Properties(props.getOnlySubProperties("parametertuner.tune"));
				TreeMap<Integer, Param> propsTemp = new TreeMap<Integer, Param>();
				TreeMap<Integer, Param.Value> initValTemp = new TreeMap<Integer, Param.Value>();
				for (String key : toTune.stringPropertyNames()) {
					if (!key.endsWith(".prop"))
						continue;
					
					int propIndex = Integer.parseInt(key.substring(0, key.length() - 5));
					String keyPrefix = propIndex + ".";
					
					String propName = toTune.getProperty(keyPrefix + "prop");
					Param.Type propType = Param.Type.valueOf(toTune.getProperty(keyPrefix + "type", "float").toUpperCase());
					Param.AdjustType adjustType = Param.AdjustType.valueOf(toTune.getProperty(keyPrefix + "adjust.type", "factor").toUpperCase());
					double adjustAmountOrFactor = toTune.getDoubleProperty(keyPrefix + "adjust.amount", 2);
					
					if (propType != Param.Type.DISCRETE && adjustType == Param.AdjustType.ALL) {
						throw new IllegalArgumentException("ParameterTuner: cannot use \"ALL\" adjust type for non-discrete property type for " + propName + ".");					
					}
					
					Param param = null;
					if (propType == Param.Type.DISCRETE) {
						String[] discreteVals = toTune.getProperty(keyPrefix + "discrete_values").split("\\s*;\\s*");
						//String prop, String type, int adjustType, double adjustAmountOrFactor, String[] discreteVals
						param = new Param(propName, propType, adjustType, adjustAmountOrFactor, discreteVals);
					}
					else {
						//String name, String type, int adjustType, double adjustAmountOrFactor, double min, double max
						double min = toTune.getDoubleProperty(keyPrefix + "min", propType == Param.Type.FLOAT ? 0 : 1);
						double max = toTune.getDoubleProperty(keyPrefix + "max", propType == Param.Type.FLOAT ? 1 : 1000);
						param = new Param(propName, propType, adjustType, adjustAmountOrFactor, min, max);
					}
					propsTemp.put(propIndex, param);
					double initVal = toTune.getDoubleProperty(keyPrefix + "initial");
					initValTemp.put(propIndex, param.getValue(initVal));
					
					if (propName.equals("popul.size")) {
						popSize = (int) Math.round(initVal);
					}
				}
				propCount = propsTemp.size();
				propsToTune = new Param[propCount];
				currentBestValues = new Param.Value[propCount];
				int j = 0;
				for (Entry<Integer, Param> e : propsTemp.entrySet()) {
					propsToTune[j] = e.getValue();
					currentBestValues[j] = initValTemp.get(e.getKey());
					j++;
				}
				
				numGens = props.getIntProperty("parametertuner.numgens", 500);
				popSize = props.getIntProperty("popul.size");
				
				// Store the total evaluations that will be performed for a run, so that if we're tuning popul.size
				// then we can adjust the number of generations accordingly.
				totalEvaluations = numGens * popSize;
				
				maxIterations = props.getIntProperty("parametertuner.maxiterations", 100);
				numRuns = props.getIntProperty("parametertuner.numruns", 50);
				solvedPerformance = props.getDoubleProperty("parametertuner.solvedperformance", 1);
				htCondorTpl = props.getProperty("parametertuner.htcondor", null);
				if (htCondorTpl != null) {
					// Clean up generated files from previous aborted runs.
					Paths paths = new Paths("./", "pt-condor-*");
					paths.delete();
	
					// TODO Allow keeping file output (tricky/painful with Condor 7.0)
					props.remove("output.dir");
				}
				
				props.setProperty("num.runs", "1"); // We'll calculate our own average so we can report progress as we go.
				props.setProperty("num.generations", "" + numGens);
				
				suppressLogging = props.getBooleanProperty("parametertuner.suppresslog", true); 
				if (suppressLogging) {
					props.setProperty("log4j.rootLogger", "OFF"); // Suppress logging.
					Logger.getRootLogger().setLevel(Level.OFF);
					props.remove("output.dir");
				}
				
				props.remove("random.seed"); // Make sure the runs are randomised.
				
				adjustIneffectiveCount = new int[propCount];
				adjustCountDown = new int[propCount];
				
				resultFile = new BufferedWriter(new FileWriter("results.csv"));
				resultFile.append("iteration, tuned property, ");
				for (int p = 0; p < propCount; p++) {
					resultFile.append(propsToTune[p].name + ", ");
				}
				resultFile.append("gens, mean perf\n");
	
				System.out.println("Initial values:");
				for (int p = 0; p < propCount; p++) {
					String propKey = propsToTune[p].name;
					props.setProperty(propKey, currentBestValues[p].toString());
					System.out.println("  " + propsToTune[p] + "=" + props.getProperty(propKey));
				}
				System.out.println("  num.generations=" + numGens);
				
				if (!props.getBooleanProperty("parametertuner.skipinitial", false)) {
					System.out.println("Determining fitness for initial values:");
					bestResult = runExecutor.submit(new DoRuns(props, "initial", "0")).get();
					System.out.println();
					if (bestResult.solvedByGeneration() != -1) {
						numGens = bestResult.solvedByGeneration();
						totalEvaluations = numGens * popSize;
						props.setProperty("num.generations", "" + numGens);
						System.out.println("Set generations to " + numGens);
					}
					System.out.println("Initial performance: " + nf.format(bestResult.performance()));
					addResult(bestResult, resultFile);
				}
				else {
					System.out.println("Skipping determining performance for initial parameter values."); 
				}
				
				iteration = 1;
			}
			else { //resume from previous checkpoint
				System.out.println("Resuming from iteration " + iteration + ", property " + propsToTune[property].name);
				
				System.out.println("Cancelling old condor jobs (if any).");
            	for (String id : runningCondorClusterIDs) {
            		try {
            			System.out.println("  condor_rm " + id);
						Runtime.getRuntime().exec("condor_rm " + id);
					} catch (IOException e) {
						e.printStackTrace();
					}
            	}
            	runningCondorClusterIDs.clear();
				
            	System.out.println("  Previous best result was " + bestResult + " using values:");
				for (int p = 0; p < propCount; p++) {
					System.out.println("    " + propsToTune[p] + "=" + currentBestValues[p]);
				}
				System.out.println("    num.generations=" + numGens + "\n");
				for (int p = 0; p < propCount; p++) {
					System.out.println("  Value adjust amount for " + propsToTune[p].name + " is " + nf.format(propsToTune[p].adjustAmountOrFactor));
				}
				System.out.println("\n");
				
				resultFile = new BufferedWriter(new FileWriter("results.csv", true));
			}
			
			// Start/resume tuning.
			for ( ; iteration <= maxIterations; iteration++) {
				System.out.println("Start iteration " + iteration);
				
				boolean adjustedAnyParams = false;
				boolean adjustCountDownZeroExists = false; // Indicates if any parameters are due to be adjusted this iteration.
				boolean noImprovement = true;
				boolean triedAtLeastOneParamAdjustment = false;

				// Adjust each property in turn.
				if (!resume) property = 0; // If we're resuming then continue on the property we left off at.
				for (; property < propCount; property++) {
					// If we're not resuming create a checkpoint.
					if (!resume) {
						ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("checkpoint"));
						oos.writeObject(this);
						oos.close();
					}
					else {
						resume = false;
					}
					
					String propKey = propsToTune[property].name;
					boolean tuningPopSize = propKey.equals("popul.size");

					// Sample fitness either side of current property value.
					if (adjustCountDown[property] == 0) {
						System.out.println("\tTuning " + propKey + " (current value is " + currentBestValues[property] + "). ");
						
						// If we didn't determine performance with the initial values, include it now.
						Param.Value[] variations = currentBestValues[property].variations(bestResult == null);
						int varCount = variations.length;
						Future<Result>[] futures = (Future<Result>[]) Array.newInstance(Future.class, varCount);
						
						for (int var = 0; var < varCount; var++) {
							System.out.println("\t\tTrying value: " + variations[var] + ".");
						}
						System.out.print("\t\t");
						long start = System.currentTimeMillis();
						for (int var = 0; var < varCount; var++) {
							props.setProperty(propKey, variations[var].toString());
							if (tuningPopSize) {
								// Adjust gens so that total evaluations per run is maintained.
								props.setProperty("num.generations", "" + (int) Math.round(totalEvaluations / variations[var].getValue()));
							}
							String name = var + "-" + propKey + "=" + variations[var].toString();
							futures[var] = runExecutor.submit(new DoRuns(props, name, ""+var));
							triedAtLeastOneParamAdjustment = true;
						}
						Param.Value newVal = null;
						int doneCount = 0;
						boolean[] done = new boolean[varCount];
						while (doneCount != varCount) {
							Thread.sleep(1000);
							for (int var = 0; var < varCount; var++) {
								if (!done[var] && futures[var].isDone()) {
									Result adjustResult = futures[var].get();
									boolean better = bestResult == null || adjustResult.betterThan(bestResult);
									System.out.println("\n\t\tValue " + variations[var] + (tuningPopSize ? " (" + adjustResult.maxGens() + " max gens)" : "") +  " gave " + adjustResult + "." + (better ? " BETTER THAN CURRENT BEST." : ""));
									if (better) {
										bestResult = adjustResult;
										newVal = variations[var];
									}
									addResult(adjustResult, resultFile);
									done[var] = true;
									doneCount++;
								}
							}
						}
						
						long finish = System.currentTimeMillis();
						
						System.out.println("\t\tTook " + DurationFormatUtils.formatPeriod(start, finish, "d:HH:mm:ss") + ".\n");
						
						resultFile.append("\n");
						
						// If the fitness was increased by an adjustment.
						if (newVal != null) {
							adjustIneffectiveCount[property] = 0;
							currentBestValues[property] = newVal;
							adjustedAnyParams = true;
							
							if (tuningPopSize) {
								popSize = (int) Math.round(newVal.getValue());
								numGens = (int) Math.round(totalEvaluations / newVal.getValue());
								props.setProperty("num.generations", "" + numGens);
							}
							
							if (bestResult.solvedByGeneration() != -1) {
								int newNumGens = (int) Math.round(bestResult.solvedByGeneration() * 1.1);
								if (newNumGens < numGens) {
									numGens = newNumGens;
									totalEvaluations = numGens * popSize;
									props.setProperty("num.generations", "" + numGens);
									System.out.println("  Reduced number of generations to " + numGens);
								}
							}
							
							noImprovement = false;
						} else {
							// If the fitness did not improve by adjusting this property then hold off adjusting it
							// for a few iterations (dependent on how many times adjusting it has been ineffective in
							// previous generations).
							adjustIneffectiveCount[property]++;
							adjustCountDown[property] = adjustIneffectiveCount[property];

						}

						// Set property to current best value.
						props.setProperty(propKey, currentBestValues[property].toString());
					} else {
						adjustCountDown[property]--;
						System.out.println("\tSkipping " + propKey + " for " + adjustCountDown[property] + " more iterations.");
					}
					
					adjustCountDownZeroExists |= adjustCountDown[property] == 0;
				}
				
				resultFile.append("\n");

				System.out.println("\nFinished iteration. Best result is " + bestResult + ". Current best values are:");
				for (int p = 0; p < propCount; p++) {
					System.out.println("  " + propsToTune[p] + "=" + currentBestValues[p]);
				}
				System.out.println("  num.generations=" + numGens);

				if (!adjustedAnyParams) {
					System.out.println();
					for (int p = 0; p < propCount; p++) {
						if (propsToTune[p].reduceAdjustAmountOrFactor())
							System.out.println("Value adjust amount for " + propsToTune[p].name + " is now " + nf.format(propsToTune[p].adjustAmountOrFactor));
					}
				}
				System.out.println("\n");

				// Make sure that at least one property is due to be adjusted. 
				while (!adjustCountDownZeroExists) {
					for (int p = 0; p < propCount; p++) {
						if (adjustCountDown[p] > 0) adjustCountDown[p]--;
						adjustCountDownZeroExists |= adjustCountDown[p] == 0;
					}
				}
				
				if (triedAtLeastOneParamAdjustment) {
					if (noImprovement) {
						stagnantCount++;
						// Finish if no improvement after 3 iterations.
						if (stagnantCount > 3) {
							break;
						}
					}
					else {
						stagnantCount = 0;
					}
				}
			}
			System.out.println("Finished adjusting parameters.");
			System.out.println("Final best property values, giving result " + bestResult + ", were:");
			for (int p = 0; p < propCount; p++) {
				System.out.println(propsToTune[p] + "=" + currentBestValues[p]);
			}
			System.out.println("num.generations=" + numGens);
			
			resultFile.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private void addResult(Result r, BufferedWriter resultFile) {
		try {
			if (iteration > 0) {
				resultFile.append("\"" + iteration + "\",\"" + this.propsToTune[property].name + "\",");
			}
			else {
				// Initial result.
				resultFile.append("\"0\",\"<initial>\",");
			}
			for (int p = 0; p < propCount; p++) {
				resultFile.append("\"" + r.getProps().get(this.propsToTune[p].name) + "\",");
			}
			resultFile.append("\"" + numGens + "\",\"" + r.performance() + "\"\n");
			resultFile.flush();
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
	
	
	
	
	private class DoRuns implements Callable<Result> {
		String name;
		Properties props;
		String label;
		
		public DoRuns(Properties props, String name, String label) {
			this.name = name;
			this.props = (Properties) props.clone();
			this.label = label;
		}
		
		@Override
		public Result call() throws Exception {
			return doRuns(props, name, label);
		}
		
		private Result doRuns(Properties props, String name, String label) throws Exception {
			if (htCondorTpl == null) {
				return new Result(props, doRunsSerial(props, label), props.getIntProperty("popul.size"), props.getIntProperty("num.generations"));
			} else {
				return new Result(props, doRunsHTCondor(props, name, label), props.getIntProperty("popul.size"), props.getIntProperty("num.generations"));
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
				if (r > 0 && r % 25 == 0) System.out.print("("+r+")");					
			}
			System.out.print(" (" + label + " finished) ");
			return new Results(performances, null);
		}
		
		private Results doRunsHTCondor(Properties props, String name, String label) throws Exception {
			// Create dir to store temp files.
			String condorOutDir = "pt-condor-" + iteration + "-" + name;
			if (condorOutDir.length() > 255) {
				// Trim to maximum length of 255 as this is the maximum on most file systems.
				condorOutDir = condorOutDir.substring(0, 255);
			}
			File condorOutDirFile = new File(condorOutDir);
			FileUtils.deleteQuietly(condorOutDirFile);
			String condorOutDirSep = condorOutDir + File.separator;
			condorOutDirFile.mkdir();
			// Save properties file.
			props.store(new FileOutputStream(condorOutDirSep + "pt.properties"), condorOutDir);
			
			// Generate condor submit file from template.
			String[] lines = htCondorTpl.split("\n");
			Map<String, String> condorSubmit = new HashMap<String, String>();
			for (String line : lines) {
				if (!line.trim().isEmpty()) {
					String[] keyVal = line.split("=", 2);
					condorSubmit.put(keyVal[0].trim(), keyVal[1].trim());
				}
			}
			
			String mainJAR = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
			mainJAR = URLDecoder.decode(mainJAR, "UTF-8");
			String jarFiles = condorSubmit.containsKey("jar_files") ? condorSubmit.get("jar_files") + " " : "";
			jarFiles += mainJAR;
			
			condorSubmit.put("universe", "java");
			condorSubmit.put("executable", mainJAR);
			condorSubmit.put("jar_files", jarFiles);
			condorSubmit.put("arguments", "com.ojcoleman.ahni.hyperneat.Run " + (suppressLogging ? "-ao" : "") + " -od ./ -f -op cp$(Process)- -ar result ./pt.properties");
			condorSubmit.put("universe", "java");
			String tif = (condorSubmit.containsKey("transfer_input_files") ? condorSubmit.get("transfer_input_files") + ", " : "") + "pt.properties";
			condorSubmit.put("transfer_input_files", tif);
			condorSubmit.put("output", "out-$(Process).txt");
			condorSubmit.put("error", "err-$(Process).txt");
			condorSubmit.put("log", "condorlog-$(Process).txt");
			condorSubmit.put("when_to_transfer_output", "ON_EXIT_OR_EVICT");
			condorSubmit.put("should_transfer_files", "YES");
			condorSubmit.put("environment", "\"AHNI_RUN_ID=$(Process)\"");
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
			// TODO use condor_q to check job status instead of output files (in case a job crashed).
			int currentDoneCount = 0;
			while (currentDoneCount != numRuns) {
				Thread.sleep(1000); // Wait for 1 second.
				Paths paths = new Paths(condorOutDir, "cp*-result-performance.csv");
				if (currentDoneCount != paths.count()) {
					for (; currentDoneCount < paths.count(); currentDoneCount++) {
						System.out.print(label);
						if (currentDoneCount > 0 && currentDoneCount % 25 == 0) System.out.print("("+currentDoneCount+")");
					}
					//if (currentDoneCount > 0 && currentDoneCount % 25 == 0) System.out.print("("+currentDoneCount+")");
				}
			}
			System.out.print(" (" + label + " finished) ");
			
			if (condorClusterID != null) {
				runningCondorClusterIDs.remove(condorClusterID);
			}
			
			// Collate results.
			Results results = PostProcess.combineResults(condorOutDirSep + "cp*-result-performance.csv", false);
			
			// Write them out to disk for later inspection if desired.
			BufferedWriter resultsAllWriter = new BufferedWriter(new FileWriter(condorOutDirSep + "results-all-performance.csv"));
			resultsAllWriter.write(results.toString());
			resultsAllWriter.close();
			BufferedWriter resultsStatsWriter = new BufferedWriter(new FileWriter(condorOutDirSep + "results-stats-performance.csv"));
			Statistics stats = new Statistics(results);
			resultsStatsWriter.write(stats.getBasicStats().toString());
			resultsStatsWriter.close();
			
			// Clean up some generated files.
			//Paths paths = new Paths("./", condorOutDir, "result-*-*.csv");
			//paths.delete();

			return results;
		}
	}
	
	
	private class Result implements Serializable {
		private static final long serialVersionUID = 1L;
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
				performance = getCroppedMean(s, g, 0.05);
				if (performance >= solvedPerformance) {
					solvedByGeneration = g;
					return;
				}
			}
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
	
	
	public static class Param implements Serializable {
		private static final long serialVersionUID = 1L;

		public static enum Type implements Serializable { FLOAT, INTEGER, BOOLEAN, DISCRETE };
		public static enum AdjustType implements Serializable { DELTA, FACTOR, ALL };
		
		Type type;
		String name;
		AdjustType adjustType;
		double adjustAmountOrFactor;
		double min, max;
		String[] discreteVals;
		
		public Param(String name, Type type, AdjustType adjustType, double adjustAmountOrFactor, double min, double max) {
			this.name = name;
			this.type = type;
			this.adjustType = adjustType;
			this.adjustAmountOrFactor = adjustAmountOrFactor;
			if (this.type == Type.BOOLEAN) {
				this.min = 0;
				this.max = 1;
			}
			else {
				this.min = min;
				this.max = max;
			}
		}
		public Param(String prop, Type type, AdjustType adjustType, double adjustAmountOrFactor, String[] discreteVals) {
			this(prop, type, adjustType, adjustAmountOrFactor, 0, discreteVals.length-1);
			this.discreteVals = discreteVals;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public Value getValue(double v) {
			return new Value(v);
		}
		
		public boolean reduceAdjustAmountOrFactor() {
			if (adjustType == AdjustType.ALL || type == Type.BOOLEAN)
				return false;
			double orig = adjustAmountOrFactor;
			if (adjustType == AdjustType.FACTOR)
				adjustAmountOrFactor = (adjustAmountOrFactor-1)/2+1;
			else
				adjustAmountOrFactor /= 2;
			
			if (adjustType == AdjustType.DELTA && (type == Type.INTEGER || type == Type.DISCRETE)) {
				adjustAmountOrFactor = Math.max(1, adjustAmountOrFactor);
			}
			return adjustAmountOrFactor != orig;
		}
		
		public class Value implements Serializable {
			private static final long serialVersionUID = 1L;
			private double value;
			
			public Value(double v) {
				value = v;
				if (type == Type.INTEGER || type == Type.BOOLEAN) value = Math.round(value);
				value = Math.max(min, Math.min(max, value));
			}
			
			public double getValue() {
				return value;
			}
			
			@Override
			public String toString() {
				if (type == Type.FLOAT) return ""+value;
				int vi = (int) Math.round(value);
				if (type == Type.INTEGER) return ""+vi;
				if (type == Type.BOOLEAN) return vi == 0 ? "false" : "true";
				return discreteVals[vi];
			}
			
			public Value up() {
				Value v = new Value(adjustType == AdjustType.DELTA ? value + adjustAmountOrFactor : value * adjustAmountOrFactor);
				return (v.value == value) ? null : v;
			}
			public Value down() {
				Value v = new Value(adjustType == AdjustType.DELTA ? value - adjustAmountOrFactor : value / adjustAmountOrFactor);
				return (v.value == value) ? null : v;
			}
			
			public Value[] variations(boolean includeCurrentForDiscrete) {
				if (type == Type.DISCRETE && adjustType == AdjustType.ALL) {
					Value[] values = new Value[discreteVals.length - (includeCurrentForDiscrete ? 0 : 1)];
					for (int i = 0, j = 0; i < discreteVals.length; i++) {
						// Skip current value.
						if (includeCurrentForDiscrete || i != value)
							values[j++] = new Value(i);
					}
					return values;
				}
				if (type == Type.BOOLEAN) {
					return new Value[]{new Value(value == 0 ? 1 : 0)};
				}
				Value up = up();
				Value down = down();
				if (up != null && down != null) {
					return new Value[]{down, up};
				} else if (up != null) {
					return new Value[]{up};
				} else if (down != null) {
					return new Value[]{down};
				}
				return new Value[0];
			}
		}
	}
}
