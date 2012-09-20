package com.anji.nn.activationfunction;


/**
 * Divide activation function (divides first input by second input).
 * 
 * @author Oliver Coleman
 */
public class DivideActivationFunction implements ActivationFunction, ActivationFunctionNonIntegrating {

	/**
	 * identifying string
	 */
	public final static String NAME = ActivationFunctionType.DIVIDE.toString();

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	DivideActivationFunction() {
		// no-op
	}

	/**
	 * Not used, returns 0.
	 */
	public float apply( float input ) {
		return 0;
	}
	
	/**
	 * Return first input divided by second input (or just first input if no second input).
	 */
	public float apply(float[] input) {
		if (input.length < 2)
			return input[0];
		if (input[1] == 0)
			return Float.MAX_VALUE * Math.signum(input[0]);
		return input[0] / input[1];
	}
	

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMaxValue()
	 */
	public float getMaxValue() {
		return Float.MAX_VALUE;
	}
	
	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMinValue()
	 */
	public float getMinValue() {
		return 0;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#cost()
	 */
	public long cost() {
		return 42;
	}
}
