package ojc.ahni.experiments;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.TranscriberException;

import ojc.ahni.evaluation.BulkFitnessFunctionMT;
import ojc.ahni.event.AHNIEvent;
import ojc.ahni.event.AHNIEventListener;
import ojc.ahni.hyperneat.HyperNEATConfiguration;
import ojc.ahni.hyperneat.HyperNEATEvolver;
import ojc.ahni.hyperneat.Properties;
import ojc.ahni.nn.BainNN;
import ojc.ahni.util.ArrayUtil;
import ojc.ahni.util.NiceWriter;
import ojc.ahni.util.Point;
import ojc.bain.neuron.rate.NeuronCollectionWithBias;

/**
 * Implements the T-maze navigation task similar to that described by: Sebastian Risi and Kenneth O. Stanley (2010) Indirectly Encoding Neural Plasticity as a
 * Pattern of Local Rules. In: Proceedings of the 11th International Conference o nSimulation of Adaptive Behavior (SAB 2010). Only the first T-maze scenario is
 * implemented.
 */
public class TMaze extends BulkFitnessFunctionMT implements AHNIEventListener {
	private static final long serialVersionUID = 1L;
	private static final NumberFormat nf = new DecimalFormat("0.00");
	private static Logger logger = Logger.getLogger(TMaze.class);
	
	public static final String TRIAL_COUNT = "fitness.function.tmaze.trial.count";
	public static final String REWARD_SWITCH_COUNT = "fitness.function.tmaze.reward.switch.count";
	public static final String REWARD_SWITCH_VARIATION = "fitness.function.tmaze.reward.switch.variation";
	public static final String REWARD_LOW = "fitness.function.tmaze.reward.low";
	public static final String REWARD_HIGH = "fitness.function.tmaze.reward.high";
	public static final String REWARD_LOW_COLOUR = "fitness.function.tmaze.reward.low.colour";
	public static final String REWARD_HIGH_COLOUR = "fitness.function.tmaze.reward.high.colour";
	public static final String REWARD_CRASH = "fitness.function.tmaze.reward.crash";
	public static final String PASSAGE_LENGTH = "fitness.function.tmaze.passage.length";
	public static final String DOUBLE_TMAZE = "fitness.function.tmaze.double";
	
	private static final byte MAP_WALL = 0;
	private static final byte MAP_PASSAGE = 1;
	
	private boolean isDouble;
	private int trialCount, rewardSwitchCount, passageLength;
	private double rewardSwitchVariation;
	private double rewardLow, rewardHigh, rewardCrash, rewardLowColour, rewardHighColour;
	private int[] rewardSwitchTrials, rewardIndexForSwitch;
	private byte[][] map; // The map of the maze, consists of values from TYPE_*, format is [x][y].
	private int startX, startY; // Initial location of agent in map.
	private int[] rewardLocationsX, rewardLocationsY;

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
		rewardCrash = props.getDoubleProperty(REWARD_CRASH, 0);
		rewardLowColour = props.getDoubleProperty(REWARD_LOW_COLOUR, 0.2);
		rewardHighColour = props.getDoubleProperty(REWARD_HIGH_COLOUR, 1);
		
		float switchTrials = (float) trialCount / (rewardSwitchCount + 1);
		int randomRange = (int) Math.round(switchTrials * rewardSwitchVariation);
		logger.info("Reward switch randomisation range is " + randomRange);
		
		// Set-up the map.
		if (!isDouble) {
			map = new byte[7 + passageLength * 2][6 + passageLength];
			// Create passage starting from bottom of T.
			int x = 3 + passageLength;
			int y;
			for (y = 2; y <= 3 + passageLength; y++) {
				map[x][y] = MAP_PASSAGE;
			}
			// Create passage starting from left arm to right arm.
			y = 3 + passageLength;
			for (x = 2; x <= 4 + passageLength * 2; x++) {
				map[x][y] = MAP_PASSAGE;
			}
			
			rewardLocationsX = new int[]{2, 4 + passageLength * 2};
			rewardLocationsY = new int[]{y, y};
		}
		else {
			int extent = 7 + passageLength * 2;
			map = new byte[extent][extent];
			// Create passage starting from bottom of T.
			int x = 3 + passageLength;
			int y;
			for (y = 2; y <= 3 + passageLength; y++) {
				map[x][y] = MAP_PASSAGE;
			}
			// Create passage starting from left arm to right arm.
			y = 3 + passageLength;
			for (x = 2; x <= 4 + passageLength * 2; x++) {
				map[x][y] = MAP_PASSAGE;
			}
			// Create final passages at ends of first T.
			for (y = 2; y <= 4 + passageLength * 2; y++) {
				map[2][y] = MAP_PASSAGE;
				map[4 + passageLength * 2][y] = MAP_PASSAGE;
			}
			
			rewardLocationsX = new int[]{2, 2, extent-3, extent-3};
			rewardLocationsY = new int[]{2, extent-3, 2, extent-3};
		}
		
		/*for (int y = 0; y < map[0].length; y++) {
			for (int x = 0; x < map.length; x++) {
				System.out.print(map[x][y] + " ");
			}
			System.out.println();
		}*/
		
		// Agent starting location.
		startX = 3 + passageLength;
		startY = 2;
		
		if (props.logFilesEnabled()) {
			props.getEvolver().addEventListener(this);
		}
	}
	
	public void initialiseEvaluation() {
		// Set-up when reward switches should occur for this set of trials.
		rewardSwitchTrials = new int[rewardSwitchCount];
		List<Integer> rewardIndexForSwitchList = new ArrayList<Integer>(rewardSwitchCount+1);
		float switchTrials = (float) trialCount / (rewardSwitchCount + 1);
		int randomRange = (int) Math.round(switchTrials * rewardSwitchVariation);
		rewardIndexForSwitchList.add(0);
		for (int i = 0; i < rewardSwitchCount; i++) {
			rewardSwitchTrials[i] = Math.round(switchTrials * (i+1) + random.nextInt(randomRange+1) * 2 - randomRange);
			rewardIndexForSwitchList.add((i + 1) % rewardLocationsX.length);
		}
		if (rewardSwitchVariation > 0) {
			Collections.shuffle(rewardIndexForSwitchList, random);
		}
		rewardIndexForSwitch = new int[rewardSwitchCount+1];
		for (int i = 0; i < rewardSwitchCount + 1; i++) rewardIndexForSwitch[i] = rewardIndexForSwitchList.get(i);
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
		if (logOutput != null) {
			logOutput.put("Map:\n");
			logOutput.put(map).put("\n");
		}
		
		int rewardSwitchTrialsIndex = 0;
		int rewardIndex = rewardIndexForSwitch[0];
		double[] input = new double[4];
		int[] walls = new int[4];
		double reward = 0;
		int correctTrialCount = 0;
		int maxSteps = isDouble ? passageLength * 3 + 4 : passageLength * 2 + 2;
		for (int trial = 0; trial < trialCount; trial++) {
			if (logOutput != null) logOutput.put("\n=== BEGIN TRIAL " + trial + "===\n");
			
			// If we should switch reward locations now.
			if (rewardSwitchTrialsIndex < rewardSwitchTrials.length && trial == rewardSwitchTrials[rewardSwitchTrialsIndex]) {
				rewardSwitchTrialsIndex++;
				rewardIndex = rewardIndexForSwitch[rewardSwitchTrialsIndex];
			}
			if (logOutput != null) logOutput.put("Reward is at " + rewardLocationsX[rewardIndex] + ", " + rewardLocationsY[rewardIndex] + "\n");
			
			int agentX = startX;
			int agentY = startY;
			int direction = 0; // 0 = up, 1 = right, 2 = down, 3 = left.
			boolean finished = false;
			int step = 0;
			double trialReward = 0;
			
			while (!finished) {
				input[3] = 0;
				if (atReward(agentX, agentY)) {
					if (agentX == rewardLocationsX[rewardIndex] && agentY == rewardLocationsY[rewardIndex]) {
						trialReward = rewardHigh;
						input[3] = rewardHighColour;
						correctTrialCount++;
					}
					else {
						trialReward = rewardLow;
						input[3] = rewardLowColour;
					}
					finished = true;
				}
				else if (map[agentX][agentY] == MAP_WALL || step > maxSteps) {
					trialReward = rewardCrash;
					finished = true;
				}
								
				// Detect walls in each direction.
				walls[0] = map[agentX - 1][agentY] == MAP_WALL ? 1 : 0; //left
				walls[1] = map[agentX][agentY + 1] == MAP_WALL ? 1 : 0; //up
				walls[2] = map[agentX + 1][agentY] == MAP_WALL ? 1 : 0; //right
				walls[3] = map[agentX][agentY - 1] == MAP_WALL ? 1 : 0; //down
				
				// Set up the range sensor inputs to the network.
				input[0] = walls[direction % 4];
				input[1] = walls[(direction + 1) % 4];
				input[2] = walls[(direction + 2) % 4];
				
				// Ask the network what it wants to do next (and/or allow the agent to update itself given the reward received).
				double[] output = substrate.next(input);
				// The action to perform is the one corresponding to the output with the highest output value.
				int action = ArrayUtil.getMaxIndex(output);
				
				if (logOutput != null) {
					logOutput.put("Agent is at " + agentX + ", " + agentY + "\n");
					logOutput.put("\tInput: " + ArrayUtil.toString(input, ", ", nf) + "\n");
					logOutput.put("\tOutput: " + ArrayUtil.toString(output, ", ", nf) + "\n");
					logOutput.put("\tAction: " + action + "\n");
					logOutput.put("\tCurrent reward: " + trialReward + "  " + (finished ? "finished" : "") + "\n");
				}
												
				if (action == 1) { // Forward movement.
					if (direction == 0) { // Up
						agentY += 1;
					}
					else if (direction == 1) { // Right
						agentX += 1;
					}
					else if (direction == 2) { // Down
						agentY -= 1;
					}
					else if (direction == 3) { // Left
						agentX -= 1;
					}
				}
				else { // Turning.
					if (action == 0) { // Left turn.
						direction = direction == 0 ? 3 : direction - 1;
					}
					else { // Right turn.
						direction = direction == 3 ? 0 : direction + 1;
					}
				}
				step++;
			}
			reward += trialReward;
		}
		double minPossibleReward = trialCount * Math.min(rewardCrash, 0);
		double maxPossibleReward = trialCount * rewardHigh;
		double possibleRewardRange = maxPossibleReward - minPossibleReward;
		genotype.setPerformanceValue((double) correctTrialCount / trialCount);
		return (int) Math.round(getMaxFitnessValue() * ((reward - minPossibleReward) / possibleRewardRange));
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
			return new int[]{4, 1};
		else if (layer == totalLayerCount - 1) // Output layer.
			// Action to perform next (left, forward, right).
			return new int[]{3, 1};
		return null;
	}
	
	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		// Coordinates are given in unit ranges and translated to -1 to 1 (or whatever range is specified by the
		// experiment properties).
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[4];
			// Sensors.
			for (int i = 0; i < 3; i++) {
				positions[i] = new Point((double) i / 2, 0.25, 0);
			}
			// "Colour" signal.
			positions[3] = new Point(0.5, 0, 0);
		}
		else if (layer == totalLayerCount - 1) { // Output layer.
			positions = new Point[3];
			// Action to perform next (left, forward, right).
			for (int i = 0; i < 3; i++) {
				positions[i] = new Point((double) i / 2, 1, 0);
			}
		}
		return positions;
	}
	
	@Override
	public void ahniEventOccurred(AHNIEvent event) {
		if (event.getType() == AHNIEvent.Type.RUN_END) {
			HyperNEATEvolver evolver = event.getEvolver();
			try {
				Chromosome bestPerforming = evolver.getBestPerformingFromLastGen();
				NiceWriter outputfile = new NiceWriter(new FileWriter(props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "best_performing-final-evaluation-" + bestPerforming.getId() + ".txt"), "0.00");
				Activator substrate = generateSubstrate(bestPerforming, null);
				evaluate(bestPerforming, substrate, 0, outputfile);
				outputfile.close();
			} catch (TranscriberException e) {
				logger.error("Error transcribing chromosome for log of evaluation:  " + e);
			} catch (IOException e) {
				logger.error("Unable to write to log file: " + e);
			}
		}
	}
}
