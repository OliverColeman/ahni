package com.anji.nn.activationfunction;

/**
 * Multiply activation function.
 * 
 * @author Oliver Coleman
 */
public class MultiplyActivationFunction implements ActivationFunction, ActivationFunctionNonIntegrating {

	/**
	 * identifying string
	 */
	public final static String NAME = "multiply";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	MultiplyActivationFunction() {
		// no-op
	}

	/**
	 * Not used, use {@link #apply(double[], double)} as this is a non-integrating function.
	 */
	public double apply(double input) {
		return 0;
	}
	
	/**
	 * Return result of inputs multiplied together.
	 */
	public double apply(double[] input, double bias) {
		if (input.length == 0) return 0;
		double result = input[0];
		for (int i = 1; i < input.length; i++)
			result *= input[i];;
		return result;
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
		return 42;
	}
}
