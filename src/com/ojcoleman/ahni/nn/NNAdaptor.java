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
}
