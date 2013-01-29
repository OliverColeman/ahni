package com.ojcoleman.ahni.evaluation;

import org.jgapcustomised.BulkFitnessFunction;

import com.ojcoleman.ahni.util.Point;

/**
 * Interface for fitness functions that provides some extra capabilities used by AHNI.
 */
public abstract class AHNIFitnessFunction implements BulkFitnessFunction {
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
}
