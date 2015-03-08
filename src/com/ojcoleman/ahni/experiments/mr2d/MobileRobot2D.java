package com.ojcoleman.ahni.experiments.mr2d;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Random;

import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;
import com.ojcoleman.ahni.evaluation.novelty.RealVectorBehaviour;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.GifSequenceWriter;
import com.ojcoleman.ahni.util.NiceWriter;
import com.ojcoleman.ahni.util.Point;

/**
 * A fitness function that assesses agents on automatically generated 2D mobile robot tasks.
 */
public class MobileRobot2D extends BulkFitnessFunctionMT {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(MobileRobot2D.class);
	private static final NumberFormat nf = new DecimalFormat("0.00");
	
	
	/**
	 * The environment class to use for simulations. Defaults to com.ojcoleman.ahni.experiments.mr2d.Dyn4JEnvironment.
	 */
	public static final String ENVIRONMENT_CLASS = "fitness.function.mr2d.env.class";
	
	/**
	 * The number of environments to evaluate candidates against. Increasing this will provide a more accurate
	 * evaluation but take longer. If the value is <= 0 then the number of environments to use will be determined
	 * automatically.
	 */
	public static final String ENVIRONMENT_COUNT = "fitness.function.mr2d.env.count";
	
	/**
	 * If and what type of reward values should be assigned to food/poison object types. Possible values are
	 * "simple", "complex" and "real" with respective possible reward values of {}, {-1, 1}, {-1, -0.5, 0, 0.5, 1}
	 * and any real value in range [-1, 1]. Default is "simple". If "none" is given then no food/poison object types are included.
	 */
	public static final String ENVIRONMENT_OBJECT_TYPE_FOOD_POISON = "fitness.function.mr2d.env.object.type.food_poison";
	/**
	 * How many "pushable" object types to include. A pushable object type may be pushed around but has no other effect.
	 * Default is 0.
	 */
	public static final String ENVIRONMENT_OBJECT_TYPE_PUSHABLE = "fitness.function.mr2d.env.object.type.pushable";
	/**
	 * The number of objects.
	 */
	public static final String ENVIRONMENT_OBJECT_COUNT = "fitness.function.mr2d.env.object.count";

	/**
	 * The fraction of environments that should be replaced with new environments per generation. This is evaluated
	 * probabilistically.
	 */
	public static final String ENVIRONMENT_CHANGE_RATE = "fitness.function.mr2d.env.replacerate";
	
	/**
	 * Seed to use to generate and simulate environments.
	 */
	public static final String ENV_RANDOM_SEED = "fitness.function.mr2d.env.randomseed";
	
	/**
	 * The number of environment simulation steps per simulated second. The higher this is the more accurate the simulation. Default is 30. 
	 */
	public static final String ENV_STEPS_PER_SEC = "fitness.function.mr2d.env.steps_per_sec";

	
	/**
	 * The maximum length of time in seconds that the agent will spend in an environment (the simulation is terminated early if the environment
	 * is "completed", for example all food items collected or some other goal has been reached). Default is 10s.
	 */
	public static final String AGENT_LIFETIME = "fitness.function.mr2d.agent.lifetime";	

	/**
	 * The number of range and colour sensors on the robot.
	 */
	public static final String AGENT_SENSOR_COUNT = "fitness.function.mr2d.agent.sensor.count";
	
	/**
	 * The view angle of the range and colour sensors on the robot.
	 */
	public static final String AGENT_SENSOR_VIEW_ANGLE = "fitness.function.mr2d.agent.sensor.viewangle";

	/**
	 * Factor controlling linear speed of the agent. Default is 1.
	 */
	public static final String AGENT_SPEED_LINEAR = "fitness.function.mr2d.agent.speed.linear";

	/**
	 * Factor controlling rotational speed of the agent. Default is 1.
	 */
	public static final String AGENT_SPEED_ROTATION = "fitness.function.mr2d.agent.speed.rotation";

	
	/**
	 * If true enables novelty search for which behaviours are defined by the current state for each step of each trial
	 * of each environment. Default is false.
	 */
	public static final String NOVELTY_SEARCH = "fitness.function.mr2d.noveltysearch";
	/**
	 * If set to an integer > 0 then this many environments will be used to characterise an agents behaviour for novelty
	 * search. Defaults to fitness.function.mr2d.env.count.
	 */
	public static final String NOVELTY_SEARCH_ENV_COUNT = "fitness.function.mr2d.noveltysearch.envs.count";
	/**
	 * The number of samples to take of each environment to generate the behaviour. Default is 1.
	 */
	public static final String NOVELTY_SEARCH_SAMPLES_PER_ENV = "fitness.function.mr2d.noveltysearch.samples_per_env";
	/**
	 * If true then makes novelty search the only objective. Performance values are still calculated. Default is false. 
	 * If true then fitness.function.mr2d.noveltysearch is forced to true and fitness.function.mr2d.env.replacerate is 
	 * forced to 0, and fitness.function.mr2d.noveltysearch.envs.count is forced to fitness.function.mr2d.env.count, 
	 * so that the same environments are used for calculating performance and novelty.
	 */
	public static final String NOVELTY_SEARCH_ONLY = "fitness.function.mr2d.noveltysearch.only";

	private Class environmentClass;
	private int environmentCount;
	private double environmentReplaceProb;
	private int sensorCount;
	private double sensorViewAngle;
	private double agentSpeedLinearFactor, agentSpeedRotationFactor;
	private double agentLifetime;
	private EnvironmentDescription environmentDescriptionSingleton;
	private EnvironmentDescription[] environmentDescription;
	private EnvironmentDescription[] nsEnvironmentDescription;
	private EnvironmentDescription[] genEnvironmentDescription;
	private int environmentCounter = 0;
	private boolean noveltySearchEnabled = false;
	private boolean noveltySearchOnly = false;
	private int noveltySearchEnvCount;
	private int noveltySearchSamplesPerEnv;
	private int totalNSBehaviourSize;
	long envRandomSeed;
	private Random envRandom;
	private double envStepPeriod;
	private int totalSimSteps;
	
	@Override
	public void init(Properties props) {
		this.props = props;
		
		try {
			environmentClass = Class.forName(props.getProperty(ENVIRONMENT_CLASS, "com.ojcoleman.ahni.experiments.mr2d.Dyn4JEnvironment").trim());
		} catch (ClassNotFoundException e1) {
			System.err.println("Could not load class " + props.getProperty(ENVIRONMENT_CLASS)); 
			e1.printStackTrace();
		}
		noveltySearchOnly = props.getBooleanProperty(NOVELTY_SEARCH_ONLY, false);
		noveltySearchEnabled = noveltySearchOnly || props.getBooleanProperty(NOVELTY_SEARCH, false);
		noveltySearchSamplesPerEnv = props.getIntProperty(NOVELTY_SEARCH_SAMPLES_PER_ENV, 1);

		environmentCount = props.getIntProperty(ENVIRONMENT_COUNT);
		environmentReplaceProb = noveltySearchOnly ? 0 : props.getDoubleProperty(ENVIRONMENT_CHANGE_RATE);
		
		sensorCount = props.getIntProperty(AGENT_SENSOR_COUNT);
		sensorViewAngle = Math.toRadians(props.getIntProperty(AGENT_SENSOR_VIEW_ANGLE));
		agentLifetime = props.getDoubleProperty(AGENT_LIFETIME);
		agentSpeedLinearFactor = props.getDoubleProperty(AGENT_SPEED_LINEAR, 0.5);
		agentSpeedRotationFactor = props.getDoubleProperty(AGENT_SPEED_ROTATION, Math.PI);
		
		envRandomSeed = props.getLongProperty(ENV_RANDOM_SEED, System.currentTimeMillis());
		logger.info("Environment generation random seed is " + envRandomSeed);
		envRandom = new Random(envRandomSeed);
		envStepPeriod = 1.0 / props.getDoubleProperty(ENV_STEPS_PER_SEC, 30);
		totalSimSteps = (int) Math.round(agentLifetime / envStepPeriod);
		
		environmentDescriptionSingleton = props.singletonObjectProperty(EnvironmentDescription.class);
		environmentDescription = new EnvironmentDescription[environmentCount];
		
		if (noveltySearchEnabled) {
			noveltySearchEnvCount = noveltySearchOnly ? environmentCount : props.getIntProperty(NOVELTY_SEARCH_ENV_COUNT, environmentCount);
			totalNSBehaviourSize = 0;

			// If the same environments aren't used throughout evolution.
			if (environmentReplaceProb > 0) {
				// Create a set of environments (that don't change over the course of evolution) to test novelty on.
				nsEnvironmentDescription = new EnvironmentDescription[noveltySearchEnvCount];
				totalNSBehaviourSize = 0;
				for (int e = 0; e < noveltySearchEnvCount; e++) {
					nsEnvironmentDescription[e] = environmentDescriptionSingleton.generateInstance(-e, envRandom, this);
					totalNSBehaviourSize += nsEnvironmentDescription[e].getNoveltyDescriptionLength(this);
				}
				logger.info("Created " + noveltySearchEnvCount + " environments for novelty search.");
			}
			else {
				if (noveltySearchEnvCount > environmentCount) {
					throw new IllegalArgumentException("The number of environments used for novelty search must not be greater than the number used for fitness/performance evaluation when " + ENVIRONMENT_CHANGE_RATE + " = 0.");
				}
				initialiseEvaluation(); // Create environments now so we can determine totalNSBehaviourSize.
				for (int i = 0; i < noveltySearchEnvCount; i++) {
					totalNSBehaviourSize += environmentDescription[i].getNoveltyDescriptionLength(this);
				}
			}
			logger.info("Novelty search behaviours have " + totalNSBehaviourSize + " dimensions.");
		}

		super.init(props);
	}


	@Override
	public void initialiseEvaluation() {
		// Create (some) new environments every generation.
		for (int i = 0; i < environmentCount; i++) {
			if (environmentDescription[i] == null || environmentReplaceProb > envRandom.nextDouble()) {
				environmentDescription[i] = environmentDescriptionSingleton.generateInstance(environmentCounter++, envRandom, this);
			}
		}
	}

	@Override
	protected void evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, double[] fitnessValues, Behaviour[] behaviours) {
		if (nsEnvironmentDescription == null) {
			// Evaluate fitness and behaviour on same environments.
			_evaluate(genotype, substrate, null, false, false, fitnessValues, behaviours, environmentDescription);
		} else {
			// Evaluate fitness on changing environments and behaviour on fixed novelty search environments.
			_evaluate(genotype, substrate, null, false, false, fitnessValues, null, environmentDescription);
			_evaluate(genotype, substrate, null, false, false, null, behaviours, nsEnvironmentDescription);
		}
	}

	@Override
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		_evaluate(genotype, substrate, baseFileName, logText, logImage, null, null, environmentDescription);
	}

	public void _evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues, Behaviour[] behaviours, EnvironmentDescription[] environmentDesc) {
		super.evaluate(genotype, substrate, baseFileName, logText, logImage);
		int solvedCount = 0;
		double reward = 0, performance = 0;
		int envCount = environmentDesc.length;
		
		ArrayRealVector behaviour = behaviours != null && behaviours.length > 0 ? new ArrayRealVector(totalNSBehaviourSize) : null;
		int behaviourIndex = 0;
		int behaviourRecordIntervalSteps = totalSimSteps / noveltySearchSamplesPerEnv;
		
		int imageSize = 512;
		
		try {
			NiceWriter logOutput = !logText ? null : new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
			BufferedImage image = null;
			Graphics2D imageGraphics = null;
			ImageOutputStream imageOS = null;
			GifSequenceWriter animWriter = null;
			
			double[] input = null, output = null;

			assert environmentDesc.length > 0;
			int envIndex = 0;
			for (EnvironmentDescription envDesc : environmentDesc) {
				Environment env = (Environment) environmentClass.newInstance();
				env.init(props, this, envDesc);
				env.reset();
				
				if (logText) {
					logOutput.put("\n\nBEGIN EVALUATION ON ENVIRONMENT " + envDesc.id + "\n");
					logOutput.put("min-max reward: " + envDesc.getReasonableRewardRange() + "\n");
				}
				if (logImage) {
					image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_3BYTE_BGR);
					imageGraphics = (Graphics2D) image.createGraphics().create(0, 0, imageSize, imageSize);
					imageOS = new FileImageOutputStream(new File(baseFileName + "-" + envDesc.id + ".gif"));
					animWriter = new GifSequenceWriter(imageOS, image.getType(), (int) Math.round(this.getEnvStepPeriod() * 1000), false);
				
					env.render(imageGraphics);
					animWriter.writeToSequence(image);
				}
				
				// Reset substrate to initial state to begin learning new environment.
				substrate.reset();
			
				// The network can perform a number of steps equal to the number of states times two.
				double rewardForEnv = 0;
				for (int step = 0; step < totalSimSteps; step++) {
					env.step(substrate);
					
					rewardForEnv += env.getRewardFromLastStep();
					
					// Record environment state at one second intervals.
					// (don't record initial state, or record (near) final state (that gets recorded outside this loop)
					if (behaviour != null && envIndex < noveltySearchEnvCount && step % behaviourRecordIntervalSteps == 0 && step > 0 && step < totalSimSteps - behaviourRecordIntervalSteps / 2) {
						behaviourIndex = env.getNoveltyDescription(behaviour, behaviourIndex, false);
					}


					if (logText) {
						input = env.getInputForLastStep();
						output = env.getOutputForLastStep();
						logOutput.put("    Input: " + ArrayUtil.toString(input, ", ", nf) + "\n");
						logOutput.put("    Output: " + ArrayUtil.toString(output, ", ", nf) + "\n");
						logOutput.put("    Reward: " + nf.format(env.getRewardFromLastStep()) + "\n\n");
					}
					if (logImage) {
						env.render(imageGraphics);
						animWriter.writeToSequence(image);
					}
				}
				
				rewardForEnv = envDesc.getRewardRange().translateToUnit(rewardForEnv);
				double performanceForEnv = envDesc.getReasonableRewardRange().translateToUnit(rewardForEnv);
				if (performanceForEnv >= 1) {
					performanceForEnv = 1;
					solvedCount++;
				}
				reward += rewardForEnv;
				performance += performanceForEnv;
				
				if (behaviour != null && envIndex < noveltySearchEnvCount) {
					behaviourIndex = env.getNoveltyDescription(behaviour, behaviourIndex, true);
				}
				
				if (logImage) {
					animWriter.close();
					imageOS.close();
				}
				
				envIndex++;
			}
			
			if (logText) {
				logOutput.close();
			}
			
			reward /= envCount;
			performance /= envCount;
			
			if (fitnessValues != null && fitnessValues.length > 0) {
				fitnessValues[0] = reward;
			}
			genotype.setPerformanceValue("0Reward", reward);
			genotype.setPerformanceValue("1Performance", performance);
			genotype.setPerformanceValue("2Solved", (double) solvedCount / envCount);
			
			if (behaviour != null) {
				assert behaviourIndex == behaviour.getDimension() : "behaviourIndex is " + behaviourIndex + " but behaviour.getDimension() is " + behaviour.getDimension();
				behaviours[0] = new RealVectorBehaviour(behaviour);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean evaluateGeneralisation(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues) {
		if (props.getEvolver().getGeneration() > 0) {
			if (genEnvironmentDescription == null) {
				// Test on twice as many environments as used for fitness evaluations.
				genEnvironmentDescription = new EnvironmentDescription[environmentCount*2];
				for (int i = 0; i < environmentCount*2; i++) {
					genEnvironmentDescription[i] = environmentDescriptionSingleton.generateInstance(environmentCounter++, envRandom, this);
				}
			}
			_evaluate(genotype, substrate, baseFileName, logText, logImage, fitnessValues, null, genEnvironmentDescription);
			return true;
		}
		return false;
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

	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) { // Input layer.
			// Sensors (range + colour) plus reinforcement signal.
			return new int[] { sensorCount * 2 + 1, 1 };
		} else if (layer == totalLayerCount - 1) { // Output layer.
			// Action to perform next.
			return new int[] { 2, 1 };
		}
		return null;
	}

	@Override
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		Point[] positions = null;
		if (layer == 0) { // Input layer.
			positions = new Point[sensorCount * 2 + 1];
			int posIndex = 0;
			// Current state.
			for (int si = 0; si < sensorCount; si++) {
				positions[posIndex++] = new Point((double) si / (sensorCount - 1), 0, 0);
				positions[posIndex++] = new Point((double) si / (sensorCount - 1), 0.5, 0);
			}
			// Reinforcement signal.
			positions[posIndex++] = new Point(1, 1, 0);
		} else if (layer == totalLayerCount - 1) { // Output layer.
			positions = new Point[2];
			positions[0] = new Point(0, 0.5, 1);
			positions[1] = new Point(1, 0.5, 1);
		}
		return positions;
	}
	
	
	/**
	 * The step period of the environment simulation in seconds. This is the amount of 
	 * time that elapses within the simulation between each step. 
	 */
	public double getEnvStepPeriod() {
		return envStepPeriod;
	}
	
	public int getAgentSensorCount() {
		return sensorCount;
	}
	public double getAgentSensorViewAngle() {
		return sensorViewAngle;
	}
	public double getAgentSpeedLinearFactor() {
		return agentSpeedLinearFactor;
	}
	public double getAgentSpeedRotationFactor() {
		return agentSpeedRotationFactor;
	}
	public double getAgentLifetime() {
		return agentLifetime;
	}
	public int getNoveltySearchSamplesPerEnv() {
		return noveltySearchSamplesPerEnv;
	}
}
