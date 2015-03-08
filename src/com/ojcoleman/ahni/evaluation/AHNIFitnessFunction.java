package com.ojcoleman.ahni.evaluation;

import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.util.Point;

/**
 * Base class for fitness functions that provides some extra capabilities used by AHNI.
 */
public abstract class AHNIFitnessFunction extends BulkFitnessFunction {
	/**
	 * Sub-classes may override this to return the dimensions of the given layer.
	 * 
	 * @param layer The 0th based index of the layer to return the dimensions of.
	 * @param totalLayerCount The total number of layers in the substrate network. This can be used to determine, for
	 *            example, if the output layer has been specified.
	 * @return The dimensions of the specified layer in the format [width, height] (0th index is width, 1st index is
	 *         height). To specify a 1D layer or a layer that does not use a grid layout (see
	 *         {@link #getNeuronPositions(int, int)}) set the height to 1 and just specify the number of neurons in the
	 *         layer via the "width". If the fitness function does not wish to define dimensions for the given layer
	 *         then null may be returned. This default implementation returns null for all layers.
	 */
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		return null;
	}

	/**
	 * Sub-classes may override this to return the locations of neurons in the given layer.
	 * 
	 * @param layer The 0th based index of the layer to return the neuron locations for.
	 * @param totalLayerCount The total number of layers in the substrate network. This can be used to determine, for
	 *            example, if the output layer has been specified.
	 * @return The location of neurons in the specified layer. Coordinates should be supplied in the range [0, 1],
	 *         {@link com.ojcoleman.ahni.transcriber.HyperNEATTranscriber#RANGE_X} and the corresponding Ranges for the x and y axes will be used to
	 *         translate to the user specified coordinate ranges. If the layer is 2D then the locations should be
	 *         specified in row packed order. If the fitness function does not wish to define neuron positions for the
	 *         given layer then null may be returned. This default implementation returns null for all layers.
	 */
	public Point[] getNeuronPositions(int layer, int totalLayerCount) {
		return null;
	}
	
	/**
	 * This method may optionally be overridden to perform an evaluation of an individual genotype and output the results to a log.
	 * The evaluation is not used for any purpose other than generating a record of the evaluation of the given genotype.
	 * @param genotype the genotype being evaluated. This is not usually required but may be useful in some cases.
	 * @param substrate the phenotypic substrate of the genotype being evaluated.
	 * @param baseFileName The base/prefix of the names of log file(s) that should be created.
	 * @param logText If true then text representations should be output.
	 * @param logImage If true then image representations should be output.
	 */
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
	}
	
	/**
	 * This method may optionally be overridden to perform an evaluation that performs a generalisation test of an 
	 * individual genotype and output the results to a log. The generalisation test should use training examples or 
	 * environments that are not used for fitness evaluations. The evaluation is not used for any purpose other than 
	 * generating a record of the generalisation performance of the given genotype. The function should set the 
	 * performance values and fitness values of the given Chromosome using the functions setFitnessValue(...) and 
	 *   setPerformanceValue(...).
	 * @param genotype the genotype being evaluated. This is not usually required but may be useful in some cases.
	 * @param substrate the phenotypic substrate of the genotype being evaluated.
	 * @param baseFileName The base/prefix of the names of log file(s) that should be created.
	 * @param logText If true then text representations should be output.
	 * @param logImage If true then image representations should be output.
	 * @return Implementations of this method must return true. This default implementation returns false to indicate
	 *   that it's a dummy implementation.
	 */
	public boolean evaluateGeneralisation(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		return false;
	}
}
