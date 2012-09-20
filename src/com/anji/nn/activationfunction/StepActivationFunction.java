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
 * Step activation function.
 * 
 * @author Philip Tucker
 */
public class StepActivationFunction implements ActivationFunction {

	/**
	 * identifying string
	 */
	public final static String NAME = "step";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	StepActivationFunction() {
		// no-op
	}

	/**
	 * @return 0 if <code>input</code>< 0, 1 otherwise
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(float)
	 */
	public float apply( float input ) {
		return ( input <= 0 ) ? 0 : 1;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMaxValue()
	 */
	public float getMaxValue() {
		return 1;
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
		return 40;
	}
}
