package com.ojcoleman.ahni.experiments;

import org.apache.log4j.Logger;


import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.TranscriberException;
import com.ojcoleman.ahni.evaluation.HyperNEATTargetFitnessFunction;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.transcriber.HyperNEATTranscriber;

/**
 * A test fitness function that determines fitness based on how close the output of a network is to a target output
 * given some input. The specific test set can be specified with the property key "fitness.function.test.type". Valid
 * values are:
 * <ul>
 * <li>pass-through: the output should match the input.</li>
 * <li>pass-through-flip: same as pass-through except the target output is a mirror of the input.</li>
 * <li>rotate90: the target output is the input rotated 90 degrees.</li>
 * <li>parity: bitwise parity over all inputs.</li>
 * <li>[More to come!]</li>
 * </ul>
 * The number of trials is determined by the width and height of the input layer as 2 ^ (width + height), thus every
 * possible input pattern is tested. See {@link HyperNEATTargetFitnessFunction} for other available parameters that may
 * be specified via the properties file.
 * 
 * @author Oliver Coleman
 */
public class TestTargetFitnessFunction extends HyperNEATTargetFitnessFunction {
	private static final long serialVersionUID = 1L;
	public static final String TEST_TYPE_KEY = "fitness.function.test.type";

	private static Logger logger = Logger.getLogger(TestTargetFitnessFunction.class);

	private int numTrials;
	String testType;
	
	public TestTargetFitnessFunction() {
	}

	public void init(Properties props) {
		super.init(props);

		testType = props.getProperty(TEST_TYPE_KEY);
		
		if (testType.equals("pass-through") || testType.equals("pass-through-flip") || testType.equals("rotate90")) {
			if (inputWidth != outputWidth || inputHeight != outputHeight) {
				throw new IllegalArgumentException("HyperNEAT substrate input and output dimensions must be the same for TestTargetFitnessFunction.");
			}
		}
		if (testType.equals("rotate90") && inputWidth != inputHeight) {
			throw new IllegalArgumentException("HyperNEAT substrate input (and output) width and height must be the same for the rotate90 test in TestTargetFitnessFunction.");
		} else if (testType.equals("parity") && (outputWidth != 1 || outputHeight != 1)) {
			throw new IllegalArgumentException("HyperNEAT substrate output width and height must be 1 for the parity test in TestTargetFitnessFunction.");
		}

		numTrials = 1 << (inputWidth * inputHeight);
		logger.info("Target fitness function generating " + numTrials + " trials (if this number seems too large use an input layer with smaller dimensions).");
		generatePatterns();
	}

	@Override
	protected void scale(int scaleCount, int scaleFactor, HyperNEATTranscriber transcriber) {
		int[] width = transcriber.getWidth();
		int[] height = transcriber.getHeight();
		int connectionRange = transcriber.getConnectionRange();
		
		for (int l = 0; l < width.length; l++) {
			width[l] *= scaleFactor;
			height[l] *= scaleFactor;
		}
		if (connectionRange != -1) {
			connectionRange *= scaleFactor;
		}
		
		transcriber.resize(width, height, connectionRange);
		
		generatePatterns();
	}

	private void generatePatterns() {
		HyperNEATTranscriber transcriber = (HyperNEATTranscriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
		int[] width = transcriber.getWidth();
		int[] height = transcriber.getHeight();
		int depth = transcriber.getDepth();
		
		double[][][] inputPatterns = new double[numTrials][height[0]][width[0]];
		double[][][] targetOutputPatterns = new double[numTrials][height[depth - 1]][width[depth - 1]];

		for (int t = 0; t < numTrials; t++) {
			int p = t;
			for (int y = 0; y < height[0]; y++) {
				for (int x = 0; x < width[0]; x++, p >>= 1) {
					inputPatterns[t][y][x] = p & 0x1;

					if (testType.equals("pass-through")) {
						targetOutputPatterns[t][y][x] = inputPatterns[t][y][x];
					}
					else if (testType.equals("pass-through-flip")) {
						targetOutputPatterns[t][y][(width[0] - 1) - x] = inputPatterns[t][y][x];
					}
					else if (testType.equals("rotate90")) {
						targetOutputPatterns[t][x][(height[0] - 1) - y] = inputPatterns[t][y][x];
					}
				}
			}
			if (testType.equals("parity")) {
				int parity = t & 0x1;
				for (int i = 1; i < 31; i++) {
					parity ^= (t >> i) & 0x1;
				}
				targetOutputPatterns[t][0][0] = parity;
			}
		}

		setPatterns(inputPatterns, targetOutputPatterns, 0, 1);
	}
}
