package com.ojcoleman.ahni.misc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeFitnessComparator;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.Genotype;
import org.jgapcustomised.NaturalSelector;
import org.jgapcustomised.Species;

import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.Properties;
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
public class NSGAIISelector extends NaturalSelector implements Configurable {
	public static final String LOG = "log.selector.nsgaii";

	private Properties props;
	private DecimalFormat nf = new DecimalFormat("0.0000");
	
	/**
	 * The population to select from, set in {@link #add(Configuration, List, List, Chromosome)}.
	 */
	protected List<Chromosome> population;
	
	
	public NSGAIISelector() {
		population = new ArrayList<Chromosome>();
		species = new ArrayList<Species>();
	}
	
	@Override
	public void init(Properties props) throws Exception {
		this.props = props;
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
		HashMap<Species, Integer> numSelectedForSpecies = new HashMap<Species, Integer>();
				
		StringBuffer log = (props.logFilesEnabled() && props.getBooleanProperty(LOG, false)) ? new StringBuffer("SID, R, CID, E, P,  F\n") : null;
		
		for (Species s : species) {
			List<List<Chromosome>> frontInSpecies = NSGAII.fastNonDominatedSort(s.getChromosomes());
			List<Chromosome> selected = null;
			List<Chromosome> elites = new ArrayList<Chromosome>();
			
			// Add elites and parents from this species if it's the only species or it hasn't been stagnant for too long
			// or it hasn't reached the minimum species age or it contains the population-wide fittest individual.
			if (species.size() == 1 || s.getStagnantGenerationsCount() < maxStagnantGenerations || s.getAge() < minAge || s.containsBestPerforming) {
				// Add parents. Always select at least one parent
				int numParentsToSelect = Math.max(1, (int) Math.round(getSurvivalRate() * s.size()));
				selected = NSGAII.getTop(frontInSpecies, numParentsToSelect);
				result.addAll(selected);
				numSelectedForSpecies.put(s, selected.size());
				// Make sure population-wide best performing is included in parents.
				ensureHighestPerformingIncluded(s, result);
				
				// Add elites.
				if (s.size() >= elitismMinSpeciesSize) {
					int numElitesToSelect = (int) Math.round(elitismProportion * s.size());
					if (numElitesToSelect < elitismMinToSelect)
						numElitesToSelect = elitismMinToSelect;
					// Don't select more elites than parents.
					if (numElitesToSelect > numParentsToSelect)
						numElitesToSelect = numParentsToSelect;
					if (numElitesToSelect > 0) {
						elites = NSGAII.getTop(frontInSpecies, numElitesToSelect);
					}
				}
				// Make sure population-wide best performing is included in elites.
				ensureHighestPerformingIncluded(s, elites);
				s.setElites(elites);
			}
			else {
				numSelectedForSpecies.put(s, 0);
			}
			
			if (log != null) {
				if (selected == null) {
					log.append(s.getID() + " None selected (stagnant generations: " + s.getStagnantGenerationsCount() + ")\n");
				}
				else {
					for (Chromosome c : selected) {
						log.append(s.getID() + ",  " + c.rank + ", " + c.getId() + ", " + (c.isElite ? "1" : "0") + ", " + nf.format(c.getPerformanceValue()) + ", " + ArrayUtil.toString(c.getFitnessValues(), ", ", nf) + "\n");
					}
				}
				log.append("\n");
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
				// Don't remove elites or single parents.
				Species s = result.get(i).getSpecie();
				if (!result.get(i).isElite && numSelectedForSpecies.get(s) > 1) {
					result.remove(i);
					numRemoved++;
					numSelectedForSpecies.put(s, numSelectedForSpecies.get(s) - 1);
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
		double overallFitness = 0;
		if (log != null) log.append("\n\n\n\n\nR, SID, CID, E, P, FO, FMO\n");
		for (List<Chromosome> front : fronts) {
			//overallFitness = Math.pow((rankMax - rank) / rankMax, 2);
			overallFitness = 2.0 / (2.0 + rank);
			for (Chromosome c : front) {
				c.setFitnessValue(overallFitness);
			
				if (log != null) {
					log.append(c.rank + ", " + c.getSpecie().getID() + ",  " + c.getId() + ", " + (c.isElite ? "1" : "0") + ", " + nf.format(c.getPerformanceValue()) + ", " + nf.format(c.getFitnessValue()) + ", " + ArrayUtil.toString(c.getFitnessValues(), ", ", nf) + "\n");
				}
			}
			if (log != null) log.append("\n");
			rank++;
		}
		
		if (log != null) {
			File dirFile = new File(props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY));
			if (!dirFile.exists())
				dirFile.mkdirs();
			try {
				BufferedWriter logFile = new BufferedWriter(new FileWriter(props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + props.getProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, "") + "nsgaii-" + props.getEvolver().getGeneration() + ".csv"));
				logFile.write(log.toString());
				logFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return result;
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
	
	public boolean changesOverallFitness() {
		return true;
	}
}