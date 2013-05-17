package com.ojcoleman.ahni.nn;

import java.awt.Graphics2D;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.util.Point;

public abstract class NNAdaptor implements Activator {
	protected Point[] coords;
	protected Point coordsMin;
	private Point coordsMax;
	protected Point coordsRange;

	/**
	 * Enable storing coordinates for neurons.
	 */
	public void enableCoords() {
		if (coords == null) {
			coords = new Point[getNeuronCount()];
			for (int i = 0; i < getNeuronCount(); i++)
				coords[i] = new Point();
			coordsMin = new Point();
			coordsMax = new Point();
			coordsRange = new Point();
		}
	}

	/**
	 * Should return the total number of neurons in this network (inputs and outputs included).
	 */
	public abstract int getNeuronCount();

	/**
	 * Returns true iff storing coordinates for neurons has been enabled.
	 */
	public boolean coordsEnabled() {
		return (coords != null);
	}

	/**
	 * Returns the coordinates of the specified neuron.
	 */
	public Point getCoord(int neuronIndex) {
		return coords[neuronIndex];
	}

	/**
	 * Returns the x coordinate of the specified neuron.
	 */
	public double getXCoord(int neuronIndex) {
		return coords[neuronIndex].x;
	}

	/**
	 * Returns the y coordinate of the specified neuron.
	 */
	public double getYCoord(int neuronIndex) {
		return coords[neuronIndex].y;
	}

	/**
	 * Returns the z coordinate of the specified neuron.
	 */
	public double getZCoord(int neuronIndex) {
		return coords[neuronIndex].z;
	}

	/**
	 * Sets the coordinate of the specified neuron.
	 */
	public void setCoords(int neuronIndex, double x, double y) {
		coords[neuronIndex].setCoordinates(x, y, 0);
		updateMinMax(coords[neuronIndex]);
	}

	/**
	 * Sets the coordinate of the specified neuron.
	 */
	public void setCoords(int neuronIndex, double x, double y, double z) {
		coords[neuronIndex].setCoordinates(x, y, z);
		updateMinMax(coords[neuronIndex]);
	}

	private void updateMinMax(Point c) {
		if (c.x < coordsMin.x) coordsMin.x = c.x; else if (c.x > coordsMax.x) coordsMax.x = c.x;
		if (c.y < coordsMin.y) coordsMin.y = c.y; else if (c.y > coordsMax.y) coordsMax.y = c.y;
		if (c.z < coordsMin.z) coordsMin.z = c.z; else if (c.z > coordsMax.z) coordsMax.z = c.z;
		coordsRange.x = coordsMax.x - coordsMin.x;
		coordsRange.y = coordsMax.y - coordsMin.y;
		coordsRange.z = coordsMax.z - coordsMin.z;
	}
	
	/**
	 * May be overridden to render this network as an image.
	 * @param g The Graphics2D on which to render the network.
	 * @param width The desired image width.
	 * @param height The desired image height.
	 * @param nodeSize The desired size of rendered neurons, generally in pixels.
	 * @return true iff the network was successfully rendered.
	 */
	public boolean render(Graphics2D g, int width, int height, int nodeSize) {
		return false;
	}
	
	
	/**
	 * Retrieve the output from network for the given input. 
	 * This method accepts an array to store the output of the network in, rather than possibly
	 * creating a new output array for every call. This default implementation simply copies
	 * the output of {@link #next(double[])} to the given output array, but sub-classes may
	 * override this method more efficiently. 
	 *   
	 * @param stimuli The input to the network.
	 * @param output An array to put the output from the network in.
	 */
	public void next(double[] stimuli, double[] output) {
		double[] o = next(stimuli);
		System.arraycopy(o, 0, output, 0, o.length);
	}

	/**
	 * Retrieve a set of output vectors from network for the given sequence of input vectors. 
	 * This method accepts an array to store the output of the network in, rather than possibly
	 * creating a new output array for every call. This default implementation simply copies
	 * the output of {@link #next(double[][])} to the given output array, but sub-classes may
	 * override this method more efficiently. 
	 *   
	 * @param stimuli An array of input vector arrays.
	 * @param output An array of output vector arrays to put the sequence of output vector values in.
	 */
	public void nextSequence(double[][] stimuli, double[][] output) {
		double[][] o = nextSequence(stimuli);
		for (int i = 0; i < stimuli.length; i++) {
			System.arraycopy(o[i], 0, output[i], 0, o[i].length);
		}
	}

	/**
	 * Retrieve the output from network for the given input. 
	 * This method accepts an array to store the output of the network in, rather than possibly
	 * creating a new output array for every call. This default implementation simply copies
	 * the output of {@link #next(double[][])} to the given output array, but sub-classes may
	 * override this method more efficiently. 
	 *   
	 * @param stimuli The input to the network.
	 * @param output An array to put the output from the network in.
	 */
	public void next(double[][] stimuli, double[][] output) {
		double[][] o = next(stimuli);
		for (int i = 0; i < stimuli.length; i++) {
			System.arraycopy(o[i], 0, output[i], 0, o[i].length);
		}
	}

	/**
	 * Retrieve a set of output matrices from network for the given sequence of input matrices. 
	 * This method accepts an array to store the output of the network in, rather than possibly
	 * creating a new output array for every call. This default implementation simply copies
	 * the output of {@link #nextSequence(double[][][])} to the given output array, but sub-classes may
	 * override this method more efficiently. 
	 *   
	 * @param stimuli An array of input matrix arrays.
	 * @param output An array of output matrix arrays to put the sequence of output matrix values in.
	 */
	public void nextSequence(double[][][] stimuli, double[][][] output) {
		double[][][] o = nextSequence(stimuli);
		for (int i = 0; i < o.length; i++) {
			for (int j = 0; j < o[i].length; j++) {
				System.arraycopy(o[i][j], 0, output[i][j], 0, o[i][j].length);
			}
		}
	}
}
