package com.anji.nn.activationfunction;

/**
 * Absolute activation function.
 * 
 * @author Oliver Coleman
 */
public abstract class LogicActivationFunction implements ActivationFunction, ActivationFunctionNonIntegrating {
	/**
	 * Not used as this is a non-integrating function, returns 0.
	 * 
	 * @see #apply(double[])
	 */
	public double apply(double input) {
		return 0;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMaxValue()
	 */
	public double getMaxValue() {
		return 1;
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
