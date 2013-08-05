package com.anji.nn.activationfunction;

/**
 * Square-root function.
 * 
 * @author Oliver Coleman
 */
public class SqrtActivationFunction implements ActivationFunction {
	/**
	 * identifying string
	 */
	public final static String NAME = "sqrt";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	SqrtActivationFunction() {
		// no-op
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(double)
	 */
	public double apply(double input) {
		if (input > 0)
			return Math.sqrt(input);
		if (input < 0)
			return -Math.sqrt(-input);
		return 0;
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
