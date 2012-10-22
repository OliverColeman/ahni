/*
 * Copyright (C) 2004 Oliver Coleman
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
 */
package com.anji.nn.activationfunction;


/**
 * Gaussian activation function.
 * 
 * @author Oliver Coleman
 */
public class GaussianActivationFunction implements ActivationFunction {

	private final static double SLOPE = 4.924273f;

	/**
	 * identifying string
	 */
	public final static String NAME = "gaussian";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	GaussianActivationFunction() {
		// no-op
	}

	/**
	 * Return <code>input</code> with Gaussian function transformation.
	 * 
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(double)
	 */
	public double apply( double input ) {
		return (double) Math.exp(-(input * input * SLOPE));
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
