package com.ojcoleman.ahni.experiments.mr2d;

import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.commons.math3.linear.ArrayRealVector;

import com.ojcoleman.ahni.util.Point2DImmut;

/**
 * Describes the properties of an object instance.
 */
class ObjectDescription {
	private static final NumberFormat nf = new DecimalFormat("0.00");
	
	/**
	 * The type of the object.
	 */
	public final ObjectType type;
	
	/**
	 * The objects initial position.
	 */
	public final Point2DImmut initialPosition;

	/**
	 * The objects initial rotation.
	 */
	public final double initialRotation;

	public ObjectDescription(ObjectType type, Point2DImmut initialPosition, double initialRotation) {
		this.type = type;
		this.initialPosition = initialPosition;
		this.initialRotation = initialRotation;
	}
	
	public ObjectDescription(ObjectDescription obj) {
		this.type = obj.type;
		this.initialPosition = obj.initialPosition;
		this.initialRotation = obj.initialRotation;
	}
	
	@Override
	public String toString() {
		return "ObjectDescription: IP:(" + nf.format(initialPosition.x) + ", " + nf.format(initialPosition.y) + ", " + nf.format(initialRotation) + ") " + type;
	}
}
