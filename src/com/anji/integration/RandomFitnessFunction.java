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


import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;

import com.anji.util.Configurable;
import com.anji.util.Properties;
import com.anji.util.Randomizer;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;

/**
 * Assigns random fitness for each chromosome. This can be used for, among other things, modeling genetic drift.
 * 
 * @author Derek James
 */
public class RandomFitnessFunction extends BulkFitnessFunction implements Configurable {
	private Random rand;

	/**
	 * Assigns random fitness for each chromosome.
	 * 
	 * @param genotypes <code>List</code> contains <code>Chromosome</code> objects
	 */
	final public void evaluate(List genotypes) {
		Iterator it = genotypes.iterator();
		while (it.hasNext()) {
			Chromosome chrom = (Chromosome) it.next();
			chrom.setFitnessValue(rand.nextDouble());
		}
	}

	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init(Properties props) throws Exception {
		Randomizer r = (Randomizer) props.singletonObjectProperty(Randomizer.class);
		rand = r.getRand();
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
