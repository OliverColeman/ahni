package ojc.ahni.experiments;

import ojc.ahni.hyperneat.HyperNEATTargetFitnessFunction;

import com.anji.util.Properties;


/**
 * A test fitness function that determines fitness based on how close the output of a network is to a target output given some input.
 * The specific test set can be specified with the property key "fitness.function.test.type". Valid values are:<ul>
 * <li>pass-through: the output should match the input, new random input patterns are generated for every generation.</li>
 * <li>pass-through-flip: same as pass-through except the target output is a mirror of the input.</li>
 * <li>rotate90: the target output is the input rotated 90 degrees.</li>
 * <li>[More to come!]</li>
 * </ul>
 * The number of trials can be specified with the property key "fitness.function.test.numtrials".
 * See {@link HyperNEATTargetFitnessFunction} for other available parameters that may be specified via the properties file.
 *
 * @author Oliver Coleman
 */
public class TestTargetFitnessFunction extends HyperNEATTargetFitnessFunction {
	public static final String TEST_TYPE_KEY = "fitness.function.test.type";
	public static final String NUM_TRIALS_KEY = "fitness.function.test.numtrials";
	
    private int numTrials;
    String testType;
    
    public TestTargetFitnessFunction(){}
    
    public void init(Properties props) {
    	super.init(props);
    	
    	int inputWidth = width[0];
    	int inputHeight = height[0];
    	int outputWidth = width[depth-1];
    	int outputHeight = height[depth-1];
    	
    	if (inputWidth != outputWidth || inputHeight != outputHeight) {
    		throw new IllegalArgumentException("HyperNEAT substrate input and output dimensions must be the same for TestTargetFitnessFunction.");
    	}
    	if (testType == "rotate90" && inputWidth != inputHeight) {
    		throw new IllegalArgumentException("HyperNEAT substrate input (and output) width and height must be the same for the rotate90 test in TestTargetFitnessFunction.");
    	}
    	
        numTrials = props.getIntProperty(NUM_TRIALS_KEY);
        testType = props.getProperty(TEST_TYPE_KEY);
        createInputAndOutputArrays();
    }
    
    public void initialiseEvaluation() {
    	int outputWidth = width[depth-1];
    	int outputHeight = height[depth-1];
    	
    	switch (testType) {
    	case "pass-through":
	        for (int t = 0; t < numTrials; t++) {
	        	for (int y = 0; y < outputHeight; y++) {
	                for (int x = 0; x < outputWidth; x++) {
	                    inputPatterns[t][y][x] = random.nextInt(2);
	                }
	            }
	        }
	        break;
	        
    	case "pass-through-flip":
	        for (int t = 0; t < numTrials; t++) {
	        	for (int y = 0; y < outputHeight; y++) {
	                for (int x = 0; x < outputWidth; x++) {
	                	double v = random.nextInt(2);
	                    inputPatterns[t][y][x] = v;
	                    targetOutputPatterns[t][y][(outputWidth-1)-x] = v;
	                }
	            }
	        }
	        break;
	        
    		case "rotate90":
		        for (int t = 0; t < numTrials; t++) {
		        	for (int y = 0; y < outputHeight; y++) {
		                for (int x = 0; x < outputWidth; x++) {
		                	double v = random.nextInt(2);
		                    inputPatterns[t][y][x] = v;
		                    targetOutputPatterns[t][x][(outputHeight-1) - y] = v;
		                }
		            }
		        }
		        break;
    	}
	}
    
    protected void scale(int scaleCount, int scaleFactor) {
    	for (int l = 0; l < width.length; l++) {
			width[l] *= scaleFactor;
			height[l] *= scaleFactor;
		}
		connectionRange *= scaleFactor;
		createInputAndOutputArrays();
    }
    
    private void createInputAndOutputArrays() {
    	inputPatterns = new double[numTrials][height[0]][width[0]];
    	targetOutputPatterns = testType == "pass-through" ? inputPatterns : new double[numTrials][height[depth-1]][width[depth-1]];
    }
}
