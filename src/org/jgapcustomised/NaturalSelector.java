/*
 * Copyright 2001-2003 Neil Rotstan
 * Copyright (C) 2004  Derek James and Philip Tucker
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
 * 
 * Modified on Feb 3, 2003 by Philip Tucker
 */
package org.jgapcustomised;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.ojcoleman.ahni.misc.NSGAII;

/**
 * Natural selectors are responsible for actually selecting a specified number of Chromosome specimens from a
 * population, using the fitness values as a guide. Usually fitness is treated as a statistic probability of survival,
 * not as the sole determining factor. Therefore, Chromosomes with higher fitness values are more likely to survive than
 * those with lesser fitness values, but it's not guaranteed.
 */
public abstract class NaturalSelector {
	protected int numChromosomes;
	protected double survivalRate = 0;
	protected double elitismProportion = 0.1f;
	protected int elitismMinToSelect = 1;
	protected int elitismMinSpeciesSize = 5;
	protected int maxStagnantGenerations = 0;
	protected int minAge = 10;
	protected boolean speciatedFitness = true;
	protected List<Chromosome> elite = new ArrayList<Chromosome>();
	protected List<Species> species;
	protected Chromosome bestPerforming;

	/**
	 * If elitism is enabled, places appropriate chromosomes in <code>elite</code> list. Elitism follows methodology in
	 * <a href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT</a>. Passes everything else to subclass
	 * <code>add( Configuration config, Chromosome c )</code> method.
	 * 
	 * @param config
	 * @param chroms <code>List</code> contains Chromosome objects
	 */
	public void add(Configuration config, List<Species> species, List<Chromosome> chroms, Chromosome bestPerforming) {
		numChromosomes += chroms.size();
		this.species = species;
		this.bestPerforming = bestPerforming;
		
		// determine elites for each species
		for (Species s : species) {
			if (s.containsBestPerforming || 
					((elitismProportion > 0 || elitismMinToSelect > 0) &&
					s.size() >= elitismMinSpeciesSize &&
					(species.size() == 1 || s.getStagnantGenerationsCount() < maxStagnantGenerations || s.getAge() < minAge))) {
				List<Chromosome> speciesElite = s.getElite(elitismProportion, elitismMinToSelect, s.containsBestPerforming ? bestPerforming : null);
				elite.addAll(speciesElite);
			}
		}
		
		assert(elite.contains(bestPerforming));
		
		// Add remaining non-elite to list to select parents from (by subclass).
		for (Chromosome c : chroms) {
			// don't add if it's already in the list of elites
			if (!c.isElite) {
				add(config, c);
			}
		}
	}

	/**
	 * @param config
	 * @param c chromosome to add to selection pool
	 */
	protected abstract void add(Configuration config, Chromosome c);

	/**
	 * Select a given number of Chromosomes from the pool that will move on to the next generation population. This
	 * selection should be guided by the fitness values. Elite chromosomes always survivie, unless there are more elite
	 * than the survival rate permits. In this case, elite with highest fitness are chosen. Remainder of survivors are
	 * determined by subclass <code>select( Configuration config, int numToSurvive )</code> method.
	 * 
	 * @param config
	 * @return List contains Chromosome objects
	 */
	public List<Chromosome> select(Configuration config) {
		// start with elites
		List<Chromosome> result = new ArrayList<Chromosome>(elite);

		int numToSelect = (int) ((numChromosomes * getSurvivalRate()) + 0.5);

		if (result.size() > numToSelect) {
			// remove least fittest from selected
			Collections.sort(result, new ChromosomeFitnessComparator(true /* asc */, speciatedFitness /* speciated fitness */));
			int numToRemove = result.size() - numToSelect;
			for (int i = 0; i < numToRemove; ++i) {
				result.remove(0);
			}
		} else if (result.size() < numToSelect) {
			int moreToSelect = numToSelect - result.size();
			List<Chromosome> more = select(config, moreToSelect);
			result.addAll(more);
		}

		return result;
	}

	/**
	 * @param config
	 * @param numToSurvive
	 * @return <code>List</code> contains <code>Chromosome</code> objects, those that have survived; size of this list
	 *         should be <code>numToSurvive</code>, unless fewer than that number of chromosomes have been added to
	 *         selector
	 */
	protected abstract List<Chromosome> select(Configuration config, int numToSurvive);

	/**
	 * clear pool of candidate chromosomes
	 * 
	 * @see NaturalSelector#emptyImpl()
	 */
	public void empty() {
		numChromosomes = 0;
		elite.clear();
		bestPerforming = null;
		emptyImpl();
	}

	/**
	 * @see NaturalSelector#empty()
	 */
	protected abstract void emptyImpl();

	/**
	 * @return double survival rate
	 */
	public double getSurvivalRate() {
		return survivalRate;
	}

	/**
	 * @param aSurvivalRate
	 */
	public void setSurvivalRate(double aSurvivalRate) {
		if (aSurvivalRate < 0.0 || aSurvivalRate > 1.0) {
			throw new IllegalArgumentException("0.0 <= survivalRate <= 1.0");
		}
		this.survivalRate = aSurvivalRate;
	}

	/**
	 * @return minimum size a specie must be to support an elite chromosome
	 */
	public int getElitismMinSpeciesSize() {
		return elitismMinSpeciesSize;
	}

	/**
	 * @param i minimum size a specie must be to support an elite chromosome
	 */
	public void setElitismMinSpeciesSize(int i) {
		elitismMinSpeciesSize = i;
	}

	/**
	 * @param p true if elitism is to be enabled
	 */
	public void setElitismProportion(double p) {
		elitismProportion = p;
	}

	/**
	 * @param i minimum size a specie must be to support an elite chromosome
	 */
	public void setElitismMinToSelect(int i) {
		elitismMinToSelect = i;
	}

	/**
	 * @param sf minimum size a specie must be to support an elite chromosome
	 */
	public void setSpeciatedFitness(boolean sf) {
		speciatedFitness = sf;
	}

	/**
	 * @param i maximum number of generations a species can survive without increasing fitness
	 */
	public void setMaxStagnantGenerations(int i) {
		maxStagnantGenerations = i;
	}

	public void setMinAge(int minAge) {
		this.minAge = minAge;
	}
	
	/**
	 * Make sure population-wide best performing is included in list.
	 */
	protected void ensureHighestPerformingIncluded(Species s, List<Chromosome> list) {
		if (s.containsBestPerforming) {
			if (!list.contains(bestPerforming)) {
				if (!list.isEmpty()) {
					// Remove lowest ranked so list size is maintained.
					list.remove(list.size()-1);
				}
				list.add(bestPerforming);
			}
		}
	}
}
