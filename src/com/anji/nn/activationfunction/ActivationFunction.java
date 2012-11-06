/*
 * Copyright (C) 2004  Derek James and Philip Tucker
 *
 * This file is part of ANJI (Another NEAT Java Implementation).
 *
 * ANJI is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Created on Feb 26, 2004 by Philip Tucker
 */
package com.anji.nn.activationfunction;

/**
 * Abstracts activation function for neurons.
 * 
 * @author Philip Tucker
 */
public interface ActivationFunction {

	/**
	 * Apply activation function to input.
	 * 
	 * @param input
	 * @return double result of applying activation function to <code>input</code>
	 */
	public abstract double apply(double input);

	/**
	 * @return ceiling value for this function
	 */
	public double getMaxValue();

	/**
	 * @return floor value for this function
	 */
	public double getMinValue();

	/**
	 * @return number corresponding to cost of activation in resources
	 */
	public long cost();

}
