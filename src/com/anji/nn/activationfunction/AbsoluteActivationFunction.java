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
 * Absolute activation function.
 * 
 * @author Oliver Coleman
 */
public class AbsoluteActivationFunction implements ActivationFunction {

	/**
	 * identifying string
	 */
	public final static String NAME = ActivationFunctionType.ABSOLUTE.toString();

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	AbsoluteActivationFunction() {
		// no-op
	}

	/**
	 * Return absolute value of <code>input</code>, clamped to range [0, 1].
	 * 
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(float)
	 */
	public float apply( float input ) {
		return Math.abs(input);
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
