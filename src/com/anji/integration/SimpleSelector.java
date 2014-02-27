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
 * created by Philip Tucker on Jan 13, 2004
 */
package com.anji.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeFitnessComparator;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.NaturalSelector;
import org.jgapcustomised.Species;

/**
 * Selects chromosomes based directly on fitness value, as opposed to a statistical probability.
 * 
 * @author Philip Tucker
 */
public class SimpleSelector extends NaturalSelector {

	private List<Chromosome> chromosomes = new ArrayList<Chromosome>();

	/**
	 * Add <code>a_chromosomeToAdd</code> to set of chromosomes to be evaluated.
	 * 
	 * @param a_activeConfigurator
	 * @param a_chromosomeToAdd
	 */
	protected void add(Configuration a_activeConfigurator, Chromosome a_chromosomeToAdd) {
		chromosomes.add(a_chromosomeToAdd);
	}
	
	@Override
	/**
	 * Modified version of {@link org.jgapcustomised.NaturalSelector#select(Configuration)} that
	 * selects a number of parents for each species based on the species fitness (similarly to 
	 * handling of elites). Rounding errors are handled by randomly selecting parents from the 
	 * population or randomly removing selected members.
	 */
	public List<Chromosome> select(Configuration config) {
		List<Chromosome> result = new ArrayList<Chromosome>();

		int numToSelect = (int) Math.round(numChromosomes * getSurvivalRate()) - elite.size();
		if (numToSelect > 0) {
			// determine parents for each species.
			Iterator<Species> speciesIter = species.iterator();
			while (speciesIter.hasNext()) {
				Species s = speciesIter.next();
				// Add parents from this species if it's the only species or it hasn't been stagnant for too long 
				// or it hasn't reached the minimum species age or it contains the population-wide fittest individual.
				if (species.size() == 1 || s.getStagnantGenerationsCount() < maxStagnantGenerations || s.getAge() < minAge || s.containsBestPerforming) {
					int numToSelectFromSpecies = (int) Math.round(getSurvivalRate() * s.size()) - s.getEliteCount();
					if (numToSelectFromSpecies > 0) {
						List<Chromosome> selected = s.getTop(numToSelectFromSpecies, false);
						result.addAll(selected);
					}
				}
			}
	
			// Adjust for rounding errors.
			if (result.size() > numToSelect) {
				// Randomly remove from parents.
				Collections.shuffle(result, config.getRandomGenerator());
				while (result.size() > numToSelect) {
					result.remove(result.size() - 1);
				}
			} else if (result.size() < numToSelect) {
				// Just select some more from population (minus elites) at large. 
				Collections.shuffle(chromosomes, config.getRandomGenerator());
				Iterator<Chromosome> it = chromosomes.iterator();
				while (it.hasNext() && result.size() < numToSelect) {
					Chromosome c = it.next();
					if (!(result.contains(c))) {
						result.add(c);
					}
				}
			}
		}
		
		result.addAll(elite);

		return result;
	}

	/**
	 * Returns the <code>a_howManyToSelect</code> chromosomes with highest fitness.
	 * 
	 * @param a_activeConfiguration
	 * @param a_howManyToSelect
	 * @return <code>List</code> contains <code>Chromosome</code> objects
	 */
	protected List<Chromosome> select(Configuration a_activeConfiguration, int a_howManyToSelect) {
		Collections.sort(chromosomes, new ChromosomeFitnessComparator(false /* asc */, speciatedFitness /*
																										 * speciated
																										 * fitness
																										 */));
		List<Chromosome> result = new ArrayList<Chromosome>(a_howManyToSelect);
		Iterator<Chromosome> it = chromosomes.iterator();
		while (it.hasNext() && (result.size() < a_howManyToSelect))
			result.add(it.next());
		return result;
	}

	/**
	 * empty chromosome list
	 */
	protected void emptyImpl() {
		chromosomes.clear();
	}
	
	public boolean changesOverallFitness() {
		return false;
	}
}
