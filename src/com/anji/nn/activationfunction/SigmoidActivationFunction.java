/*
 * Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of ANJI (Another NEAT Java Implementation).
 * 
 * ANJI is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Created on Feb 26, 2004 by Philip Tucker
 */
package com.anji.nn.activationfunction;


/**
 * Modified classic sigmoid. Copied from <a href="http://www.jooneworld.com/">JOONE</a>
 * <code>SigmoidLayer</code>.
 * 
 * @author Philip Tucker
 */
public class SigmoidActivationFunction implements ActivationFunction {

	private final static double SLOPE = 4.924273f;
	
	private final static int LUT_RESOLUTION = 5000;
	private final static double MAX_LUT_INPUT = 2.5f; //after 2.5 (before -2.5) value is 1 (0) for all practical purposes 
	private final static double MIN_LUT_INPUT = -2.5f;
	private final static double LUT_INPUT_RANGE = MAX_LUT_INPUT - MIN_LUT_INPUT;
	private final static double LUT_MULT = LUT_RESOLUTION / LUT_INPUT_RANGE;
	
	private double[] sigmoid; //lookup table (measured at 150x faster on my Intel(R) Core(TM)2 Duo CPU, but of course it could cache the entire LUT in the CPU in a simple performance test)

	/**
	 * identifying string
	 */
	public final static String NAME = "sigmoid";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	SigmoidActivationFunction() {
		//initialise lookup table
		sigmoid = new double[LUT_RESOLUTION+1];
		double input = 0;
		double output = 0;
		for (int i = 0; i <= LUT_RESOLUTION; i++) {
			input = (LUT_INPUT_RANGE / LUT_RESOLUTION) * (i+0.5f) + MIN_LUT_INPUT;
			output = (double) (1.0 / (1.0 + Math.exp(-(input * SLOPE))));
			sigmoid[i] = output;
		}
	}

	/**
	 * Modified classic sigmoid.
	 * 
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(double)
	 */
	public double apply( double input ) {
		if (input < MIN_LUT_INPUT)
			return 0;
		else if (input > MAX_LUT_INPUT)
			return 1;
		return sigmoid[(int) ((input - MIN_LUT_INPUT) * LUT_MULT)];
		
		//return 1.0 / (1.0 + Math.exp(-(input * SLOPE)));
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
		return 497;
	}
	
	
	public static void main (String[] args) {
		/*
		SigmoidActivationFunction func = new SigmoidActivationFunction();
		
		long start = System.currentTimeMillis();
		
		for (int c = 0; c < 10; c++) {
		  	for (double i = MIN_INPUT; i <= MAX_INPUT; i+=0.00001) {
		  		func.apply(i);
		  	}
		}
	  	long end = System.currentTimeMillis();
	  	
	  	System.out.println("time: " + (end - start) / 1000.0);
	  	*/
		
		
		int s = 10000000;
		double[] a = new double[s];
		double[] b = new double[s];
		double[] c = new double[s];
		for (int i = 0; i < s; i++) {
			a[i] = (double) Math.random();
			b[i] = (double) Math.random() * 3;
		}
		
		long start = System.currentTimeMillis();
		
		for (int j = 0; j < 10; j++)
			for (int i = 1; i < s; i++)
				c[i] = c[i-1] + a[i] * b[i];
		
		long end = System.currentTimeMillis();
	  	
	  	System.out.println("time: " + (end - start) / 1000.0);
	  	
	}
}
