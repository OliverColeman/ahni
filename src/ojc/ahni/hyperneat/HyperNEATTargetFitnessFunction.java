package ojc.ahni.hyperneat;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.*;
import com.anji.util.Properties;

/**
 * <p>Given a set of genotypes that encode a neural network, and a set of training examples consisting of input and target
 * (desired) output pattern pairs, determines fitness based on how close the output of the network encoded by each
 * genome is to the target output given some input.</p>
 * 
 * <p>Subclasses may wish to override {@link #evolutionFinished(ojc.ahni.hyperneat.HyperNEATEvolver)} to perform testing or other analysis
 * on the fittest and/or best performing Chromosomes evolved during the run; the method
 * {@link #generateSubstrate(Chromosome, Activator) may be used to create substrates for a Chromosome.</p>
 * 
 * @author Oliver Coleman
 */
public class HyperNEATTargetFitnessFunction extends HyperNEATFitnessFunction {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(HyperNEATTargetFitnessFunction.class);

	/**
	 * The type of error calculation to perform for each input and target output pair. Valid types are:
	 * <ul>
	 * <li>sum: the sum of the differences between each target and actual output value.</li>
	 * <li>sum-squared: the sum of the squared differences between each target and actual output value.</li>
	 * <li>squared-sum: the squared sum of the differences between each target and actual output value.</li>
	 * <li>squared-sum-squared: the squared sum of the squared differences between each target and actual output value.</li>
	 * </ul>
	 */
	public static final String ERROR_TYPE_KEY = "fitness.function.error.type";

	/**
	 * Array containing stimuli (input) examples, in the form [trial][y][x]. The dimensions should match those of the
	 * input layer of the substrate network.
	 */
	protected double[][][] inputPatterns;

	/**
	 * Array containing target (desired output) examples, in the form [trial][y][x]. The dimensions should match those
	 * of the output layer of the substrate network.
	 */
	protected double[][][] targetOutputPatterns;

	private int maxFitnessValue = 1000000; // The maximum possible fitness value.
	private String errorType;
	private boolean squareErrorPerOutput;
	private boolean squareErrorPerTrial;

	protected HyperNEATTargetFitnessFunction() {
	}

	/**
	 * Create a HyperNEATTargetFitnessFunction with the specified input and output examples.
	 * 
	 * @param inputPatterns Array containing input patterns, in the form [trial][y][x]. The dimensions should match
	 *            those of the input layer of the substrate network.
	 * @param targetOutputPatterns Array containing target output patterns, in the form [trial][y][x]. The dimensions
	 *            should match those of the output layer of the substrate network.
	 * @param minTargetValue The minimum possible value in the given input patterns.
	 * @param maxTargetValue The maximum possible value in the given target patterns.
	 */
	public HyperNEATTargetFitnessFunction(double[][][] inputPatterns, double[][][] targetOutputPatterns, double minTargetValue, double maxTargetValue) {
		this.inputPatterns = inputPatterns;
		this.targetOutputPatterns = targetOutputPatterns;
	}

	public void init(Properties props) {
		super.init(props);
		errorType = props.getProperty(ERROR_TYPE_KEY, "squared-sum-squared");
		squareErrorPerOutput = (errorType == "sum-squared" || errorType == "squared-sum-squared");
		squareErrorPerTrial = (errorType == "squared-sum" || errorType == "squared-sum-squared");
	}

	/**
	 * @return maximum possible fitness value for this function.
	 */
	public int getMaxFitnessValue() {
		return maxFitnessValue;
	}

	/**
	 * Sets the maximum possible fitness value this function will return. Default is 1000000 which is fine for nearly
	 * all purposes.
	 * 
	 * @param newMaxFitnessValue The new maximum fitness.
	 */
	protected void setMaxFitnessValue(int newMaxFitnessValue) {
		maxFitnessValue = newMaxFitnessValue;
	}

	@Override
	protected int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return evaluate(genotype, substrate, evalThreadIndex, false);
	}
	
	protected int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, boolean log) {
		double minResponse = substrate.getMinResponse();
		double maxResponse = substrate.getMaxResponse();
		double responseRange = maxResponse - minResponse;
		double maxErrorPerOutput = squareErrorPerOutput ? responseRange * responseRange : responseRange;
		double maxErrorPerTrial = width[depth - 1] * height[depth - 1] * maxErrorPerOutput;
		if (squareErrorPerTrial) {
			maxErrorPerTrial *= maxErrorPerTrial;
		}
		double maxError = inputPatterns.length * maxErrorPerTrial;
		double[][][] responses = substrate.nextSequence(inputPatterns);
		double error = 0;
		StringBuilder logString = null;
		if (log) {
			logString = new StringBuilder("HyperNEATTargetFitnessFunction trials:");
		}
		for (int i = 0; i < responses.length; ++i) {
			double trialError = 0;
			double[][] response = responses[i];
			double[][] target = targetOutputPatterns[i];
			for (int y = 0; y < target.length; y++) {
				for (int x = 0; x < target[0].length; x++) {
					double diff = Math.abs(response[y][x] - target[y][x]);
					trialError += squareErrorPerOutput ? diff * diff : diff;
				}
			}			
			error += squareErrorPerTrial ? trialError * trialError : trialError;
			
			if (log && i < 25) {
				logString.append("" + 
						"\n\tInput:  " + Arrays.deepToString(inputPatterns[i]) + 
						"\n\tTarget: " + Arrays.deepToString(targetOutputPatterns[i]) + 
						"\n\tOutput: " + Arrays.deepToString(responses[i]) + "\n");
			}
		}
		if (log) {
			logger.info(logString);
		}
		return maxFitnessValue - (int) Math.round((error / maxError) * maxFitnessValue);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>This default implementation prints out the best performing substrate using its toString() method and the input, target and output patterns for each trial.</p>
	 */
	public void evolutionFinished(HyperNEATEvolver evolver) {
		super.evolutionFinished(evolver);
		try {
			Chromosome bestPerforming = evolver.getBestPerformingFromLastGen();
			Activator substrate = generateSubstrate(bestPerforming, null);
			evaluate(bestPerforming, substrate, 0, true);
		} catch (TranscriberException e) {
			System.err.println("Error transcribing best performing individual.");
			e.printStackTrace();
		}
	}
}
