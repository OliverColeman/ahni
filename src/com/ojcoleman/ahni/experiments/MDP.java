package com.ojcoleman.ahni.experiments;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;
import com.ojcoleman.ahni.evaluation.novelty.RealVectorBehaviour;
import com.ojcoleman.ahni.event.AHNIEvent;
import com.ojcoleman.ahni.event.AHNIEventListener;
import com.ojcoleman.ahni.experiments.TSPSuperimposedWaves.EvalType;
import com.ojcoleman.ahni.experiments.mr2d.EnvironmentDescription;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.DoubleVector;
import com.ojcoleman.ahni.util.NiceWriter;
import com.ojcoleman.ahni.util.Point;
import com.ojcoleman.ahni.util.Range;

/**
 * A fitness function that generates MDP environments to assess agents against.
 */
public class MDP extends BulkFitnessFunctionMT implements AHNIEventListener {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(BulkFitnessFunctionMT.class);
	private static final NumberFormat nf = new DecimalFormat("0.00");
	
	/**
	 * The task, either 'act' or 'predict'. Default is 'act'.
	 */
	public static final String EVAL_TYPE = "fitness.function.mdp.task";
	
	/**
	 * The initial number of states in the generated environments.
	 */
	public static final String STATE_COUNT_INITIAL = "fitness.function.mdp.states.initial";
	/**
	 * The amount to increase the number of states in the generated environments when the current size has been
	 * sufficiently mastered (see {@link #DIFFICULTY_INCREASE_PERFORMANCE}. If the value is followed by an "x" then the
	 * value is considered a factor (and so should be > 1).
	 */
	public static final String STATE_COUNT_INCREASE_DELTA = "fitness.function.mdp.states.delta";
	/**
	 * The maximum amount to increase the number of states in the generated environments to.
	 */
	public static final String STATE_COUNT_MAX = "fitness.function.mdp.states.maximum";
	/**
	 * The initial number of actions available in the generated environments.
	 */
	public static final String ACTION_COUNT_INITIAL = "fitness.function.mdp.actions.initial";
	/**
	 * The amount to increase the available number of actions in the generated environments when the current size has
	 * been sufficiently mastered (see {@link #DIFFICULTY_INCREASE_PERFORMANCE}. If the value is followed by an "x" then
	 * the value is considered a factor (and so should be > 1).
	 */
	public static final String ACTION_COUNT_INCREASE_DELTA = "fitness.function.mdp.actions.delta";
	/**
	 * The maximum amount to increase the available number of actions in the generated environments to.
	 */
	public static final String ACTION_COUNT_MAX = "fitness.function.mdp.actions.maximum";
	/**
	 * The performance indicating when the environment size/difficulty/count should be increased as the current value has been
	 * sufficiently mastered.
	 */
	public static final String DIFFICULTY_INCREASE_PERFORMANCE = "fitness.function.mdp.difficulty.increase.performance";
	/**
	 * The proportion of actions that will map to some other state. This is evaluated probabilistically for all states
	 * and actions when generating an environment.
	 */
	public static final String ACTION_MAP_RATIO = "fitness.function.mdp.action.map.ratio";
	/**
	 * The proportion of state transitions that will yield a reward value greater than 0.
	 */
	public static final String TRANSITION_REWARD_RATIO = "fitness.function.mdp.transition.reward.ratio";
	/**
	 * The randomness of state transitions. A value of 0 will make state transitions deterministic (purely determined by
	 * the action performed), a value of 1 will make transitions completely envRandom (action performed will be
	 * ignored).
	 */
	public static final String TRANSITION_RANDOMNESS = "fitness.function.mdp.transition.randomness";
	/**
	 * The number of environments to evaluate candidates against. Increasing this will provide a more accurate
	 * evaluation but take longer.
	 */
	public static final String ENVIRONMENT_COUNT = "fitness.function.mdp.environment.count";
	/**
	 * The fraction of environments that should be replaced with new environments per generation. This is evaluated
	 * probabilistically.
	 */
	public static final String ENVIRONMENT_CHANGE_RATE = "fitness.function.mdp.environment.replacerate";
	/**
	 * Factor to increase the total number of environments by when the current environment(s) have been 
	 * sufficiently mastered (see {@link #DIFFICULTY_INCREASE_PERFORMANCE}. May be fractional; if the factor is 
	 * greater than 1 then the number of environments will be increased by at least 1. Default is 1 (no change).
	 */
	public static final String ENVIRONMENT_INCREASE_RATE = "fitness.function.mdp.environment.count.increase";
	/**
	 * If {@link #DIFFICULTY_INCREASE_PERFORMANCE} is greater than 1, then this is the maximum number of 
	 * environments to increase to. Default is 128.
	 */
	public static final String ENVIRONMENT_COUNT_MAX = "fitness.function.mdp.environment.count.max";
	
	/**
	 * The number of trials per environment. If not set or set to <= 0 then this will be set to the grid size if
	 * fitness.function.mdp.grid is true and fitness.function.mdp.single_reward_state is true, otherwise to
	 * fitness.function.mdp.environment.count if fitness.function.mdp.environment.replacerate is 0, otherwise to
	 * fitness.function.mdp.states.maximum * fitness.function.mdp.actions.maximum .
	 */
	public static final String TRIAL_COUNT = "fitness.function.mdp.trial.count";
	/**
	 * The number of steps in the environment per trial. If not set or set to <= 0 then this will be set depending on
	 * the number of trials. If the number of trials is 1 then it will be set to (actionCount * stateCount * stateCount)
	 * / trialCount, if the number of trials > 1 then it will be set to stateCount.
	 */
	public static final String STEPS_PER_TRIAL = "fitness.function.mdp.trial.steps";

	/**
	 * If true enables novelty search for which behaviours are defined by the current state for each step of each trial
	 * of each environment. Default is false.
	 */
	public static final String NOVELTY_SEARCH = "fitness.function.mdp.noveltysearch";
	/**
	 * If set to an integer > 0 then this many environments will be used to characterise an agents behaviour for novelty
	 * search. Defaults to fitness.function.mdp.environment.count.
	 */
	public static final String NOVELTY_SEARCH_ENV_COUNT = "fitness.function.mdp.noveltysearch.envs.count";
	/**
	 * If true then makes novelty search the only objective.  Performance values are still calculated. 
	 * Default is false. If true then fitness.function.mdp.noveltysearch is also forced to true.
	 */
	public static final String NOVELTY_SEARCH_ONLY = "fitness.function.mdp.noveltysearch.only";

	/**
	 * Seed to use to generate and simulate environments.
	 */
	public static final String ENV_RANDOM_SEED = "fitness.function.mdp.environment.randomseed";

	/**
	 * Whether to include the previously performed action in the input to the agent. Default is false.
	 */
	public static final String INPUT_INCLUDE_PREVIOUS_ACTION = "fitness.function.mdp.input.include.previous.action";
	/**
	 * Whether to include the previous state in the input to the agent. Default is false.
	 */
	public static final String INPUT_INCLUDE_PREVIOUS_STATE = "fitness.function.mdp.input.include.previous.state";
	/**
	 * Whether to include an input that indicates whether the agent should be exploring to learn about the environment
	 * or exploiting the knowledge its learnt. Default is false.
	 */
	public static final String INPUT_INCLUDE_EXPL = "fitness.function.mdp.input.include.expl";

	/**
	 * Whether to generate environments where the states are organised in a grid. The number of states is forced to
	 * (ceil(sqrt(state_count)))^2. The number of actions is forced to 4 and each action will move the agent to a
	 * neighbouring state. The input to the agent is the row and column index of the current state (1-of-N encoding for
	 * each). The environments are forced to be deterministic (no transition randomness). The grid wraps around (is a
	 * toroidal grid). Default is false.
	 */
	public static final String GRID_ENVIRONMENT = "fitness.function.mdp.grid";

	/**
	 * Whether to make the grid toroidal (wrap around at edges). Default is false.
	 */
	public static final String GRID_ENVIRONMENT_WRAP = "fitness.function.mdp.grid.wrap";

	/**
	 * Whether there should be a single state which when reached yields a reward value of 1 (rather than any transition
	 * to any state yielding a randomly generated reward value according to
	 * fitness.function.mdp.transition.reward.ratio.
	 */
	public static final String SINGLE_REWARD_STATE = "fitness.function.mdp.single_reward_state";

	public enum EvalType { ACT, PREDICT }
	
	private EvalType evalType;
	private int environmentCount;
	private double environmentReplaceProb;
	private double environmentIncreaseRate;
	private int environmentCountMax;
	int trialCount;
	private int stateCount;
	private int actionCount;
	int stateCountMax;
	private int actionCountMax;
	private double transitionRandomness;
	private double mapRatio;
	private double transitionRewardRatio;
	private int stepsForEval;
	ArrayList<Environment> environments;
	ArrayList<Environment> nsEnvironments;
	private ArrayList<Environment> genEnvironments;
	private int environmentCounter = 0;
	//private int environmentMasteredTrialCount = 3;
	private int stepsPerTrial;
	private boolean noveltySearchEnabled = false;
	private boolean noveltySearchOnly = false;
	private int noveltySearchEnvCount;
	private long envRandomSeed;
	private Random envRandom;
	private boolean includePrevAction;
	private boolean includePrevState;
	private boolean includeExpl;
	private boolean gridEnvs, gridWrap;
	private int gridSize, gridSizeMax; // Only if gridEnvs == true;
	private boolean singleRewardState;

	// Indexes into agent input array.
	private int currentStateIndex = 0;
	private int previousStateIndex = -1;
	private int previousActionIndex = -1;
	private int currentActionIndex = -1;
	private int explIndex = -1;
	private int rewardIndex = -1;
	
	// Flag indicating if getNeuronPositions has been called 
	// (which we use to calculate the above indexes).
	private boolean getNeuronPositionsCalled = false;
	
	@Override
	public void init(Properties props) {
		this.props = props;
		
		evalType = EvalType.valueOf(props.getProperty(EVAL_TYPE, "act").toUpperCase());
		
		noveltySearchOnly = props.getBooleanProperty(NOVELTY_SEARCH_ONLY, false);
		
		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		environmentReplaceProb = props.getDoubleProperty(ENVIRONMENT_CHANGE_RATE, 0);
		environmentIncreaseRate = noveltySearchOnly ? 0 : props.getDoubleProperty(ENVIRONMENT_INCREASE_RATE, 1);
		environmentCountMax = environmentIncreaseRate > 1 ? props.getIntProperty(ENVIRONMENT_COUNT_MAX) : environmentCount;
		
		if (environmentReplaceProb > 1 && environmentIncreaseRate > 1) {
			throw new IllegalArgumentException(ENVIRONMENT_CHANGE_RATE + " and " + ENVIRONMENT_INCREASE_RATE + " are mutually exclusive.");
		}
		if (noveltySearchOnly && environmentReplaceProb > 1) {
			throw new IllegalArgumentException(ENVIRONMENT_CHANGE_RATE + " and " + NOVELTY_SEARCH_ONLY + " are mutually exclusive.");
		}
		if (noveltySearchOnly && environmentIncreaseRate > 1) {
			throw new IllegalArgumentException(ENVIRONMENT_INCREASE_RATE + " and " + NOVELTY_SEARCH_ONLY + " are mutually exclusive.");
		}
		
		actionCount = props.getIntProperty(ACTION_COUNT_INITIAL);
		stateCount = props.getIntProperty(STATE_COUNT_INITIAL);
		actionCountMax = props.getIntProperty(ACTION_COUNT_MAX);
		stateCountMax = props.getIntProperty(STATE_COUNT_MAX);
		trialCount = props.getIntProperty(TRIAL_COUNT, 0);
		
		// Action and State count delta and increasing number of environments are mutually exclusive.
		if (environmentIncreaseRate > 1) {
			for (String key : new String[]{ACTION_COUNT_INCREASE_DELTA, STATE_COUNT_INCREASE_DELTA}) {
				String deltaString = props.getProperty(key, "0").trim().toLowerCase();
				boolean isFactor = deltaString.endsWith("x");
				double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
				if (delta >= 1) {
					if (!isFactor || delta > 1) {
						throw new IllegalArgumentException(key + " and " + ENVIRONMENT_INCREASE_RATE + " are mutually exclusive.");
					}
				}
			}
		}

		singleRewardState = props.getBooleanProperty(SINGLE_REWARD_STATE, false);
		gridEnvs = props.getBooleanProperty(GRID_ENVIRONMENT, false);
		gridWrap = props.getBooleanProperty(GRID_ENVIRONMENT_WRAP, false);
		adustStateCountForEnvType();

		transitionRandomness = props.getDoubleProperty(TRANSITION_RANDOMNESS);
		Range.checkUnitRange(transitionRandomness, TRANSITION_RANDOMNESS);
		mapRatio = props.getDoubleProperty(ACTION_MAP_RATIO);
		Range.checkUnitRange(mapRatio, ACTION_MAP_RATIO, false, true);
		transitionRewardRatio = props.getDoubleProperty(TRANSITION_REWARD_RATIO);
		Range.checkUnitRange(transitionRewardRatio, TRANSITION_REWARD_RATIO, false, true);

		determineStepsPerTrial();
		
		envRandomSeed = props.getLongProperty(ENV_RANDOM_SEED, System.currentTimeMillis());
		logger.info("MDP random seed is " + envRandomSeed);
		envRandom = new Random(envRandomSeed);

		includePrevAction = props.getBooleanProperty(INPUT_INCLUDE_PREVIOUS_ACTION, false);
		includePrevState = props.getBooleanProperty(INPUT_INCLUDE_PREVIOUS_STATE, false);
		includeExpl = props.getBooleanProperty(INPUT_INCLUDE_EXPL, false);

		if (gridEnvs) {
			if (actionCount != 4 || actionCountMax != 4) {
				actionCount = 4;
				actionCountMax = 4;
				logger.warn("MDP: forcing initial and maximum action counts to 4 for grid environment.");
			}
			if (mapRatio != 1) {
				mapRatio = 1;
				logger.warn("MDP: forcing fitness.function.mdp.action.map.ratio to 1 for grid environment.");
			}
		}

		environments = new ArrayList<Environment>();
		for (int e = 0; e < environmentCountMax; e++) {
			Environment newEnv = new Environment();
			if (newEnv.setUp(environmentCounter, environments)) {
				environments.add(newEnv);
				environmentCounter++;
			}
		}
		if (environments.size() != environmentCountMax) {
			environmentCountMax = environments.size();
			if (environmentCount > environmentCountMax) {
				environmentCount = environmentCountMax;
			}
			logger.warn("Could not generate specified number of MDP environments, (maximum) number of environments set to " + environmentCountMax);
		}

		noveltySearchEnabled = noveltySearchOnly || props.getBooleanProperty(NOVELTY_SEARCH, false);
		if (noveltySearchEnabled) {
			noveltySearchEnvCount = props.getIntProperty(NOVELTY_SEARCH_ENV_COUNT, 0);
			
			// If the same environments aren't used throughout evolution.
			if (environmentReplaceProb > 0 || noveltySearchEnvCount > environmentCountMax) {
				if (noveltySearchEnvCount <= 0)
					noveltySearchEnvCount = environmentCount;
				// Create a set of environments that don't change over the course of evolution to test novelty on.
				nsEnvironments = new ArrayList<Environment>();
				for (int e = 0; e < noveltySearchEnvCount; e++) {
					Environment newEnv = new Environment();
					// If possible, generate environments of varying difficulty.
					// for (int i = 1; i < e && nsEnvironments[e].increaseDifficultyPossible(); i++) {
					// nsEnvironments[e].increaseDifficulty();
					// }
					if (newEnv.setUp(nsEnvironments.size(), nsEnvironments)) {
						nsEnvironments.add(newEnv);
					}
				}
				if (nsEnvironments.size() != noveltySearchEnvCount) {
					noveltySearchEnvCount = nsEnvironments.size();
					logger.warn("Could not generate specified number of MDP environments for novelty search, number of environments for novelty search set to " + noveltySearchEnvCount);
				}
				logger.info("Created " + noveltySearchEnvCount + " environments for novelty search.");
			}
			
			if (noveltySearchEnvCount == 0) noveltySearchEnvCount = environments.size();
			
			logger.info("Novelty search behaviours have dimensionality " + (noveltySearchEnvCount * trialCount * stepsPerTrial));
		}
		
		if (trialCount <= 0) {
			if (gridEnvs && singleRewardState)
				trialCount = gridSize;
			else if (environmentReplaceProb == 0)
				trialCount = environmentCount;
			else
				trialCount = actionCountMax * stateCountMax;
			logger.info("MDP trial count set to " + trialCount);
		}

		((Properties) props).getEvolver().addEventListener(this);

		super.init(props);
	}

	private void determineStepsPerTrial() {
		stepsPerTrial = props.getIntProperty(STEPS_PER_TRIAL, 0);
		if (stepsPerTrial <= 0) {
			if (gridEnvs && singleRewardState) {
				// Enough steps to reach any state in the grid (Manhattan distance).
				stepsPerTrial = 2 * gridSizeMax - 1;
			} else if (!gridEnvs && singleRewardState) {
				// Enough steps to reach any state.
				stepsPerTrial = stateCountMax - 1;
			} else if (environmentReplaceProb > 0) {
				if (trialCount == 1) {
					// Enough steps to try every action in every state at least once then exploit the info gained.
					// The last term should be same as stepsForEval below for single trial.
					stepsPerTrial = actionCount * stateCount * stateCount + stateCount * 2;
				} else {
					// Enough steps to try every action in every state at least once then exploit the info gained.
					stepsPerTrial = (actionCount * stateCount * stateCount) / (trialCount - 1);
				}
			} else {
				// Somewhat arbitrary, probably always want it to be >= stateCount
				stepsPerTrial = stateCount*actionCount*3;
			}
			logger.info("MDP steps per trial set to " + stepsPerTrial);
		}
		
		// If trialCount > 1 then the reward received during the entire last trial will be used.
		stepsForEval = trialCount == 1 ? stateCount * 2 : stepsPerTrial;
		logger.info("MDP number of final steps contributing to fitness set to " + stepsForEval);
	}

	/**
	 * Make sure stateCount meets requirements of environment type (eg grid environments).
	 */
	private void adustStateCountForEnvType() {
		if (gridEnvs) {
			int initStateCount = stateCount;
			gridSize = (int) Math.ceil(Math.sqrt(stateCount));
			stateCount = gridSize * gridSize;
			if (stateCount != initStateCount) {
				logger.warn("MDP: forcing state count to " + stateCount + " for grid environment.");
			}

			initStateCount = stateCountMax;
			gridSizeMax = (int) Math.ceil(Math.sqrt(stateCountMax));
			stateCountMax = gridSizeMax * gridSizeMax;
			if (stateCountMax != initStateCount) {
				logger.warn("MDP: forcing maximum state count to " + stateCountMax + " for grid environment.");
			}
		}
	}

	@Override
	public void initialiseEvaluation() {
		// Create (some) new environments every generation.
		for (int i = 0; i < environments.size(); i++) {
			if (environmentReplaceProb > envRandom.nextDouble()) {
				Environment newEnv = new Environment();
				if (newEnv.setUp(environmentCounter++, environments)) {
					environments.set(i, newEnv);
				} else {
					logger.warn("Could not replace existing MDP environment with a newly generated one.");					
				}
			}
		}
		
		if (!getNeuronPositionsCalled) {
			// getNeuronPositions is used to calculate indexes into the input neurons.
			this.getNeuronPositions(0, 2);
			this.getNeuronPositions(1, 2);
		}
	}

	@Override
	protected void evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, double[] fitnessValues, Behaviour[] behaviours) {
		if (nsEnvironments == null) {
			// Evaluate fitness and behaviour on same environments.
			List envs = environments.subList(0, Math.max(environmentCount, noveltySearchEnvCount));
			_evaluate(genotype, substrate, null, false, false, fitnessValues, behaviours, envs, environmentCount);
		} else {
			// Evaluate fitness on changing environments and behaviour on fixed novelty search environments.
			_evaluate(genotype, substrate, null, false, false, fitnessValues, null, environments.subList(0, environmentCount), 0);
			_evaluate(genotype, substrate, null, false, false, null, behaviours, nsEnvironments, 0);
		}
	}

	@Override
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		_evaluate(genotype, substrate, baseFileName, logText, logImage, null, null, environments.subList(0, environmentCount), 0);
	}

	public void _evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues, Behaviour[] behaviours, List<Environment> environments, int numEnvsToUseForEval) {
		super.evaluate(genotype, substrate, baseFileName, logText, logImage);
		
		int environmentCount = environments.size();
		if (numEnvsToUseForEval == 0) {
			numEnvsToUseForEval = environmentCount;
		}
		
		double randomCompare = 0;
		int solvedCount = 0;
		// Random testRandom = new Random(System.currentTimeMillis());
		
		int[][][] behaviour = behaviours != null && behaviours.length > 0 ? new int[noveltySearchEnvCount][trialCount][environments.get(0).getStepsPerTrial()] : null;

		int imageScale = 128;

		try {
			NiceWriter logOutput = !logText ? null : new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");

			double[] input = new double[substrate.getInputDimension()[0]];
			double[] avgRewardOrRMSEForEachTrial = new double[trialCount];
			int envIndex = 0;
			for (Environment env : environments) {

				if (logText) {
					logOutput.put("\n\nBEGIN EVALUATION ON ENVIRONMENT " + env.id + "\n");
					logOutput.put(env).put("\n");
				}
				
				// Reset substrate to initial state to begin learning new environment.
				substrate.reset();

				double trialRewardOrRMSE = 0;
				int consecutivePerfectTrialCount = 0;
				for (int trial = 0; trial < trialCount; trial++) {
					// Create an RNG instance that is the same for a given trial in a given environment so that the
					// environment behaviour is exactly the same for each agent and across runs if necessary.
					Random envTrialRandom = new Random((envRandomSeed + env.id * trialCount + trial) * 10);
					if (logText) {
						logOutput.put("\n  BEGIN TRIAL " + (trial + 1) + " of " + trialCount + "\n");
					}

					BufferedImage image = null;
					Graphics2D g = null;
					if (logImage && gridEnvs) {
						image = new BufferedImage(gridSize * imageScale, gridSize * imageScale, BufferedImage.TYPE_3BYTE_BGR);
						g = image.createGraphics();
						g.setColor(Color.WHITE);
						g.fillRect(0, 0, gridSize * imageScale, gridSize * imageScale);
						if (singleRewardState) {
							State rewardState = env.getRewardState();
							g.setColor(Color.GREEN);
							g.fillRect(rewardState.x * imageScale, rewardState.y * imageScale, imageScale, imageScale);
						}
					}

					State currentState = env.states[0];
					Transition transition = null;
					trialRewardOrRMSE = 0;
					double[] output = null;
					double[] previousOutput = new double[substrate.getOutputDimension()[0]];
					int prevAction = -1;
					State prevState = null;
					boolean stepCountsTowardFitness = false;
					
					int step = 0;
					
					for (step = 0; step < env.getStepsPerTrial(); step++) {
						// If we're doing multiple trials then only count reward for last trial,
						// or if we're doing a single long trial then only include reward for last half,
						// or if there's a single reward state then include reward as we'll exit if the agent found
						// the reward state.
						stepCountsTowardFitness = (trialCount > 1 && trial == trialCount - 1) || (trialCount == 1 && step >= stepsPerTrial - stepsForEval);
						
						// Set up the inputs to the network.
						Arrays.fill(input, 0);
						input[currentStateIndex + currentState.id] = 1;
						if (includePrevState && prevState != null) {
							input[previousStateIndex + prevState.id] = 1;
						}
						prevState = currentState;
						if (includePrevAction && prevAction >= 0) {
							input[previousActionIndex + prevAction] = 1;
						}
						if (includeExpl && stepCountsTowardFitness) {
							input[explIndex] = 1;
						}
						
						int action;
						
						// If the agent is to perform actions in the environment (rather than just predict the next state given current state and a given action).
						if (evalType == EvalType.ACT) {
							if (transition != null) {
								// Set reward signal from previous transition.
								input[rewardIndex] = transition.reward;
							}
							
							// Ask the network what it wants to do next, and let it know the reward for the previous state
							// transition.
							output = substrate.next(input);
							// The action to perform is the one corresponding to the output with the highest output value.
							action = ArrayUtil.getMaxIndex(output);
							prevAction = action;
							// int action = testRandom.nextInt(actionCount);
	
							if (logText) {
								boolean outputChanged = step > 0 && !ArrayUtils.isEquals(output, previousOutput);
								logOutput.put("    Agent is at " + currentState.id + "\n");
								
								String inputStr = "    Input: CS=";
								int inputIdx = 0;
								for (; inputIdx < stateCount; inputIdx++)
									inputStr += (int) input[inputIdx];
								if (includePrevState) {
									inputStr += "  PS=";
									int endIdx = inputIdx + stateCount;
									for (; inputIdx < endIdx; inputIdx++)
										inputStr += (int) input[inputIdx];
								}
								if (includePrevAction) {
									inputStr += "  PA=";
									int endIdx = inputIdx + actionCount;
									for (; inputIdx < endIdx; inputIdx++)
										inputStr += (int) input[inputIdx];
								}
								if (includeExpl) {
									inputStr += "  Ex=";
									inputStr += (int) input[inputIdx++];
								}
								inputStr += "  R=";
								inputStr += input[inputIdx++];
								logOutput.put(inputStr + "  (" + ArrayUtil.toString(input, ", ", nf) + ")\n");
								
								//logOutput.put("    Input: " + ArrayUtil.toString(input, ", ", nf) + "\n");
								
								logOutput.put("    Output: " + ArrayUtil.toString(output, ", ", nf) + (outputChanged ? " [changed]" : "") + " (Action: " + action + ")\n");
								logOutput.put("    Current reward: " + nf.format(trialRewardOrRMSE) + "\n\n");
								System.arraycopy(output, 0, previousOutput, 0, output.length);
							}
							if (logImage && gridEnvs) {
								float c = ((float) step / env.getStepsPerTrial()) * 0.75f;
								int size = ((env.getStepsPerTrial() - step) * (imageScale / 2)) / env.getStepsPerTrial() + imageScale / 2 - 2;
								int offset = (imageScale - size) / 2;
								g.setColor(new Color(0, c, c));
								g.fillOval(currentState.x * imageScale + offset, currentState.y * imageScale + offset, size, size);
							}
	
							// If a transition is defined for the current state and specified action.
							transition = currentState.getNextTransition(action, envTrialRandom);
							if (transition != null) {
								currentState = transition.nextState;
								// Add reward to record if reward counts toward fitness,
								// or if there's a single reward state then include reward as we'll exit if the agent found
								// the reward state.
								if (stepCountsTowardFitness || singleRewardState) {
									trialRewardOrRMSE += transition.reward;
								}
							}
	
							if (behaviour != null && envIndex < noveltySearchEnvCount) {
								behaviour[env.id][trial][step] = currentState.id;
							}
	
							if (singleRewardState && currentState.id == env.rewardState) {
								break;
							}
	
							// If the maximum reward has been reached, end the trial.
							// if (trialReward > env.getMaxReward() * 0.99) {
							// break;
							// }
						}
						// evalType == EvalType.PREDICT
						else {
							// The agent is to predict the next state given current state and a given action (rather than perform actions in the environment).
							
							// Pick an action at random.
							action = envTrialRandom.nextInt(actionCount);
							
							input[currentActionIndex + action] = 1;
							
							// Ask the network what it thinks will happen next, and let it know the current state and next action.
							output = substrate.next(input);
							
							if (logText) {
								boolean outputChanged = step > 0 && !ArrayUtils.isEquals(output, previousOutput);
								
								logOutput.put("    Agent is at " + currentState.id + "\n");
								logOutput.put("    Next action is " + action + "\n");
								
								String inputStr = "    Input: CS=";
								int inputIdx = 0;
								for (; inputIdx < stateCount; inputIdx++)
									inputStr += (int) input[inputIdx];
								
								inputStr += "  CA=";
								int endIdx = inputIdx + actionCount;
								for (; inputIdx < endIdx; inputIdx++)
									inputStr += (int) input[inputIdx];
								
								if (includePrevState) {
									inputStr += "  PS=";
									endIdx = inputIdx + stateCount;
									for (; inputIdx < endIdx; inputIdx++)
										inputStr += (int) input[inputIdx];
								}
								if (includePrevAction) {
									inputStr += "  PA=";
									endIdx = inputIdx + actionCount;
									for (; inputIdx < endIdx; inputIdx++)
										inputStr += (int) input[inputIdx];
								}
								if (includeExpl) {
									inputStr += "  Ex=";
									inputStr += (int) input[inputIdx++];
								}
								if (evalType != EvalType.PREDICT) {
									inputStr += "  R=";
									inputStr += input[inputIdx++];
								}
								
								logOutput.put(inputStr + "  (" + ArrayUtil.toString(input, ", ", nf) + ")\n");
								
								logOutput.put("    Output: " + ArrayUtil.toString(output, ", ", nf) + (outputChanged ? " [changed]" : "") + "\n");
								System.arraycopy(output, 0, previousOutput, 0, output.length);
							}
							
							// If a transition is defined for the current state and specified action.
							transition = currentState.getNextTransition(action, envTrialRandom);
							if (transition != null) {
								currentState = transition.nextState;
							}
							
							// Add error to record if this step counts toward fitness.
							if (stepCountsTowardFitness) {
								for (int i = 0; i < stateCountMax; i++) {
									// Only the output corresponding to current state should be 1.
									double error = Math.abs((i == currentState.id ? 1 : 0) - output[i]);
									// It's only an error if it's the wrong side of the halfway mark.
									if (error >= 0.5) {
										// Scale to ~ [0.1, 1].
										error = (error - 0.445) * 1.8;
										trialRewardOrRMSE += error * error;
									}
								}
							}
	
							if (behaviour != null && envIndex < noveltySearchEnvCount) {
								behaviour[env.id][trial][step] = currentState.id;
							}
						}
					}

					if (logText) {
						logOutput.put("    Agent is at " + currentState.id + "\n");
						if (evalType == EvalType.ACT) {
							logOutput.put("    Current reward: " + nf.format(trialRewardOrRMSE) + "\n\n");
						}
					}

					if (logImage && gridEnvs) {
						float c = ((float) step / env.getStepsPerTrial());
						int size = ((env.getStepsPerTrial() - step) * (imageScale / 2)) / env.getStepsPerTrial() + imageScale / 2 - 2;
						int offset = (imageScale - size) / 2;
						g.setColor(new Color(0, c, c));
						g.fillOval(currentState.x * imageScale + offset, currentState.y * imageScale + offset, size, size);

						File outputfile = new File(baseFileName + ".env_ " + env.id + ".trial_" + trial + ".png");
						ImageIO.write(image, "png", outputfile);
					}
						
					// If we're including this environment in the evaluation.
					if (envIndex < numEnvsToUseForEval) {
						if (evalType == EvalType.ACT) {
							// If this is the last trial.
							if (trial == trialCount - 1) {
								// Ratio of reward received versus reward received by random agent.
								randomCompare += trialRewardOrRMSE / env.getRandomReward();
							}
							
							avgRewardOrRMSEForEachTrial[trial] += trialRewardOrRMSE / env.getMaxReward();
						}
						else {
							avgRewardOrRMSEForEachTrial[trial] += Math.sqrt(trialRewardOrRMSE / (stepsForEval * stateCountMax));
						}
					}

					// If environmentMasteredTrialCount perfect trials have been executed then we can probably assume
					// this environment has been mastered [this won't work until we use Q-learning or similar to
					// determine optimal reward.]
					// if (trialReward > 0.99) {
					// consecutivePerfectTrialCount++;
					// if (consecutivePerfectTrialCount == environmentMasteredTrialCount) {
					// solvedCount++;
					// // Fill in reward values for trials we're skipping.
					// for (int t = trial+1; t < trialCount; t++) {
					// avgRewardForEachTrial[t] += trialReward;
					// envReward += trialReward;
					// perf += trialReward / env.getRandomReward();
					// }
					// if (logText) {
					// logOutput.put("  ENVIRONMENT MASTERED.\n\n");
					// }
					// break; // breaks out of: for (int trial = 0; trial < trialCount; trial++)
					// }
					// } else {
					// consecutivePerfectTrialCount = 0;
					// }
				}

				// For the acting task, if we're including this environment in the 
				// evaluation and the agent got 99% of the maximum reward last trial.
				if (evalType == EvalType.ACT && envIndex < numEnvsToUseForEval && trialRewardOrRMSE / env.getMaxReward() > 0.99) {
					solvedCount++;
				}
				
				envIndex++;
			}

			for (int trial = 0; trial < trialCount; trial++) {
				avgRewardOrRMSEForEachTrial[trial] /= numEnvsToUseForEval;
			}
			if (logText) {
				logOutput.put("\n\n" + (evalType == EvalType.ACT ? "Reward" : "RMSE") + " for each trial averaged over all environments:\n");
				logOutput.put(Arrays.toString(avgRewardOrRMSEForEachTrial));
				logOutput.close();
			}
			
			double fitness = 0;
			
			if (evalType == EvalType.ACT) {
				randomCompare /= numEnvsToUseForEval;
				
				if (getEnvOptimalRewardCalcType().equals(EnvOptimalRewardCalcType.NOT_SUPPORTED)) {
					// Use comparison to random agent if optimal reward not available for comparison.
					// Fitness is 10% of ratio of reward received versus reward received by random agent.
					fitness = randomCompare > 10 ? 1 : randomCompare * 0.1 * 0.99;
				} else {
					// Average of final trial from each environment.
					fitness = avgRewardOrRMSEForEachTrial[trialCount - 1] * 0.1;
					genotype.setPerformanceValue("1VSOptimal", fitness);
				}
				
				genotype.setPerformanceValue("0VSRandom", randomCompare > 10 ? 1 : randomCompare * 0.1 * 0.99);
				genotype.setPerformanceValue("2Solved", (double) solvedCount / numEnvsToUseForEval);
			}
			else {
				// Inverse average RMSE of final trial from each environment.
				fitness = 1.0 / (1 + avgRewardOrRMSEForEachTrial[trialCount - 1]);
				genotype.setPerformanceValue("0RMSE", avgRewardOrRMSEForEachTrial[trialCount - 1]);
			}
			
			if (fitness == 0)
				fitness = Double.MIN_VALUE;
			
			if (fitnessValues != null && fitnessValues.length > 0)
				fitnessValues[0] = fitness;

			if (behaviours != null && behaviours.length > 0) {
				behaviours[0] = new MDPBehaviour(this, behaviour);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean evaluateGeneralisation(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues) {
		if (props.getEvolver().getGeneration() > 0) {
			if (genEnvironments == null) {
				// Test on twice as many environments as used for fitness evaluations.
				genEnvironments = new ArrayList<Environment>();
				for (int i = 0; i < environmentCountMax*2; i++) {
					Environment newEnv = new Environment();
					if (newEnv.setUp(genEnvironments.size(), genEnvironments)) {
						genEnvironments.add(newEnv);
					}
				}
			}
			_evaluate(genotype, substrate, baseFileName, logText, logImage, fitnessValues, null, genEnvironments, 0);
			return true;
		}
		return false;
	}

	private boolean increaseDifficulty() {
		boolean increasedDifficulty = false;
		if (stateCount < props.getIntProperty(STATE_COUNT_MAX)) {
			String deltaString = props.getProperty(STATE_COUNT_INCREASE_DELTA).trim().toLowerCase();
			boolean isFactor = deltaString.endsWith("x");
			double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
			if (delta >= 1) {
				int initStateCount = stateCount;
				if (!isFactor) {
					stateCount += (int) Math.round(delta);
				} else if (delta > 1) {
					stateCount = Math.max(1, (int) Math.round(stateCount * delta));
				}

				adustStateCountForEnvType();
				increasedDifficulty = initStateCount != stateCount;

				if (increasedDifficulty) {
					determineStepsPerTrial();
				}
			}
		}

		if (actionCount < props.getIntProperty(ACTION_COUNT_MAX)) {
			String deltaString = props.getProperty(ACTION_COUNT_INCREASE_DELTA).trim().toLowerCase();
			boolean isFactor = deltaString.endsWith("x");
			double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
			if (delta >= 1) {
				if (!isFactor) {
					actionCount += (int) Math.round(delta);
					increasedDifficulty = true;
				} else if (delta > 1) {
					actionCount = Math.max(1, (int) Math.round(actionCount * delta));
					increasedDifficulty = true;
				}
			}
		}

		if (increasedDifficulty) {
			reportedEnvOptimalRewardCalcType = null;
		}

		return increasedDifficulty;
	}

	@Override
	public int noveltyObjectiveCount() {
		return noveltySearchEnabled ? 1 : 0;
	}

	@Override
	public int fitnessObjectivesCount() {
		return noveltySearchOnly ? 0 : 1;
	}
	
	@Override 
	public String[] objectiveLabels() {
		String[] labels = new String[fitnessObjectivesCount() + noveltyObjectiveCount()];
		int i = 0;
		if (!noveltySearchOnly) labels[i++] = "Fitness";
		if (noveltySearchEnabled) labels[i++] = "Novelty";
		return labels;
	}

	@Override
	public boolean fitnessValuesStable() {
		return false; // environmentReplaceProb == 0;
	}

	// The method to use to determine the (approx) optimal reward per step in an environment.
	enum EnvOptimalRewardCalcType {
		NOT_SUPPORTED, APPROXIMATE, EXACT
	}

	static EnvOptimalRewardCalcType reportedEnvOptimalRewardCalcType;

	private synchronized EnvOptimalRewardCalcType getEnvOptimalRewardCalcType() {
		if (reportedEnvOptimalRewardCalcType == null) {
			if (transitionRandomness > 0) {
				reportedEnvOptimalRewardCalcType = EnvOptimalRewardCalcType.NOT_SUPPORTED;
				logger.info("Calculating optimal reward for non-deterministic environments not currently supported.");
			} else {
				// If it looks like we can calculate an exact optimal value (no more than a million steps to try).
				if (Math.pow(actionCount, stepsPerTrial + 1) <= 1024 * 1024) {
					reportedEnvOptimalRewardCalcType = EnvOptimalRewardCalcType.EXACT;
					logger.info("Calculating exact optimal reward for environments.");
				} else {
					reportedEnvOptimalRewardCalcType = EnvOptimalRewardCalcType.APPROXIMATE;
					logger.info("Calculating approximate optimal reward for environments.");
				}
			}
		}
		return reportedEnvOptimalRewardCalcType;
	}

	class Environment {
		static final double MIN_REWARD = 0.0;
		static final int MAX_SETUP_RETRIES = 100;
		
		public int id;
		private double maxReward, randomReward;
		private State[] states;
		private int[] optimalStateSequence;
		private int[] optimalActionSequence;
		private int rewardState = -1; // Only used if singleRewardState == true.
		private int envStateCount;
		private int envActionCount;
		private int envStepsPerTrial;
		private int envStepsForReward;
		
		
		public int getStateCount() {
			return envStateCount;
		}

		public int getActionCount() {
			return envActionCount;
		}

		public int getStepsPerTrial() {
			return envStepsPerTrial;
		}

		public int getStepsForReward() {
			return envStepsForReward;
		}

		private boolean setUp(int id, ArrayList<Environment> otherEnvs) {
			this.id = id;
			envStateCount = stateCount;
			envActionCount = actionCount;
			envStepsPerTrial = stepsPerTrial;
			envStepsForReward = stepsForEval;
			
			// Create an RNG instance that is the same given the same seed and environment ID,
			// so we can reproduce the same environments in different runs.
			Random envSetupRandom = new Random(id * 1000 + envRandomSeed);

			// Only used if singleRewardState == true.
			if (singleRewardState) {
				rewardState = envSetupRandom.nextInt(envStateCount);
			}
			
			double overallPrimaryNextStateProb = 1 - transitionRandomness + transitionRandomness * (1.0 / envActionCount);
			double primaryNextStateProbVariance = 1 - overallPrimaryNextStateProb;

			boolean atLeastOneNonZeroReward = false;
			int retries = 0;
			do {
				if (gridEnvs) {
					// Create environment where states organised in a grid with transitions only to neighbouring states.

					// Create states.
					states = new State[envStateCount];
					State[][] stateGrid = new State[gridSize][gridSize];
					for (int x = 0, s = 0; x < gridSize; x++) {
						for (int y = 0; y < gridSize; y++, s++) {
							states[s] = new State(s, x, y);
							stateGrid[x][y] = states[s];
						}
					}
					// Create transitions.
					for (int x = 0; x < gridSize; x++) {
						for (int y = 0; y < gridSize; y++) {
							// If there's a single reward state, and this is it, don't add transitions away from it.
							if (stateGrid[x][y].id != rewardState) {
								for (int actionID = 0; actionID < 4; actionID++) {
									Action action = new Action(actionID);

									int xt = x + (actionID == 0 ? -1 : (actionID == 2 ? 1 : 0));
									int yt = y + (actionID == 1 ? -1 : (actionID == 3 ? 1 : 0));

									// If we're not wrapping and action would lead to out of bounds.
									if (!gridWrap && (xt < 0 || xt >= gridSize))
										continue;
									if (!gridWrap && (yt < 0 || yt >= gridSize))
										continue;

									if (xt < 0)
										xt += gridSize;
									else if (xt >= gridSize)
										xt -= gridSize;

									if (yt < 0)
										yt += gridSize;
									else if (yt >= gridSize)
										yt -= gridSize;

									State nextState = stateGrid[xt][yt];

									double reward = rewardForTransition(nextState, envSetupRandom);
									atLeastOneNonZeroReward |= reward > MIN_REWARD;

									Transition tran = new Transition(1, nextState, reward);
									action.addTransition(tran);
									stateGrid[x][y].addAction(action);
								}
							}
						}
					}
				} else {
					// Create completely random environment.
					// Create states.
					states = new State[envStateCount];
					for (int s = 0; s < envStateCount; s++) {
						states[s] = new State(s);
					}

					atLeastOneNonZeroReward = false;
					for (int stateID = 0; stateID < envStateCount; stateID++) {
						for (int actionID = 0; actionID < envActionCount; actionID++) {
							// If a mapping should be created for this action.
							if (mapRatio > envSetupRandom.nextDouble()) {
								Action action = new Action(actionID);

								double primaryNextStateProb = overallPrimaryNextStateProb + (2 * primaryNextStateProbVariance * envSetupRandom.nextDouble() - primaryNextStateProbVariance);
								int primaryNextState = envSetupRandom.nextInt(envStateCount);
								while (primaryNextState == stateID)
									primaryNextState = envSetupRandom.nextInt(envStateCount);

								double reward = rewardForTransition(states[primaryNextState], envSetupRandom);
								atLeastOneNonZeroReward |= reward > MIN_REWARD;

								Transition tran = new Transition(primaryNextStateProb, states[primaryNextState], reward);
								action.addTransition(tran);

								if (transitionRandomness > 0) {
									// Actions map probabilistically to (any possible) next state.
									// Generate list of probabilities for transitions to non-primary state;
									double[] tranProb = new double[envStateCount];
									do {
										for (int nextStateID = 0; nextStateID < envStateCount; nextStateID++) {
											if (nextStateID != primaryNextState && transitionRandomness > envSetupRandom.nextDouble()) {
												tranProb[nextStateID] = envSetupRandom.nextDouble();
											}
										}
									} while (ArrayUtil.sum(tranProb) == 0);
									ArrayUtil.normaliseSum(tranProb);
									ArrayUtil.multiply(tranProb, 1 - primaryNextStateProb);

									for (int nextStateID = 0; nextStateID < envStateCount; nextStateID++) {
										if (nextStateID != primaryNextState) {
											reward = rewardForTransition(states[nextStateID], envSetupRandom);
											atLeastOneNonZeroReward |= reward > MIN_REWARD;

											tran = new Transition(tranProb[nextStateID], states[nextStateID], reward);
											action.addTransition(tran);
										}
									}
								}

								states[stateID].addAction(action);
							}
						}
					}
				}

				// System.err.println(toString());
				//
				// for (int stateID = 0; stateID < stateCount; stateID++) {
				// System.err.println(stateID);
				// for (int actionID = 0; actionID < actionCount; actionID++) {
				// System.err.println("\t" + actionID);
				// int[] counts = new int[stateCount];
				// for (int i = 0; i < 1000; i++) {
				// counts[states[stateID].getNextState(actionID, envRandom).id]++;
				// }
				// for (int nextStateID = 0; nextStateID < stateCount; nextStateID++) {
				// System.err.println("\t\t" + counts[nextStateID]);
				// }
				// }
				// }
				
				if (retries > MAX_SETUP_RETRIES) {
					break;
				}
				retries++;
			} while (!atLeastOneNonZeroReward || !allStatesReachableFromAnyState() || equivalentEnvironmentExists(otherEnvs));

			if (retries > MAX_SETUP_RETRIES) {
				return false;
			}
			
			// } while (!atLeastOneNonZeroReward);

			// TODO: ensure non-zero reward can be received when starting from first state using a TD-learning method.
			// (and use the best policy found to determine the maximum reward value so we can accurately measure
			// performance).

			// For now set max reward to theoretical upper bound. Reward values are in range (0, 1).
			// If we're doing a single long trial then only reward from the last half of it is included.

			// maxReward = stepsForEval;
			if (evalType == EvalType.ACT) {
				//if (getEnvOptimalRewardCalcType().equals(EnvOptimalRewardCalcType.NOT_SUPPORTED)) {
					// Determine reward received by a random agent, for comparison.
					randomReward = calcRandomReward(envSetupRandom);
					randomReward *= envStepsForReward;
				//} else {
					maxReward = calcOptimalReward();
					if (!singleRewardState) {
						maxReward *= envStepsForReward;
					}
				//}
			}
			return true;
		}
		
		private boolean equivalentEnvironmentExists(ArrayList<Environment> otherEnvs) {
			for (Environment other : otherEnvs) {
				if (this.isEquivalent(other))
					return true;
			}
			return false;
		}

		/**
		 * Returns true iff the given environment is equal to this environment.
		 */
		public boolean isEquivalent(Environment e) {
			if (envStateCount != e.envStateCount || envActionCount != e.envActionCount || envStepsPerTrial != e.envStepsPerTrial)
				return false;
			
			for (int stateID = 0; stateID < envStateCount; stateID++) {
				if (!states[stateID].isEquivalent(e.states[stateID]))
					return false;
			}
			return true;
		}

		public State getRewardState() {
			if (rewardState != -1) {
				return states[rewardState];
			}
			return null;
		}

		private double rewardForTransition(State nextState, Random envSetupRandom) {
			if (singleRewardState) {
				if (nextState.id == rewardState) {
					return 1;
				}
			} else {
				// return (transitionRewardRatio > envSetupRandom.nextDouble()) ? envSetupRandom.nextDouble() :
				// MIN_REWARD;
				return (transitionRewardRatio > envSetupRandom.nextDouble()) ? envSetupRandom.nextInt(2) : MIN_REWARD;
			}
			return MIN_REWARD;
		}

		// Make sure all states are reachable and that there are no dead ends.
		private boolean allStatesReachableFromAnyState() {
			if (gridEnvs) {
				return true;
			}

			HashSet<Integer> toExplore = new HashSet<Integer>();
			HashSet<Integer> nextToExplore = new HashSet<Integer>();

			for (int si = 0; si < envStateCount; si++) {
				boolean[] covered = new boolean[envStateCount];
				toExplore.clear();
				nextToExplore.clear();

				toExplore.add(si);
				covered[si] = true;
				int coveredCount = 1;

				while (!toExplore.isEmpty()) {
					for (Integer s : toExplore) {
						for (Action a : states[s].getActions()) {
							for (Transition t : a.getTransitions()) {
								if (!covered[t.nextState.id]) {
									nextToExplore.add(t.nextState.id);
									covered[t.nextState.id] = true;
									coveredCount++;
								}
							}
						}
					}

					HashSet<Integer> t = toExplore;
					toExplore = nextToExplore;
					nextToExplore = t;
					nextToExplore.clear();
				}

				if (coveredCount != envStateCount)
					return false;
			}
			return true;
		}

		// Determine reward received by a random agent, for comparison.
		// Returns a value in range (0, 1), representing average reward for a single step.
		private double calcRandomReward(Random random) {
			// Determine reward received by a random agent, for comparison.
			double randomReward = 0;
			State currentState = states[0];
			for (int step = 0; step < 1000; step++) {
				int action = random.nextInt(envActionCount);

				// If a transition is defined for the current state and specified action.
				Transition transition = currentState.getNextTransition(action, random);
				if (transition != null) {
					currentState = transition.nextState;
					randomReward += transition.reward;
				}
			}
			randomReward /= 1000;
			return randomReward;
		}

		// Determine the reward received by an optimal agent, for comparison.
		// Returns a value in range (0, 1), representing average reward for a single step.
		private double calcOptimalReward() {
			if (singleRewardState) {
				return 1;
			}

			if (getEnvOptimalRewardCalcType().equals(EnvOptimalRewardCalcType.EXACT)) {
				double optReward = 0;
				optimalStateSequence = new int[envStepsPerTrial + 1];
				optimalActionSequence = new int[envStepsPerTrial];
				MultidimensionalCounter mc = new MultidimensionalCounter(ArrayUtil.newArray(envStepsPerTrial, envActionCount));
				MultidimensionalCounter.Iterator mci = mc.iterator();
				// Try every combination of action sequences of length stepsPerTrial starting from start state.
				while (mci.hasNext()) {
					mci.next();
					State currentState = states[0];
					double reward = 0;
					int[] stateSequence = new int[envStepsPerTrial];
					for (int step = 0; step < envStepsPerTrial; step++) {
						int action = mci.getCount(step);
						// If a transition is defined for the current state and specified action.
						Transition transition = currentState.getNextTransition(action, random);
						if (transition != null) {
							currentState = transition.nextState;
							stateSequence[step] = currentState.id;
							reward += transition.reward;
						}
					}
					// Average over steps taken.
					reward /= envStepsPerTrial;
					if (reward > optReward) {
						optReward = reward;
						System.arraycopy(mci.getCounts(), 0, optimalActionSequence, 0, envStepsPerTrial);
						System.arraycopy(stateSequence, 0, optimalStateSequence, 1, envStepsPerTrial);
					}
				}
				return optReward;
			} else if (getEnvOptimalRewardCalcType().equals(EnvOptimalRewardCalcType.APPROXIMATE)) {
				// Since every state is reachable from every other state and we're using deterministic transitions,
				// the optimal sequence of actions will be of length in range [2, stateCount] (shortest loop to longest
				// loop). So just try every permutation of actions of length [2, stateCount]. Since the start state may
				// not be in this loop and the number of steps per trial might not be a factor of the loop length
				// this will only give an approximate optimal reward value.
				double optReward = 0;
				for (int sequenceLength = 2; sequenceLength <= envStateCount; sequenceLength++) {
					MultidimensionalCounter mc = new MultidimensionalCounter(ArrayUtil.newArray(sequenceLength, envActionCount));
					for (int startState = 0; startState < envStateCount; startState++) {
						MultidimensionalCounter.Iterator mci = mc.iterator();
						while (mci.hasNext()) {
							mci.next();
							State currentState = states[startState];
							double reward = 0;
							int[] stateSequence = new int[sequenceLength + 1];
							stateSequence[0] = startState;
							for (int step = 0; step < sequenceLength; step++) {
								int action = mci.getCount(step);
								// If a transition is defined for the current state and specified action.
								Transition transition = currentState.getNextTransition(action, random);
								if (transition != null) {
									currentState = transition.nextState;
									stateSequence[step + 1] = currentState.id;
									reward += transition.reward;
								}
							}

							// We can only include this sequence if it forms a loop (which can then be repeated).
							if (currentState.id == startState) {
								// Average over steps taken.
								reward /= sequenceLength;
								if (reward > optReward) {
									optReward = reward;
									optimalStateSequence = new int[sequenceLength + 1];
									optimalActionSequence = new int[sequenceLength];
									System.arraycopy(stateSequence, 0, optimalStateSequence, 0, sequenceLength + 1);
									System.arraycopy(mci.getCounts(), 0, optimalActionSequence, 0, sequenceLength);
								}
							}
						}
					}
				}
				return optReward;
			}
			optimalActionSequence = null;
			return 1; // Assume maximum possible value.
		}

		public double getMaxReward() {
			return maxReward;
		}

		public double getRandomReward() {
			return randomReward;
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			//sb.append("Random reward: " + nf.format(randomReward) + "\n\n");
			//sb.append("(Approx) optimal reward: " + nf.format(maxReward) + "\n");
			if (optimalStateSequence != null) {
				//sb.append("\tState sequence: " + ArrayUtil.toString(optimalStateSequence, "\t", null) + "\n");
				//sb.append("\tAction sequence:  " + ArrayUtil.toString(optimalActionSequence, "\t  ", null) + "\n");
			}
			if (gridEnvs && singleRewardState) {
				sb.append("Goal state: " + rewardState);
			} else {
				for (int s = 0; s < envStateCount; s++) {
					sb.append(states[s] + "\n");
				}
			}
			return sb.toString();
		}
	}

	private class State {
		final int id;
		final int x, y;
		// Available actions.
		private final ArrayList<Action> actions;

		public State(int id) {
			this.id = id;
			x = id;
			y = 0;
			actions = new ArrayList<Action>();
		}

		public boolean isEquivalent(State s) {
			if (actions.size() != s.actions.size()) return false;
			for (int actionID = 0; actionID < actions.size(); actionID++) {
				if (!actions.get(actionID).isEquivalent(s.actions.get(actionID)))
					return false;
			}
			return true;
		}

		public State(int id, int x, int y) {
			this.id = id;
			this.x = x;
			this.y = y;
			actions = new ArrayList<Action>();
		}

		public void addAction(Action a) {
			actions.add(a);
		}

		public Transition getNextTransition(int actionID, Random r) {
			// Search for the specified action in the list of allowed actions.
			for (Action a : actions) {
				if (a.id == actionID) {
					return a.getNextTransition(r);
				}
			}
			// If this action is not allowed then the state doesn't change.
			return null;
		}

		public List<Action> getActions() {
			return Collections.unmodifiableList(actions);
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("State " + id + (gridEnvs ? "(" + x + ", " + y + ")" : "") + ", actions:\n");
			for (Action a : actions) {
				sb.append(a + "\n");
			}
			return sb.toString();
		}
		
	}

	// An action performed in some State.
	private class Action {
		final int id;
		// Probabilities for transitioning to each available state.
		final ArrayList<Transition> transitions;
		final DoubleVector probSum;
		private boolean transitionsOrdered = false;

		public Action(int id) {
			this.id = id;
			transitions = new ArrayList<Transition>();
			probSum = new DoubleVector();
		}

		public boolean isEquivalent(Action a) {
			if (!transitionsOrdered) {
				orderTransitions();
			}
			if (transitions.size() != a.transitions.size()) return false;
			for (int tranID = 0; tranID < transitions.size(); tranID++) {
				if (!transitions.get(tranID).isEquivalent(a.transitions.get(tranID)))
					return false;
			}
			return true;
		}

		private void orderTransitions() {
			Collections.sort(transitions);
			probSum.clear();
			for (Transition t : transitions) {
				probSum.add(getTotalProbSum() + t.probability);
			}
		}

		public void addTransition(Transition t) {
			transitions.add(t);
			probSum.add(getTotalProbSum() + t.probability);
		}

		public Transition getNextTransition(Random r) {
			if (transitions.size() == 1)
				return transitions.get(0);

			int index = probSum.binarySearchIP(r.nextDouble() * getTotalProbSum());
			return transitions.get(index);
		}

		private double getTotalProbSum() {
			return probSum.isEmpty() ? 0 : probSum.get(probSum.size() - 1);
		}

		public List<Transition> getTransitions() {
			return Collections.unmodifiableList(transitions);
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("\tAction " + id + ", transitions:\n");
			for (Transition t : transitions) {
				sb.append("\t\t" + t + "\n");
			}
			return sb.toString();
		}
	}

	private class Transition implements Comparable {
		final double probability;
		final State nextState;
		final double reward;

		public Transition(double probability, State nextState, double reward) {
			this.probability = probability;
			this.nextState = nextState;
			this.reward = reward;
		}

		public boolean isEquivalent(Transition t) {
			return probability == t.probability && nextState.id == t.nextState.id && reward == t.reward;
		}

		public String toString() {
			return nextState.id + ", " + nf.format(probability) + ", " + nf.format(reward);
		}

		@Override
		public int compareTo(Object otherTransition) {
			State otherNextState = ((Transition) otherTransition).nextState;
			if (nextState.id > otherNextState.id) return 1;
			if (nextState.id < otherNextState.id) return -1;
			return 0;
		}
	}

	@Override
	public void ahniEventOccurred(AHNIEvent event) {
		if (event.getType() == AHNIEvent.Type.GENERATION_END) {
			Chromosome bestPerforming = event.getEvolver().getBestPerformingFromLastGen();
			
			if (targetPerformanceType == 1 && bestPerforming.getPerformanceValue() >= props.getDoubleProperty(DIFFICULTY_INCREASE_PERFORMANCE) 
					|| targetPerformanceType == 0 && bestPerforming.getPerformanceValue() <= props.getDoubleProperty(DIFFICULTY_INCREASE_PERFORMANCE)) {
				
				if (environmentIncreaseRate > 1) {
					int origEnvCount = environmentCount;
					environmentCount = Math.max(environmentCount + 1, (int) Math.round(environmentCount * environmentIncreaseRate));
					if (environmentCount > environmentCountMax) {
						environmentCount = environmentCountMax;
					}
					if (environmentCount != origEnvCount) {
						logger.info("Increased difficulty. Number of environments to test against is now " + environmentCount + ".");
					}
				}
				else if (increaseDifficulty()) {
					logger.info("Increased difficulty. State/action counts are now " + stateCount + " / " + actionCount);
				}
			}
		}
	}

	private int getInputSize() {
		return stateCountMax + (evalType == EvalType.PREDICT ? stateCountMax : 0) + (includePrevState ? stateCountMax : 0) + 
				(includePrevAction ? actionCountMax : 0) + (includeExpl ? 1 : 0) + (evalType != EvalType.PREDICT ? 1 : 0);
	}

	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) { // Input layer.
			return new int[] { getInputSize(), 1 };
		} else if (layer == totalLayerCount - 1) { // Output layer.
			if (evalType == EvalType.ACT) {
				// Action to perform next.
				return new int[] { actionCountMax, 1 };
			}
			else {
				// Predicted next state.
				return new int[] { stateCountMax, 1 };
			}
		}
		return null;
	}

	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		getNeuronPositionsCalled = true;
		
		Point[] positions = null;
	
		if (gridEnvs) {
			if (layer == 0) { // Input layer.
				positions = new Point[getInputSize()];
				int posIndex = 0;
				// Current state.
				for (int x = 0; x < gridSizeMax; x++) {
					for (int y = 0; y < gridSizeMax; y++) {
						positions[posIndex++] = new Point((double) x / (gridSizeMax - 1), (double) y / (gridSizeMax - 1), 0);
					}
				}
				// if (includePrevAction || includePrevState) {
				// double horizFactor = includePrevAction && includePrevState ? 1.0 / 3 : 0.5;
				// int horizPos = 1;
				// if (includePrevState) {
				// for (int i = 0; i < stateCountMax; i++) {
				// positions[posIndex++] = new Point((double) i / (stateCountMax - 1), horizPos * horizFactor, 0);
				// }
				// horizPos++;
				// }
				// if (includePrevAction) {
				// for (int i = 0; i < actionCountMax; i++) {
				// positions[posIndex++] = new Point((double) i / (actionCountMax - 1), horizPos * horizFactor, 0);
				// }
				// }
				// }

				// if (includeExpl) {
				// // Exploration/Exploitation signal, at top-left.
				// positions[posIndex++] = new Point(0, 1, 0);
				// }

				// Reinforcement signal, behind state input layer.
				positions[stateCountMax] = new Point(0.5, 0.5, -0.5);
			} else if (layer == totalLayerCount - 1) { // Output layer.
				positions = new Point[actionCountMax];

				// int xt = x + (actionID == 0 ? -1 : (actionID == 2 ? 1 : 0));
				positions[0] = new Point(0, 0.5, 1);
				positions[2] = new Point(1, 0.5, 1);

				// int yt = y + (actionID == 1 ? -1 : (actionID == 3 ? 1 : 0));
				positions[1] = new Point(0.5, 0, 1);
				positions[3] = new Point(0.5, 1, 1);
			}
		} else {
			if (layer == 0) { // Input layer.
				positions = new Point[getInputSize()];
				int posIndex = 0;
				// Current state.
				for (int i = 0; i < stateCountMax; i++) {
					// Horizontal along bottom (y=0)
					positions[posIndex++] = new Point((double) i / (stateCountMax - 1), 0, 0);
				}
				
				double rowCount = (evalType == EvalType.PREDICT ? 1 : 0) + (includePrevState ? 1 : 0) + (includePrevAction ? 1 : 0) + 
							      ((includeExpl || evalType != EvalType.PREDICT) ? 1 : 0); // if include exploitation/exploration signal or include the reward signal. 
				double rowDelta = rowCount > 0 ? 1.0 / rowCount : 0;
				int row = 1;
				
				// Include current (randomly selected) action for prediction task.
				if (evalType == EvalType.PREDICT) {
					currentActionIndex = posIndex;
					for (int i = 0; i < actionCountMax; i++) {
						positions[posIndex++] = new Point((double) i / (actionCountMax - 1), row * rowDelta, 0);
					}
					row++;
				}
				if (includePrevState) {
					previousStateIndex = posIndex;
					for (int i = 0; i < stateCountMax; i++) {
						positions[posIndex++] = new Point((double) i / (stateCountMax - 1), row * rowDelta, 0);
					}
					row++;
				}
				if (includePrevAction) {
					previousActionIndex = posIndex;
					for (int i = 0; i < actionCountMax; i++) {
						positions[posIndex++] = new Point((double) i / (actionCountMax - 1), row * rowDelta, 0);
					}
				}
			
				if (includeExpl) {
					explIndex = posIndex;
					// Exploration/Exploitation signal, at top-left.
					positions[posIndex++] = new Point(0, 1, 0);
				}
				
				// Don't include reward signal for predict task.
				if (evalType != EvalType.PREDICT) {
					rewardIndex = posIndex;
					// Reinforcement signal, at top-right.
					positions[posIndex++] = new Point(1, 1, 0);
				}
			} else if (layer == totalLayerCount - 1) { // Output layer.
				if (evalType == EvalType.ACT) {
					positions = new Point[actionCountMax];
					// Action to perform next.
					for (int i = 0; i < actionCountMax; i++) {
						// Horizontal along middle.
						positions[i] = new Point((double) i / (actionCountMax - 1), 0.5, 1);
					}
				}
				// Predict task.
				else {
					positions = new Point[stateCountMax];
					// Predicted next state.
					for (int i = 0; i < stateCountMax; i++) {
						// Horizontal along middle.
						positions[i] = new Point((double) i / (stateCountMax - 1), 0.5, 1);
					}
				}
			}
		}
		return positions;
	}
}
