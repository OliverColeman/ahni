/*
 * Copyright 2001-2003 Neil Rotstan Copyright (C) 2004 Derek James and Philip Tucker
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
 * Modified on Feb 3, 2003 by Philip Tucker
 */
package org.jgapcustomised;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jgapcustomised.event.GeneticEvent;

import org.apache.log4j.Logger;

import com.anji.neat.Evolver;
import com.anji.util.Properties;

/**
 * Genotypes are fixed-length populations of chromosomes. As an instance of a <code>Genotype</code> is evolved, all of
 * its <code>Chromosome</code> objects are also evolved. A <code>Genotype</code> may be constructed normally, whereby an
 * array of <code>Chromosome</code> objects must be provided, or the static <code>randomInitialGenotype()</code> method
 * can be used to generate a <code>Genotype</code> with a randomized <code>Chromosome</code> population. Changes made by
 * Tucker and James for <a href="http://anji.sourceforge.net/">ANJI </a>:
 * <ul>
 * <li>added species</li>
 * <li>modified order of operations in <code>evolve()</code></li>
 * <li>added <code>addChromosome*()</code> methods</li>
 * </ul>
 */
public class Genotype implements Serializable {
	private static Logger logger = Logger.getLogger(Genotype.class);
	/**
	 * The current active Configuration instance.
	 */
	protected Configuration m_activeConfiguration;
	protected Properties props;
	protected SpeciationParms m_specParms;
	/**
	 * Species that makeup this Genotype's population.
	 */
	protected List<Species> m_species = new ArrayList<Species>();
	/**
	 * Chromosomes that makeup thie Genotype's population.
	 */
	protected List<Chromosome> m_chromosomes = new ArrayList<Chromosome>();

	protected int generation;
	protected int lastGenChangedSpeciesCompatThreshold;

	protected int targetPerformanceType;
	protected Chromosome fittest = null;
	protected Chromosome bestPerforming = null;
	protected int zeroFitnessCount = 0;
	protected int eliteCount = 0;

	protected int maxSpeciesSize, minSpeciesSize;

	/**
	 * This constructor is used for random initial Genotypes. Note that the Configuration object must be in a valid
	 * state when this method is invoked, or a InvalidconfigurationException will be thrown.
	 * 
	 * @param a_activeConfiguration The current active Configuration object.
	 * @param a_initialChromosomes <code>List</code> contains Chromosome objects: The Chromosome population to be
	 *            managed by this Genotype instance.
	 * @throws IllegalArgumentException if either the given Configuration object or the array of Chromosomes is null, or
	 *             if any of the Genes in the array of Chromosomes is null.
	 * @throws InvalidConfigurationException if the given Configuration object is in an invalid state.
	 */
	public Genotype(Properties props, Configuration a_activeConfiguration, List<Chromosome> a_initialChromosomes) throws InvalidConfigurationException {
		// Sanity checks: Make sure neither the Configuration, the array
		// of Chromosomes, nor any of the Genes inside the array are null.
		// ---------------------------------------------------------------
		if (a_activeConfiguration == null) {
			throw new IllegalArgumentException("The Configuration instance may not be null.");
		}

		if (a_initialChromosomes == null) {
			throw new IllegalArgumentException("The array of Chromosomes may not be null.");
		}

		for (int i = 0; i < a_initialChromosomes.size(); i++) {
			if (a_initialChromosomes.get(i) == null) {
				throw new IllegalArgumentException("The Chromosome instance at index " + i + " of the array of " + "Chromosomes is null. No instance in this array may be null.");
			}
		}

		this.props = props;

		targetPerformanceType = props.getProperty(Evolver.PERFORMANCE_TARGET_TYPE_KEY, "higher").toLowerCase().trim().equals("higher") ? 1 : 0;

		// Lock the settings of the Configuration object so that the cannot
		// be altered.
		// ----------------------------------------------------------------
		a_activeConfiguration.lockSettings();
		m_activeConfiguration = a_activeConfiguration;

		m_specParms = m_activeConfiguration.getSpeciationParms();

		adjustChromosomeList(a_initialChromosomes, a_activeConfiguration.getPopulationSize(), null);

		addChromosomes(a_initialChromosomes);

		generation = 0;
		lastGenChangedSpeciesCompatThreshold = 0;
	}

	/**
	 * adjust chromosome list to fit population size; first, clone population (starting at beginning of list) until we
	 * reach or exceed pop. size or trim excess (from end of list)
	 * 
	 * @param chroms <code>List</code> contains <code>Chromosome</code> objects
	 * @param targetSize
	 */
	private void adjustChromosomeList(List<Chromosome> chroms, int targetSize, Chromosome popFittest) {
		List<Chromosome> originals = new ArrayList<Chromosome>(chroms);
		while (chroms.size() < targetSize) {
			int idx = chroms.size() % originals.size();
			Chromosome orig = originals.get(idx);
			Chromosome clone = new Chromosome(orig.cloneMaterial(), m_activeConfiguration.nextChromosomeId());
			chroms.add(clone);
			orig.getSpecie().add(clone);
		}
		if (chroms.size() > targetSize) {
			// remove random chromosomes
			Collections.shuffle(m_chromosomes, m_activeConfiguration.getRandomGenerator());
			Iterator<Chromosome> popIter = m_chromosomes.iterator();
			while (chroms.size() > targetSize && popIter.hasNext()) {
				Chromosome c = popIter.next();
				// don't randomly remove elites or population fittest (they're supposed to survive till next generation)
				if (!c.isElite && !c.equals(popFittest) && (c.getSpecie() == null || !c.getSpecie().getRepresentative().equals(c))) {
					if (c.getSpecie() != null)
						c.getSpecie().remove(c); // remove from species
					popIter.remove();
				}
			}
		}
	}

	/**
	 * Add the specified chromosomes to this Genotype.
	 * 
	 * @param chromosomes A collection of Chromosome objects.
	 */
	protected void addChromosomes(Collection<Chromosome> chromosomes) {
		Iterator<Chromosome> iter = chromosomes.iterator();
		while (iter.hasNext()) {
			Chromosome c = iter.next();
			m_chromosomes.add(c);
		}
	}

	/**
	 * Add Chromosomes to this Genotype described by the given ChromosomeMaterial objects.
	 * 
	 * @param chromosomeMaterial A collection of ChromosomeMaterial objects.
	 */
	protected void addChromosomesFromMaterial(Collection<ChromosomeMaterial> chromosomeMaterial) {
		Iterator<ChromosomeMaterial> iter = chromosomeMaterial.iterator();
		while (iter.hasNext()) {
			ChromosomeMaterial cMat = iter.next();
			Chromosome chrom = new Chromosome(cMat, m_activeConfiguration.nextChromosomeId());
			m_chromosomes.add(chrom);
		}
	}

	/**
	 * @param cMat chromosome material from which to construct new chromosome object
	 * @see Genotype#addChromosome(Chromosome)
	 */
	/*
	 * protected void addChromosomeFromMaterial(ChromosomeMaterial cMat) { Chromosome chrom = new Chromosome(cMat,
	 * m_activeConfiguration.nextChromosomeId()); m_chromosomes.add(chrom); }
	 */

	/**
	 * add chromosome to population and to appropriate specie
	 * 
	 * @param chrom
	 */
	/*
	 * protected void addChromosome(Chromosome chrom) { m_chromosomes.add(chrom);
	 * 
	 * // specie collection boolean added = false; Species specie = null; Iterator<Species> iter = m_species.iterator();
	 * while (iter.hasNext() && !added) { specie = iter.next(); if (specie.match(chrom)) { specie.add(chrom); added =
	 * true; } } if (!added) { specie = new Species(m_activeConfiguration.getSpeciationParms(), chrom);
	 * m_species.add(specie); //System.out.println("adding species"); } }
	 */
	/**
	 * Clears all species and creates a fresh speciation.
	 */
	protected void respeciate() {
		m_species.clear();
		speciate();
	}

	/**
	 * (Re)assigns all chromosomes to a species. If no existing species are compatible with a chromosome a new species
	 * is created.
	 */
	protected void speciate() {
		// If true then chromosomes from previous generations will never be removed from their current species.
		boolean keepExistingSpecies = true;
		
		// sort so fittest are first as the fittest is used as the representative of a species (in case of creating new
		// species)
		Collections.sort(m_chromosomes, new ChromosomeFitnessComparator<Chromosome>(false, false));
		//Collections.shuffle(m_chromosomes, m_activeConfiguration.getRandomGenerator());

		// First determine new species for each chromosome (but don't assign yet).
		for (Chromosome chrom : m_chromosomes) {
			if (!keepExistingSpecies || chrom.getSpecie() == null) {
				chrom.resetSpecie();
				boolean added = false;
				for	(Species species : m_species) {
					if (species.match(chrom)) {
						chrom.setSpecie(species);
						added = true;
						break;
					}
				}
				if (!added) {
					// this also sets the species of chrom to the new species.
					Species species = new Species(m_activeConfiguration.getSpeciationParms(), chrom); 
					m_species.add(species);
					// System.out.println("Added new species");
				}
			}
		}

		// remove chromosomes from all species and record previous fittest
		for	(Species species : m_species) {
			species.setPreviousFittest(species.getFittest());
			species.clear();
		}

		// then (re)assign chromosomes to their correct species
		for (Chromosome chrom : m_chromosomes) {
			Species newSpecies = chrom.getSpecie();
			chrom.resetSpecie();
			newSpecies.add(chrom); // Also updates species reference in chrom.
		}

		// remove empty species (if any), and collect some stats
		minSpeciesSize = Integer.MAX_VALUE;
		maxSpeciesSize = 0;
		Iterator<Species> speciesIter = m_species.iterator();
		while (speciesIter.hasNext()) {
			Species species = speciesIter.next();

			if (species.isEmpty()) {
				species.originalSize = 0;
				speciesIter.remove();
				// System.out.println("Removed species (empty after speciation): " + species.getID() + "  age: " +
				// species.getAge());
			} else {
				if (species.size() > maxSpeciesSize)
					maxSpeciesSize = species.size();
				if (species.size() < minSpeciesSize)
					minSpeciesSize = species.size();

				species.originalSize = species.size();
			}
		}
	}

	/**
	 * @return List contains Chromosome objects, the population of Chromosomes.
	 */
	public synchronized List<Chromosome> getChromosomes() {
		return m_chromosomes;
	}

	/**
	 * @return List contains Species objects
	 */
	public synchronized List<Species> getSpecies() {
		return m_species;
	}

	/**
	 * Retrieves the Chromosome in the population with the highest fitness value.
	 * 
	 * @return The Chromosome with the highest fitness value, or null if there are no chromosomes in this Genotype.
	 */
	public synchronized Chromosome getFittestChromosome() {
		if (getChromosomes().isEmpty()) {
			return null;
		}

		// Set the highest fitness value to that of the first chromosome.
		// Then loop over the rest of the chromosomes and see if any has
		// a higher fitness value.
		// --------------------------------------------------------------
		Iterator<Chromosome> iter = getChromosomes().iterator();
		Chromosome fittestChromosome = iter.next();
		int fittestValue = fittestChromosome.getFitnessValue();

		while (iter.hasNext()) {
			Chromosome chrom = iter.next();
			if (chrom.getFitnessValue() > fittestValue) {
				fittestChromosome = chrom;
				fittestValue = fittestChromosome.getFitnessValue();
			}
		}

		return fittestChromosome;
	}

	Chromosome previousFittest = null;
	int previousFittestFitness = 0;

	/**
	 * Performs one generation cycle, evaluating fitness, selecting survivors, repopulting with offspring, and mutating
	 * new population. This is a modified version of original JGAP method which changes order of operations and splits
	 * <code>GeneticOperator</code> into <code>ReproductionOperator</code> and <code>MutationOperator</code>. New order
	 * of operations:
	 * <ol>
	 * <li>assign <b>fitness </b> to all members of population with <code>BulkFitnessFunction</code> or
	 * <code>FitnessFunction</code></li>
	 * <li><b>select </b> survivors and remove casualties from population</li>
	 * <li>re-fill population with offspring via <b>reproduction </b> operators</li>
	 * <li><b>mutate </b> offspring (note, survivors are passed on un-mutated)</li>
	 * </ol>
	 * Genetic event <code>GeneticEvent.GENOTYPE_EVALUATED_EVENT</code> is fired between steps 2 and 3. Genetic event
	 * <code>GeneticEvent.GENOTYPE_EVOLVED_EVENT</code> is fired after step 4.
	 */
	public synchronized Chromosome evolve() {
		try {
			//long start = System.currentTimeMillis();

			m_activeConfiguration.lockSettings();
			BulkFitnessFunction bulkFunction = m_activeConfiguration.getBulkFitnessFunction();
			int maxFitness = bulkFunction.getMaxFitnessValue();
			Iterator<Chromosome> it;
			
			// Reset performance values.
			it = m_chromosomes.iterator();
			while (it.hasNext()) {
				it.next().setPerformanceValue(-1);
			}

			//long startEval = System.currentTimeMillis();
			
			// Fire an event to indicate we're now evaluating all chromosomes.
			// -------------------------------------------------------
			m_activeConfiguration.getEventManager().fireGeneticEvent(new GeneticEvent(GeneticEvent.GENOTYPE_START_EVALUATION_EVENT, this));

			// If a bulk fitness function has been provided, then convert the
			// working pool to an array and pass it to the bulk fitness
			// function so that it can evaluate and assign fitness values to
			// each of the Chromosomes.
			// --------------------------------------------------------------
			if (bulkFunction != null) {
				bulkFunction.evaluate(m_chromosomes);
			} else {
				// Refactored such that Chromosome does not need a reference to Configuration. Left this
				// in for backward compatibility, but it makes more sense to use BulkFitnessFunction
				// now.
				FitnessFunction function = m_activeConfiguration.getFitnessFunction();
				it = m_chromosomes.iterator();
				while (it.hasNext()) {
					Chromosome c = it.next();
					int fitness = function.getFitnessValue(c);
					c.setFitnessValue(fitness);
				}
			}

			//long finishEval = System.currentTimeMillis();

			// find fittest and best performing
			if (previousFittest != null && m_chromosomes.contains(previousFittest)) {
				// Attempt to reuse previous fittest if available.
				fittest = previousFittest;
			}
			else {
				fittest = null;
			}
			bestPerforming = null;
			zeroFitnessCount = 0;
			for (Chromosome c : m_chromosomes) {
				if (fittest == null || fittest.getFitnessValue() < c.getFitnessValue()) {
					fittest = c;
				}
				if (bestPerforming == null || ((targetPerformanceType == 1 && bestPerforming.getPerformanceValue() < c.getPerformanceValue()) || (targetPerformanceType == 0 && bestPerforming.getPerformanceValue() > c.getPerformanceValue())) || (bestPerforming.getPerformanceValue() == c.getPerformanceValue() && bestPerforming.getFitnessValue() < c.getFitnessValue())) {
					bestPerforming = c;
					// System.out.println("bpv="+bestPerforming.getPerformanceValue());
				}
				if (c.getFitnessValue() <= 1) {
					zeroFitnessCount++;
				}
			}

			// check if best fitness has dropped a lot (indication that evaluation function is too inconsistent)
			if (previousFittest != null) {
				int oldFitnessPercent = (previousFittestFitness * 100) / maxFitness;
				int newFitnessPercent = (fittest.getFitnessValue() * 100) / maxFitness;
				//if (oldFitnessPercent > (newFitnessPercent + 5)) // if dropped more than 5%
				if (oldFitnessPercent > (newFitnessPercent)) // if dropped
					logger.info("(Fitness drop in percent:" + oldFitnessPercent + " > " + newFitnessPercent + ")");
			}
			
			//logger.info("Fittest: \n" + fittest.cloneMaterial().toString());
			
			previousFittest = fittest;
			previousFittestFitness = fittest.getFitnessValue();

			// Fire an event to indicate we've evaluated all chromosomes.
			// -------------------------------------------------------
			m_activeConfiguration.getEventManager().fireGeneticEvent(new GeneticEvent(GeneticEvent.GENOTYPE_EVALUATED_EVENT, this));

			//long startSelect = System.currentTimeMillis();

			// Speciate population.
			speciate();
			
			// attempt to stay within 20% of species count target
			int targetSpeciesCount = m_specParms.getSpeciationTarget();
			if ((targetSpeciesCount > 0 && (generation - lastGenChangedSpeciesCompatThreshold > 5) && // don't change
																										// threshold too
																										// frequently
			(Math.abs(m_species.size() - targetSpeciesCount) > Math.round(0.2 * targetSpeciesCount)))
			// || (generation - lastGenChangedSpeciesCompatThreshold > 100) //force respeciation every 100 generations
			) {

				if (targetSpeciesCount > 0) {
					// adjust number of species by altering speciation threshold if necessary
					if (m_species.size() > targetSpeciesCount) {
						m_specParms.setSpeciationThreshold(m_specParms.getSpeciationThreshold() + m_specParms.getSpeciationThresholdChange());
						// System.out.println("\tSpeciation threshold increased to " +
						// m_specParms.getSpeciationThreshold() + "   current species count: " + m_species.size());
					} else if (m_species.size() < targetSpeciesCount && m_specParms.getSpeciationThreshold() > 0.1 + m_specParms.getSpeciationThresholdChange()) {
						m_specParms.setSpeciationThreshold(m_specParms.getSpeciationThreshold() - m_specParms.getSpeciationThresholdChange());
						// System.out.println("\tSpeciation threshold reduced to " +
						// m_specParms.getSpeciationThreshold() + "   current species count: " + m_species.size());
					}
				}

				// int oldSpeciesCount = m_species.size();
				// respeciate();
				// logger.info("Respeciating: species count was " + oldSpeciesCount + ", is now " + m_species.size() +
				// " (target=" + targetSpeciesCount + "). Speciation threshold is now " +
				// m_specParms.getSpeciationThreshold());

				//logger.info("Adjusted species compatability threshold to " + m_specParms.getSpeciationThreshold());

				lastGenChangedSpeciesCompatThreshold = generation;
			}
			
			// Set which species contains the fittest individual.
			for (Species s : m_species) {
				s.containsFittest = false;
			}
			fittest.getSpecie().containsFittest = true;

			// Select chromosomes to generate new population from,
			// and determine elites that will survive unchanged to next generation
			// Note that speciation must occur before selection to allow using speciated fitness.
			// ------------------------------------------------------------
			eliteCount = 0;
			NaturalSelector selector = m_activeConfiguration.getNaturalSelector();
			selector.add(m_activeConfiguration, m_species, m_chromosomes);
			m_chromosomes = selector.select(m_activeConfiguration);
			selector.empty();
			
			// Sometimes the fittest can be removed if its species is too large (when using speciated fitness, 
			// in selection an individuals fitness is divided by it's species size)?
			if (fittest.getSpecie().getStagnantGenerationsCount() < selector.maxStagnantGenerations && !m_chromosomes.contains(fittest)) {
				m_chromosomes.add(fittest);
			}
			
			// System.out.println("Selected: " + m_chromosomes.size());

			// cull species down to contain only parent chromosomes.
			Iterator<Species> speciesIter = m_species.iterator();
			while (speciesIter.hasNext()) {
				Species s = speciesIter.next();

				// remove any individuals not in m_chromosomes from the species
				s.cull(m_chromosomes);
				if (s.isEmpty()) {
					s.originalSize = 0;
					speciesIter.remove();
					// System.out.println("Removed species (none selected): " + s.getID() + "  age: " + s.getAge());
				} else {
					eliteCount += s.getEliteCount();
				}
			}
			if (m_species.isEmpty()) {
				logger.info("All species removed!");
			}
			
			//long finishSelect = System.currentTimeMillis();

			// Repopulate the population of species and chromosomes with those selected
			// by the natural selector
			// -------------------------------------------------------

			// Fire an event to indicate we're starting genetic operators. Among
			// other things this allows for RAM conservation.
			m_activeConfiguration.getEventManager().fireGeneticEvent(new GeneticEvent(GeneticEvent.GENOTYPE_START_GENETIC_OPERATORS_EVENT, this));

			//long startReprod = System.currentTimeMillis();

			// Execute Reproduction Operators.
			// -------------------------------------
			List<ChromosomeMaterial> offspring = new ArrayList<ChromosomeMaterial>();
			for (ReproductionOperator operator : m_activeConfiguration.getReproductionOperators()) {
				operator.reproduce(m_activeConfiguration, m_species, offspring);
			}

			// Execute Mutation Operators.
			// -------------------------------------
			for (MutationOperator operator : m_activeConfiguration.getMutationOperators()) {
				operator.mutate(m_activeConfiguration, offspring);
			}
			
			//long finishReprod = System.currentTimeMillis();

			// cull population down to just elites (only elites survive to next gen)
			m_chromosomes.clear();
			speciesIter = m_species.iterator();
			while (speciesIter.hasNext()) {
				Species s = speciesIter.next();
				s.cullToElites(fittest);
				if (s.isEmpty()) {
					s.originalSize = 0;
					speciesIter.remove();
					// System.out.println("Removed species (no elites): " + s.getID() + "  age: " + s.getAge());
				} else {
					s.newGeneration(); // updates internal variables
					m_chromosomes.addAll(s.getChromosomes());
				}
			}
			
			// System.out.println("After cull to elites: " + m_chromosomes.size());

			// add offspring
			// ------------------------------
			addChromosomesFromMaterial(offspring);
			
			// in case we're off due to rounding errors
			if (m_chromosomes.size() != m_activeConfiguration.getPopulationSize()) {
				adjustChromosomeList(m_chromosomes, m_activeConfiguration.getPopulationSize(), fittest);
			}
			
			assert (m_chromosomes.contains(fittest));

			// Fire an event to indicate we've finished genetic operators. Among
			// other things this allows for RAM conservation.
			// -------------------------------------------------------
			m_activeConfiguration.getEventManager().fireGeneticEvent(new GeneticEvent(GeneticEvent.GENOTYPE_FINISH_GENETIC_OPERATORS_EVENT, this));

			// Fire an event to indicate we've performed an evolution.
			// -------------------------------------------------------
			m_activeConfiguration.getEventManager().fireGeneticEvent(new GeneticEvent(GeneticEvent.GENOTYPE_EVOLVED_EVENT, this));

			generation++;

			//long finish = System.currentTimeMillis();

			//double timeTotal = (finish - start) / 1000f;
			//double timeEval = (finishEval - startEval) / 1000f;
			//double timeSelect = (finishSelect - startSelect) / 1000f;
			//double timeReprod = (finishReprod - startReprod) / 1000f;

			// System.out.println("Time total: " + timeTotal + ", eval: " + timeEval + " (" + (timeEval/timeTotal) +
			// "), select: " + timeSelect + "(" + (timeSelect/timeTotal) + "), reprod: " + timeReprod + "(" +
			// (timeReprod/timeTotal) + ")");
		} catch (InvalidConfigurationException e) {
			throw new RuntimeException("bad config", e);
		}

		return fittest;
		// return bestPerforming;
	}

	public Chromosome getFittest() {
		return fittest;
	}

	public Chromosome getBestPerforming() {
		return bestPerforming;
	}
	
	public int getNumberOfChromosomesWithZeroFitnessFromLastGen() {
		return zeroFitnessCount;
	}

	public SpeciationParms getParameters() {
		return m_specParms;
	}

	public int getMaxSpeciesSize() {
		return maxSpeciesSize;
	}

	public int getMinSpeciesSize() {
		return minSpeciesSize;
	}

	/**
	 * @return <code>String</code> representation of this <code>Genotype</code> instance.
	 */
	public String toString() {
		StringBuffer buffer = new StringBuffer();

		Iterator<Chromosome> iter = m_chromosomes.iterator();
		while (iter.hasNext()) {
			Chromosome chrom = iter.next();
			buffer.append(chrom.toString());
			buffer.append(" [");
			buffer.append(chrom.getFitnessValue());
			buffer.append(']');
			buffer.append('\n');
		}

		return buffer.toString();
	}

	/**
	 * Convenience method that returns a newly constructed Genotype instance configured according to the given
	 * Configuration instance. The population of Chromosomes will created according to the setup of the sample
	 * Chromosome in the Configuration object, but the gene values (alleles) will be set to random legal values.
	 * <p>
	 * Note that the given Configuration instance must be in a valid state at the time this method is invoked, or an
	 * InvalidConfigurationException will be thrown.
	 * 
	 * @param a_activeConfiguration
	 * @return A newly constructed Genotype instance.
	 * @throws InvalidConfigurationException if the given Configuration instance not in a valid state.
	 */
	public static Genotype randomInitialGenotype(Properties props, Configuration a_activeConfiguration) throws InvalidConfigurationException {
		if (a_activeConfiguration == null) {
			throw new IllegalArgumentException("The Configuration instance may not be null.");
		}

		a_activeConfiguration.lockSettings();

		// Create an array of chromosomes equal to the desired size in the
		// active Configuration and then populate that array with Chromosome
		// instances constructed according to the setup in the sample
		// Chromosome, but with random gene values (alleles). The Chromosome
		// class' randomInitialChromosome() method will take care of that for
		// us.
		// ------------------------------------------------------------------
		int populationSize = a_activeConfiguration.getPopulationSize();
		List<Chromosome> chroms = new ArrayList<Chromosome>(populationSize);

		for (int i = 0; i < populationSize; i++) {
			ChromosomeMaterial material = ChromosomeMaterial.randomInitialChromosomeMaterial(a_activeConfiguration);
			chroms.add(new Chromosome(material, a_activeConfiguration.nextChromosomeId()));
		}

		return new Genotype(props, a_activeConfiguration, chroms);
	}

	public double getAveragePopulationFitness() {
		long fitness = 0;
		Iterator<Chromosome> iter = m_chromosomes.iterator();
		while (iter.hasNext()) {
			Chromosome chrom = iter.next();
			fitness += chrom.getFitnessValue();
		}
		return (double) ((double) fitness / ((long) m_activeConfiguration.getBulkFitnessFunction().getMaxFitnessValue() * (long) m_chromosomes.size()));
	}

	/**
	 * Compares this Genotype against the specified object. The result is true if the argument is an instance of the
	 * Genotype class, has exactly the same number of chromosomes as the given Genotype, and, for each Chromosome in
	 * this Genotype, there is an equal chromosome in the given Genotype. The chromosomes do not need to appear in the
	 * same order within the populations.
	 * 
	 * @param other The object to compare against.
	 * @return true if the objects are the same, false otherwise.
	 */
	public boolean equals(Object other) {
		try {
			// First, if the other Genotype is null, then they're not equal.
			// -------------------------------------------------------------
			if (other == null) {
				return false;
			}

			Genotype otherGenotype = (Genotype) other;

			// First, make sure the other Genotype has the same number of
			// chromosomes as this one.
			// ----------------------------------------------------------
			if (m_chromosomes.size() != otherGenotype.m_chromosomes.size()) {
				return false;
			}

			// Next, prepare to compare the chromosomes of the other Genotype
			// against the chromosomes of this Genotype. To make this a lot
			// simpler, we first sort the chromosomes in both this Genotype
			// and the one we're comparing against. This won't affect the
			// genetic algorithm (it doesn't care about the order), but makes
			// it much easier to perform the comparison here.
			// --------------------------------------------------------------
			Collections.sort(m_chromosomes);
			Collections.sort(otherGenotype.m_chromosomes);

			Iterator<Chromosome> iter = m_chromosomes.iterator();
			Iterator<Chromosome> otherIter = otherGenotype.m_chromosomes.iterator();
			while (iter.hasNext() && otherIter.hasNext()) {
				Chromosome chrom = iter.next();
				Chromosome otherChrom = otherIter.next();
				if (!(chrom.equals(otherChrom))) {
					return false;
				}
			}

			return true;
		} catch (ClassCastException e) {
			return false;
		}
	}
}
