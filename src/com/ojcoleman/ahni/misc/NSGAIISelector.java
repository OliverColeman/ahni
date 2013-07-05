package com.ojcoleman.ahni.misc;

import java.util.*;

import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeFitnessComparator;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.Genotype;
import org.jgapcustomised.NaturalSelector;
import org.jgapcustomised.Species;

import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * This class implements a selector based on the multi-objective genetic algorithm NSGA-II as described in DEB,
 * Kalyanmoy ; PRATAP, Amrit ; AGARWAL, Sameer A. ; MEYARIVAN, T.:
 * "A Fast and Elitist Multiobjective Genetic Algorithm: NSGA-II". In: IEEE Transactions on Evolutionary Computation,
 * vol. 6, no. 2, April 2002, pp. 182-197.
 * 
 * In the original NSGA-II algorithm offspring are generated from the entire population, then the entire population
 * including the new offspring are sorted in non-dominating order and the best N individuals are kept, where N is the
 * desired population size. In this implementation a proportion of parents are selected from the population, using the
 * non-dominated sorting, which are then used to create offspring for the next generation. A proportion of elites are
 * also selected which will survive to the next generation unchanged. Thus rather than generating offspring from the
 * entire population and then sorting and removing excess individuals, only the best members of the population are used
 * to create offspring, and only the required number of offspring are produced to create the next population.
 * 
 * Offspring are produced via implementations of {@link org.jgapcustomised.ReproductionOperator}. ReproductionOperator
 * determines how many offspring to produce per species based on the average fitness of a species (ie using fitness
 * sharing as per NEAT). The average fitness is based on the "overall" fitness of each individual in the species. In
 * order to base this on the non-domination ranking produced by NSGA-II the method {@link #select(Configuration)} will
 * set the overall fitness of each individual according to its ranking within the entire population: f_i = ((rank_max -
 * rank_i) / rank_max) ^ 2, where f_i is the fitness of individual i and rank_i is its rank in the range [0, rank_max].
 * 
 * This code is based on JNSGA2 by Joachim Melcher, Institut AIFB, Universitaet Karlsruhe (TH), Germany
 * http://sourceforge.net/projects/jnsga2
 */
public class NSGAIISelector extends NaturalSelector {
	/**
	 * The population to select from, set in {@link #add(Configuration, List, List, Chromosome)}.
	 */
	protected List<Chromosome> population;

	public NSGAIISelector() {
		population = new ArrayList<Chromosome>();
		species = new ArrayList<Species>();
	}

	/**
	 * Modified version of {@link NaturalSelector#add(Configuration, List, List, Chromosome)} to prevent selecting elites from each
	 * species based on only a single objective.
	 * 
	 * @param config Configuration object for current run.
	 * @param chroms Chromosomes from current population.
	 */
	@Override
	public void add(Configuration config, List<Species> species, List<Chromosome> chroms, Chromosome bestPerforming) {
		numChromosomes += chroms.size();
		population.addAll(chroms);
		this.species.addAll(species);
		this.bestPerforming = bestPerforming;
	}

	/**
	 * This method should not be used, only the {@link #add(Configuration, List, List, Chromosome)} method
	 * should be used. An IllegalStateException is thrown if this method is called.
	 */
	protected void add(Configuration config, Chromosome chroms) {
		throw new IllegalStateException("The method add(Configuration, Chromosome) should not be called on an " + NSGAIISelector.class + " object, only the add(Configuration, List<Species>, List<Chromosome>) method should be used.");
	}

	/**
	 * Modified version of {@link org.jgapcustomised.NaturalSelector#select(Configuration)} that selects a number of
	 * elites and parents for each species based on the species fitness and the non-dominated sorting. Rounding errors
	 * in number of parents are handled by randomly selecting parents from the population or randomly removing selected
	 * (non-elite) members.
	 */
	@Override
	public List<Chromosome> select(Configuration config) {
		List<Chromosome> result = new ArrayList<Chromosome>();
		
		for (Species s : species) {
			List<List<Chromosome>> frontInSpecies = NSGAII.fastNonDominatedSort(s.getChromosomes());
			
			// Add elites and parents from this species if it's the only species or it hasn't been stagnant for too long
			// or it hasn't reached the minimum species age or it contains the population-wide fittest individual.
			if (species.size() == 1 || s.getStagnantGenerationsCount() < maxStagnantGenerations || s.getAge() < minAge || s.containsBestPerforming) {
				// Add parents.
				int minParentsToSelect = s.containsBestPerforming ? Math.max(1, elitismMinToSelect) : 0;
				int numParentsToSelect = (int) Math.round(getSurvivalRate() * s.size());
				if (numParentsToSelect < minParentsToSelect)
					numParentsToSelect = minParentsToSelect;
				
				if (numParentsToSelect > 0) {
					List<Chromosome> selected = NSGAII.getTop(frontInSpecies, numParentsToSelect);
					result.addAll(selected);
					// Make sure population-wide best performing is included in parents.
					ensureHighestPerformingIncluded(s, result);
				}
				
				// Add elites.
				int numElitesToSelect = (int) Math.round(elitismProportion * s.size());
				if (numElitesToSelect < elitismMinToSelect)
					numElitesToSelect = elitismMinToSelect;
				// Don't select more elites than parents.
				if (numElitesToSelect > numParentsToSelect)
					numElitesToSelect = numParentsToSelect;
				if (numElitesToSelect > 0) {
					List<Chromosome> elites = NSGAII.getTop(frontInSpecies, numElitesToSelect);
					// Make sure population-wide best performing is included in elites.
					ensureHighestPerformingIncluded(s, elites);
					s.setElites(elites);
				}
			}
		}
		
		// Address rounding errors.
		int numToSelect = (int) Math.round(numChromosomes * getSurvivalRate());
		if (result.size() > numToSelect) {
			// Remove randomly selected chromosomes.
			Collections.shuffle(result, config.getRandomGenerator());
			int numToRemove = result.size() - numToSelect;
			int numRemoved = 0;
			for (int i = result.size() - 1; i >= 0 && numRemoved < numToRemove; i--) {
				// Don't remove elites.
				if (!result.get(i).isElite) {
					result.remove(i);
				}
			}
		} else if (result.size() < numToSelect) {
			// Just select some more from population at large.
			Collections.shuffle(result, config.getRandomGenerator());
			Iterator<Chromosome> it = population.iterator();
			while (it.hasNext() && result.size() < numToSelect) {
				Chromosome c = it.next();
				if (!result.contains(c)) {
					result.add(c);
				}
			}
		}

		// Set the overall fitness of each individual in the population according to its ranking in the entire population.
		// This is used by ReproductionOperators to determine how many offspring to produce for each species.
		List<List<Chromosome>> fronts = NSGAII.fastNonDominatedSort(population);
		int rank = 0;
		double rankMax = fronts.size() - 1;
		double overallFitness = 0;
		for (List<Chromosome> front : fronts) {
			overallFitness = rankMax == 0 ? 1 : Math.pow((rankMax - rank) / rankMax, 2);
			for (Chromosome c : front) {
				c.setFitnessValue(overallFitness);
			}
			rank++;
		}

		return result;
	}
	
	/**
	 * Make sure population-wide best performing is included in list.
	 */
	private void ensureHighestPerformingIncluded(Species s, List<Chromosome> list) {
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

	/**
	 * This method should not be used, only the {@link #select(Configuration)} method should be used. An
	 * IllegalStateException is thrown if this method is called.
	 */
	protected List<Chromosome> select(Configuration a_activeConfiguration, int a_howManyToSelect) {
		throw new IllegalStateException("The method select(Configuration, int) should not be called on an " + NSGAIISelector.class + " object, only the select(Configuration) method should be used.");
	}

	/**
	 * empty chromosome list
	 */
	protected void emptyImpl() {
		population.clear();
		species.clear();
	}
}