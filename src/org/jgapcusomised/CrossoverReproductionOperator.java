/*
 * Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of JGAP.
 * 
 * JGAP is free software; you can redistribute it and/or modify it under the terms of the GNU
 * Lesser Public License as published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * JGAP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License along with JGAP; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Created on Feb 3, 2003 by Philip Tucker
 */
package org.jgapcusomised;

import java.util.List;

import org.jgapcustomised.impl.CloneReproductionOperator;

/**
 * Abstract crossover reporduction operator handles iteration over population to
 * determine pairs of parent chromosomes. Subclass determines specific crossover
 * logic by implementing <code>reproduce(Configuration config, 
 * Chromosome dominantChrom, Chromosome recessiveChrom)</code>.
 * 
 * @author Philip Tucker
 */
public abstract class CrossoverReproductionOperator extends ReproductionOperator {

	// TODO - redo such that pairing logic and crossover logic can be swapped
	// independantly; i.e., use aggregation not inheritance

	/**
	 * @param config
	 * @param dominantChrom
	 * @param recessiveChrom
	 * @return offspring of <code>dominantChrom</code> and
	 *         <code>recessiveChrom</code>
	 */
	protected abstract ChromosomeMaterial reproduce(Configuration config, Chromosome dominantChrom, Chromosome recessiveChrom);

	/**
	 * Adds new children of <code>parents</code> to <code>offspring</code>.
	 * Chromosomes "mate" only within their species, and the number of offspring
	 * for each specie is determined by the size and average fitness of that
	 * specie. Species containing only 1 chromosome generate offspring via
	 * cloning.
	 * 
	 * @param config
	 * @param parentChroms
	 *            <code>List</code> contains <code>Chromosome</code> objects
	 * @param offspring
	 *            <code>List</code> contains <code>ChromosomeMaterial</code>
	 *            objects, offspring of parents; total number of chromosomes in
	 *            parents and offspring should equal config.getPopulationSize()
	 * @see ReproductionOperator#reproduce(Configuration, List, int, List)
	 */
	final protected void reproduce(final Configuration config, final List parentChroms, int numOffspring, List offspring) {
		if (parentChroms.size() < 1)
			throw new IllegalArgumentException("crossover requires at least 1 parent");

		// if only one parent, clone instead of crossover
		if (parentChroms.size() < 2)
			CloneReproductionOperator.reproduce(parentChroms, numOffspring, offspring);
		else {
			int targetSize = offspring.size() + numOffspring;

			while (offspring.size() < targetSize) {
				// select random "mother" and "father"
				int motherIdx = config.getRandomGenerator().nextInt(parentChroms.size());
				int fatherIdx = motherIdx;
				while (fatherIdx == motherIdx)
					fatherIdx = config.getRandomGenerator().nextInt(parentChroms.size());

				// determine dominance/recessiveness
				Chromosome dominant = null;
				Chromosome recessive = null;
				Chromosome mother = (Chromosome) parentChroms.get(motherIdx);
				Chromosome father = (Chromosome) parentChroms.get(fatherIdx);
				// TODO - use speciated fitness here? (won't make any difference if from same species)
				if (mother.getFitnessValue() > father.getFitnessValue()) {
					dominant = mother;
					recessive = father;
				} else {
					recessive = mother;
					dominant = father;
				}
				ChromosomeMaterial child = reproduce(config, dominant, recessive);
				offspring.add(child);
			}
		}
	}

}
