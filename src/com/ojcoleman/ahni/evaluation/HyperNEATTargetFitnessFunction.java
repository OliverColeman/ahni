package com.ojcoleman.ahni.evaluation;

import java.io.FileWriter;
import java.io.IOException;

import org.jgapcustomised.*;

import com.anji.integration.*;
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
public class HyperNEATTargetFitnessFunction extends HyperNEATFitnessFunction {
	private static final long serialVersionUID = 1L;
		
	private TargetFitnessCalculator fitnessCalculator;
	
	private double[][][] inputPatterns;
	private double[][][] targetOutputPatterns;
	private double minTargetOutputValue;
	private double maxTargetOutputValue;

	protected HyperNEATTargetFitnessFunction() {
	}

	/**
	 * Create a HyperNEATTargetFitnessFunction with the specified input and output examples.
	 * 
	 * @param inputPatterns Array containing stimuli (input) examples, in the form [trial][y][x]. The dimensions should match those of the
	 * input layer of the substrate network.
	 * @param targetOutputPatterns Array containing target response (output) examples, in the form [trial][y][x]. The dimensions should match those of the
	 * output layer of the substrate network.
	 * @param minTargetOutputValue The smallest value that occurs in the target outputs.
	 * @param maxTargetOutputValue The largest value that occurs in the target outputs.
	 */
	public HyperNEATTargetFitnessFunction(double[][][] inputPatterns, double[][][] targetOutputPatterns, double minTargetOutputValue, double maxTargetOutputValue) {
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
	 * Set the input and target output pattern pairs to use for evaluations.
	 * @param inputPatterns Array containing stimuli (input) examples, in the form [trial][y][x]. The dimensions should match those of the
	 * input layer of the substrate network.
	 * @param targetOutputPatterns Array containing target response (output) examples, in the form [trial][y][x]. The dimensions should match those of the
	 * output layer of the substrate network.
	 * @param minTargetOutputValue The smallest value that occurs in the target outputs.
	 * @param maxTargetOutputValue The largest value that occurs in the target outputs.
	 */
	public void setPatterns(double[][][] inputPatterns, double[][][] targetOutputPatterns, double minTargetOutputValue, double maxTargetOutputValue) {
		this.inputPatterns = inputPatterns;
		this.targetOutputPatterns = targetOutputPatterns;
		this.minTargetOutputValue = minTargetOutputValue;
		this.maxTargetOutputValue = maxTargetOutputValue;
	}

	@Override
	protected double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return evaluate(genotype, substrate, null, false, false);
	}
	
	@Override
	public double evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		if (baseFileName == null) {
			TargetFitnessCalculator.Results results = fitnessCalculator.evaluate(substrate, inputPatterns, targetOutputPatterns, minTargetOutputValue, maxTargetOutputValue, null);
			genotype.setPerformanceValue(results.performance);
			return results.fitness;
		}
		else if (logText) {
			try {
				NiceWriter outputFile = new NiceWriter(new FileWriter(baseFileName + ".txt"), "0.00");
				TargetFitnessCalculator.Results results = fitnessCalculator.evaluate(substrate, inputPatterns, targetOutputPatterns, minTargetOutputValue, maxTargetOutputValue, outputFile);
				outputFile.close();
				return results.fitness;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return 0;
	}
}
