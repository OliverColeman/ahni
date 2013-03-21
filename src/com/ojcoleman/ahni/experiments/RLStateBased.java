package com.ojcoleman.ahni.experiments;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.event.AHNIEvent;
import com.ojcoleman.ahni.event.AHNIEventListener;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.NiceWriter;
import com.ojcoleman.ahni.util.Point;

public class RLStateBased extends BulkFitnessFunctionMT implements AHNIEventListener {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(BulkFitnessFunctionMT.class);
	
	/**
	 * The initial number of states in the generated environments.
	 */
	public static final String STATE_COUNT_INITIAL = "fitness.function.rlstate.states.initial";
	/**
	 * The amount to increase the number of states in the generated environments when the current size has been sufficiently mastered (see {@link #DIFFICULTY_INCREASE_PERFORMANCE}.
	 * If the value is followed by an "x" then the value is considered a factor (and so should be > 1).
	 */
	public static final String STATE_COUNT_INCREASE_DELTA = "fitness.function.rlstate.states.delta";
	/**
	 * The maximum amount to increase the number of states in the generated environments to.
	 */
	public static final String STATE_COUNT_MAX = "fitness.function.rlstate.states.maximum";
	/**
	 * The initial number of actions available in the generated environments.
	 */
	public static final String ACTION_COUNT_INITIAL = "fitness.function.rlstate.actions.initial";
	/**
	 * The amount to increase the available number of actions in the generated environments when the current size has been sufficiently mastered (see {@link #DIFFICULTY_INCREASE_PERFORMANCE}.
	 * If the value is followed by an "x" then the value is considered a factor (and so should be > 1).
	 */
	public static final String ACTION_COUNT_INCREASE_DELTA = "fitness.function.rlstate.actions.delta";
	/**
	 * The maximum amount to increase the available number of actions in the generated environments to.
	 */
	public static final String ACTION_COUNT_MAX = "fitness.function.rlstate.actions.maximum";
	/**
	 * The performance indicating when the environment size/difficulty should be increased as the current size has been sufficiently mastered. Performance is calculated
	 * as a proportion of the maximum possible fitness (which is the sum of 
	 */
	public static final String DIFFICULTY_INCREASE_PERFORMANCE = "fitness.function.rlstate.difficulty.increase.performance";
	/**
	 * The proportion of actions that will map to some other state. This is evaluated probabilistically for all states and actions when generating an environment.
	 */
	public static final String ACTION_MAP_RATIO = "fitness.function.rlstate.action.map.ratio";
	/**
	 * The proportion of states that will contain a reward value greater than 0.
	 */
	public static final String REWARD_STATE_RATIO = "fitness.function.rlstate.states.reward.ratio";
	/**
	 * The number of environments to evaluate candidates against. Increasing this will provide a more accurate evaluation but take longer.
	 */
	public static final String ENVIRONMENT_COUNT = "fitness.function.rlstate.environment.count";
	/**
	 * The fraction of environments that should be replaced with new environments per generation. This is evaluated probabilistically.
	 */
	public static final String ENVIRONMENT_CHANGE_RATE = "fitness.function.rlstate.environment.replacerate";
	
	//public static final String TRIAL_COUNT = "fitness.function.rlstate.trial.count";
	
	private int environmentCount;
	private double environmentReplaceProb;
	private int trialCount;
	private int stateCount;
	private int actionCount;
	private int stateCountMax;
	private int actionCountMax;
	private Environment[] environments;

	@Override
	public void init(Properties props) {
		super.init(props);
		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		environmentReplaceProb = props.getDoubleProperty(ENVIRONMENT_CHANGE_RATE);
		actionCount = props.getIntProperty(ACTION_COUNT_INITIAL);
		stateCount = props.getIntProperty(STATE_COUNT_INITIAL);
		actionCountMax = props.getIntProperty(ACTION_COUNT_MAX);
		stateCountMax = props.getIntProperty(STATE_COUNT_MAX);
		//trialCount = props.getIntProperty(TRIAL_COUNT);
		// Allow for 5 extra trials to allow the agent to try every possible combination of actions from each state 
		// and then performing 5 perfect trials, at which point we declare the environment as solved by the agent.
		trialCount = stateCount * actionCount + 5;
		logger.info("RLStateBased trial count: " + trialCount);
		environments = new Environment[environmentCount];
		for (int e = 0; e < environments.length; e++) {
			environments[e] = new Environment();
			environments[e].setUp();
		}
		
		((Properties) props).getEvolver().addEventListener(this);
	}
	
	public void initialiseEvaluation() {
		// Create (some) new environments every generation.
		for (Environment e : environments) {
			if (environmentReplaceProb > random.nextDouble()) {
				e.setUp();
			}
		}
	}
	
	@Override
	public int getMaxFitnessValue() {
		return 1000000;
	}
	
	@Override
	protected int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		try {
			return evaluate(genotype, substrate, evalThreadIndex, null);
		} catch (IOException e) {
			// IOException is only for writing to log, which is null in this invocation, so shouldn't ever get here...
			return 0;
		}
	}
	
	protected int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, NiceWriter logOutput) throws IOException {
		// Either the substrate input array has length equal to the maximum state count + the maximum action count + 1,
		// or the current state count + current action count + 1.
		int inputActionOffset = stateCount; // Initialise with current state + action count.
		int inputRewardOffset = stateCount + actionCount;
		// If using max state count + max action count + 1.
		if (stateCountMax + actionCountMax + 1 == (substrate.getInputDimension()[0])) {
			inputActionOffset = stateCountMax;
			inputRewardOffset = stateCountMax + actionCountMax;
		}
		double[] input = new double[substrate.getInputDimension()[0]];
		
		double reward = 0;
		double[] avgRewardForEachTrial = new double[trialCount];
		int solvedCount = 0;
		for (Environment env : environments) {
			substrate.reset();
			double envReward = 0;
			double trialReward = 0;
			int consecutivePerfectTrialCount = 0;
			for (int trial = 0; trial < trialCount; trial++) {
				State currentState = env.states[0];
				int previousAction = 0;
				boolean[] stateVisited = new boolean[stateCount];
				stateVisited[currentState.id] = true;
				trialReward = 0;
				
				// The network can perform a number of steps equal to the number of states minus the start state.
				// The challenge is to find a sequence of actions (of which there is at least one) to visit all 
				// the states containing a reward.
				for (int step = 0; step < stateCount-1; step++) {
					// Set up the inputs to the network.
					Arrays.fill(input, 0);
					input[currentState.id] = 1; // Current state.
					input[inputActionOffset + previousAction] = 1; // Previously performed action.
					if (!stateVisited[currentState.id]) { // The reward from each state can only be counted once.
						input[inputRewardOffset] = currentState.reward; // Reward from previous action and resultant state.
						trialReward += currentState.reward;
					}
										
					stateVisited[currentState.id] = true;
					
					// Ask the network what it wants to do next, and let it know the reward for the current state.
					double[] output = substrate.next(input);
					
					// If the maximum reward has been reached, end the trial.
					if (trialReward == 1) {
						break;
					}
					
					// The action to perform is the one corresponding to the output with the highest output value.
					int action = ArrayUtil.getMaxIndex(output);
					// If a new state is defined for the current state and specified action.
					if (currentState.actionStateMap[action] != null) {
						currentState = currentState.actionStateMap[action];
					}
				}
				
				// If 5 perfect trials have been executed then we can probably assume this environment has been mastered.
				if (trialReward > 0.999) {
					consecutivePerfectTrialCount++;
					if (consecutivePerfectTrialCount == 5) {
						solvedCount++;
						// Fill in values for trials we're skipping.
						for (int t = trial; t < trialCount; t++) {
							avgRewardForEachTrial[trial] += trialReward;
							envReward += trialReward;
						}
						break; // breaks out of for (int trial = 0; trial < trialCount; trial++)
					}
				}
				else {
					consecutivePerfectTrialCount = 0;
				}
				envReward += trialReward;
				
				avgRewardForEachTrial[trial] += trialReward;
			}
			reward += envReward / trialCount;
		}
		
		if (logOutput != null) {
			for (int trial = 0; trial < trialCount; trial++) {
				avgRewardForEachTrial[trial] /= environmentCount;
			}
			logOutput.put(Arrays.toString(avgRewardForEachTrial));
		}
		
		genotype.setPerformanceValue((double) solvedCount / environmentCount);
		reward /= environmentCount;
		return (int) Math.round(getMaxFitnessValue() * reward);
	}
	
	private boolean increaseDifficulty() {
		boolean increasedDifficulty = false;
		if (stateCount < props.getIntProperty(STATE_COUNT_MAX)) {
			String deltaString = props.getProperty(STATE_COUNT_INCREASE_DELTA).trim().toLowerCase();
			boolean isFactor = deltaString.endsWith("x");
			double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
			if (delta >= 1) {
				if (!isFactor) {
					stateCount += (int) Math.round(delta);
				}
				else if (delta > 1) {
					stateCount = Math.max(1, (int) Math.round(stateCount * delta));
				}
			}
			increasedDifficulty = true;
		}
		
		if (actionCount < props.getIntProperty(ACTION_COUNT_MAX)) {
			String deltaString = props.getProperty(ACTION_COUNT_INCREASE_DELTA).trim().toLowerCase();
			boolean isFactor = deltaString.endsWith("x");
			double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
			if (delta >= 1) {
				if (!isFactor) {
					actionCount += (int) Math.round(delta);
				}
				else if (delta > 1) {
					actionCount = Math.max(1, (int) Math.round(actionCount * delta));
				}
			}
			increasedDifficulty = true;
		}
		return increasedDifficulty;
	}
	
	private class Environment {
		private State[] states;
		
		private void setUp() {
			// Generate random reward value for each state.
			double[] rewards = ArrayUtil.newRandom(stateCount, random);
			rewards[0] = 0; // First state has no reward.
			// Optionally only allow some proportion of states to have a reward.
			// Subtract 2 as the first state never has a reward and we always want at least one state to have a reward.
			int noRewardStateCount = stateCount - (int) Math.round((stateCount-2) * props.getDoubleProperty(REWARD_STATE_RATIO))-2;
			if (noRewardStateCount > 0) {
				for (int s = 1; s <= noRewardStateCount; s++) {
					rewards[s] = 0;
				}
			}
			// Total reward over all states equals 1.
			ArrayUtil.normaliseSum(rewards);
			
			// Create states.
			states = new State[stateCount];
			for (int s = 0; s < stateCount; s++) {
				states[s] = new State(s, rewards[s], false);
			}
			
			// Create a random path visiting all states. This ensures the environment is solvable.
			List<Integer> order = new ArrayList<Integer>(stateCount);
			for (int i = 1; i < stateCount; i++) {
				order.add(i);
			}
			Collections.shuffle(order, random);
			order.add(0, 0);
			for (int i = 0; i < stateCount; i++) {
				states[order.get(i)].actionStateMap[random.nextInt(actionCount)] = states[order.get((i+1)%stateCount)];
			}
			
			// Add some extra random state transitions.
			double mapRatio = props.getDoubleProperty(ACTION_MAP_RATIO);
			// Account for already-assigned action for path created above.
			// (We want (actionCount-1) * mapRatio == 1, as we iterate over all possible actions minus 1 below).
			mapRatio = (mapRatio * actionCount - 1) / (actionCount - 1);
			for (int state = 0; state < stateCount; state++) {
				State[] map = states[state].actionStateMap;
				// For each action (minus the one assigned for the path created above).
				for (int a = 0; a < actionCount-1; a++) {
					// If a mapping should be created.
					if (mapRatio > random.nextDouble()) {
						// Randomly select an unmapped action.
						int action = random.nextInt(actionCount);
						while (map[action] != null) action = random.nextInt(actionCount);
						// Randomly select a state other than this one.
						int nextState = random.nextInt(stateCount);
						while (nextState == state) nextState = random.nextInt(stateCount);
						map[action] = states[nextState];
					}
				}
			}
			
			//System.err.println(toString());
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int s = 0; s < stateCount; s++) {
				sb.append(s + " (" + (float) states[s].reward + ") => ");
				for (int a = 0; a < actionCount; a++) {
					sb.append((states[s].actionStateMap[a] == null ? "-" : states[s].actionStateMap[a].id) + ", ");
				}
				sb.append("\n");
			}
			return sb.toString();
		}
	}
	
	private class State {
		private int id;
		private State[] actionStateMap; // Map from actions performed in this state to other states.
		private double reward;
		
		public State(int id, double reward, boolean isFinish) {
			this.id = id;
			this.reward = reward;
			actionStateMap = new State[actionCount];
		}
	}


	@Override
	public void ahniEventOccurred(AHNIEvent event) {
		if (event.getType() == AHNIEvent.Type.GENERATION_END) {
			Chromosome bestPerforming = event.getEvolver().getBestPerformingFromLastGen();
			if (bestPerforming.getPerformanceValue() >= props.getDoubleProperty(DIFFICULTY_INCREASE_PERFORMANCE)) {
				if (increaseDifficulty())
					logger.info("Increased difficulty. State/action counts are now " + stateCount + " / " + actionCount);
			}
		}
	}
	
	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) // Input layer.
			// Current state plus previously performed action plus reinforcement signal.
			return new int[]{stateCountMax + actionCountMax + 1, 1};
		else if (layer == totalLayerCount - 1) // Output layer.
			// Action to perform next.
			return new int[]{actionCountMax, 1};
		return null;
	}
	
	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[stateCountMax + actionCountMax + 1];
			// Current state.
			for (int i = 0; i < stateCountMax; i++) {
				// Horizontal along bottom. 
				positions[i] = new Point((double) i / (stateCountMax - 1), 0, 0);
			}
			// Previously performed action.
			for (int i = 0; i < actionCountMax; i++) {
				// Horizontal along middle.
				positions[stateCountMax + i] = new Point((double) i / (actionCountMax - 1), 0.5, 0);
			}
			// Reinforcement signal, at top-middle.
			positions[stateCountMax + actionCountMax] = new Point(0.5, 1, 0);
		}
		else if (layer == totalLayerCount - 1) { // Output layer.
			positions = new Point[actionCountMax];
			// Action to perform next.
			for (int i = 0; i < actionCountMax; i++) {
				// Horizontal along middle.
				positions[i] = new Point((double) i / (actionCountMax - 1), 0.5, 1);
			}
		}
		return positions;
	}
}
