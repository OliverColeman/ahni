package ojc.ahni.experiments;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;

import ojc.ahni.evaluation.BulkFitnessFunctionMT;
import ojc.ahni.event.AHNIEvent;
import ojc.ahni.event.AHNIEventListener;
import ojc.ahni.hyperneat.Properties;
import ojc.ahni.util.ArrayUtil;
import ojc.ahni.util.NiceWriter;
import ojc.ahni.util.Point;
import sun.net.ProgressSource.State;

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
	//public static final String TRIAL_COUNT = "fitness.function.rlstate.trial.count";
	
	private Properties properties;
	
	private int environmentCount;
	private int trialCount;
	private int stateCount;
	private int actionCount;
	private int stateCountMax;
	private int actionCountMax;
	private Environment[] environments;

	@Override
	public void init(Properties props) {
		super.init(props);
		this.properties = props;
		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		actionCount = props.getIntProperty(ACTION_COUNT_INITIAL);
		stateCount = props.getIntProperty(STATE_COUNT_INITIAL);
		actionCountMax = props.getIntProperty(ACTION_COUNT_MAX);
		stateCountMax = props.getIntProperty(STATE_COUNT_MAX);
		//trialCount = props.getIntProperty(TRIAL_COUNT);
		trialCount = stateCount * actionCount;
		
		environments = new Environment[environmentCount];
		for (int e = 0; e < environments.length; e++) {
			environments[e] = new Environment();
		}
		
		((Properties) props).getEvolver().addEventListener(this);
	}
	
	public void initialiseEvaluation() {
		// Create new environments every generation.
		for (Environment e : environments) {
			e.setUp();
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
					
					// Ask the network what it wants to do next.
					double[] output = substrate.next(input);
					
					// The action to perform is the one corresponding to the output with the highest output value.
					int action = ArrayUtil.getMaxIndex(output);
					currentState = currentState.actionStateMap[action];
				}
				
				// If 5 perfect trials have been executed then we can probably assume this environment has been mastered.
				/*if (trialReward > 0.999) {
					consecutivePerfectTrialCount++;
					if (consecutivePerfectTrialCount == 5) {
						envReward += trialReward * (trialCount - trial);
						break;
					}
				}
				else {
					consecutivePerfectTrialCount = 0;
				}*/
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
			states = new State[stateCount];
			// Generate reward value for each state.
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
			for (int s = 0; s < stateCount; s++) {
				states[s] = new State(s, rewards[s], false);
			}
			boolean allRewardStatesVisitable = false;
			do {
				Deque<State> rewardStatesNotVisited = new ArrayDeque<State>();
				
				// Generate random action->state map for each state.
				double actionMapRatio = properties.getDoubleProperty(ACTION_MAP_RATIO);
				for (int s = 0; s < stateCount; s++) {
					for (int a = 0; a < actionCount; a++) {
						int nextState = actionMapRatio > random.nextDouble() ? random.nextInt(stateCount) : s;
						states[s].actionStateMap[a] = states[nextState];
					}
					if (rewards[s] > 0) 
						rewardStatesNotVisited.add(states[s]);
				}
				
				// Ensure all reward states are visitable from the start state.
				Set<State> visited = new HashSet<State>();
				Set<State> newlyVisited = new HashSet<State>();
				newlyVisited.add(states[0]);
				int stepCount = 0;
				while (visited.size() != stateCount && !rewardStatesNotVisited.isEmpty() && stepCount <= stateCount+1) {
					Set<State> newlyFound = new HashSet<State>();
					for (State s : newlyVisited) {
						for (State nextState : s.actionStateMap) {
							if (!visited.contains(nextState) && !newlyVisited.contains(nextState)) {
								newlyFound.add(nextState);
							}
						}
					}
					visited.addAll(newlyVisited);
					rewardStatesNotVisited.removeAll(newlyVisited);
					newlyVisited = newlyFound;
					stepCount++;
				}
				allRewardStatesVisitable = rewardStatesNotVisited.isEmpty();
			} while (!allRewardStatesVisitable);
			
			//System.out.println(toString());
			//System.out.println();
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int s = 0; s < stateCount; s++) {
				sb.append(s + " (" + (float) states[s].reward + ") => ");
				for (int a = 0; a < actionCount; a++) {
					sb.append(states[s].actionStateMap[a].id + ", ");
				}
				sb.append("\n");
			}
			return sb.toString();
		}
	}
	
	private class State {
		private int id;
		private State[] actionStateMap;
		private double reward;
		private boolean isFinish;
		
		public State(int id, double reward, boolean isFinish) {
			this.id = id;
			this.reward = reward;
			this.isFinish = isFinish;
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
				positions[i] = new Point((double) i / (stateCountMax - 1), 0, 0);
			}
			// Previously performed action.
			for (int i = 0; i < actionCountMax; i++) {
				positions[stateCountMax + i] = new Point((double) i / (actionCountMax - 1), 0.5, 0);
			}
			// Reinforcement signal.
			positions[stateCountMax + actionCountMax] = new Point(0.5, 1, 0);
		}
		else if (layer == totalLayerCount - 1) { // Output layer.
			positions = new Point[actionCountMax];
			// Action to perform next.
			for (int i = 0; i < actionCountMax; i++) {
				positions[i] = new Point((double) i / (actionCountMax - 1), 0.5, 1);
			}
		}
		return positions;
	}
}
