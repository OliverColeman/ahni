package com.anji.nn.activationfunction;


/**
 * Negative linear activation function.
 */
public class NegatedLinearActivationFunction implements ActivationFunction {

	/**
	 * identifying string
	 */
	public final static String NAME = ActivationFunctionType.NEGATED_LINEAR.toString();

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	NegatedLinearActivationFunction() {
		// no-op
	}

	/**
	 * Return <code>input</code> with opposite sign.
	 * 
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(float)
	 */
	public float apply( float input ) {
		return -input;
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
		return -Float.MAX_VALUE;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#cost()
	 */
	public long cost() {
		return 42;
	}
}
