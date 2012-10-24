package ojc.ahni.experiments;

import ojc.ahni.hyperneat.HyperNEATTargetFitnessFunction;
import ojc.ahni.integration.TargetFitnessFunctionMT;

import com.anji.neat.NeatConfiguration;
import com.anji.util.Properties;


/**
 * A test fitness function that determines fitness based on how close the output of a network is to a target output given some input.
 * The specific test set can be specified with the property key "fitness.function.test.type". Valid values are:<ul>
 * <li>pass-through: the output should match the input, new random input patterns are generated for every generation.</li>
 * <li>[More to come!]</li>
 * </ul>
 * The number of trials can be specified with the property key "fitness.function.test.numtrials".
 * See {@link TargetFitnessFunctionMT} for other available parameters that may be specified via the properties file.
 *
 * @author Oliver Coleman
 */
public class TestTargetFitnessFunctionNEAT extends TargetFitnessFunctionMT {
	public static final String TEST_TYPE_KEY = "fitness.function.test.type";
	public static final String NUM_TRIALS_KEY = "fitness.function.test.numtrials";
	
    private int numTrials;
    String testType;
    
    private int inputSize, outputSize;
    
    public TestTargetFitnessFunctionNEAT(){}
    
    public void init(Properties props) {
    	super.init(props);

        numTrials = props.getIntProperty(NUM_TRIALS_KEY);
        testType = props.getProperty(TEST_TYPE_KEY);

    	inputSize = props.getShortProperty(NeatConfiguration.STIMULUS_SIZE_KEY, NeatConfiguration.DEFAULT_STIMULUS_SIZE);
    	outputSize = props.getShortProperty(NeatConfiguration.RESPONSE_SIZE_KEY, NeatConfiguration.DEFAULT_RESPONSE_SIZE);
    	
    	if (testType.equals("pass-through") && inputSize != outputSize) {
    		throw new IllegalArgumentException("Network input and output dimensions must be the same for pass-through test in TestTargetFitnessFunctionNEAT.");
    	}
    	
    	inputPatterns = new double[numTrials][inputSize];
    	targetOutputPatterns = new double[numTrials][outputSize];
    }
    
    public void initialiseEvaluation() {
    	switch (testType) {
    	case "pass-through":
	        for (int t = 0; t < numTrials; t++) {
	        	for (int i = 0; i < inputSize; i++) {
                    inputPatterns[t][i] = random.nextInt(2);
                    targetOutputPatterns[t][i] = inputPatterns[t][i];
	            }
	        }
	        break;
    	}
	}
}
