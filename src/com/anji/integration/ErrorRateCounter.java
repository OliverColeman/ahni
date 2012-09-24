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
 * created by Derek James on August 19th, 2004
 */

package com.anji.integration;

import org.apache.log4j.Logger;

/**
 * @author Derek James
 */
public class ErrorRateCounter {

private static Logger logger = Logger.getLogger( ErrorRateCounter.class );

private static ErrorRateCounter instance = null;

private ErrorRateCounter() {
	super();
}

/**
 * @return singleton instance
 */
public static ErrorRateCounter getInstance() {
	if ( instance == null )
		instance = new ErrorRateCounter();
	return instance;
}

/**
 * @param targets
 * @param responses
 * @see ErrorRateCounter#countErrors(String, double[][], double[][])
 */
public void countErrors( double[][] targets, double[][] responses ) {
	countErrors( "", targets, responses );
}

/**
 * Calculate the sum of differences between <code>responses</code> and target values.
 * 
 * @param logPrefix <code>String</code> to prepend to all logs and exceptions
 * @param targets
 * @param responses values to compare to targets
 */
public void countErrors( String logPrefix, double[][] targets, double[][] responses ) {
	int truePositives = 0;
	int falsePositives = 0;
	int trueNegatives = 0;
	int falseNegatives = 0;
	for ( int i = 0; i < targets.length; ++i ) {
		double[] response = responses[ i ];
		double[] target = targets[ i ];
		if ( response.length != target.length )
			throw new IllegalArgumentException( logPrefix + ": for training set " + i
					+ " dimensions do not match for response [" + response.length + "] and target ["
					+ target.length + "]" );
		for ( int j = 0; j < target.length; ++j ) {
			if ( target[ j ] > 0.5 ) {
				if ( response[ j ] > 0.5 )
					++truePositives;
				else
					++falseNegatives;
			}
			else {
				if ( response[ j ] > 0.5 )
					++falsePositives;
				else
					++trueNegatives;
			}
		}
	}
	logger.info( logPrefix + ": TP/FN/FP/TN: " + truePositives + "/" + falseNegatives + "/"
			+ falsePositives + "/" + trueNegatives );
}
}
