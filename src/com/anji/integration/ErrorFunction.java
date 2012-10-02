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
 * created by Philip Tucker on Jul 17, 2004
 */

package com.anji.integration;

import com.anji.util.Properties;


/**
 * @author Philip Tucker
 */
public class ErrorFunction {

	private final static String TARGETS_RANGE_KEY = "targets.range";

	private double targetRange = 0;

	private static ErrorFunction instance = null;
	
	private ErrorFunction() {
		super();
	}

	/**
	 * @return singleton instance
	 */
	public static ErrorFunction getInstance() {
		if ( instance == null )
			instance = new ErrorFunction();
		return instance;
	}
	
	/**
	 * @param props
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init( Properties props ) {
		targetRange = props.getFloatProperty( TARGETS_RANGE_KEY, 0.0f );
	}

	/**
	 * @param responseCount
	 * @param minMaxRange
	 * @param sumOfSquares
	 * @return maximum error given the number and range of responses
	 */
	public double getMaxError( int responseCount, double minMaxRange, boolean sumOfSquares) {
        double maxDiffPerResponse = minMaxRange - targetRange;
        if ( sumOfSquares )
			maxDiffPerResponse *= maxDiffPerResponse;
		return maxDiffPerResponse * responseCount;
	}
	
	/**
	 * Calculate the sum of differences between <code>responses</code> and target values.
	 * 
	 * @param targets
	 * @param responses values to compare to target.
	 * @param sumOfSquares if true, square each diff before summing
	 * @return total sum of differences
	 */
	public double calculateError( double[][] targets, double[][] responses, boolean sumOfSquares ) {
		double result = 0;
		for ( int i = 0; i < targets.length; ++i ) {
			double[] response = responses[ i ];
			double[] target = targets[ i ];
			if ( response.length != target.length )
				throw new IllegalArgumentException( "for training set " + i
						+ " dimensions do not match for response [" + response.length + "] and target ["
						+ target.length + "]" );
			for ( int j = 0; j < target.length; ++j ) {
				double diff = 0;
				if ( response[ j ] > ( target[ j ] + targetRange ) )
					diff = ( response[ j ] - ( target[ j ] + targetRange ) );
				else if ( response[ j ] < ( target[ j ] - targetRange ) )
					diff = ( ( target[ j ] - targetRange ) - response[ j ] );

				if ( sumOfSquares )
					result += ( diff * diff );
				else
					result += diff;
			}
		}
		return result;
	}


    /**
	 * Calculate the sum of differences between <code>responses</code> and target values.
	 *
	 * @param targetOutputPatterns
	 * @param responses values to compare to target.
	 * @param sumOfSquares if true, square each diff before summing
	 * @return total sum of differences
	 */
	public double calculateError( double[][][] targetOutputPatterns, double[][][] responses, boolean sumOfSquares ) {
		double result = 0;
		for ( int i = 0; i < targetOutputPatterns.length; ++i ) {
			double[][] response = responses[ i ];
			double[][] target = targetOutputPatterns[ i ];
			if ( response.length != target.length )
				throw new IllegalArgumentException( "for training set " + i
						+ " dimensions do not match for response [" + response.length + "] and target ["
						+ target.length + "]" );
			for ( int j = 0; j < target.length; ++j ) {
                for ( int k = 0; k < target[0].length; ++k ) {
                    double diff = 0;
                    if ( response[j][k] > ( target[j][k] + targetRange ) )
                        diff = ( response[j][k] - ( target[j][k] + targetRange ) );
                    else if ( response[j][k] < ( target[j][k] - targetRange ) )
                        diff = ( ( target[j][k] - targetRange ) - response[j][k] );

                    if ( sumOfSquares )
                        result += ( diff * diff );
                    else
                        result += diff;
                }
			}
		}
		return result;
	}
	
	/**
	 * @return if response is within this range of the target, error is 0
	 */
	protected double getTargetRange() {
		return targetRange;
	}
}

