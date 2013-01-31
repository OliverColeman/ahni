package com.ojcoleman.ahni.evaluation;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;


import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.Activator;
import com.anji.integration.TranscriberException;
import com.ojcoleman.ahni.event.AHNIEvent;
import com.ojcoleman.ahni.event.AHNIEventListener;
import com.ojcoleman.ahni.event.AHNIEvent.Type;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.NiceWriter;

/**
 * <p>Given a set of genotypes that encode a neural network, and a set of training examples consisting of input and target
 * (desired) output pattern pairs, determines fitness based on how close the output of the network encoded by each
 * genome is to the target output given some input.</p>
 * 
 * <p>See {@link com.ojcoleman.ahni.evaluation.TargetFitnessCalculator} for a list of property keys to specify how the error and fitness calculations are performed.</p>
 * 
 * @author Oliver Coleman
 */
public class TargetFitnessFunctionMT extends BulkFitnessFunctionMT {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(TargetFitnessFunctionMT.class);
	
	private TargetFitnessCalculator fitnessCalculator;

	private double[][] inputPatterns;
	private double[][] targetOutputPatterns;
	private double minTargetOutputValue;
	private double maxTargetOutputValue;

	protected TargetFitnessFunctionMT() {
	}

	/**
	 * Create a TargetFitnessFunctionMT with the specified input and output examples.
	 * 
	 * @param inputPatterns Array containing stimuli (input) examples, in the form [trial][input]. The dimensions should match those of the
	 * input layer of the substrate network.
	 * @param targetOutputPatterns Array containing target response (output) examples, in the form [trial][output]. The dimensions should match those of the
	 * output layer of the substrate network.
	 * @param minTargetOutputValue The smallest value that occurs in the target outputs.
	 * @param maxTargetOutputValue The largest value that occurs in the target outputs.
	 */
	public TargetFitnessFunctionMT(double[][] inputPatterns, double[][] targetOutputPatterns, double minTargetOutputValue, double maxTargetOutputValue) {
		this.inputPatterns = inputPatterns;
		this.targetOutputPatterns = targetOutputPatterns;
		this.minTargetOutputValue = minTargetOutputValue;
		this.maxTargetOutputValue = maxTargetOutputValue;
	}

	public void init(Properties props) {
		super.init(props);
		fitnessCalculator = (TargetFitnessCalculator) props.newObjectProperty(TargetFitnessCalculator.class);
	}

	/**
	 * @return maximum possible fitness value for this function.
	 */
	public int getMaxFitnessValue() {
		return fitnessCalculator.getMaxFitnessValue();
	}

	/**
	 * Sets the maximum possible fitness value this function will return. Default is 1000000 which is fine for nearly
	 * all purposes.
	 * 
	 * @param newMaxFitnessValue The new maximum fitness.
	 */
	protected void setMaxFitnessValue(int newMaxFitnessValue) {
		fitnessCalculator.setMaxFitnessValue(newMaxFitnessValue);
	}
	
	/**
	 * Set the input and target output pattern pairs to use for evaluations.
	 * @param inputPatterns Array containing stimuli (input) examples, in the form [trial][input]. The dimensions should match those of the
	 * input layer of the substrate network.
	 * @param targetOutputPatterns Array containing target response (output) examples, in the form [trial][output]. The dimensions should match those of the
	 * output layer of the substrate network.
	 * @param minTargetOutputValue The smallest value that occurs in the target outputs.
	 * @param maxTargetOutputValue The largest value that occurs in the target outputs.
	 */
	protected void setPatterns(double[][] inputPatterns, double[][] targetOutputPatterns, double minTargetOutputValue, double maxTargetOutputValue) {
		this.inputPatterns = inputPatterns;
		this.targetOutputPatterns = targetOutputPatterns;
		this.minTargetOutputValue = minTargetOutputValue;
		this.maxTargetOutputValue = maxTargetOutputValue;
	}

	@Override
	protected int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return evaluate(genotype, substrate, null);
	}
	
	@Override
	public int evaluate(Chromosome genotype, Activator substrate, String baseFileName) {
		if (baseFileName == null) {
			TargetFitnessCalculator.Results results = fitnessCalculator.evaluate(substrate, inputPatterns, targetOutputPatterns, minTargetOutputValue, maxTargetOutputValue, null);
			genotype.setPerformanceValue(results.performance);
			return results.fitness;
		}
		else {
			try {
				NiceWriter outputFile = new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
				TargetFitnessCalculator.Results results = fitnessCalculator.evaluate(substrate, inputPatterns, targetOutputPatterns, minTargetOutputValue, maxTargetOutputValue, outputFile);
				outputFile.close();
				return results.fitness;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0;
		}
	}
}
