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
 * created by Derek James
 */
package com.anji.integration;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.jgapcusomised.BulkFitnessFunction;
import org.jgapcusomised.Chromosome;

import com.anji.util.Configurable;
import com.anji.util.Properties;
import com.anji.util.Randomizer;

/**
 * Assigns random fitness for each chromosome. This can be used for, among other things,
 * modeling genetic drift.
 * 
 * @author Derek James
 */
public class RandomFitnessFunction implements BulkFitnessFunction, Configurable {

	private final static int MAX_FITNESS = 1000000;

	private Random rand;
	
	/**
	 * @return max fitness value
	 * @see BulkFitnessFunction#getMaxFitnessValue()
	 */
	public int getMaxFitnessValue() {
		return MAX_FITNESS;
	}
	

	public float getPerformanceFromFitnessValue(int fitness) {
		return (float) fitness / MAX_FITNESS;
	}

	/**
	 * Assigns random fitness for each chromosome between 1 and <code>MAX_FITNESS</code>
	 * inclusive.
	 * 
	 * @param genotypes <code>List</code> contains <code>Chromosome</code> objects
	 */
	final public void evaluate( List genotypes ) {
		Iterator it = genotypes.iterator();
		while ( it.hasNext() ) {
			Chromosome chrom = (Chromosome) it.next();
			int randomFitness = rand.nextInt( MAX_FITNESS );
			chrom.setFitnessValue( randomFitness + 1 );
		}
	}

	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init( Properties props ) throws Exception {
		Randomizer r = (Randomizer) props.singletonObjectProperty( Randomizer.class );
		rand = r.getRand();
	}
	
	public boolean endRun() {
    	return false;
    }
	@Override
    public void dispose() {
	}
}
