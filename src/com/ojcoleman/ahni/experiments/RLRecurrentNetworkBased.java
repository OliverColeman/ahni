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
import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.amd.aparapi.Kernel;
import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.event.AHNIEvent;
import com.ojcoleman.ahni.event.AHNIEventListener;
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
	 * inputs to the environment network.
	 */
	public static final String MANIPULABLE = "fitness.function.rlrnn.manipulable";

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

	private int environmentCount;
	private double environmentReplaceProb;
	private int trialCount;
	private int iterationCount;
	private int networkSize;
	private int observable;
	private int manipulable;
	private int inDegree;
	private int inDegreeMax;
	private double weightVariance;
	private double weightVarianceMax;
	private double difficultyIncreasePerformance;
	private Environment[] environments;
	private int environmentCounter = 0;
	private Random envRand;

	@Override
	public void init(Properties props) {
		super.init(props);

		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		environmentReplaceProb = props.getDoubleProperty(ENVIRONMENT_CHANGE_RATE);
		trialCount = props.getIntProperty(TRIAL_COUNT);
		iterationCount = props.getIntProperty(ITERATION_COUNT);

		networkSize = props.getIntProperty(SIZE);
		observable = Math.max(1, props.getIntProperty(OBSERVABLE));
		manipulable = Math.max(1, props.getIntProperty(MANIPULABLE));
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

		if (environmentCount > numThreads) {
			logger.warn("The number of environments is less than the number of threads used for evaluation. This will waste CPU cycles while a thread waits for an available environment. It is recommended to set the number of threads to be less than or equal to the number of environments.");
		}

		if (observable + manipulable > networkSize) {
			throw new IllegalArgumentException("The number of observable plus number of manipulable nodes must not be greater than the network size (it doesn't make sense to allow these sets to overlap).");
		}

		((Properties) props).getEvolver().addEventListener(this);
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
	protected double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return evaluate(genotype, substrate, null, false, false);
	}

	@Override
	public double evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		double reward = 0;
		NNAdaptor nn = (NNAdaptor) substrate;

		if (substrate.getInputCount() != observable || substrate.getOutputCount() != manipulable) {
			throw new IllegalArgumentException("Substrate input (or output) size does not match number of observable (manipulable) environment nodes.");
		}

		try {
			NiceWriter logOutput = !logText ? null : new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
			int imageScale = 8;

			double[] avgRewardForEachTrial = new double[trialCount];

			// We need to evaluate over all environments, but other threads are also evaluating over the
			// same environments, so find an available one or wait for one to become available.
			int environmentsDone = 0;
			boolean[] environmentDone = new boolean[environmentCount];
			int envIndex = 0;
			while (environmentsDone < environmentCount) {
				// Keep cycling through all environments until we find one that we haven't done and that we can obtain a
				// lock on.
				// Assuming the number of environments is >= the number of threads this doesn't waste CPU cycles.
				Environment env = environments[envIndex];
				if (!environmentDone[envIndex] && env.lock()) {
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

						BufferedImage image = null;
						Graphics2D g = null;
						if (logText) {
							logOutput.put("\n  BEGIN TRIAL " + (trial + 1) + " of " + trialCount + "\n");
						}
						if (baseFileName != null && logImage) {
							// Log activation levels to time series plot.
							image = new BufferedImage(iterationCount * imageScale, networkSize * imageScale, BufferedImage.TYPE_BYTE_GRAY);
							g = image.createGraphics();
						}
					
						// Reset environment to initial state.
						env.rnn.reset();

						for (int step = 0; step < iterationCount; step++) {
							// Step environment, providing previous output from agent as action/input to environment.
							env.rnn.next(agentOutput, envOutput);

							// Ask agent what it wants to do next, given output from environment.
							// This also provides agent with reinforcement signal in the last element of the array.
							// The aim is to keep the output as close to 0 as possible, so the "reward" signal is
							// actually inverted, but the evolved network doesn't care.
							nn.next(envOutput, agentOutput);

							// Fitness/performance is based on how the close the output is to zero.
							// The max output magnitude is 1 since we are using a (bipolar) Sigmoid activation function.
							int controlCount = 2;
							double controlOutputSum = 0;
							for (int oc = 0; oc < controlCount; oc++) {
								controlOutputSum += Math.abs(envOutput[envOutput.length - 1 - oc]);
							}
							double stepReward = 1 - Math.abs(controlOutputSum / controlCount);

							if (logText) {
								logOutput.put("    ");
								for (int i = 0; i < manipulable; i++) {
									logOutput.put(nf.format(agentOutput[i]) + ", ");
								}
								double[] envActivation = env.rnn.getNeuralNetwork().getNeurons().getOutputs();
								for (int i = manipulable; i < networkSize; i++) {
									logOutput.put(nf.format(envActivation[i]) + ", ");
								}
								logOutput.put("\n");
							}
							if (logImage) {
								for (int i = 0; i < manipulable; i++) {
									float c = (float) ((agentOutput[i] + 1) / 2);
									g.setColor(new Color(c, c, c));
									g.fillOval(step * imageScale, i * imageScale, imageScale, imageScale);
								}
								double[] envActivation = env.rnn.getNeuralNetwork().getNeurons().getOutputs();
								for (int i = manipulable; i < networkSize; i++) {
									float c = (float) ((envActivation[i] + 1) / 2);
									g.setColor(new Color(c, c, c));
									if (i < networkSize - observable) {
										g.fillRect(step * imageScale, i * imageScale, imageScale, imageScale);
									} else {
										g.fillOval(step * imageScale, i * imageScale, imageScale, imageScale);
									}
								}
							}

							// Only count reward for last half of evaluation.
							if (step * 2 >= iterationCount)
								trialReward += stepReward;
						}

						trialReward /= iterationCount / 2;

						envReward += trialReward;
						avgRewardForEachTrial[trial] += trialReward;

						if (image != null) {
							File outputfile = new File(baseFileName + ".timeseries.env_ " + envIndex + ".trial_" + trial + ".png");
							ImageIO.write(image, "png", outputfile);
						}
					}
					reward += envReward / trialCount;

					// Run environment with no input from agent to see what the unperturbed dynamics are like.
					if (logImage) {
						BufferedImage image = new BufferedImage(iterationCount * imageScale, (networkSize - manipulable) * imageScale, BufferedImage.TYPE_BYTE_GRAY);
						Graphics2D g = image.createGraphics();

						// Reset environment to initial state.
						env.rnn.reset();

						for (int step = 0; step < iterationCount; step++) {
							// Step environment.
							env.rnn.next();
							double[] envActivation = env.rnn.getNeuralNetwork().getNeurons().getOutputs();
							for (int i = manipulable; i < networkSize; i++) {
								float c = (float) ((envActivation[i] + 1) / 2);
								g.setColor(new Color(c, c, c));
								g.fillRect(step * imageScale, (i - manipulable) * imageScale, imageScale, imageScale);
							}
						}

						File outputfile = new File(baseFileName + ".timeseries.env_ " + envIndex + ".png");
						ImageIO.write(image, "png", outputfile);
					}

					environmentsDone++;
					environmentDone[envIndex] = true;
					env.unlock();
				}

				envIndex = (envIndex + 1) % environmentCount;
			}

			for (int trial = 0; trial < trialCount; trial++) {
				avgRewardForEachTrial[trial] /= environmentCount;
			}
			if (logOutput != null) {
				logOutput.put("\n\nReward for each trial averaged over all environments:\n");
				logOutput.put(Arrays.toString(avgRewardForEachTrial));

				logOutput.close();
			}

			reward /= environmentCount;
			return reward;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
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

		public Environment() {
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

		public void setUp(int id) {
			this.id = id;

			NeuronCollectionWithBias neurons = (NeuronCollectionWithBias) rnn.getNeuralNetwork().getNeurons();
			SynapseCollection synapses = rnn.getNeuralNetwork().getSynapses();
			// Create inDegree incoming connections from randomly selected neurons to all neurons except input
			// (manipulable by the agent) neurons.
			int synapseIndex = 0;
			// For each target neuron.
			for (int t = manipulable; t < networkSize; t++) {
				// Very small bias, makes environment more likely to be solvable (use in-degree and weight variance to
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

		public synchronized boolean lock() {
			if (locked)
				return false;
			locked = true;
			return true;
		}

		public synchronized boolean isLocked() {
			return true;
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
					event.getEvolver().logChamp(bestPerforming, true);

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
			return new int[] { manipulable, 1 };
		return null;
	}

	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[observable];
			for (int i = 0; i < observable; i++) {
				positions[i] = new Point((double) i / (observable - 1), 0.5, 0);
			}
		} else if (layer == totalLayerCount - 1) { // Output layer.
			positions = new Point[manipulable];
			for (int i = 0; i < manipulable; i++) {
				positions[i] = new Point((double) i / (manipulable - 1), 0.5, 1);
			}
		}
		return positions;
	}

	private String makePad(int l) {
		return new String(new char[l]).replace('\0', ' ');
	}
}
