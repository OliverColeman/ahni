package com.anji.nn.activationfunction;

/**
 * Reciprocal function (inverse).
 * 
 * @author Oliver Coleman
 */
public class RecipriocalActivationFunction implements ActivationFunction {
	/**
	 * identifying string
	 */
	public final static String NAME = "reciprocal";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	RecipriocalActivationFunction() {
		// no-op
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(double)
	 */
	public double apply(double input) {
		double val = 1 / input;
		if (Double.isInfinite(val) || Double.isNaN(val)) return input < 0 ? -Double.MAX_VALUE : Double.MAX_VALUE;
		return val;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMaxValue()
	 */
	public double getMaxValue() {
		return Double.MAX_VALUE;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMinValue()
	 */
	public double getMinValue() {
		return -Double.MAX_VALUE;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#cost()
	 */
	public long cost() {
		return 75;
	}
}
