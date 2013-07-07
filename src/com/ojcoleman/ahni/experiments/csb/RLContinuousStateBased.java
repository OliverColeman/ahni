package com.ojcoleman.ahni.experiments.csb;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.amd.aparapi.Kernel;
import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;
import com.ojcoleman.ahni.evaluation.novelty.NoveltySearch;
import com.ojcoleman.ahni.evaluation.novelty.RealVectorBehaviour;
import com.ojcoleman.ahni.event.AHNIEvent;
import com.ojcoleman.ahni.event.AHNIEventListener;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.ahni.nn.BainNN.Topology;
import com.ojcoleman.ahni.nn.NNAdaptor;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.NiceWriter;
import com.ojcoleman.ahni.util.Point;
import com.ojcoleman.bain.NeuralNetwork;
import com.ojcoleman.bain.base.ComponentConfiguration;
import com.ojcoleman.bain.base.NeuronCollection;
import com.ojcoleman.bain.base.SynapseCollection;
import com.ojcoleman.bain.neuron.rate.NeuronCollectionWithBias;
import com.ojcoleman.bain.neuron.rate.SigmoidBipolarNeuronCollection;
import com.ojcoleman.bain.neuron.rate.SigmoidNeuronCollection;
import com.ojcoleman.bain.neuron.rate.SigmoidNeuronConfiguration;
import com.ojcoleman.bain.synapse.rate.FixedSynapseCollection;

public class RLContinuousStateBased extends BulkFitnessFunctionMT implements AHNIEventListener {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(BulkFitnessFunctionMT.class);
	private static final NumberFormat nf = new DecimalFormat(" 0.00;-0.00");

	/**
	 * The number of variables defining an environment state.
	 */
	public static final String SIZE = "fitness.function.rlcss.size";
	/**
	 * The class implementing the environment to use. Must extend com.ojcoleman.ahni.experiments.csb.Environment. Defaults to {@link com.ojcoleman.ahni.experiments.csb.SimpleNavigationEnvironment}.
	 */
	public static final String ENVIRONMENT_CLASS = "fitness.function.rlcss.environment.class";
	/**
	 * The number of environments to evaluate candidates against. Increasing this will provide a more accurate
	 * evaluation but take longer.
	 */
	public static final String ENVIRONMENT_COUNT = "fitness.function.rlcss.environment.count";
	/**
	 * The fraction of environments that should be replaced with new environments per generation. This is evaluated
	 * probabilistically.
	 */
	public static final String ENVIRONMENT_CHANGE_RATE = "fitness.function.rlcss.environment.replacerate";
	/**
	 * The number of trials per environment.
	 */
	public static final String TRIAL_COUNT = "fitness.function.rlcss.trial.count";
	/**
	 * The performance indicating when the environment difficulty should be increased as the current difficulty has been
	 * sufficiently mastered.
	 */
	public static final String DIFFICULTY_INCREASE_PERFORMANCE = "fitness.function.rlcss.difficulty.increase.performance";
	
	/**
	 * If true enables novelty search. Default is false.
	 */
	public static final String NOVELTY_SEARCH = "fitness.function.rlcss.noveltysearch.enable";


	private int envSize;
	private int environmentCount;
	private double environmentReplaceProb;
	private int trialCount;
	private Environment[] environments;
	// Used to determine behaviour when environments used to determine fitness will be replaced over the course of 
	// evolution (meaning that there would be no constant behaviour metric over the course of evolution).
	private Environment[] nsEnvironments;
	private int environmentCounter = 0;
	private int[] environmentOrder; // evaluation order of environments
	protected boolean enableNoveltySearch;
	protected double difficultyIncreasePerformance = 0.99;
	protected double targetPerformance;
	
	// If novelty search enabled then record the environment state at 4 intervals (25%, 50%, 75%, 100%)
	private int behaviourRecordCount = 1;
	
	@Override
	public void init(Properties props) {
		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		
		envSize = props.getIntProperty(SIZE);
		environmentReplaceProb = props.getDoubleProperty(ENVIRONMENT_CHANGE_RATE);
		trialCount = props.getIntProperty(TRIAL_COUNT);
		difficultyIncreasePerformance = props.getDoubleProperty(DIFFICULTY_INCREASE_PERFORMANCE);
		targetPerformance = props.getDoubleProperty(HyperNEATEvolver.PERFORMANCE_TARGET_KEY);
		environments = new Environment[environmentCount];
		for (int e = 0; e < environments.length; e++) {
			environments[e] = props.newObjectProperty(props.getClassProperty(ENVIRONMENT_CLASS, SimpleNavigationEnvironment.class));
			environments[e].rlcss = this;
		}
		enableNoveltySearch = props.getBooleanProperty(NOVELTY_SEARCH, false);
		
		if (enableNoveltySearch && environmentReplaceProb > 0) {
			//throw new IllegalArgumentException("Can't enable novelty search and environment replacement.");
			
			// Create a set of environments that don't change over the course of evolution to test novelty on
			nsEnvironments = new Environment[environmentCount];
			for (int e = 0; e < environments.length; e++) {
				nsEnvironments[e] = props.newObjectProperty(props.getClassProperty(ENVIRONMENT_CLASS, SimpleNavigationEnvironment.class));
				nsEnvironments[e].rlcss = this;
				// If possible, generate environments of varying difficulty.
				for (int i = 1; i < e && nsEnvironments[e].increaseDifficultyPossible(); i++) {
					nsEnvironments[e].increaseDifficulty();
				}
			}
		}

		((Properties) props).getEvolver().addEventListener(this);
		
		super.init(props);
	}

	@Override
	public void initialiseEvaluation() {
		// If it's the first generation then initialise the environments.
		if (props.getEvolver().getGeneration() == 0) {
			for (int e = 0; e < environments.length; e++) {
				environments[e].setUp(environmentCounter++);			
			}
			if (nsEnvironments != null) {
				for (int e = 0; e < nsEnvironments.length; e++) {
					nsEnvironments[e].setUp(0);	
				}
			}
		}
		else { 
			// Create (some) new environments every generation.
			for (int e = 0; e < environments.length; e++) {
				if (environmentReplaceProb > random.nextDouble()) {
					environments[e].setUp(environmentCounter++);
				}
			}
		}
		
		environmentOrder = ArrayUtil.newRandomIndexing(environmentCount, random);
	}

	@Override
	protected double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return evaluate(genotype, substrate, null, false, false);
	}

	@Override
	public double evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		super.evaluate(genotype, substrate, baseFileName, logText, logImage);
		double reward = 0;
		NNAdaptor nn = (NNAdaptor) substrate;

		if (substrate.getInputCount() != environments[0].outputSize || substrate.getOutputCount() != environments[0].inputSize) {
			throw new IllegalArgumentException("Substrate input (or output) size does not match number of outputSize (inputSize) environment nodes.");
		}

		try {
			NiceWriter logOutput = !logText ? null : new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
			int imageSize = 256;

			double[] avgRewardForEachTrial = new double[trialCount];
			ArrayRealVector behaviour = enableNoveltySearch && nsEnvironments == null ? new ArrayRealVector(environmentCount * trialCount * envSize * behaviourRecordCount) : null;
			int behaviourIndex = 0;

			for (int ei = 0; ei < environmentCount; ei++) {
				int envIndex = environmentOrder[ei];
				Environment env = environments[envIndex];
				if (logText) {
					logOutput.put("\n\nBEGIN EVALUATION ON ENVIRONMENT " + env.id + "\n");

					NiceWriter logOutputEnv = new NiceWriter(new FileWriter(baseFileName + ".environment.txt"), "0.00");
					logOutputEnv.put(env);
					logOutputEnv.close();
				}

				// Reset substrate to initial state to begin learning (new) environment.
				substrate.reset();
				
				double envReward = 0;
				int noveltySearchStepsPerRecord = env.getMinimumStepsToSolve() / behaviourRecordCount;
				for (int trial = 0; trial < trialCount; trial++) {
					double trialReward = 0;
					double[] agentOutput = new double[env.inputSize];
					
					// Get initial state and environment output.
					ArrayRealVector state = env.startState.copy();
					double[] agentInput = new double[env.outputSize];
					env.getOutputForState(state, agentInput);
					
					BufferedImage image = null;
					Graphics2D g = null;
					if (logText) {
						logOutput.put("\n  BEGIN TRIAL " + (trial + 1) + " of " + trialCount + "\n");
						logOutput.put("    step, , state, , agent output, , agent input\n");
					}
					if (logImage && env.size == 2) {
						// Log environment state in 2D map.
						image = new BufferedImage(imageSize+2, imageSize+2, BufferedImage.TYPE_3BYTE_BGR);
						g = image.createGraphics();
						env.logToImage(g, imageSize);
						g.setColor(Color.YELLOW);
						g.drawOval((int) Math.round(env.startState.getEntry(0) * imageSize - 3.5), (int) Math.round(env.startState.getEntry(1) * imageSize - 3.5), 7, 7);
						g.setColor(Color.RED);
						g.drawRect((int) Math.round(env.goalState.getEntry(0) * imageSize - 2.5), (int) Math.round(env.goalState.getEntry(1) * imageSize - 2.5), 5, 5);
						g.setColor(Color.WHITE);
					}
					
					int behaviourRecordIndex = 0;
					
					for (int step = 0; step < env.getMinimumStepsToSolve(); step++) {
						if (logText) {
							logOutput.put("    " + step + ", , ");
							for (int i = 0; i < env.size; i++) {
								logOutput.put(nf.format(state.getEntry(i)) + ", ");
							}
							logOutput.put(", ");
							for (int i = 0; i < env.inputSize; i++) {
								logOutput.put(nf.format(agentOutput[i]) + ", ");
							}
							logOutput.put(", ");
							for (int i = 0; i < env.outputSize; i++) {
								logOutput.put(nf.format(agentInput[i]) + ", ");
							}
							logOutput.put("\n");
						}	
						if (logImage && env.size == 2) {
							int x = (int) Math.round(state.getEntry(0) * imageSize-0.5);
							int y = (int) Math.round(state.getEntry(1) * imageSize-0.5);
							g.drawRect(x, y, 1, 1);
						}
						

						if (enableNoveltySearch && nsEnvironments == null && step > 0 && step % noveltySearchStepsPerRecord == 0) {
							// Behaviour is concatenation of states at behaviourRecordCount times from each trial from each environment.
							behaviour.setSubVector(behaviourIndex, state);
							behaviourIndex += envSize;
							behaviourRecordIndex++;
						}
						
						// If goal reached.
						//if (agentInput[env.outputSize-1] >= targetPerformance) {
						//	break;
						//}
						
						// Ask agent what it wants to do next, given output from environment.
						// This also provides agent with reinforcement signal in the last element of the array.
						nn.next(agentInput, agentOutput);
						
						// Get updated environment output.
						env.updateStateAndOutput(state, agentOutput, agentInput);
					}

					// Reward for trial is reward received in last step.
					trialReward = Math.max(0, agentInput[env.outputSize-1]);
					envReward += trialReward;
					avgRewardForEachTrial[trial] += trialReward;

					if (logImage) {
						File outputfile = new File(baseFileName + ".env_ " + envIndex + ".trial_" + trial + ".png");
						ImageIO.write(image, "png", outputfile);
					}
					
					if (enableNoveltySearch && nsEnvironments == null) {
						// Fill rest of behaviour vector with final state (if the goal was reached before the maximum 
						// steps were used then this will ensure the behaviour vector is the right length).
						for (; behaviourRecordIndex < behaviourRecordCount; behaviourRecordIndex++) {
							behaviour.setSubVector(behaviourIndex, state);
							behaviourIndex += envSize;
						}
					}
				}
				reward += envReward / trialCount;				
			} //for (int envIndex = 0; envIndex < environmentCount; envIndex++) {
			
			if (enableNoveltySearch) {
				if (nsEnvironments == null) {
					genotype.behaviour.add(new RealVectorBehaviour(behaviour));
				}
				else {
					getBehaviourForNovelty(genotype, nn);
				}
			}


			for (int trial = 0; trial < trialCount; trial++) {
				avgRewardForEachTrial[trial] /= environmentCount;
			}
			if (logText) {
				logOutput.put("\n\nReward for each trial averaged over all environments:\n");
				logOutput.put(Arrays.toString(avgRewardForEachTrial));
				logOutput.close();
			}

			reward /= environmentCount;
			genotype.setPerformanceValue(reward);
			return reward; 
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	// Used to determine behaviour when environments used to determine fitness will be replaced over the course of evolution.
	private void getBehaviourForNovelty(Chromosome genotype, NNAdaptor nn) {
		ArrayRealVector behaviour = new ArrayRealVector(nsEnvironments.length * trialCount * envSize * behaviourRecordCount);
		int behaviourIndex = 0;
		for (int ei = 0; ei < nsEnvironments.length; ei++) {
			int envIndex = environmentOrder[ei];
			Environment env = environments[envIndex];
			// Reset substrate to initial state to begin learning (new) environment.
			nn.reset();
			// Record the environment state at 4 intervals (25%, 50%, 75%, 100%)
			int noveltySearchStepsPerRecord = env.getMinimumStepsToSolve() / behaviourRecordCount;
			for (int trial = 0; trial < trialCount; trial++) {
				double[] agentOutput = new double[env.inputSize];
				// Get initial state and environment output.
				ArrayRealVector state = env.startState.copy();
				double[] agentInput = new double[env.outputSize];
				env.getOutputForState(state, agentInput);
				int behaviourRecordIndex = 0;
				for (int step = 0; step < env.getMinimumStepsToSolve(); step++) {
					if (step > 0 && step % noveltySearchStepsPerRecord == 0) {
						// Behaviour is concatenation of states at behaviourRecordCount times from each trial from each environment.
						behaviour.setSubVector(behaviourIndex, state);
						behaviourIndex += envSize;
						behaviourRecordIndex++;
					}
					
					// If goal reached.
					if (agentInput[env.outputSize-1] >= targetPerformance) {
						break;
					}
					// Ask agent what it wants to do next, given output from environment.
					// This also provides agent with reinforcement signal in the last element of the array.
					nn.next(agentInput, agentOutput);
					// Get updated environment output.
					env.updateStateAndOutput(state, agentOutput, agentInput);
				}
				// Fill rest of behaviour vector with final state (if the goal was reached before the maximum 
				// steps were used then this will ensure the behaviour vector is the right length).
				for (; behaviourRecordIndex < behaviourRecordCount; behaviourRecordIndex++) {
					behaviour.setSubVector(behaviourIndex, state);
					behaviourIndex += envSize;
				}
			}				
		} //for (int envIndex = 0; envIndex < environmentCount; envIndex++) {
		genotype.behaviour.add(new RealVectorBehaviour(behaviour));
	}


	@Override
	public void ahniEventOccurred(AHNIEvent event) {
		// We don't put this in finaliseEvaluation() because we might need to use 
		// the environments for logging purposes after evaluation has been finished.
		if (event.getType() == AHNIEvent.Type.GENERATION_END) {
			Chromosome bestPerforming = event.getEvolver().getBestPerformingFromLastGen();
			if (bestPerforming.getPerformanceValue() >= difficultyIncreasePerformance) {
				if (environments[0].increaseDifficultyPossible()) {
					event.getEvolver().logChamp(bestPerforming, true);

					for (int e = 0; e < environments.length; e++) {
						environments[0].increaseDifficulty();
						environments[e].setUp(environmentCounter++);
					}
					logger.info("Increased difficulty.");
				}
			}
		}
	}

	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) // Input layer.
			return new int[] { environments[0].outputSize, 1 };
		else if (layer == totalLayerCount - 1) // Output layer.
			return new int[] { environments[0].inputSize, 1 };
		return null;
	}

	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[environments[0].outputSize];
			// env state across "top".
			for (int i = 0; i < environments[0].outputSize-1; i++) {
				positions[i] = new Point((double) i / (environments[0].outputSize - 2), 0, 0);
			}
			// reward at bottom-centre.
			positions[environments[0].outputSize-1] = new Point(0.5, 1, 0);
		} else if (layer == totalLayerCount - 1) { // Output layer.
			positions = new Point[environments[0].inputSize];
			for (int i = 0; i < environments[0].inputSize; i++) {
				positions[i] = new Point((double) i / (environments[0].inputSize - 1), 0, 1);
			}
		}
		return positions;
	}
	
	@Override
	public boolean definesNovelty() {
		return enableNoveltySearch;
	}
	
	@Override 
	public void postEvaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		// If multiple instances of RLContinuousStateBased are being used in a multi-objective setup
		// then set the performance to be the average of the fitness values over all instances of
		// RLContinuousStateBased
		double perf = genotype.getFitnessValue(0);
		int count = 1;
		if (multiFitnessFunctions != null) {
			for (int i = 0; i < multiFitnessFunctions.length; i++) {
				BulkFitnessFunctionMT f = multiFitnessFunctions[i];
				if (f instanceof RLContinuousStateBased) {
					perf += genotype.getFitnessValue(i+1);
					count++;
				}
			}
		}
		genotype.setPerformanceValue(perf / count);
	}
}
