/*
 * Copyright 2001-2003 Neil Rotstan
 *
 * This file is part of JGAP.
 *
 * JGAP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * JGAP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with JGAP; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jgap;

import java.util.Properties;


/**
 * Fitness functions are used to determine how optimal a particular solution
 * is relative to other solutions. This abstract class should be extended and
 * the evaluate() method implemented. The fitness function is given a Chromosome
 * to evaluate and should return a positive integer that reflects its fitness
 * value. The higher the value, the more fit the Chromosome. The actual range
 * of fitness values isn't important (other than the fact that they must be
 * positive integers): it's the relative difference as a percentage that
 * tends to determine the success or failure of a Chromosome. So in other words,
 * two Chromosomes with respective fitness values of 1 and 100 have the same
 * relative fitness to each other as two Chromosomes with respective fitness
 * values of 10 and 1000 (in each case, the first is 1% as fit as the second).
 * <p>
 * Note: Two Chromosomes with equivalent sets of genes should always be
 * assigned the same fitness value by any implementation of this interface.
 * @deprecated use <code>BulkFitnessFunction</code> instead
 * @see org.jgap.BulkFitnessFunction
 */
public abstract class FitnessFunction implements java.io.Serializable
{

private int maxFitnessValue;

/**
 * @return int maximum fitness that can be returned by this function
 */
public int getMaxFitnessValue() {
	return maxFitnessValue;
}

/**
 * @param aMaxFitnessValue maximum fitness that can be returned by this function
 */
public void setMaxFitnessValue(int aMaxFitnessValue) {
	this.maxFitnessValue = aMaxFitnessValue;
}

/**
 * @param newProps configuration parameters
 */
public abstract void init(Properties newProps);

    /**
     * Retrieves the fitness value of the given Chromosome. The fitness
     * value will be a positive integer.
     *
     * @param a_subject the Chromosome for which to compute and return the
     *                  fitness value.
     * @return the fitness value of the given Chromosome.
     */
    public final int getFitnessValue( Chromosome a_subject )
    {
        // Delegate to the evaluate() method to actually compute the
        // fitness value. We use the Math.max function to guarantee
        // that the value is always > 0.
        // ---------------------------------------------------------
        return Math.max( 1, evaluate( a_subject ) );
    }


    /**
     * Determine the fitness of the given Chromosome instance. The higher the
     * return value, the more fit the instance. This method should always
     * return the same fitness value for two equivalent Chromosome instances.
     *
     * @param a_subject The Chromosome instance to evaluate.
     *
     * @return A positive integer reflecting the fitness rating of the given
     *         Chromosome.
     */
    protected abstract int evaluate( Chromosome a_subject );
}


