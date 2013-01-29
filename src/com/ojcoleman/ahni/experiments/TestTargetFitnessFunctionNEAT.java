package com.ojcoleman.ahni.experiments;


import com.anji.neat.NeatConfiguration;
import com.ojcoleman.ahni.evaluation.TargetFitnessFunctionMT;
import com.ojcoleman.ahni.hyperneat.Properties;

/**
 * A test fitness function that determines fitness based on how close the output of a network is to a target output
 * given some input. The specific test set can be specified with the property key "fitness.function.test.type". Valid
 * values are:
 * <ul>
 * <li>pass-through: the output should match the input, new random input patterns are generated for every generation.</li>
 * <li>parity: bitwise parity over all inputs.</li>
 * <li>[More to come!]</li>
 * </ul>
 * The number of trials is determined by the number of inputs as 2 ^ (inputCount), thus every
 * possible input pattern is tested. See
 * {@link TargetFitnessFunctionMT} for other available parameters that may be specified via the properties file.
 * 
 * @author Oliver Coleman
 */
public class TestTargetFitnessFunctionNEAT extends TargetFitnessFunctionMT {
	private static final long serialVersionUID = 1L;

	public static final String TEST_TYPE_KEY = "fitness.function.test.type";

	private int numTrials;
	String testType;

	private int inputSize, outputSize;
	
	private double[][] inputPatterns, targetOutputPatterns;

	public TestTargetFitnessFunctionNEAT() {
	}

	public void init(Properties props) {
		super.init(props);

		testType = props.getProperty(TEST_TYPE_KEY);
		inputSize = props.getShortProperty(NeatConfiguration.STIMULUS_SIZE_KEY, NeatConfiguration.DEFAULT_STIMULUS_SIZE);
		outputSize = props.getShortProperty(NeatConfiguration.RESPONSE_SIZE_KEY, NeatConfiguration.DEFAULT_RESPONSE_SIZE);

		if (testType.equals("pass-through") && inputSize != outputSize) {
			throw new IllegalArgumentException("Network input and output dimensions must be the same for pass-through test in TestTargetFitnessFunctionNEAT.");
		}
		if (testType.equals("parity") && outputSize != 1) {
			throw new IllegalArgumentException("Network output size must be 1 for the pass-through test in TestTargetFitnessFunctionNEAT.");
		}
		
		numTrials = 1 << inputSize;
		
		generatePatterns();
		inputPatterns = new double[numTrials][inputSize];
		targetOutputPatterns = new double[numTrials][outputSize];
	}
	
	private void generatePatterns() {
		inputPatterns = new double[numTrials][inputSize];
		targetOutputPatterns = new double[numTrials][outputSize];
		
		for (int t = 0; t < numTrials; t++) {
			int p = t;
			for (int i = 0; i < inputSize; i++, p >>= 1) {
				inputPatterns[t][i] = p & 0x1;

				if (testType.equals("pass-through")) {
					targetOutputPatterns[t][i] = inputPatterns[t][i];
				}
			}
			if (testType.equals("parity")) {
				int parity = t & 0x1;
				for (int i = 1; i < 31; i++) {
					parity ^= (t >> i) & 0x1;
				}
				targetOutputPatterns[t][0] = parity;
			}
		}

		setPatterns(inputPatterns, targetOutputPatterns, 0, 1);
	}
}
