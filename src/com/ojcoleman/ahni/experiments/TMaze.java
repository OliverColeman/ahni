package com.ojcoleman.ahni.experiments;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.TranscriberException;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.event.AHNIEvent;
import com.ojcoleman.ahni.event.AHNIEventListener;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.NiceWriter;
import com.ojcoleman.ahni.util.Point;

/**
 * <p>
 * Implements the T-maze navigation task, for example see: Sebastian Risi and Kenneth O. Stanley (2010) Indirectly
 * Encoding Neural Plasticity as a Pattern of Local Rules. In: Proceedings of the 11th International Conference on
 * Simulation of Adaptive Behavior (SAB 2010).
 * </p>
 * <p>
 * The input and output formats can be set to match the various schemes used by previous authors, and either a single or
 * double T-maze can be specified.
 * </p>
 */
public class TMaze extends BulkFitnessFunctionMT {
	private static final long serialVersionUID = 1L;
	private static final NumberFormat nf = new DecimalFormat("0.00");
	private static Logger logger = Logger.getLogger(TMaze.class);

	/**
	 * The total number of trials to evaluate an agent over.
	 */
	public static final String TRIAL_COUNT = "fitness.function.tmaze.trial.count";
	/**
	 * The total number of times to move the high value reward to a different maze end location during an evaluation.
	 */
	public static final String REWARD_SWITCH_COUNT = "fitness.function.tmaze.reward.switch.count";
	/**
	 * The variation in switch times (fraction of number of trials between switching).
	 */
	public static final String REWARD_SWITCH_VARIATION = "fitness.function.tmaze.reward.switch.variation";
	/**
	 * Reward value of low reward (used in fitness calculation).
	 */
	public static final String REWARD_LOW = "fitness.function.tmaze.reward.low";
	/**
	 * Reward value of high reward (used in fitness calculation).
	 */
	public static final String REWARD_HIGH = "fitness.function.tmaze.reward.high";
	/**
	 * "Colour" of low reward (reward signal input to agent).
	 */
	public static final String REWARD_LOW_COLOUR = "fitness.function.tmaze.reward.low.colour";
	/**
	 * "Colour" of high reward (reward signal input to agent).
	 */
	public static final String REWARD_HIGH_COLOUR = "fitness.function.tmaze.reward.high.colour";
	/**
	 * Reward value given upon crashing into a wall (used in fitness calculation).
	 */
	public static final String REWARD_CRASH = "fitness.function.tmaze.reward.crash";
	/**
	 * Reward value given upon failing to return home (used in fitness calculation). If this is set to 0
	 * then the agent is not required to return home.
	 */
	public static final String REWARD_FAIL_RETURN_HOME = "fitness.function.tmaze.reward.failreturnhome";
	/**
	 * Length of passages of maze.
	 */
	public static final String PASSAGE_LENGTH = "fitness.function.tmaze.passage.length";
	/**
	 * Set to "true" to specify a double T-maze.
	 */
	public static final String DOUBLE_TMAZE = "fitness.function.tmaze.double";
	/**
	 * Set to "range" to use range-finder type inputs indicating if walls are present to the left, right and forward,
	 * and a reward input. Set to "features" to use inputs that indicate the following conditions: turn required; maze
	 * end reached; home position reached; reward.
	 */
	public static final String INPUT_TYPE = "fitness.function.tmaze.input.type";
	/**
	 * Set to "single" to use a single output to indicate the action to take next. Set to "multiple" to use three
	 * outputs to indicate which action to take next.
	 */
	public static final String OUTPUT_TYPE = "fitness.function.tmaze.output.type";

	private enum InputType {
		RANGE, FEATURES
	};

	private enum OutputType {
		SINGLE, MULTIPLE
	};

	private boolean isDouble;
	private int trialCount, rewardSwitchCount, passageLength;
	private double rewardSwitchVariation;
	private double rewardLow, rewardHigh, rewardCrash, rewardFailReturnHome, rewardLowColour, rewardHighColour;
	private int[] rewardSwitchTrials, rewardIndexForSwitch;
	private boolean[][] map; // The map of the maze, true indicates passage, false indicates walls.
	private int startX, startY; // Initial location of agent in map.
	private int[] rewardLocationsX, rewardLocationsY;
	private InputType inputType;
	private OutputType outputType;

	@Override
	public void init(Properties props) {
		super.init(props);
		isDouble = props.getBooleanProperty(DOUBLE_TMAZE, false);
		trialCount = props.getIntProperty(TRIAL_COUNT, 200);
		rewardSwitchCount = props.getIntProperty(REWARD_SWITCH_COUNT, 3);
		rewardSwitchVariation = props.getDoubleProperty(REWARD_SWITCH_VARIATION, 0.2);
		passageLength = props.getIntProperty(PASSAGE_LENGTH, 3);
		rewardLow = props.getDoubleProperty(REWARD_LOW, 0.1);
		rewardHigh = props.getDoubleProperty(REWARD_HIGH, 1);
		rewardCrash = props.getDoubleProperty(REWARD_CRASH, -0.4);
		rewardFailReturnHome = props.getDoubleProperty(REWARD_FAIL_RETURN_HOME, -0.3);
		rewardLowColour = props.getDoubleProperty(REWARD_LOW_COLOUR, 0.2);
		rewardHighColour = props.getDoubleProperty(REWARD_HIGH_COLOUR, 1);
		inputType = props.getEnumProperty(INPUT_TYPE, InputType.class, InputType.FEATURES);
		outputType = props.getEnumProperty(OUTPUT_TYPE, OutputType.class, OutputType.SINGLE);

		float switchTrials = (float) trialCount / (rewardSwitchCount + 1);
		int randomRange = (int) Math.round(switchTrials * rewardSwitchVariation);
		logger.info("Reward switch randomisation range is " + randomRange);

		// Set-up the map.
		if (!isDouble) {
			map = new boolean[7 + passageLength * 2][6 + passageLength];
			// Create passage starting from bottom of T.
			int x = 3 + passageLength;
			int y;
			for (y = 2; y <= 3 + passageLength; y++) {
				map[x][y] = true;
			}
			// Create passage starting from left arm to right arm.
			y = 3 + passageLength;
			for (x = 2; x <= 4 + passageLength * 2; x++) {
				map[x][y] = true;
			}

			rewardLocationsX = new int[] { 2, 4 + passageLength * 2 };
			rewardLocationsY = new int[] { y, y };
		} else {
			int extent = 7 + passageLength * 2;
			map = new boolean[extent][extent];
			// Create passage starting from bottom of T.
			int x = 3 + passageLength;
			int y;
			for (y = 2; y <= 3 + passageLength; y++) {
				map[x][y] = true;
			}
			// Create passage starting from left arm to right arm.
			y = 3 + passageLength;
			for (x = 2; x <= 4 + passageLength * 2; x++) {
				map[x][y] = true;
			}
			// Create final passages at ends of first T.
			for (y = 2; y <= 4 + passageLength * 2; y++) {
				map[2][y] = true;
				map[4 + passageLength * 2][y] = true;
			}

			rewardLocationsX = new int[] { 2, 2, extent - 3, extent - 3 };
			rewardLocationsY = new int[] { 2, extent - 3, 2, extent - 3 };
		}

		/*
		 * for (int y = 0; y < map[0].length; y++) { for (int x = 0; x < map.length; x++) { System.out.print(map[x][y] +
		 * " "); } System.out.println(); }
		 */

		// Agent starting location.
		startX = 3 + passageLength;
		startY = 2;
	}

	@Override
	public void initialiseEvaluation() {
		// Set-up when reward switches should occur for this set of trials.
		rewardSwitchTrials = new int[rewardSwitchCount];
		List<Integer> rewardIndexForSwitchList = new ArrayList<Integer>(rewardSwitchCount + 1);
		float switchTrials = (float) trialCount / (rewardSwitchCount + 1);
		int randomRange = (int) Math.round(switchTrials * rewardSwitchVariation);
		rewardIndexForSwitchList.add(0);
		for (int i = 0; i < rewardSwitchCount; i++) {
			rewardSwitchTrials[i] = Math.round(switchTrials * (i + 1));
			if (randomRange > 0) rewardSwitchTrials[i] += random.nextInt(randomRange + 1) * 2 - randomRange;
			rewardIndexForSwitchList.add((i + 1) % rewardLocationsX.length);
		}
		if (rewardSwitchVariation > 0) {
			Collections.shuffle(rewardIndexForSwitchList, random);
		}
		rewardIndexForSwitch = new int[rewardSwitchCount + 1];
		for (int i = 0; i < rewardSwitchCount + 1; i++)
			rewardIndexForSwitch[i] = rewardIndexForSwitchList.get(i);
	}

	@Override
	protected double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return _evaluate(genotype, substrate, null, false, false);
	}

	@Override
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		_evaluate(genotype, substrate, baseFileName, logText, logImage);
	}	
		
	
	public double _evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		try {
			NiceWriter logOutput = !logText ? null : new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
			StringBuilder logSummary = logOutput == null ? null : new StringBuilder();
			
			if (logText) {
				logOutput.put("Map:\n");
				logOutput.put(map).put("\n");
			}
	
			int rewardSwitchTrialsIndex = 0;
			int rewardHighIndex = rewardIndexForSwitch[0];
			double[] input = new double[4];
			int[] walls = new int[4];
			double reward = 0;
			int correctTrialCount = 0, highRewardCount = 0, lowRewardCount = 0, crashCount = 0, failReturnHomeCount = 0;
			int maxSteps = isDouble ? passageLength * 6 + 12 : passageLength * 4 + 8;
			if (rewardFailReturnHome == 0) { // If returning home is not required.
				maxSteps = isDouble ? passageLength * 3 + 5 : passageLength * 2 + 3;
			}
			for (int trial = 0; trial < trialCount; trial++) {
				if (logText) {
					logOutput.put("\n=== BEGIN TRIAL " + trial + "===\n");
					logSummary.append("Trial " + trial + "\n");
				}
	
				// If we should switch reward locations now.
				if (rewardSwitchTrialsIndex < rewardSwitchTrials.length && trial == rewardSwitchTrials[rewardSwitchTrialsIndex]) {
					rewardSwitchTrialsIndex++;
					rewardHighIndex = rewardIndexForSwitch[rewardSwitchTrialsIndex];
				}
				if (logText)
					logOutput.put("Reward is at " + rewardLocationsX[rewardHighIndex] + ", " + rewardLocationsY[rewardHighIndex] + "\n");
	
				int agentX = startX;
				int agentY = startY;
				int direction = 0; // 0 = up, 1 = right, 2 = down, 3 = left.
				boolean collectedReward = false, collectedHighReward = false;
				boolean finished = false;
				int step = 0;
				double trialReward = 0;
				int action = 0;
	
				while (!finished) {
					Arrays.fill(input, 0);
					if (!collectedReward && atReward(agentX, agentY)) {
						if (agentX == rewardLocationsX[rewardHighIndex] && agentY == rewardLocationsY[rewardHighIndex]) {
							trialReward += rewardHigh;
							input[3] = rewardHighColour;
							collectedHighReward = true;
							highRewardCount++;
							if (logSummary != null) logSummary.append("\tHigh reward collected.\n");
						} else {
							trialReward += rewardLow;
							input[3] = rewardLowColour;
							lowRewardCount++;
							if (logSummary != null) logSummary.append("\tLow reward collected.\n");
						}
						collectedReward = true;
						if (rewardFailReturnHome == 0) { // If returning home is not required.
							finished = true;
							if (rewardLowColour == rewardHighColour || collectedHighReward)
								correctTrialCount++;
						}
					} else if (collectedReward && agentX == startX && agentY == startY) {
						// If collected reward and returned home.
						finished = true;
						if (rewardLowColour == rewardHighColour || collectedHighReward)
							correctTrialCount++;
						if (logSummary != null) logSummary.append("\tReturned home.\n");
					} else if (!map[agentX][agentY]) { // If it's hit a wall.
						trialReward += rewardCrash;
						finished = true;
						crashCount++;
						if (logSummary != null) logSummary.append("\tCrashed.\n");
					} else if (step >= maxSteps) { // If it's taken too long then it's taken a wrong turn.
						trialReward += rewardFailReturnHome;
						finished = true;
						failReturnHomeCount++;
						if (logSummary != null) logSummary.append("\tFailed to return home, or took too long.\n");
					}
	
					// Detect walls in each direction. 1 indicates a wall, 0 passage.
					walls[0] = map[agentX - 1][agentY] ? 0 : 1; // left
					walls[1] = map[agentX][agentY + 1] ? 0 : 1; // up
					walls[2] = map[agentX + 1][agentY] ? 0 : 1; // right
					walls[3] = map[agentX][agentY - 1] ? 0 : 1; // down
	
					// If input type is range sensor.
					if (inputType == InputType.RANGE) {
						for (int i = 0; i < 3; i++)
							input[i] = walls[(direction + i) % 4];
					} 
					else { // input type is maze features.
						// Turn point (only one wall in any direction).
						if (ArrayUtil.sum(walls) == 1)
							input[0] = 1;
						// Maze end.
						else if (atReward(agentX, agentY))
							input[1] = 1;
						// Home (check collectedReward instead of step > 0?).
						else if (step > 0 && agentX == startX && agentY == startY)
							input[2] = 1;
					}
	
					// Ask the network what it wants to do next (and/or allow the agent to update itself given the reward
					// received).
					double[] output = substrate.next(input);
	
					// Determine action to perform.
					if (outputType == OutputType.MULTIPLE) {
						// The action to perform is the one corresponding to the output with the highest output value.
						action = ArrayUtil.getMaxIndex(output);
					} else {
						// The action to perform depends on the value of the single output.
						double o = output[0];
						if (substrate.getMinResponse() >= 0) { // if output function doesn't do negative values.
							o = (o * 2) - 1; // scale to [-1, 1]
						}
						action = (o < -0.3) ? 0 : ((o > 0.3) ? 2 : 1);
					}
	
					if (logText) {
						logOutput.put("Agent is at " + agentX + ", " + agentY + "\n");
						logOutput.put("\tInput: " + ArrayUtil.toString(input, ", ", nf) + "\n");
						logOutput.put("\tOutput: " + ArrayUtil.toString(output, ", ", nf) + "\n");
						logOutput.put("\tAction: " + action + "\n");
						logOutput.put("\tCurrent reward: " + trialReward + "  " + (finished ? "finished" : "") + "\n");
					}
	
					if (action == 1) { // Forward movement.
						if (direction == 0) { // Up
							agentY += 1;
						} else if (direction == 1) { // Right
							agentX += 1;
						} else if (direction == 2) { // Down
							agentY -= 1;
						} else if (direction == 3) { // Left
							agentX -= 1;
						}
					} else { // Turning.
						if (action == 0) { // Left turn.
							direction = direction == 0 ? 3 : direction - 1;
						} else { // Action == 2, right turn.
							direction = direction == 3 ? 0 : direction + 1;
						}
					}
					step++;
				}
				reward += trialReward;
			}
			
			
			reward /= trialCount;
			double minPossibleReward = Math.min(Math.min(rewardCrash, rewardFailReturnHome), 0);
			double possibleRewardRange = rewardHigh - minPossibleReward;
			double fitness = (reward - minPossibleReward) / possibleRewardRange;
			// Allow for numerical imprecision.
			if (fitness < 0 && fitness > -0.000001) {
				fitness = 0;
			}
			// Highest performance is reached when all trials are correct except those where the reward was moved and the agent had to explore all possible options.
			double performance = (double) correctTrialCount / (trialCount - ((rewardSwitchCount+1) * (isDouble ? 3 : 1)));
			if (performance > 1) performance = 1; // Sometimes there's a lucky one.
			
			if (logText) {
				logOutput.put("\n=== Summary ===\n" + logSummary);
				logOutput.put("\n=== Stats ===\nfitness: " + fitness + "\nperformance: " + performance + "\nhigh reward count: " + highRewardCount + "\nlow reward count: " + lowRewardCount + "\ncrash count: " + crashCount + "\nfail return home (or took too long) count: " + failReturnHomeCount + "\n");
				logOutput.close();
			}

			genotype.setPerformanceValue(performance);
			return fitness;
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	private boolean atReward(int agentX, int agentY) {
		for (int i = 0; i < rewardLocationsX.length; i++) {
			if (agentX == rewardLocationsX[i] && agentY == rewardLocationsY[i])
				return true;
		}
		return false;
	}

	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) // Input layer.
			// 3 range sensors plus reward.
			return new int[] { 4, 1 };
		else if (layer == totalLayerCount - 1) { // Output layer.
			if (outputType == OutputType.SINGLE)
				return new int[] { 1, 1 };
			return new int[] { 3, 1 }; // Action to perform next (left, forward, right).
		}
		return null;
	}

	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		// Coordinates are given in unit ranges and translated to whatever range is specified by the
		// experiment properties.
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[4];
			if (inputType == InputType.RANGE) {
				// Range sensors.
				for (int i = 0; i < 3; i++) {
					positions[i] = new Point((double) i / 2, 0, 0);
				}
				// "Colour/reward" signal.
				positions[3] = new Point(0.5, 1, 0);
			}
			else { // FEATURES
				positions[0] = new Point(0, 0, 0);
				positions[1] = new Point(0, 1, 0);
				positions[2] = new Point(1, 0, 0);
				positions[3] = new Point(1, 1, 0);
			}
		} else if (layer == totalLayerCount - 1) { // Output layer.
			if (outputType == OutputType.SINGLE) {
				positions = new Point[] { new Point(0.5, 0.5, 1) };
			} else {
				positions = new Point[3];
				// Action to perform next (left, forward, right).
				for (int i = 0; i < 3; i++) {
					positions[i] = new Point((double) i / 2, 0.5, 1);
				}
			}
		}
		return positions;
	}
}
