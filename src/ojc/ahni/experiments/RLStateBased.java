package ojc.ahni.experiments;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.util.Properties;

import ojc.ahni.evaluation.BulkFitnessFunctionMT;
import ojc.ahni.event.AHNIEvent;
import ojc.ahni.event.AHNIEventListener;
import ojc.ahni.event.AHNIRunProperties;
import ojc.ahni.util.ArrayUtil;
import ojc.ahni.util.Point;

public class RLStateBased extends BulkFitnessFunctionMT implements AHNIEventListener {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(BulkFitnessFunctionMT.class);
	
	public static final String STATE_COUNT_INITIAL = "fitness.function.rlstate.states.initial";
	public static final String STATE_COUNT_INCREASE_DELTA = "fitness.function.rlstate.states.delta";
	public static final String STATE_COUNT_MAX = "fitness.function.rlstate.states.maximum";
	public static final String ACTION_COUNT_INITIAL = "fitness.function.rlstate.actions.initial";
	public static final String ACTION_COUNT_INCREASE_DELTA = "fitness.function.rlstate.actions.delta";
	public static final String ACTION_COUNT_MAX = "fitness.function.rlstate.actions.maximum";
	public static final String DIFFICULTY_INCREASE_PERFORMANCE = "fitness.function.rlstate.difficulty.increase.performance";
	public static final String ACTION_MAP_RATIO = "fitness.function.rlstate.action.map.ratio";
	public static final String ENVIRONMENT_COUNT = "fitness.function.rlstate.environment.count";
	public static final String TRIAL_COUNT = "fitness.function.rlstate.trial.count";
	
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
		this.properties = props;
		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		trialCount = props.getIntProperty(TRIAL_COUNT);
		actionCount = props.getIntProperty(ACTION_COUNT_INITIAL);
		stateCount = props.getIntProperty(STATE_COUNT_INITIAL);
		actionCountMax = props.getIntProperty(ACTION_COUNT_MAX);
		stateCountMax = props.getIntProperty(STATE_COUNT_MAX);
		
		environments = new Environment[environmentCount];
		for (int e = 0; e < environments.length; e++) {
			environments[e] = new Environment();
		}
		
		((AHNIRunProperties) props).getEvolver().addEventListener(this);
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
		// Either the substrate input array has length equal to the maximum state count + the maximum action count + 1,
		// or the current state count + current action count + 1.
		int inputActionOffset = stateCount; // Assume current state + action count
		int inputRewardOffset = stateCount + actionCount;
		if (stateCountMax + actionCountMax + 1 == (substrate.getInputDimension()[0])) {
			inputActionOffset = stateCountMax;
			inputRewardOffset = stateCountMax + actionCountMax;
		}
		double[] input = new double[substrate.getInputDimension()[0]];
		
		double reward = 0;
		for (Environment env : environments) {
			substrate.reset();
			for (int trial = 0; trial < trialCount; trial++) {
				State currentState = env.states[0];
				int previousAction = 0;
				boolean[] stateVisited = new boolean[stateCount];
				stateVisited[currentState.id] = true;
				
				// The network can perform a number of steps equal to the number of states minus the start state.
				for (int step = 0; step < stateCount-1; step++) {
					// Set up the inputs to the network.
					Arrays.fill(input, 0);
					input[currentState.id] = 1; // Current state.
					input[inputActionOffset + previousAction] = 1; // Previously performed action.
					if (!stateVisited[currentState.id]) { // The reward from each state can only be counted once.
						input[inputRewardOffset] = currentState.reward; // Reward from previous action and resultant state.
						reward += currentState.reward;
					}
					stateVisited[currentState.id] = true;
					
					// Ask the network what it wants to do next.
					double[] output = substrate.next(input);
					
					// The action to perform is the one corresponding to the output with the highest output value.
					int action = ArrayUtil.getMaxIndex(output);
					currentState = currentState.actionStateMap[action];
				}
			}
		}
		return (int) Math.round(getMaxFitnessValue() * (reward / (environmentCount * trialCount)));
	}
	
	private void increaseDifficulty() {
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
		}
	}
	
	private class Environment {
		private State[] states;
		
		private void setUp() {
			states = new State[stateCount];
			// Generate reward value for each state. Total reward over all states equals 1.
			double[] rewards = ArrayUtil.newRandom(stateCount, random);
			rewards[0] = 0; // First state has no reward.
			ArrayUtil.normaliseSum(rewards);
			for (int s = 0; s < stateCount; s++) {
				states[s] = new State(s, rewards[s], false);
			}
			
			// Generate random action->state map for each state.
			double actionMapRatio = properties.getDoubleProperty(ACTION_MAP_RATIO);
			for (int s = 0; s < stateCount; s++) {
				for (int a = 0; a < actionCount; a++) {
					int ns = actionMapRatio > random.nextDouble() ? random.nextInt(stateCount) : s;
					states[s].actionStateMap[a] = states[ns];
				}
			}
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
				increaseDifficulty();
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
