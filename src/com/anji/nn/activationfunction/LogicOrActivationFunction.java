package com.anji.nn.activationfunction;

/**
 * Logic OR activation function.
 * 
 * @author Oliver Coleman
 */
public class LogicOrActivationFunction extends LogicActivationFunction {
	/**
	 * identifying string
	 */
	public final static String NAME = "or";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessed via ActivationFunctionFactory.
	 */
	LogicOrActivationFunction() {
	}

	/**
	 * Returns the result of a logical OR over all inputs, where an input value greater than or equal to 0.5 is considered logical true, and less than 0.5 false.
	 * @return 1 or 0 depending on result of logic operation.
	 */
	public double apply(double[] input) {
		boolean result = false;
		for (int i = 0; i < input.length; i++)
			result |= input[i] >= 0.5;
		return result ? 1 : 0;
	}
}
