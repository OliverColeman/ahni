package ojc.ahni.experiments;

import ojc.ahni.evaluation.HyperNEATTargetFitnessFunction;

import com.anji.util.Properties;

/**
 * Implements the Left and Right Retina Problem as described in J. Clune, B. E. Beckmann, P. McKinley, and C. Ofria
 * (2010) Investigating whether hyperneat produces modular neural networks. In GECCO '10: Proceedings of the Genetic and
 * Evolutionary Computation Conference, pages 635-642, ACM. with the difference that in the reported experimental set-up
 * input values range from -3 to 3 and target output values range from -1 to 1 but in this implementation input values
 * range from 0 to 3 and target output values from 0 to 1.
 * 
 * @author Oliver Coleman
 */
public class RetinaProblemFitnessFunction extends HyperNEATTargetFitnessFunction {
	private static final long serialVersionUID = 1L;

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

	public RetinaProblemFitnessFunction() {
	}

	public void init(Properties props) {
		super.init(props);

		int validPatternCount = validPatternsLeft.length;
		int retinaWidth = validPatternsLeft[0][0].length;
		int retinaHeight = validPatternsLeft[0].length;

		int inputWidth = width[0];
		int inputHeight = height[0];
		int outputWidth = width[depth - 1];
		int outputHeight = height[depth - 1];

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
					inputPatterns[t][y][x] = 3 * (p & 0x1);
				}
			}
			int prVal = (t >> 4) & 0x0F;
			int plVal = t & 0x0F;
			for (int i = 0; i < validPatternCount; i++) {
				if (validPatternsLeftVal[i] == plVal)
					targetOutputPatterns[t][0][0] = 1;
				if (validPatternsRightVal[i] == prVal)
					targetOutputPatterns[t][0][1] = 1;
			}
		}

		setPatterns(inputPatterns, targetOutputPatterns, 0, 1);
	}
}
