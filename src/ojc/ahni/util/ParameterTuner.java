package ojc.ahni.util;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import ojc.ahni.hyperneat.Properties;
import ojc.ahni.hyperneat.Run;

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
			int numRuns = props.getIntProperty("parametertuner.numruns", 30);
			
			props.setProperty("num.runs", "1"); // We'll calculate our own average so we can report progress as we go.
			props.setProperty("log4j.rootLogger", "OFF"); // Suppress logging.
			Logger.getRootLogger().setLevel(Level.OFF);
			props.remove("random.seed"); // Make sure the runs are randomised.

			int[] adjustIneffectiveCount = new int[propCount];
			int[] adjustCountDown = new int[propCount];
			double adjustDownFitness = 0, adjustUpFitness = 0;
			double adjustDownVal = 0, adjustUpVal = 0;

			System.out.println("Determining initial fitness.");
			double bestFitness = doRuns(props, args, numRuns);
			System.out.println("Initial fitness: " + bestFitness);
			
			for (int i = 0; i < maxIterations; i++) {
				System.out.println("Start iteration " + i);
				
				boolean adjustedAnyParams = false;
				boolean adjustCountDownZeroExists = false; // Indicates if any parameters are due to be adjusted this iteration.

				// Adjust each parameter in turn.
				for (int p = 0; p < propCount; p++) {
					String propKey = propsToTune[p];

					// Sample fitness either side of current parameter value.
					if (adjustCountDown[p] == 0) {
						System.out.println("\tTuning " + propKey + " (current value is " + currentBestVals[p] + ").");
						double newVal = currentBestVals[p];
						double newBestFitness = bestFitness;

						adjustDownVal = Math.min(maxValues[p], Math.max(minValues[p], currentBestVals[p] / valAdjustFactor));
						if (adjustDownVal != currentBestVals[p]) {
							props.setProperty(propKey, "" + adjustDownVal);
							adjustDownFitness = doRuns(props, args, numRuns);
							boolean better = adjustDownFitness > bestFitness * significanceFactor;
							System.out.println("\t\tValue " + adjustDownVal + " gave fitness of " + adjustDownFitness + " (" + (!better ? "not significantly " : "") + "greater than current best).");

							if (better) {
								newBestFitness = adjustDownFitness;
								newVal = adjustDownVal;
							}
						}

						adjustUpVal = Math.min(maxValues[p], Math.max(minValues[p], currentBestVals[p] * valAdjustFactor));
						if (adjustUpVal != currentBestVals[p]) {
							props.setProperty(propKey, "" + adjustUpVal);
							adjustUpFitness = doRuns(props, args, numRuns);
							boolean better = adjustUpFitness > bestFitness * significanceFactor && adjustUpFitness > adjustDownFitness;
							System.out.println("\t\tValue " + adjustUpVal + " gave fitness of " + adjustUpFitness + " (" + (!better ? "not significantly " : "") + "greater than current best and/or downward adjustment).");

							if (better) {
								newBestFitness = adjustUpFitness;
								newVal = adjustUpVal;
							}
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
					} else {
						adjustCountDown[p]--;
						System.out.println("\tSkipping " + propKey + " for " + adjustCountDown[p] + " more iterations.");
					}
					
					adjustCountDownZeroExists |= adjustCountDown[p] == 0;
				}

				System.out.println("Finished iteration. Current best values are:\n" + Arrays.toString(currentBestVals));

				if (!adjustedAnyParams) { 
					valAdjustFactor = (valAdjustFactor-1)/2+1;
					System.out.println("Value adjust factor is now " + valAdjustFactor);
					
				}

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
	
	private static double doRuns(Properties props, String[] args, int numRuns) throws Exception {
		double sumFitness = 0;
		System.out.print("\t\tStarting " + numRuns + " runs: ");
		for (int r = 0; r < numRuns; r++) {
			System.out.print(r + " ");
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

	/**
	 * command line usage
	 */
	private static void usage() {
		System.out.println("Usage:\n" + "Parameter tuning can be run with:\n  <cmd> <properties-file>");
	}
}
