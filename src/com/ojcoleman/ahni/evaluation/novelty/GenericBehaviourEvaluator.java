package com.ojcoleman.ahni.evaluation.novelty;

import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.NNAdaptor;
import com.ojcoleman.ahni.util.ArrayUtil;

public class GenericBehaviourEvaluator extends BulkFitnessFunctionMT {
	private static final long serialVersionUID = 1L;
	
	/**
	 * The number of sequences to test individuals on.
	 */
	public static final String SEQUENCES = "fitness.function.generic_novelty.sequence_count";
	/**
	 * The number of output samples to record for each sequence.
	 */
	public static final String SAMPLES = "fitness.function.generic_novelty.sample_count";
	/**
	 * Output samples will be taken every [fitness.function.generic_novelty.sampling_interval]th step in the sequence. Default is 1 (take a sample every step).
	 */
	public static final String SAMPLING_INTERVAL = "fitness.function.generic_novelty.sampling_interval";
	/**
	 * The minimum input value. Default is 0.
	 */
	public static final String MIN_VALUE = "fitness.function.generic_novelty.input.min";
	/**
	 * The maximum input value. Default is 1.
	 */
	public static final String MAX_VALUE = "fitness.function.generic_novelty.input.max";

	
	protected int sequenceCount;
	protected int sampleCount;
	protected int samplingInterval;
	protected int outputSize;
	protected double minOutput;
	protected double maxOutput;
	protected double outputRange;
	protected Properties props;
	protected double[][][][] input;
	
	@Override
	public void init(Properties props) {
		this.props = props;
		sequenceCount = props.getIntProperty(SEQUENCES);
		sampleCount = props.getIntProperty(SAMPLES);
		samplingInterval = props.getIntProperty(SAMPLING_INTERVAL, 1);
	}
	
	@Override
	public int fitnessObjectivesCount() {
		return 0;
	}
	
	@Override
	public int noveltyObjectiveCount() {
		return 1;
	}
	
	@Override
	public boolean fitnessValuesStable() {
		return true;
	}

	@Override
	protected void evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, double[] fitnessValues, Behaviour[] behaviours) {
		synchronized (this) {
			if (input == null) {
				// Initial set-up based on first instance of a substrate.
				int inputSize = substrate.getInputCount();
				outputSize = substrate.getOutputCount();
				minOutput = substrate.getMinResponse();
				maxOutput = substrate.getMaxResponse();
				if (minOutput < -Double.MAX_VALUE*0.5 || maxOutput > Double.MAX_VALUE*0.5) {
					throw new IllegalStateException("Substrates with output ranges greater than Doubele.MAX_VALUE not currently supported.");
				}
				outputRange = maxOutput - minOutput;
				Random random = props.getEvolver().getConfig().getRandomGenerator();
				double minInput = props.getDoubleProperty(MIN_VALUE, 0);
				double maxInput = props.getDoubleProperty(MAX_VALUE, 1);
				
				input = new double[sequenceCount][sampleCount][samplingInterval][inputSize];
				for (int sequence = 0; sequence < sequenceCount; sequence++) {
					for (int sample = 0; sample < sampleCount; sample++) {
						for (int intervalCount = 0; intervalCount < samplingInterval; intervalCount++) {
							input[sequence][sample][intervalCount] = ArrayUtil.newRandom(inputSize, random, minInput, maxInput);
						}
					}
				}
			}
		}
		
		NNAdaptor nn = (NNAdaptor) substrate;
		ArrayRealVector behaviour = new ArrayRealVector(outputSize * sequenceCount * sampleCount);
		int behaviorIndex = 0;
		double[] output = new double[outputSize];
		for (int sequence = 0; sequence < sequenceCount; sequence++) {
			nn.reset();
			for (int sample = 0; sample < sampleCount; sample++) {
				nn.next(input[sequence][sample][0], output);
				for (int intervalCount = 1; intervalCount < samplingInterval - 1; intervalCount++) {
					nn.next(input[sequence][sample][intervalCount], output);
				}
				if (minOutput < 0 || maxOutput > 1) {
					// Normalise output values to range [0, 1] (suitable for RealVectorBehaviour).
					for (int o = 0; o < outputSize; o++) {
						output[o] = (output[o] - minOutput) / outputRange;
					}
				}
				behaviour.setSubVector(behaviorIndex, output);
				behaviorIndex += outputSize;
			}
		}
		behaviours[0] = new RealVectorBehaviour(behaviour);
	}
}
