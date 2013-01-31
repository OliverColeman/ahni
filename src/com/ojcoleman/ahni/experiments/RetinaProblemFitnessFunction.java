package com.ojcoleman.ahni.experiments;

import com.ojcoleman.ahni.evaluation.HyperNEATTargetFitnessFunction;
import com.ojcoleman.ahni.hyperneat.Properties;



/**
 * Implements the Left and Right Retina Problem as described in Constraining Connectivity to Encourage Modularity in
 * HyperNEAT (2011) In: Proceedings of the Genetic and Evolutionary Computation Conference (GECCO 2011).
 * 
 * @author Oliver Coleman
 */
public class RetinaProblemFitnessFunction extends HyperNEATTargetFitnessFunction {
	private static final long serialVersionUID = 1L;

	public static final String INPUT_MIN_VALUE_KEY = "fitness.function.retinaproblem.input.min";
	public static final String INPUT_MAX_VALUE_KEY = "fitness.function.retinaproblem.input.max";
	public static final String OUTPUT_MIN_VALUE_KEY = "fitness.function.retinaproblem.output.min";
	public static final String OUTPUT_MAX_VALUE_KEY = "fitness.function.retinaproblem.output.max";

	private static double[][][] validPatternsLeft = new double[][][] { { { 0, 1 }, { 1, 1 } }, // 7
			{ { 1, 0 }, { 1, 1 } }, // 11
			{ { 0, 0 }, { 1, 1 } }, // 3
			{ { 0, 0 }, { 0, 0 } }, // 0
			{ { 0, 1 }, { 0, 0 } }, // 4
			{ { 1, 0 }, { 0, 0 } }, // 8
			{ { 0, 0 }, { 0, 1 } }, // 1
			{ { 0, 0 }, { 1, 0 } } };// 2
	// Must be same dimensions as validPatternsLeft.
	private static double[][][] validPatternsRight = new double[][][] { { { 0, 1 }, { 0, 0 } }, // 4
			{ { 1, 0 }, { 0, 0 } }, // 8
			{ { 0, 0 }, { 0, 1 } }, // 1
			{ { 0, 0 }, { 1, 0 } }, // 2
			{ { 1, 1 }, { 0, 1 } }, // 13
			{ { 1, 1 }, { 1, 0 } }, // 14
			{ { 1, 1 }, { 0, 0 } }, // 12
			{ { 0, 0 }, { 0, 0 } } };// 0

	private double inputMinValue = -3, inputMaxValue = 3;
	private double outputMinValue = -1, outputMaxValue = 1;

	public RetinaProblemFitnessFunction() {
	}

	public void init(Properties props) {
		super.init(props);

		int validPatternCount = validPatternsLeft.length;
		int retinaWidth = validPatternsLeft[0][0].length;
		int retinaHeight = validPatternsLeft[0].length;

		if (inputWidth != retinaWidth * 2 || inputHeight != retinaHeight) {
			throw new IllegalArgumentException("HyperNEAT substrate input layer width and height must be 4 and 2 respectively for the Retina Problem.");
		}
		if (outputWidth != 2 || outputHeight != 1) {
			throw new IllegalArgumentException("HyperNEAT substrate output layer width and height must be 2 and 1 respectively for the Retina Problem.");
		}

		int trialCount = 1 << (inputWidth * inputHeight);

		double[][][] inputPatterns = new double[trialCount][inputHeight][inputWidth];
		double[][][] targetOutputPatterns = new double[trialCount][outputHeight][outputWidth];
		

		int[] validPatternsLeftVal = new int[validPatternCount];
		int[] validPatternsRightVal = new int[validPatternCount];
		for (int i = 0; i < validPatternCount; i++) {
			int valLeft = 0;
			int valRight = 0;
			for (int y = 0; y < retinaHeight; y++) {
				for (int x = 0; x < retinaWidth; x++) {
					valLeft <<= 1;
					valRight <<= 1;
					valLeft |= (int) validPatternsLeft[i][y][x];
					valRight |= (int) validPatternsRight[i][y][x];
				}
			}
			validPatternsLeftVal[i] = valLeft;
			validPatternsRightVal[i] = valRight;
		}

		for (int t = 0; t < trialCount; t++) {
			int p = t;
			for (int y = 0; y < inputHeight; y++) {
				for (int x = 0; x < inputWidth; x++, p >>= 1) {
					inputPatterns[t][y][x] = (p & 0x1) == 1 ? inputMaxValue : inputMinValue;
				}
			}
			
			// Fill target outputs with default value.
			for (int y = 0; y < outputHeight; y++) {
				for (int x = 0; x < outputWidth; x++) {
					targetOutputPatterns[t][y][x] = outputMinValue;
				}
			}
			
			// If this trial corresponds to a valid pattern on the left or right then set the outputs accordingly.
			int prVal = (t >> 4) & 0x0F;
			int plVal = t & 0x0F;
			for (int i = 0; i < validPatternCount; i++) {
				if (validPatternsLeftVal[i] == plVal)
					targetOutputPatterns[t][0][0] = outputMaxValue;
				if (validPatternsRightVal[i] == prVal)
					targetOutputPatterns[t][0][1] = outputMaxValue;
			}
		}

		setPatterns(inputPatterns, targetOutputPatterns, outputMinValue, outputMaxValue);
	}
}
