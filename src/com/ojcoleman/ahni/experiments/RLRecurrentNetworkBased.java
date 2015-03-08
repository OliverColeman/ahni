package com.ojcoleman.ahni.experiments;

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
import java.util.HashSet;
import java.util.List;
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
import com.ojcoleman.ahni.evaluation.novelty.RealVectorBehaviour;
import com.ojcoleman.ahni.event.AHNIEvent;
import com.ojcoleman.ahni.event.AHNIEventListener;
import com.ojcoleman.ahni.experiments.mr2d.EnvironmentDescription;
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

public class RLRecurrentNetworkBased extends BulkFitnessFunctionMT implements AHNIEventListener {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(BulkFitnessFunctionMT.class);
	private static final NumberFormat nf = new DecimalFormat(" 0.00;-0.00");
	private static final int TASK_CONTROL = 1, TASK_PREDICT = 2;

	/**
	 * The task that the agent is required to perform, can be "predict" or "control".
	 */
	public static final String TASK = "fitness.function.rlrnn.task";
	/**
	 * The seed to use for the RNG to generate the network. Comment out to use seed based on current time.
	 */
	public static final String SEED = "fitness.function.rlrnn.seed";
	/**
	 * The number of nodes in the network.
	 */
	public static final String SIZE = "fitness.function.rlrnn.size";
	/**
	 * The number of nodes for which the activation level is directly observable by the agent (includes the reward value
	 * node). This will always be at least one to allow the agent to observe at least the node representing the reward
	 * value.
	 */
	public static final String OBSERVABLE = "fitness.function.rlrnn.observable";
	/**
	 * The number of nodes for which the activation level is directly manipulable by the agent. These are represented as
	 * inputs to the environment network. This is only used when fitness.function.rlrnn.task=control.
	 */
	public static final String MANIPULABLE = "fitness.function.rlrnn.manipulable";
	/**
	 * The number of nodes for which the activation level should be predicted by the agent. 
	 * This is only used when fitness.function.rlrnn.task=control.
	 */
	public static final String PREDICT = "fitness.function.rlrnn.predict";

	/**
	 * The initial in-degree, or number of incoming connections to nodes. The degree influences the complexity of the
	 * dynamics of the network.
	 */
	public static final String DEGREE = "fitness.function.rlrnn.indegree.initial";
	/**
	 * The amount to increase the in-degree when environments with the current value have been sufficiently mastered
	 * (see {@link #DIFFICULTY_INCREASE_PERFORMANCE}. If the value is followed by an "x" then the value is considered a
	 * factor (and so should be > 1).
	 */
	public static final String DEGREE_INCREASE_DELTA = "fitness.function.rlrnn.indegree.delta";
	/**
	 * The maximum amount to increase the in-degree to.
	 */
	public static final String DEGREE_MAX = "fitness.function.rlrnn.indegree.maximum";
	/**
	 * The initial variance in weight values. The variance of the weight values influences the complexity of the
	 * dynamics of the network.
	 */
	public static final String WEIGHT_VARIANCE_INITIAL = "fitness.function.rlrnn.weightvariance.initial";
	/**
	 * The amount to increase the variance in weight values when environments with the current value have been
	 * sufficiently mastered (see {@link #DIFFICULTY_INCREASE_PERFORMANCE}. If the value is followed by an "x" then the
	 * value is considered a factor (and so should be > 1).
	 */
	public static final String WEIGHT_VARIANCE_INCREASE_DELTA = "fitness.function.rlrnn.weightvariance.delta";
	/**
	 * The maximum amount to increase variance in weight values to.
	 */
	public static final String WEIGHT_VARIANCE_MAX = "fitness.function.rlrnn.weightvariance.maximum";
	/**
	 * The performance indicating when the environment difficulty should be increased as the current difficulty has been
	 * sufficiently mastered.
	 */
	public static final String DIFFICULTY_INCREASE_PERFORMANCE = "fitness.function.rlrnn.difficulty.increase.performance";
	/**
	 * The number of environments to evaluate candidates against. Increasing this will provide a more accurate
	 * evaluation but take longer.
	 */
	public static final String ENVIRONMENT_COUNT = "fitness.function.rlrnn.environment.count";
	/**
	 * The fraction of environments that should be replaced with new environments per generation. This is evaluated
	 * probabilistically.
	 */
	public static final String ENVIRONMENT_CHANGE_RATE = "fitness.function.rlrnn.environment.replacerate";
	/**
	 * The number of trials per environment.
	 */
	public static final String TRIAL_COUNT = "fitness.function.rlrnn.trial.count";
	/**
	 * The number of simulation iterations per trial.
	 */
	public static final String ITERATION_COUNT = "fitness.function.rlrnn.iteration.count";
	
	/**
	 * If true enables novelty search for which behaviours are defined by the output of the RNN for the last  
	 * iterations in the final trial. Default is false.
	 */
	public static final String NOVELTY_SEARCH = "fitness.function.rlrnn.noveltysearch";
	/**
	 * The number of iterations to record the output for when creating a novelty search behaviour. Default is 8. 
	 */
	public static final String NOVELTY_SEARCH_RECORD_LENGTH = "fitness.function.rlrnn.noveltysearch.record_length";
	/**
	 * If set to an integer > 0 then this many environments will be used to characterise an agents behaviour for novelty
	 * search. Defaults to fitness.function.rlrnn.env.count.
	 */
	public static final String NOVELTY_SEARCH_ENV_COUNT = "fitness.function.rlrnn.noveltysearch.envs.count";
	/**
	 * If true then makes novelty search the only objective. Performance values are still calculated. Default is false. 
	 * If true then fitness.function.rlrnn.noveltysearch is forced to true and fitness.function.rlrnn.env.replacerate is 
	 * forced to 0, and fitness.function.rlrnn.noveltysearch.envs.count is forced to fitness.function.rlrnn.environment.count, 
	 * so that the same environments are used for calculating performance and novelty.
	 */
	public static final String NOVELTY_SEARCH_ONLY = "fitness.function.rlrnn.noveltysearch.only";

	private int task;
	private int environmentCount;
	private double environmentReplaceProb;
	private int trialCount;
	private int iterationCount;
	private int networkSize;
	private int observable;
	private int manipulable;
	private int predict;
	private int inDegree;
	private int inDegreeMax;
	private double weightVariance;
	private double weightVarianceMax;
	private double difficultyIncreasePerformance;
	private Environment[] environments;
	private Environment[] nsEnvironments;
	private Environment[] genEnvironments;
	private int environmentCounter = 0;
	private Random envRand;
	private boolean noveltySearchEnabled = false;
	private boolean noveltySearchOnly = false;
	private int noveltySearchEnvCount;
	private int totalNSBehaviourSize;
	private int nsRecordLength;
	private int nsStartRecordItr;

	@Override
	public void init(Properties props) {
		noveltySearchOnly = props.getBooleanProperty(NOVELTY_SEARCH_ONLY, false);
		noveltySearchEnabled = noveltySearchOnly || props.getBooleanProperty(NOVELTY_SEARCH, false);
		
		String taskStr = props.getProperty(TASK).trim().toLowerCase();
		if (taskStr.equals("control")) task = TASK_CONTROL;
		else if (taskStr.equals("predict")) task = TASK_PREDICT;
		else throw new IllegalArgumentException("Invalid value provided for property " + TASK + ". Must be one of 'predict' or 'control'.");
		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		environmentReplaceProb = noveltySearchOnly ? 0 : props.getDoubleProperty(ENVIRONMENT_CHANGE_RATE);
		trialCount = props.getIntProperty(TRIAL_COUNT);
		iterationCount = props.getIntProperty(ITERATION_COUNT);
		nsRecordLength = props.getIntProperty(NOVELTY_SEARCH_RECORD_LENGTH, 8);
		nsStartRecordItr = iterationCount - nsRecordLength;

		networkSize = props.getIntProperty(SIZE);
		observable = Math.max(1, props.getIntProperty(OBSERVABLE));
		manipulable = task == TASK_CONTROL ? Math.max(1, props.getIntProperty(MANIPULABLE)) : 0;
		predict = task == TASK_PREDICT ? Math.max(1, props.getIntProperty(PREDICT)) : 0;
		inDegree = props.getIntProperty(DEGREE);
		inDegreeMax = props.getIntProperty(DEGREE_MAX);
		weightVariance = props.getDoubleProperty(WEIGHT_VARIANCE_INITIAL);
		weightVarianceMax = props.getDoubleProperty(WEIGHT_VARIANCE_MAX);
		difficultyIncreasePerformance = props.getDoubleProperty(DIFFICULTY_INCREASE_PERFORMANCE);

		envRand = new Random(props.getLongProperty(SEED, System.currentTimeMillis()));

		environments = new Environment[environmentCount];
		for (int e = 0; e < environments.length; e++) {
			environments[e] = new Environment();
			environments[e].setUp(environmentCounter++);
		}
		
		if (noveltySearchEnabled) {
			noveltySearchEnvCount = noveltySearchOnly ? environmentCount : props.getIntProperty(NOVELTY_SEARCH_ENV_COUNT, environmentCount);
			
			// If the same environments aren't used throughout evolution.
			if (environmentReplaceProb > 0) {
				// Create a set of environments (that don't change over the course of evolution) to test novelty on.
				nsEnvironments = new Environment[noveltySearchEnvCount];
				totalNSBehaviourSize = 0;
				for (int e = 0; e < noveltySearchEnvCount; e++) {
					nsEnvironments[e] = new Environment();
					nsEnvironments[e].setUp(-e);
				}
				logger.info("Created " + noveltySearchEnvCount + " environments for novelty search.");
			}
			else {
				if (noveltySearchEnvCount > environmentCount) {
					throw new IllegalArgumentException("The number of environments used for novelty search must be less than the number used for fitness/performance evaluation when " + ENVIRONMENT_CHANGE_RATE + " = 0.");
				}
			}
			totalNSBehaviourSize = noveltySearchEnvCount * nsRecordLength * (predict + manipulable);
			logger.info("Novelty search behaviours have " + totalNSBehaviourSize + " dimensions.");
		}

		if (environmentCount > numThreads) {
			logger.warn("The number of environments is less than the number of threads used for evaluation. This will waste CPU cycles while a thread waits for an available environment. It is recommended to set the number of threads to be less than or equal to the number of environments.");
		}

		if (observable + manipulable > networkSize) {
			throw new IllegalArgumentException("The number of observable plus number of manipulable nodes must not be greater than the network size (it doesn't make sense to allow these sets to overlap).");
		}

		((Properties) props).getEvolver().addEventListener(this);
		
		super.init(props);
	}

	public void initialiseEvaluation() {
		// Create (some) new environments every generation.
		for (Environment e : environments) {
			if (environmentReplaceProb > random.nextDouble()) {
				e.setUp(environmentCounter++);
			}
		}
	}

	@Override
	protected void evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, double[] fitnessValues, Behaviour[] behaviours) {
		if (nsEnvironments == null) {
			// Evaluate fitness and behaviour on same environments.
			_evaluate(genotype, substrate, null, false, false, fitnessValues, behaviours, environments);
		} else {
			// Evaluate fitness on changing environments and behaviour on fixed novelty search environments.
			_evaluate(genotype, substrate, null, false, false, fitnessValues, null, environments);
			_evaluate(genotype, substrate, null, false, false, null, behaviours, nsEnvironments);
		}
	}
	
	@Override
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		_evaluate(genotype, substrate, baseFileName, logText, logImage, null, null, environments);
		super.evaluate(genotype, substrate, baseFileName, logText, logImage);
	}
	
				  
	public void _evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues, Behaviour[] behaviours, Environment[] envs) {
		int envCount = envs.length;
		double reward = 0;
		NNAdaptor nn = (NNAdaptor) substrate;

		if (substrate.getInputCount() != observable || 
				(task == TASK_CONTROL && substrate.getOutputCount() != manipulable) ||
				(task == TASK_PREDICT && substrate.getOutputCount() != predict)) {
			throw new IllegalArgumentException("Substrate input (or output) size does not match number of observable (manipulable) environment nodes.");
		}
		
		ArrayRealVector behaviour = behaviours != null && behaviours.length > 0 ? new ArrayRealVector(totalNSBehaviourSize) : null;

		try {
			NiceWriter logOutput = !logText ? null : new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
			int imageScale = 4;
			BufferedImage image = null;
			Graphics2D g = null;
			int imgEnvHeight = (networkSize + (task == TASK_PREDICT ? predict : 0) + 2) * imageScale; // +2 to add 2 blank rows between environments.
			int imgWidth = iterationCount * imageScale;
			if (baseFileName != null && logImage) {
				// Log activation levels to time series plot.
				image = new BufferedImage(imgWidth, (envCount * imgEnvHeight - 2*imageScale), BufferedImage.TYPE_BYTE_GRAY);
				g = image.createGraphics();
				
				g.setColor(Color.WHITE);
				for (int envIndex = 0; envIndex < envCount; envIndex++) {
					int yOffset = imgEnvHeight * envIndex-1;
					g.drawLine(0, yOffset, imgWidth, yOffset);
					if (task == TASK_CONTROL) {
						g.drawLine(0, manipulable * imageScale + yOffset, imgWidth, manipulable * imageScale + yOffset);
					}
					g.drawLine(0, (networkSize-observable) * imageScale + yOffset, imgWidth, (networkSize-observable) * imageScale + yOffset);
					g.drawLine(0, networkSize * imageScale + yOffset, imgWidth, networkSize * imageScale + yOffset);
					if (task == TASK_PREDICT) {
						g.drawLine(0, (networkSize - predict) * imageScale + yOffset, imgWidth, (networkSize - predict) * imageScale + yOffset);
						g.drawLine(0, (networkSize + predict) * imageScale + yOffset, imgWidth, (networkSize + predict) * imageScale + yOffset);
					}
				}
			}

			double[] avgRewardForEachTrial = new double[trialCount];

			for (int envIndex = 0; envIndex < envCount; envIndex++) {
				Environment env = envs[envIndex].getInstance();
				if (logText) {
					logOutput.put("\n\nBEGIN EVALUATION ON ENVIRONMENT " + env.id + "\n");
	
					NiceWriter logOutputEnv = new NiceWriter(new FileWriter(baseFileName + ".environment.txt"), "0.00");
					logOutputEnv.put("Environment: " + env.id + "\nWeight variance: " + weightVariance + "\nIn-degree: " + inDegree + "\n\n" + env);
					logOutputEnv.close();
				}
	
				// Reset substrate to initial state to begin learning (new) environment.
				substrate.reset();
	
				double envReward = 0;
				double trialReward = 0;
				for (int trial = 0; trial < trialCount; trial++) {
					trialReward = 0;
					double[] agentOutput = new double[nn.getOutputCount()];
					double[] envOutput = new double[env.rnn.getOutputCount()];
	
					if (logText) {
						logOutput.put("\n  BEGIN TRIAL " + (trial + 1) + " of " + trialCount + "\n");
					}
				
					// Reset environment to initial state.
					env.rnn.reset();
					
					if (task == TASK_PREDICT) {
						// Run environment for a while so it can get into a stable dynamic (if it's going to).
						for (int step = 0; step < iterationCount; step++) {
							env.rnn.next(null, envOutput);
						}
					}
					
					boolean recordBehaviour = envIndex < noveltySearchEnvCount && trial == trialCount - 1 && behaviour != null;
	
					double[] prevAgentOutput = new double[predict];
					for (int step = 0; step < iterationCount; step++) {
						// Step environment.
						if (task == TASK_CONTROL) {
							// Provide previous output from agent as action/input to environment.
							env.rnn.next(agentOutput, envOutput);
						}
						else { //(task == TASK_PREDICT)
							env.rnn.next(null, envOutput);
							System.arraycopy(agentOutput, 0, prevAgentOutput, 0, predict);
						}

						assert !Double.isNaN(ArrayUtil.sum(envOutput));
						
						// Ask agent what it wants to do next (TASK_CONTROL) or what it thinks the next environment 
						// output will be (TASK_PREDICT), given output from environment.
						// In the case of the control task, this also provides agent with reinforcement signal in 
						// the last element of the array. The aim is to keep the output as close to 0 as possible, 
						// so the "reward" signal is actually inverted, but the evolved network doesn't care.
						nn.next(envOutput, agentOutput);
						
						if (Double.isNaN(ArrayUtil.sum(agentOutput))) {
							double[] agentOut = ((BainNN) nn).getNeuralNetwork().getNeurons().getOutputs();
							System.err.println(ArrayUtil.toString(agentOut, ", ", nf));
							System.err.println(nn);
						}
						
						// Calculate fitness.
						double stepReward = 0;
						if (task == TASK_CONTROL) {
							// Fitness/performance is based on how close the output is to zero.
							// The max output magnitude is 1 since we are using a (bipolar) Sigmoid activation function.
							int controlCount = 2;
							double controlOutputSum = 0;
							for (int oc = 0; oc < controlCount; oc++) {
								controlOutputSum += Math.abs(envOutput[envOutput.length - 1 - oc]);
							}
							stepReward = 1 - Math.abs(controlOutputSum / controlCount);
						}
						else { //(task == TASK_PREDICT)
							// Fitness/performance is based on how close the previous agent output is to the current 
							// output of the last fitness.function.rlrnn.predict nodes. 
							double error = 0;
							for (int o=0, e=envOutput.length-predict; o < predict; o++, e++) {
								double diff = prevAgentOutput[o] - envOutput[e];
								//error += diff * diff
								error += Math.abs(diff);
							}
							//error = Math.sqrt(error);
							stepReward = 1.0 / (1 + error);
						}
						
						// Only count reward for last half of evaluation.
						if (step >= iterationCount / 2)
							trialReward += stepReward;
						
						if (recordBehaviour && step >= nsStartRecordItr) {
							int behaviourIndex = (envIndex * nsRecordLength + (step-nsStartRecordItr)) * predict;
							for (int i = 0; i < predict; i++, behaviourIndex++) {
								behaviour.setEntry(behaviourIndex, (agentOutput[i] + 1) / 2);
								//behaviour.setEntry(behaviourIndex, (i + 1.0) / predict);
							}
						}
						
						if (logText) {
							double[] envActivation = env.rnn.getNeuralNetwork().getNeurons().getOutputs();
							int i = 0;
							if (task == TASK_CONTROL) {
								logOutput.put("    M: ");
								for (; i < manipulable; i++) {
									logOutput.put(nf.format(agentOutput[i]) + ", ");
								}
							}
							logOutput.put("  H: ");
							for (; i < networkSize - observable; i++) {
								logOutput.put(nf.format(envActivation[i]) + ", ");
							}
							logOutput.put("    O: ");
							for (; i < networkSize; i++) {
								logOutput.put(nf.format(envActivation[i]) + ", ");
							}
							if (task == TASK_PREDICT) {
								logOutput.put("    P: ");
								for (i = 0; i < predict; i++) {
									logOutput.put(nf.format(prevAgentOutput[i]) + ", ");
								}
								logOutput.put("  SE: " + nf.format((1.0 / stepReward)-1));
							}
							logOutput.put("\n");
						}
						
						if (logImage) {
							int yOffset = imgEnvHeight * envIndex;
							double[] envActivation = env.rnn.getNeuralNetwork().getNeurons().getOutputs();
							if (manipulable > 0) {
								for (int i = 0; i < manipulable; i++) {
									float c = (float) ((agentOutput[i] + 1) / 2);
									g.setColor(new Color(c, c, c));
									g.fillRect(step * imageScale, i * imageScale + yOffset, imageScale-1, imageScale-1);
								}
							}
							for (int i = manipulable; i < networkSize; i++) {
								float c = (float) ((envActivation[i] + 1) / 2);
								g.setColor(new Color(c, c, c));
								g.fillRect(step * imageScale, i * imageScale + yOffset, imageScale-1, imageScale-1);
							}
							if (task == TASK_PREDICT) {
								for (int i = 0; i < predict; i++) {
									float c = (float) ((prevAgentOutput[i] + 1) / 2);
									g.setColor(new Color(c, c, c));
									g.fillRect(step * imageScale, (i + networkSize) * imageScale + yOffset, imageScale-1, imageScale-1);
								}
							}
						}
					}
	
					trialReward /= iterationCount / 2;
					
					if (logText) {
						logOutput.put("\n    Trial reward: " + nf.format(trialReward) + "\n");
					}
	
					envReward += trialReward;
					avgRewardForEachTrial[trial] += trialReward;
				}
				reward += envReward / trialCount;
				
				// Run environment with no input from agent to see what the unperturbed dynamics are like.
				if (logImage && task == TASK_CONTROL) {
					BufferedImage image2 = new BufferedImage(iterationCount * imageScale, (networkSize - manipulable) * imageScale, BufferedImage.TYPE_BYTE_GRAY);
					Graphics2D g2 = image2.createGraphics();
	
					// Reset environment to initial state.
					env.rnn.reset();
	
					for (int step = 0; step < iterationCount; step++) {
						// Step environment.
						env.rnn.next();
						double[] envActivation = env.rnn.getNeuralNetwork().getNeurons().getOutputs();
						for (int i = manipulable; i < networkSize; i++) {
							float c = (float) ((envActivation[i] + 1) / 2);
							g2.setColor(new Color(c, c, c));
							g2.fillRect(step * imageScale, (i - manipulable) * imageScale, imageScale-1, imageScale-1);
						}
					}
	
					File outputfile = new File(baseFileName + ".timeseries.env_ " + envIndex + ".png");
					ImageIO.write(image2, "png", outputfile);
				}
	
				env.unlock();
			}

			for (int trial = 0; trial < trialCount; trial++) {
				avgRewardForEachTrial[trial] /= envCount;
			}
			if (logOutput != null) {
				logOutput.put("\n\nReward for each trial averaged over all environments:\n");
				logOutput.put(Arrays.toString(avgRewardForEachTrial));

				logOutput.close();
			}
			if (image != null) {
				File outputfile = new File(baseFileName + ".timeseries.png");
				ImageIO.write(image, "png", outputfile);
			}

			reward /= envCount;
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (fitnessValues != null && fitnessValues.length > 0) {
			fitnessValues[0] = reward;
			genotype.setPerformanceValue(reward);
		}
		
		if (behaviour != null) {
			behaviours[0] = new RealVectorBehaviour(behaviour);
		}
	}
	
	@Override
	public boolean evaluateGeneralisation(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues) {
		if (genEnvironments == null) {
			// Test on twice as many environments as used for fitness evaluations.
			genEnvironments = new Environment[environmentCount*2];
			for (int i = 0; i < environmentCount*2; i++) {
				genEnvironments[i] = new Environment();
				genEnvironments[i].setUp(environmentCounter++);
			}
		}
		_evaluate(genotype, substrate, baseFileName, logText, logImage, fitnessValues, null, genEnvironments);
		return true;
	}

	private boolean increaseDifficulty() {
		boolean increasedDifficulty = false;

		int inDegreeMax = props.getIntProperty(DEGREE_MAX);
		if (inDegree < inDegreeMax) {
			String deltaString = props.getProperty(DEGREE_INCREASE_DELTA).trim().toLowerCase();
			boolean isFactor = deltaString.endsWith("x");
			double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
			if (delta >= 1) {
				if (!isFactor) {
					inDegree += (int) Math.round(delta);
					increasedDifficulty = true;
				} else if (delta > 1) {
					inDegree = Math.max(1, (int) Math.round(inDegree * delta));
					increasedDifficulty = true;
				}

				if (inDegree > inDegreeMax)
					inDegree = inDegreeMax;
			}
		}

		double varianceMax = props.getDoubleProperty(WEIGHT_VARIANCE_MAX);
		if (weightVariance < varianceMax) {
			String deltaString = props.getProperty(WEIGHT_VARIANCE_INCREASE_DELTA).trim().toLowerCase();
			boolean isFactor = deltaString.endsWith("x");
			double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
			if (!isFactor || delta >= 1) {
				if (!isFactor) {
					weightVariance += delta;
					increasedDifficulty = true;
				} else if (delta > 1) {
					weightVariance *= delta;
					increasedDifficulty = true;
				}

				if (weightVariance > varianceMax)
					weightVariance = varianceMax;
			}
		}

		return increasedDifficulty;
	}

	private class Environment {
		public int id;
		public BainNN rnn = null;
		boolean locked = false;
		private ArrayList<Environment> instances = null;
		private boolean isCopyInstance = false;

		public Environment() {
			init();
			instances = new ArrayList<Environment>();
		}
		
		public Environment(Environment origEnv) {
			init();
			isCopyInstance = true;
			id = origEnv.id;
			
			NeuronCollectionWithBias origNeurons = (NeuronCollectionWithBias) origEnv.rnn.getNeuralNetwork().getNeurons();
			SynapseCollection origSynapses = origEnv.rnn.getNeuralNetwork().getSynapses();
			NeuronCollectionWithBias neurons = (NeuronCollectionWithBias) rnn.getNeuralNetwork().getNeurons();
			SynapseCollection synapses = rnn.getNeuralNetwork().getSynapses();
			
			for (int n = 0; n < networkSize; n++) {
				neurons.setBias(n, origNeurons.getBias(n));
			}
			int synapseCount = (networkSize - manipulable) * inDegree;
			for (int s = 0; s < synapseCount; s++) {
				synapses.setPreAndPostNeurons(s, origSynapses.getPreNeuron(s), origSynapses.getPostNeuron(s));
				synapses.setEfficacy(s, origSynapses.getEfficacy(s));
			}
			
			assert rnn.toString().equals(origEnv.rnn.toString());
		}
		
		private void init() {
			SigmoidBipolarNeuronCollection neurons = new SigmoidBipolarNeuronCollection(networkSize);
			neurons.addConfiguration(new SigmoidNeuronConfiguration()); // Default configuration for all neurons.

			int synapseCount = (networkSize - manipulable) * inDegree;
			FixedSynapseCollection synapses = new FixedSynapseCollection(synapseCount);

			NeuralNetwork nn = new NeuralNetwork(1000, neurons, synapses, Kernel.EXECUTION_MODE.SEQ);

			int[] inputDimensions = new int[] { manipulable };
			int[] outputDimensions = new int[] { observable };
			try {
				rnn = new BainNN(nn, inputDimensions, outputDimensions, 1, BainNN.Topology.RECURRENT);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		
		public synchronized void setUp(int id) {
			if (isCopyInstance) throw new IllegalStateException("Shouldn't be calling setUp() on an RLRecurrentNetworkBased.Environment which is an instance copy.");

			this.id = id;
			
			NeuronCollectionWithBias neurons = (NeuronCollectionWithBias) rnn.getNeuralNetwork().getNeurons();
			SynapseCollection synapses = rnn.getNeuralNetwork().getSynapses();
			// Create inDegree incoming connections from randomly selected neurons to all neurons except input
			// (manipulable by the agent) neurons.
			int synapseIndex = 0;
			// For each target neuron.
			for (int t = manipulable; t < networkSize; t++) {
				// Very small bias, makes environment more likely to be solvable and less likely to result in dynamics 
				// where neuron activation levels switch between limits (use in-degree and weight variance to
				// control difficulty).
				neurons.setBias(t, envRand.nextDouble() * 0.1 - 0.05);

				for (int d = 0; d < inDegree; d++) {
					// randomly select source neuron.
					int s = envRand.nextInt(networkSize);
					synapses.setPreAndPostNeurons(synapseIndex, s, t);
					// if (t < networkSize-1) {
					// Set weight to random value with magnitude according to current variance.
					synapses.setEfficacy(synapseIndex, envRand.nextGaussian() * weightVariance);
					// }
					// else {
					// synapses.setEfficacy(synapseIndex, 1);
					// }
					synapseIndex++;
				}
			}
			
			instances.clear();
			instances.add(this);
		}
		
		public void increaseVariance(int id, double factor) {
			this.id = id;
			
			SynapseCollection synapses = rnn.getNeuralNetwork().getSynapses();
			for (int synapseIndex = 0; synapseIndex < synapses.getSize(); synapseIndex++) {
				double e = synapses.getEfficacy(synapseIndex);
				e *= factor;
				synapses.setEfficacy(synapseIndex, e);
			}
		}
		
		public synchronized Environment getInstance() {
			for (Environment e : instances) {
				if (!e.locked) {
					e.locked = true;
					return e;
				}
			}
			Environment newInst = new Environment(this);
			instances.add(newInst);
			newInst.locked = true;
			return newInst;
		}
		
		public synchronized void unlock() {
			locked = false;
		}
		
		public String toString() {
			return rnn.toString();
		}
	}
	
	@Override
	public void ahniEventOccurred(AHNIEvent event) {
		if (event.getType() == AHNIEvent.Type.GENERATION_END) {
			Chromosome bestPerforming = event.getEvolver().getBestPerformingFromLastGen();
			if (bestPerforming.getPerformanceValue() >= props.getDoubleProperty(DIFFICULTY_INCREASE_PERFORMANCE)) {
				double previousWeightVariance = weightVariance;
				int previousInDegree = inDegree;
				if (increaseDifficulty()) {
					event.getEvolver().logChamp(bestPerforming, true, "");

					// Create new environment networks to allow for possibly increased number of connections.
					for (int e = 0; e < environments.length; e++) {
						// If the in-degree has changed, create all new environments.
						if (previousInDegree != inDegree) {
							environments[e] = new Environment();
							environments[e].setUp(environmentCounter++);
						}
						// Otherwise just increase the weight variance of existing connections by multiplying them by
						// the variance increase factor.
						else {
							environments[e].increaseVariance(environmentCounter++, weightVariance / previousWeightVariance);
						}
					}
					logger.info("Increased difficulty. In-degree and weight variance are now " + inDegree + " and " + nf.format(weightVariance) + ". " + (previousInDegree == inDegree ? "Existing environments were modified to increase the weight variance." : "All new environments were created."));
				}
			}
		}
	}

	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) // Input layer.
			return new int[] { observable, 1 };
		else if (layer == totalLayerCount - 1) // Output layer.
			return new int[] { task == TASK_CONTROL ? manipulable : predict, 1 };
		return null;
	}

	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[observable];
			if (observable == 1) {
				positions[0] = new Point(0.5, 0.5, 0);
			}
			else {
				for (int i = 0; i < observable; i++) {
					positions[i] = new Point((double) i / (observable - 1), 0.5, 0);
				}
			}
		} else if (layer == totalLayerCount - 1) { // Output layer.
			int outCount = task == TASK_CONTROL ? manipulable : predict;
			positions = new Point[outCount];
			if (outCount == 1) {
				positions[0] = new Point(0.5, 0.5, 1);
			}
			else {
				for (int i = 0; i < outCount; i++) {
					positions[i] = new Point((double) i / (outCount - 1), 0.5, 1);
				}
			}
		}
		return positions;
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
		if (!noveltySearchOnly) labels[i++] = "Reward";
		if (noveltySearchEnabled) labels[i++] = "Novelty";
		return labels;
	}
	
	@Override
	public boolean fitnessValuesStable() {
		return false; // environmentReplaceProb == 0;
	}

	private String makePad(int l) {
		return new String(new char[l]).replace('\0', ' ');
	}
}
