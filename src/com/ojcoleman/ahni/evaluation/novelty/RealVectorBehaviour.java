package com.ojcoleman.ahni.evaluation.novelty;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.jgapcustomised.Chromosome;

import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * Representation of a behaviour as real-valued vector. All values in the vector should be in the range [0, 1].
 */
public class RealVectorBehaviour extends Behaviour {
	public static final NumberFormat nf = new DecimalFormat("0.000");
	public ArrayRealVector p;
	double maxDist;
	
	public RealVectorBehaviour(ArrayRealVector p) {
		this.p = p;
		maxDist = Math.sqrt(p.getDimension());
		assert p.getMaxValue() <= 1 && p.getMinValue() >= 0 : "Values in RealVectorBehaviour must be in the range [0, 1] but " + p + " was given.";
	}
	
	@Override
	public double distanceFrom(Behaviour b) {
		return p.getDistance(((RealVectorBehaviour) b).p) / maxDist;
	}
	
	@Override
	public String toString() {
		return ArrayUtil.toString(p.getDataRef(), "  ", nf);
	}
}
