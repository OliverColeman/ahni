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
import com.ojcoleman.ahni.util.Range;
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
	
	private int behaviourRecordCount;
	
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
		
		// If novelty search enabled then record the environment state either just at the end of each trial, 
		// or multiple times during the course of a single trial.
		behaviourRecordCount = trialCount > 1 ? 1 : 4;
		
		if (enableNoveltySearch && environmentReplaceProb > 0) {
		//if (enableNoveltySearch) {
			//throw new IllegalArgumentException("Can't enable novelty search and environment replacement.");
			
			// Create a set of environments that don't change over the course of evolution to test novelty on.
			//nsEnvironments = new Environment[environmentCount];
			nsEnvironments = new Environment[(int) Math.pow(3, envSize)-1];
			//nsEnvironments = new Environment[1];
			for (int e = 0; e < nsEnvironments.length; e++) {
				nsEnvironments[e] = props.newObjectProperty(props.getClassProperty(ENVIRONMENT_CLASS, SimpleNavigationEnvironment.class));
				nsEnvironments[e].rlcss = this;
				// If possible, generate environments of varying difficulty.
				for (int i = 1; i < e && nsEnvironments[e].increaseDifficultyPossible(); i++) {
					nsEnvironments[e].increaseDifficulty();
				}
			}
			logger.info("Created " + nsEnvironments.length + " environments for novelty search."); 
		}

		props.getEvolver().addEventListener(this);
		
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
				for (int e = 0, id = -1; e < nsEnvironments.length; e++, id--) {
					if (e == nsEnvironments.length/2) id--; // Skip middle point where goal state is.
					nsEnvironments[e].setUp(id);
					///nsEnvironments[e].setMinimumStepsToSolve(behaviourRecordCount);
				}
				//logger.info("Novelty behaviour dimensionality: " + (nsEnvironments.length * trialCount * envSize * behaviourRecordCount));
				logger.info("Novelty behaviour dimensionality: " + (nsEnvironments.length * 1 * envSize * behaviourRecordCount));
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
	protected void evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, double[] fitnessValues, Behaviour[] behaviours) {
		_evaluate(genotype, substrate, null, false, false, fitnessValues, behaviours);
	}
	
	@Override
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		_evaluate(genotype, substrate, baseFileName, logText, logImage, null, null);
	}
	
	public void _evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues, Behaviour[] behaviours) {
		super.evaluate(genotype, substrate, baseFileName, logText, logImage);
		double fitness = 0;
		double performance = 0;
		ArrayRealVector[][] finalStates = new ArrayRealVector[environmentCount][trialCount];
		NNAdaptor nn = (NNAdaptor) substrate;

		if (substrate.getInputCount() != environments[0].getOutputSize() || substrate.getOutputCount() != environments[0].getInputSize()) {
			throw new IllegalArgumentException("Substrate input (or output) size does not match number of outputSize (inputSize) environment nodes.");
		}

		try {
			NiceWriter logOutput = !logText ? null : new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
			int imageSize = 256;

			double[] avgRewardForEachTrial = new double[trialCount];
			ArrayRealVector behaviour = enableNoveltySearch && nsEnvironments == null ? new ArrayRealVector(environmentCount * trialCount * envSize * behaviourRecordCount) : null;
			int behaviourIndex = 0;
			double maxFitness = 0;
			boolean logImageTemp = logImage;
			for (int ei = 0; ei < environmentCount; ei++) {
				int envIndex = (logText || logImageTemp) ? ei : environmentOrder[ei];
				Environment env = environments[envIndex];
				if (logText) {
					logOutput.put("\n\nBEGIN EVALUATION ON ENVIRONMENT " + env.id + "\n");
					logOutput.put("\tEnvironment description:\n" + env + "\n");

					//NiceWriter logOutputEnv = new NiceWriter(new FileWriter(baseFileName + ".environment-" + env.id + ".txt"), "0.00");
					//logOutputEnv.put(env);
					//logOutputEnv.close();
				}
				// Don't log more than 20 environments to images.
				if (ei >= 20) {
					logImageTemp = false;
				}
				if (logImageTemp) {
					env.logToImage(baseFileName + ".env_ " + envIndex, imageSize);
				}

				// Reset substrate to initial state to begin learning (new) environment.
				substrate.reset();
				
				double envFitness = 0;
				int noveltySearchStepsPerRecord = (int) Math.ceil((double) env.getMinimumStepsToSolve() / behaviourRecordCount);
				double previousTrialReward = 0;
				ArrayRealVector state = null;
				for (int trial = 0; trial < trialCount; trial++) {
					double trialReward = 0;
					double[] agentOutput = new double[env.getInputSize()];
					
					// Get initial state and environment output.
					state = env.startState.copy();
					double[] agentInput = new double[env.getOutputSize()];
					env.getOutputForState(state, agentInput);
					
					BufferedImage image = null;
					Graphics2D g = null;
					if (logText) {
						logOutput.put("\n  BEGIN TRIAL " + (trial + 1) + " of " + trialCount + "\n");
						logOutput.put("    step, , state, , agent output, , agent input\n");
					}
					if (logImageTemp && (env.getOutputSize() == 3 || env.size == 2)) {
						image = new BufferedImage(imageSize+2, imageSize+2, BufferedImage.TYPE_3BYTE_BGR);
						g = image.createGraphics();
						if (env.getOutputSize() == 3) {
							// Log agent perception in 2D map.
							g.setColor(Color.LIGHT_GRAY);
							double[] p = new double[env.getOutputSize()];
							env.getOutputForState(env.startState, p);
							g.drawOval((int) Math.round(p[0] * imageSize - 3.5), (int) Math.round(p[1] * imageSize - 3.5), 7, 7);
							env.getOutputForState(env.goalState, p);
							g.drawRect((int) Math.round(p[0] * imageSize - 2.5), (int) Math.round(p[1] * imageSize - 2.5), 5, 5);
						}
						if (env.size == 2) {
							// Log environment state in 2D map.
							env.logToImageForTrial(g, imageSize);
							g.setColor(Color.WHITE);
							g.drawOval((int) Math.round(env.startState.getEntry(0) * imageSize - 3.5), (int) Math.round(env.startState.getEntry(1) * imageSize - 3.5), 7, 7);
							g.drawRect((int) Math.round(env.goalState.getEntry(0) * imageSize - 2.5), (int) Math.round(env.goalState.getEntry(1) * imageSize - 2.5), 5, 5);
						}
					}
					
					int behaviourRecordIndex = 0;
					
					for (int step = 0; step < env.getMinimumStepsToSolve(); step++) {
						if (logText) {
							logOutput.put("    " + step + ", , ");
							for (int i = 0; i < env.size; i++) {
								logOutput.put(nf.format(state.getEntry(i)) + ", ");
							}
							logOutput.put(", ");
							for (int i = 0; i < env.getInputSize(); i++) {
								logOutput.put(nf.format(agentOutput[i]) + ", ");
							}
							logOutput.put(", ");
							for (int i = 0; i < env.getOutputSize(); i++) {
								logOutput.put(nf.format(agentInput[i]) + ", ");
							}
							logOutput.put("\n");
						}	
						if (logImageTemp) {
							if (env.getOutputSize() == 3) {
								g.setColor(Color.LIGHT_GRAY);
								int x = (int) Math.round(agentInput[0] * imageSize-0.5);
								int y = (int) Math.round(agentInput[1] * imageSize-0.5);
								g.drawRect(x, y, 1, 1);
							}
							if (env.size == 2) {
								g.setColor(Color.WHITE);
								int x = (int) Math.round(state.getEntry(0) * imageSize-0.5);
								int y = (int) Math.round(state.getEntry(1) * imageSize-0.5);
								g.drawRect(x, y, 1, 1);
							}
						}
						

						if (enableNoveltySearch && nsEnvironments == null && step > 0 && step % noveltySearchStepsPerRecord == 0) {
							// Behaviour is concatenation of states at behaviourRecordCount times from each trial from each environment.
							behaviour.setSubVector(behaviourIndex, state);
							behaviourIndex += envSize;
							behaviourRecordIndex++;
						}
						
						// If goal reached.
						//if (agentInput[env.getOutputSize()-1] >= targetPerformance) {
						//	break;
						//}
						
						// Ask agent what it wants to do next, given output from environment.
						// This also provides agent with reinforcement signal in the last element of the array.
						nn.next(agentInput, agentOutput);
						
						// Get updated environment output.
						env.updateStateAndOutput(state, agentOutput, agentInput);
					}

					// Reward for trial is reward received in last step.
					trialReward = env.getRewardForState(state);
					avgRewardForEachTrial[trial] += trialReward;
					
					// Reward received in initial trials isn't worth as much as for later trials, as agent can't know 
					// which environment it's in in initial trials (favour agents that find the right behaviour and 
					// then stick to it)
					double factor = Math.pow((double) (trial+1.0) / trialCount, 2);
					maxFitness += factor;
					fitness += trialReward * factor;
					
					// Just grab reference as a new initial state is created from a copy of the start state at start of each trial.
					finalStates[ei][trial] = state;
					
					if (logText) {
						logOutput.put("    " + env.getMinimumStepsToSolve() + ", , ");
						for (int i = 0; i < env.size; i++) {
							logOutput.put(nf.format(state.getEntry(i)) + ", ");
						}
						logOutput.put(", ");
						for (int i = 0; i < env.getInputSize(); i++) {
							logOutput.put(nf.format(agentOutput[i]) + ", ");
						}
						logOutput.put(", ");
						for (int i = 0; i < env.getOutputSize(); i++) {
							logOutput.put(nf.format(agentInput[i]) + ", ");
						}
						logOutput.put("\n");
					}	
					if (logImageTemp && env.size == 2) {
						if (env.getOutputSize() == 3) {
							g.setColor(Color.LIGHT_GRAY);
							int x = (int) Math.round(agentInput[0] * imageSize-0.5);
							int y = (int) Math.round(agentInput[1] * imageSize-0.5);
							g.drawRect(x, y, 1, 1);
						}
						if (env.size == 2) {
							g.setColor(Color.WHITE);
							int x = (int) Math.round(state.getEntry(0) * imageSize-0.5);
							int y = (int) Math.round(state.getEntry(1) * imageSize-0.5);
							g.drawRect(x, y, 1, 1);
						}
						
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
				
				// Only show performance for last trial.
				performance += env.getPerformanceForState(state);
				
			} //for (int envIndex = 0; envIndex < environmentCount; envIndex++) {

			for (int trial = 0; trial < trialCount; trial++) {
				avgRewardForEachTrial[trial] /= environmentCount;
			}
			if (logText) {
				logOutput.put("\n\nReward for each trial averaged over all environments:\n");
				logOutput.put(Arrays.toString(avgRewardForEachTrial));
				logOutput.close();
			}

			fitness /= maxFitness;
			performance /= environmentCount;
			genotype.setPerformanceValue(performance);
			
			// Determine the average distance between each pair of final states from each trial, over all environments.
			double finalStatesDiff = 0;
			int count = 0;
			for (int ei = 0; ei < environmentCount; ei++) {
				for (int trial = 0; trial < trialCount; trial++) {
					for (int trial2 = trial+1; trial2 < trialCount; trial2++) {
						finalStatesDiff += finalStates[ei][trial].getDistance(finalStates[ei][trial2]);
						count++;
					}
				}
			}
			finalStatesDiff /= count * Math.sqrt(envSize);
			
			if (fitnessValues != null && fitnessValues.length > 0) {
				fitnessValues[0] = fitness;
				if (fitnessValues.length > 1) {
					fitnessValues[1] = finalStatesDiff;
				}
			}
			
			if (enableNoveltySearch && behaviours != null) {
				if (nsEnvironments == null) {
					behaviours[0] = new RealVectorBehaviour(behaviour);
				}
				else {
					behaviours[0] = new RealVectorBehaviour(getBehaviourForNovelty(genotype, nn));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// Used to determine behaviour when environments used to determine fitness will be replaced over the course of evolution.
	private ArrayRealVector getBehaviourForNovelty(Chromosome genotype, NNAdaptor nn) {
		//int trialCount = 1;
		Range outputRange = new Range(nn.getMinResponse() < -1000 ? -1000 : nn.getMinResponse(), nn.getMaxResponse() > 1000 ? 1000 : nn.getMaxResponse());
		
		int recordSize = envSize;
		//int recordSize = nn.getOutputCount();
		//int recordSize = 2;
		ArrayRealVector behaviour = new ArrayRealVector(nsEnvironments.length * trialCount * recordSize * behaviourRecordCount);
		int behaviourIndex = 0;
		for (int ei = 0; ei < nsEnvironments.length; ei++) {
			Environment env = nsEnvironments[ei];
			// Reset substrate to initial state to begin learning (new) environment.
			nn.reset();
			// Record the environment state at specified intervals.
			int noveltySearchStepsPerRecord = (int) Math.ceil((double) env.getMinimumStepsToSolve() / behaviourRecordCount);
			for (int trial = 0; trial < trialCount; trial++) {
				double[] agentOutput = new double[env.getInputSize()];
				// Get initial state and environment output.
				ArrayRealVector state = env.startState.copy();
				double[] agentInput = new double[env.getOutputSize()];
				env.getOutputForState(state, agentInput);
				
				assert state.getEntry(0) >= 0 && state.getEntry(0) <= 1 : state.getEntry(0);
				
				double prevReward = Math.max(0, agentInput[agentInput.length-1]);
				double[] prevOutput = new double[agentOutput.length];
				System.arraycopy(agentOutput, 0, prevOutput, 0, agentOutput.length);
				for (int step = 0; step < env.getMinimumStepsToSolve(); step++) {					
					assert agentInput[0] >= 0 && agentInput[0] <= 1 : agentInput[0];
					assert agentInput[1] >= 0 && agentInput[1] <= 1 : agentInput[1];
					
					// Ask agent what it wants to do next, given output from environment.
					// This also provides agent with reinforcement signal in the last element of the array.
					nn.next(agentInput, agentOutput);
					
					assert agentOutput[0] >= -1 && agentOutput[0] <= 1 : agentOutput[0];
						
					// Get updated environment output.
					env.updateStateAndOutput(state, agentOutput, agentInput);
					
					if (step > 0 && step % noveltySearchStepsPerRecord == 0) {
						//System.err.println("  " + step + " : " + behaviourIndex);
						
						// Behaviour is concatenation of states at behaviourRecordCount times from each trial from each environment.
						behaviour.setSubVector(behaviourIndex, state);
						
						//double reward = Math.max(0, agentInput[agentInput.length-1]);
						
						// Behaviour is concatenation of agent output and the current reward signal at behaviourRecordCount times from each trial from each environment.
						//double[] b = ArrayUtil.scaleToUnit(Arrays.copyOf(agentOutput, recordSize), outputRange);
						//b[b.length-1] = reward; // Reward.
						//behaviours.setSubVector(behaviourIndex, b);
						
						/*double b = prevReward - reward;
						b = (1 + b) / 2; // diff could be in range [-1, 1], scale to [0, 1].
						behaviours.setEntry(behaviourIndex, b); // Reward diff.
						prevReward = reward;*/
						/*
						double ri = reward > prevReward ? 1 : 0;
						//behaviours.setEntry(behaviourIndex, ri); // Reward diff.
						for (int i = 0; i < agentOutput.length; i++) {
							int directionNow = agentOutput[i] > 0 ? 1 : 0;
							int directionPrev = prevOutput[i] > 0 ? 1 : 0;
							behaviours.setEntry(behaviourIndex+i, directionNow != directionPrev ? 1 : 0); // Direction change.
						}
						prevReward = reward;
						System.arraycopy(agentOutput, 0, prevOutput, 0, agentOutput.length);
						*/
						
						behaviourIndex += recordSize;
					}
				}
				// Fill rest of behaviour vector with final state (if the goal was reached before the maximum 
				// steps were used then this will ensure the behaviour vector is the right length).
				while (behaviourIndex < (ei+1) * (trial+1) * envSize * behaviourRecordCount) {
					behaviour.setSubVector(behaviourIndex, state);
					behaviourIndex += recordSize;
					//System.err.println("    " + behaviourIndex);
				}
			}		
		} //for (int envIndex = 0; envIndex < environmentCount; envIndex++) {
		return behaviour;
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
						environments[e].increaseDifficulty();
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
			return new int[] { environments[0].getOutputSize(), 1 };
		else if (layer == totalLayerCount - 1) // Output layer.
			return new int[] { environments[0].getInputSize(), 1 };
		return null;
	}

	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[environments[0].getOutputSize()];
			// env state across "top".
			for (int i = 0; i < environments[0].getOutputSize()-1; i++) {
				positions[i] = new Point(environments[0].getOutputSize() > 2 ? (double) i / (environments[0].getOutputSize() - 2) : 0.5, 0, 0);
			}
			// reward at bottom-centre.
			positions[environments[0].getOutputSize()-1] = new Point(0.5, 1, 0);
		} else if (layer == totalLayerCount - 1) { // Output layer.
			positions = new Point[environments[0].getInputSize()];
			for (int i = 0; i < environments[0].getInputSize(); i++) {
				positions[i] = new Point(environments[0].getInputSize() > 1 ? (double) i / (environments[0].getInputSize() - 1) : 0.5, 0, 1);
			}
		}
		return positions;
	}
	
	@Override
	public int noveltyObjectiveCount() {
		return enableNoveltySearch ? 1 : 0;
	}
	
	@Override
	public int fitnessObjectivesCount() {
		if (trialCount > 1) return 2;
		return 1;
	}
	
	@Override
	public boolean fitnessValuesStable() {
		return environmentReplaceProb == 0;
	}
	
	@Override 
	public void postEvaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		// If multiple instances of RLContinuousStateBased are being used in a multi-objective setup
		// then set the performance to be the average of the performance values over all instances of
		// RLContinuousStateBased
		double perf = genotype.getPerformanceValue();
		int count = 1;
		if (multiFitnessFunctions != null) {
			for (int i = 0; i < multiFitnessFunctions.length; i++) {
				BulkFitnessFunctionMT f = multiFitnessFunctions[i];
				if (f instanceof RLContinuousStateBased) {
					perf += genotype.getPerformanceValue();
					count++;
				}
			}
		}
		if (!Double.isNaN(perf)) {
			genotype.setPerformanceValue(perf / count);
		}
	}
}
