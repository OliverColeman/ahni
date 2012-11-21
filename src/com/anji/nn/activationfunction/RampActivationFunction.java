package com.anji.nn.activationfunction;

/**
 * Ramp activation function.
 * 
 * @author Oliver Coleman
 */
public class RampActivationFunction implements ActivationFunction {

	/**
	 * identifying string
	 */
	public final static String NAME = "ramp";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	RampActivationFunction() {
		// no-op
	}

	/**
	 * Returns 0 if the input <= 0, otherwise the input value.
	 * 
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(double)
	 */
	public double apply(double input) {
		if (input < 0) return 0;
		return input;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMaxValue()
	 */
	public double getMaxValue() {
		return Float.MAX_VALUE;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMinValue()
	 */
	public double getMinValue() {
		return 0;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#cost()
	 */
	public long cost() {
		return 42;
	}
}
