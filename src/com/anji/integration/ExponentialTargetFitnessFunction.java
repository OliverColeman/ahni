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
 * created by Philip Tucker
 */
package com.anji.integration;

import java.util.Arrays;


import com.anji.util.Properties;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;

/**
 * Fitness function where error is exponential; i.e., as the error gets closer to 0, the fitness increases
 * exponentially, at a greater rate than simply squaring the error. Fitness is skewed such that max fitness is
 * <code>Integer.MAX_VALUE</code>. See <code>calculateErrorFitness()</code> and <code>calculateSkewedFitness()</code>
 * for details.
 * 
 * @author Philip Tucker
 * @see com.anji.integration.TargetFitnessFunction
 */
public class ExponentialTargetFitnessFunction extends TargetFitnessFunction {

	private final static boolean SUM_OF_SQUARES = true;

	private double[][] nullResponses;

	private final static int MAX_FITNESS = Integer.MAX_VALUE;

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param newProps configuration parameters
	 */
	public void init(Properties newProps) {
		try {
			super.init(newProps);

			ErrorFunction errorFunction = ErrorFunction.getInstance();
			errorFunction.init(newProps);

			nullResponses = new double[getTargets().length][getTargets()[0].length];
			for (int i = 0; i < nullResponses.length; ++i)
				Arrays.fill(nullResponses[i], 0);

			setMaxFitnessValue(MAX_FITNESS);
		} catch (Exception e) {
			throw new IllegalArgumentException("invalid properties: " + e.getClass().toString() + ": " + e.getMessage());
		}
	}

	/**
	 * @param rawFitness exponential fitness
	 * @param expFactor
	 * @param nullRawFitness
	 * @return fitness skewed such that max is <code>Integer.MAX_VALUE</code>.
	 */
	private double calculateSkewedFitness(double rawFitness, double expFactor, double nullRawFitness) {
		double exponent = (expFactor * rawFitness) - (nullRawFitness - (double) Math.E);
		return (double) Math.exp(exponent);
	}

	/**
	 * @param responses
	 * @param minResponse
	 * @param maxResponse
	 * @return int exponential fitness, calculated by squaring the error of each response, summing those errors, and
	 *         using that error as an exponent to <code>Math.E</code>.
	 */
	protected int calculateErrorFitness(double[][] responses, double minResponse, double maxResponse) {
		ErrorFunction errorFunction = ErrorFunction.getInstance();
		double maxRawFitnessValue = errorFunction.getMaxError(getTargets().length * getTargets()[0].length, (maxResponse - minResponse), SUM_OF_SQUARES);
		double nullRawFitness = maxRawFitnessValue - errorFunction.calculateError(getTargets(), nullResponses, SUM_OF_SQUARES);

		// set expFactor such that max skewed fitness is Integer.MAX_VALUE
		double expFactor = (double) (Math.log(MAX_FITNESS) + (nullRawFitness - Math.E)) / maxRawFitnessValue;

		double sumSqDiff = ErrorFunction.getInstance().calculateError(getTargets(), responses, true);
		if (sumSqDiff > maxRawFitnessValue)
			throw new IllegalStateException("sum squared diff > max fitness value");
		double rawFitnessValue = maxRawFitnessValue - sumSqDiff;
		double skewedFitness = calculateSkewedFitness(rawFitnessValue, expFactor, nullRawFitness);
		int result = (int) skewedFitness;
		return result;
	}

	public double getPerformanceFromFitnessValue(int fitness) {
		return (double) fitness / MAX_FITNESS;
	}

	public boolean endRun() {
		return false;
	}

	@Override
	public void dispose() {
	}
	
	@Override
	public void evolutionFinished(HyperNEATEvolver evolver) {
	}
}
