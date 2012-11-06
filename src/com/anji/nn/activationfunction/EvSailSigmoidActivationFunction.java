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
 * Modified classic sigmoid. Submitted to NEAT group by zenguyuno@yahoo.com from EvSail ANN package.
 * 
 * @author Philip Tucker
 */
public class EvSailSigmoidActivationFunction implements ActivationFunction {

	private final static double SEP = 0.3f;
	private final static double DENOMINATOR = 2 * SEP * SEP;

	/**
	 * identifying string
	 */
	public final static String NAME = "evsail-sigmoid";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	EvSailSigmoidActivationFunction() {
		// no-op
	}

	/**
	 * Approximation of classic sigmoid.
	 * 
	 * @see com.anji.nn.activationfunction.ActivationFunction#apply(double)
	 */
	public double apply(double input) {
		if (input <= -SEP)
			return 0;
		else if (input <= 0) {
			double tmp = input + SEP;
			return (tmp * tmp) / DENOMINATOR;
		} else if (input < SEP) {
			double tmp = input - SEP;
			return 1 - ((tmp * tmp) / DENOMINATOR);
		} else
			return 1;
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
		return 166;
	}
}
