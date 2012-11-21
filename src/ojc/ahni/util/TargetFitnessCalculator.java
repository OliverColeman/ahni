package ojc.ahni.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import ojc.ahni.integration.AHNIRunProperties;
import ojc.ahni.integration.BainNN;

import org.apache.log4j.Logger;

import com.anji.integration.*;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * <p>Utility class to perform target fitness function calculations.</p>
 * 
 * @see ojc.ahni.integration.TargetFitnessFunctionMT
 * @see ojc.ahni.hyperneat.HyperNEATTargetFitnessFunction
 * @author Oliver Coleman
 */
public class TargetFitnessCalculator implements Configurable {
	private static Logger logger = Logger.getLogger(TargetFitnessCalculator.class);
	
	
	/**
	 * The type of error calculation to perform for each input and target output pair. Valid types are:
	 * <ul>
	 * <li>sum: the sum of the differences between each target and actual output value.</li>
	 * <li>sum-squared: the sum of the squared differences between each target and actual output value.</li>
	 * <li>squared-sum: the squared sum of the differences between each target and actual output value.</li>
	 * <li>squared-sum-squared: the squared sum of the squared differences between each target and actual output value.</li>
	 * </ul>
	 * The preferred method of specifying error type is to use the {@link #ERROR_TYPE_TRIAL_KEY} and {@link #ERROR_TYPE_OUTPUT_KEY} properties.
	 */
	@Deprecated
	public static final String ERROR_TYPE_KEY = "fitness.function.error.type";
	
	/**
	 * The type of error calculation to perform over the error of each trial. 
	 * Any type in {@link TargetFitnessCalculator.ErrorType} may be used.
	 * The default is RMSE.
	 * @see #ERROR_TYPE_OUTPUT_KEY
	 */
	public static final String ERROR_TYPE_TRIAL_KEY = "fitness.function.error.type.trial";
	
	/**
	 * The type of error calculation to perform over the error of each output.
	 * Any type in {@link TargetFitnessCalculator.ErrorType} may be used.
	 * The default is SAE.
	 * @see #ERROR_TYPE_TRIAL_KEY
	 */
	public static final String ERROR_TYPE_OUTPUT_KEY = "fitness.function.error.type.output";
	
	/**
	 * The method for calculating a fitness value from the error value. Valid types are:
	 * <ul>
	 * <li>proportional: The fitness is calculated as <em>MAX_FITNESS * (error / MAX_ERROR)</em>, where MAX_FITNESS is some large constant and MAX_ERROR is the maximum possible error value.</li>
	 * <li>inverse: The fitness is calculated as <em>MAX_FITNESS / (1 + error)</em>, where MAX_FITNESS is some large constant.</li>
	 * </ul>
	 * The default is proportional.
	 */
	public static final String FITNESS_CONVERSION_TYPE_KEY = "fitness.function.error.conversion.type";

	private int maxFitnessValue = 1000000; // The maximum possible fitness value.
	private ErrorType errorTypeTrial;
	private ErrorType errorTypeOutput;
	private String fitnessConversionType;
	private Random random;

	public TargetFitnessCalculator() {
	}

	public void init(Properties props) {
		// If old error type specified.
		if (props.containsKey(ERROR_TYPE_KEY)) {
			if (props.containsKey(ERROR_TYPE_TRIAL_KEY) || props.containsKey(ERROR_TYPE_OUTPUT_KEY)) {
				logger.warn("Both new (" + ERROR_TYPE_TRIAL_KEY + " and/or " + ERROR_TYPE_OUTPUT_KEY + ") and deprecated (" + ERROR_TYPE_KEY + ") error types specified for target fitness function, using deprecated type.");
			}
			else {
				logger.warn(ERROR_TYPE_KEY + " property is deprecated, use " + ERROR_TYPE_TRIAL_KEY + " and " + ERROR_TYPE_OUTPUT_KEY + ".");
			}
			String errorType = props.getProperty(ERROR_TYPE_KEY, "").trim().toLowerCase();
			String[] validErrorTypes = new String[]{"sum", "sum-squared", "squared-sum", "squared-sum-squared"};
			if (!Arrays.asList(validErrorTypes).contains(errorType)) {
				throw new IllegalArgumentException(FITNESS_CONVERSION_TYPE_KEY + " property must be one of: " + Arrays.toString(validErrorTypes));
			}
			boolean squareErrorPerOutput = (errorType == "sum-squared" || errorType == "squared-sum-squared");
			boolean squareErrorPerTrial = (errorType == "squared-sum" || errorType == "squared-sum-squared");
			errorTypeTrial = squareErrorPerTrial ? ErrorType.SSE : ErrorType.SAE;
			errorTypeOutput = squareErrorPerOutput ? ErrorType.SSE : ErrorType.SAE;
		}
		else {
			errorTypeTrial = ErrorType.valueOf(props.getProperty(ERROR_TYPE_TRIAL_KEY, "").trim().toUpperCase());
			errorTypeOutput = ErrorType.valueOf(props.getProperty(ERROR_TYPE_OUTPUT_KEY, "").trim().toUpperCase());
			if (errorTypeOutput.rootTotalError() && errorTypeTrial.squareErrors()) {
				logger.warn("It doesn't make sense to take the square root of the total error over all outputs for a single trial (" + ERROR_TYPE_OUTPUT_KEY + "=" + errorTypeOutput + ") and then square the error for each trial in calculating the error over all trials (" + ERROR_TYPE_TRIAL_KEY + "=" + errorTypeTrial + ").");
			}
		}
		
		fitnessConversionType = props.getProperty(FITNESS_CONVERSION_TYPE_KEY, "proportional").trim().toLowerCase();
		if (!fitnessConversionType.equals("proportional") && !fitnessConversionType.equals("inverse")) {
			throw new IllegalArgumentException(FITNESS_CONVERSION_TYPE_KEY + " property must be one of \"proportional\" or \"inverse\".");
		}
		
		random = ((AHNIRunProperties) props).getEvolver().getConfig().getRandomGenerator();
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
	public void setMaxFitnessValue(int newMaxFitnessValue) {
		maxFitnessValue = newMaxFitnessValue;
	}
	
	/**
	 * Evaluate the given network on the given input and target output pairs. Currently only 1 or 2 dimensional input and output arrays are supported 
	 * (thus inputPatterns and targetOutputPatterns can have 2 or 3 dimensions). 
	 * @param substrate The network to evaluate.
	 * @param inputPatterns Array containing stimuli (input) examples, in the form [trial][dN]...[d0]. The dimensions should match those of the
	 * input layer of the substrate network.
	 * @param targetOutputPatterns Array containing stimuli (input) examples, in the form [trial][dN]...[d0]. The dimensions should match those of the
	 * input layer of the substrate network.
	 * @param minTargetOutputValue The smallest value that occurs in the target outputs.
	 * @param maxTargetOutputValue The largest value that occurs in the target outputs.
	 * @param logOutput If not null then for each pattern the input, target and output will be written to this.
	 * @return The calculated fitness value.
	 */
	public Results evaluate(Activator substrate, Object inputPatterns, Object targetOutputPatterns, double minTargetOutputValue, double maxTargetOutputValue, NiceWriter logOutput) {
		//if (substrate instanceof BainNN && ((BainNN) substrate).getTopology() == BainNN.Topology.RECURRENT) {
		//	logger.debug("Setting fitness to 0 due to recurrent topology for target fitness function.");
		//	return new Results();
		//}
		
		int dim = (inputPatterns instanceof double[][]) ? 1 : 2;
		double[][] input1D = null, output1D = null, responses1D = null;
		double[][][] input2D = null, output2D = null, responses2D = null;
		if (dim == 1) {
			input1D = (double[][]) inputPatterns;
			output1D = (double[][]) targetOutputPatterns;
			responses1D = substrate.nextSequence(input1D);
		}
		else {
			input2D = (double[][][]) inputPatterns;
			output2D = (double[][][]) targetOutputPatterns;
			responses2D = substrate.nextSequence(input2D);
		}
		
		double maxError = 0;
		int trialCount = dim == 1 ? input1D.length : input2D.length;
		int outputCount = substrate.getOutputCount();
		if (substrate.getMinResponse() > minTargetOutputValue || substrate.getMaxResponse() < maxTargetOutputValue) {
			throw new IllegalStateException("The response range of the substrate does not encompass the target output range.");
		}
		
		double maxErrorPerOutput = Math.max(substrate.getMaxResponse() - minTargetOutputValue, maxTargetOutputValue - substrate.getMinResponse());
		if (errorTypeOutput.squareErrors()) maxErrorPerOutput = maxErrorPerOutput * maxErrorPerOutput;
		if (errorTypeOutput.sumErrors()) maxErrorPerOutput = outputCount * maxErrorPerOutput;
		if (errorTypeOutput.rootTotalError()) maxErrorPerOutput = Math.sqrt(maxErrorPerOutput);
		else if (errorTypeOutput.squareTotalError()) maxErrorPerOutput = maxErrorPerOutput * maxErrorPerOutput;
		
		maxError = errorTypeTrial.squareErrors() ? maxErrorPerOutput * maxErrorPerOutput : maxErrorPerOutput;
		if (errorTypeTrial.sumErrors()) maxError =  trialCount * maxError;
		if (errorTypeTrial.rootTotalError()) maxError = Math.sqrt(maxError);
		else if (errorTypeTrial.squareTotalError()) maxError = maxError * maxError;
		
		List<Integer> trialIndexes = new ArrayList<Integer>(trialCount);
		for (int i = 0; i < trialCount; i++) trialIndexes.add(i);
		if (logOutput == null) // Keep trials in order when logging.
			Collections.shuffle(trialIndexes, random);
		
		double totalError = 0;
		for (int i = 0; i < trialCount; i++) {
			int trial = trialIndexes.get(i);
			double trialError = 0;
			if (dim == 1) {
				for (int x = 0; x < output1D[trial].length; x++) {
					double diff = responses1D[trial][x] - output1D[trial][x];
					trialError += errorTypeOutput.squareErrors() ? diff * diff : Math.abs(diff);
				}
				if (logOutput != null) {
					try {
						logOutput.put(trial).  
								put("\tInput:  ").put(input1D[trial]). 
								put("\n\tTarget: ").put(output1D[trial]). 
								put("\n\tOutput: ").put(responses1D[trial]). 
								put("\n\tError: ").put(trialError).put((errorTypeOutput.squareErrors() ? " (sum of squared)" : "") + "\n\n");
					} catch (IOException e) {
						logger.info("Error writing to evaluation log file: " + Arrays.toString(e.getStackTrace()));
					}
				}
			}
			else {
				for (int y = 0; y < output2D[trial].length; y++) {
					for (int x = 0; x < output2D[trial][0].length; x++) {
						double diff = responses2D[trial][y][x] - output2D[trial][y][x];
						trialError += errorTypeOutput.squareErrors() ? diff * diff : Math.abs(diff);
					}
				}
				if (logOutput != null) {
					try {
						logOutput.put(trial).  
						put("\tInput:  ").put(input2D[trial]). 
						put("\n\tTarget: ").put(output2D[trial]). 
						put("\n\tOutput: ").put(responses2D[trial]). 
						put("\n\tError: ").put(trialError).put((errorTypeOutput.squareErrors() ? " (sum of squared)" : "") + "\n\n");
					} catch (IOException e) {
						logger.info("Error writing to evaluation log file: " + Arrays.toString(e.getStackTrace()));
					}
				}
			}
			if (errorTypeOutput.avgErrors()) trialError /= outputCount;
			if (errorTypeOutput.rootTotalError()) trialError = Math.sqrt(trialError);
			else if (errorTypeOutput.squareTotalError()) trialError = trialError * trialError;
				
			totalError += errorTypeTrial.squareErrors() ? trialError * trialError : trialError;
		}
		
		if (errorTypeTrial.avgErrors()) totalError /= trialCount;
		if (errorTypeTrial.rootTotalError()) totalError = Math.sqrt(totalError);
		else if (errorTypeTrial.squareTotalError()) totalError = totalError * totalError;
		
		Results results = new Results();
		results.proportionalFitness = maxFitnessValue - (int) Math.round((totalError / maxError) * maxFitnessValue);
		results.inverseFitness = (int) Math.round(maxFitnessValue / (1 + totalError));
		results.fitness = fitnessConversionType.equals("proportional") ? results.proportionalFitness : results.inverseFitness;
		return results;
	}
	
	public String getFitnessConversionType() {
		return fitnessConversionType;
	}
	
	/**
	 * Storage for the results of a fitness evaluation.
	 */
	public class Results {
		public int fitness;
		public int inverseFitness;
		public int proportionalFitness;
	}
	
	
	/**
	 * The type of error calculation to use.
	 * @see TargetFitnessCalculator#ERROR_TYPE_TRIAL_KEY
	 * @see TargetFitnessCalculator#ERROR_TYPE_OUTPUT_KEY
	 */
	public enum ErrorType {
		/**
		 * Sum of Absolute Errors, the sum of the absolute of the errors.
		 */
		SAE,
		/**
		 * Squared Sum of Absolute Errors, the squared sum of the absolute of the errors.
		 */
		SSAE,
		/**
		 * Sum of Squared Errors, the sum of the squared errors.
		 */
		SSE,
		/**
		 * Root of Sum of Squared Errors, the square root of the sum of the squared errors.
		 */
		RSSE,
		/**
		 * Mean of Absolute Errors, the average of the absolute errors.
		 */
		MAE,
		/**
		 * Squared Mean of Absolute Errors, the squared average of the absolute errors.
		 */
		SMAE,
		/**
		 * MSE: Mean of Squared Errors, the average of the squared errors.
		 */
		MSE,
		/**
		 * Root of Mean of Squared Errors, the square root of the average of the squared errors.
		 */
		RMSE;
		
		/**
		 * @return True iff the individual errors are to be squared.
		 */
		public boolean squareErrors() {
			return this == SSE || this == RSSE || this == MSE || this == RMSE;
		}
		
		/**
		 * @return True iff the individual errors are to be summed (possibly after being squared).
		 */
		public boolean sumErrors() {
			return this == SAE || this == SSAE || this == SSE || this == RSSE;
		}
		
		/**
		 * @return True iff the individual errors are to be averaged (possibly after being squared). This will always return the opposite of {@link #sumErrors()}.
		 */
		public boolean avgErrors() {
			return !sumErrors();
		}
		
		/**
		 * @return True iff the total error is to be square rooted.
		 */
		public boolean rootTotalError() {
			return this == RSSE || this == RMSE;
		}
		
		/**
		 * @return True iff the total error is to be squared.
		 */
		public boolean squareTotalError() {
			return this == SSAE || this == SMAE;
		}
	}
}
