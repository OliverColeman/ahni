package com.ojcoleman.ahni.experiments.mr2d;

import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.commons.math3.linear.ArrayRealVector;

import com.ojcoleman.ahni.util.Range;

class ObjectInstance {
	private static final NumberFormat nf = new DecimalFormat("0.00");
	
	public final EnvironmentDescription environmentDescription;
	public final ObjectDescription description;
	public final Point2D.Double currentPosition;
	public double currentRotation;
	private int collected;
		
	public ObjectInstance(EnvironmentDescription envDesc, ObjectDescription desc) {
		environmentDescription = envDesc;
		description = desc;
		currentPosition = new Point2D.Double();
		reset();
	}
	
	public void reset() {
		currentPosition.x = description.initialPosition.x;
		currentPosition.y = description.initialPosition.y;
		currentRotation = description.initialRotation;
		collected = 0;
	}
	
	public boolean isCollected() {
		return collected > 0;
	}
	public void setCollected(int collectedCount) {
		this.collected = collectedCount;
	}
	
	/**
	 * Generates a novelty behaviour description for the current state of this object and puts it in the given vector starting at the given index.
	 * @param finalState True indicates that we're recording the final state of the environment and may need to record some additional information.
	 * @return the next index position after the description for this object.
	 */
	public int getNoveltyDescription(ArrayRealVector n, int index, boolean finalState) {
		if (description.type.mass > 0 || description.type.isAgent) {
			n.setEntry(index++, Range.UNIT.clamp(currentPosition.x));
			n.setEntry(index++, Range.UNIT.clamp(currentPosition.y));
		}
		// Record order in which collectible objects collected at end of simulation.
		if (description.type.collectible && finalState) {
			// Record order in which objects are collected.
			double c = collected;
			if (c > 0) c++; // Help differentiate between collected (c == 0) and not.
			// (We don't add 1 to objectDescriptions.size() to compensate because we subtract one for the agent object description.)
			n.setEntry(index++, c / (environmentDescription.objectDescriptions.size()));
		} 
		return index;
	}
	
	@Override
	public String toString() {
		return "ObjectInstance: CP:(" + nf.format(currentPosition.x) + ", " + nf.format(currentPosition.y) + ", " + nf.format(currentRotation) + ") C:" + collected + " " + description;
	}
}
