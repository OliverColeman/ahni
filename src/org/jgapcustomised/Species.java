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
package org.jgapcustomised;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Species are reproductively isolated segments of a population. They are used to ensure diversity in the population.
 * This can protect innovation, and also serve to maintain a broader search space, avoiding being trapped in local
 * optima.
 * 
 * @author Philip Tucker
 */
public class Species {

	/**
	 * XML base tag
	 */
	public final static String SPECIE_TAG = "species";

	/**
	 * XML ID tag
	 */
	public final static String ID_TAG = "id";

	/**
	 * XML count tag
	 */
	public final static String COUNT_TAG = "count";

	/**
	 * XML chromosome tag
	 */
	public final static String CHROMOSOME_TAG = "chromosome";

	/**
	 * XML fitness tag
	 */
	public final static String FITNESS_TAG = "fitness";

	private static long idCount = 0;

	/**
	 * chromosomes active in current population; these logically should be a <code>Set</code>, but we use a
	 * <code>List</code> to make random selection easier, specifically in <code>ReproductionOperator</code>
	 */
	private List<Chromosome> chromosomes = Collections.synchronizedList(new LinkedList<Chromosome>());

	private ChromosomeMaterial representative = null;

	private SpeciationParms speciationParms = null;

	private Chromosome bestPerforming = null, previousBestPerforming = null;

	private int stagnantGenerationsCount = 0;
	private double bestPerformanceEver = 0;
	private int age = 0;

	private int eliteCount;

	public int originalSize;
	public int previousOriginalSize;
	
	private double averageFitness = 0;

	private long id;

	/**
	 * True iff this species contains the bestPerforming individual from the entire population.
	 */
	public boolean containsBestPerforming;

	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return (int) id;
		//return representative.hashCode();
		//return getFittest().hashCode();
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object o) {
		Species other = (Species) o;
		return id == other.id;
		//return representative.equals(other.representative);
		//return getFittest().equals(other.getFittest());
	}

	/**
	 * Create new species defined by given representative material.
	 * 
	 * @param aSpeciationParms
	 * @param representativeMaterial The representative chromosome material. Need not correspond to any material in actual population, it just represents a point in genome space. The material is cloned.
	 */
	public Species(SpeciationParms aSpeciationParms, ChromosomeMaterial representativeMaterial) {
		representative = representativeMaterial.clone(null);
		bestPerforming = null;
		speciationParms = aSpeciationParms;
		// Synchronize on ID_TAG, for want of something better (can't synch on idCount as Long is immutable.
		synchronized (ID_TAG) {
			id = idCount;
			idCount++;
		}
	}

	/**
	 * Create new species with given Chromosome as first member of species. The material of given Chromosome is cloned and used as the (initial) representative material. 
	 * 
	 * @param aSpeciationParms
	 * @param first
	 */
	public Species(SpeciationParms aSpeciationParms, Chromosome first) {
		representative = first.getMaterial().clone(null);
		speciationParms = aSpeciationParms;
		// Synchronize on ID_TAG, for want of something better (can't synch on idCount as Long is immutable.
		synchronized (ID_TAG) {
			id = idCount;
			idCount++;
		}
		add(first);
		bestPerforming = first;
	}

	/**
	 * @return representative chromosome
	 */
	public ChromosomeMaterial getRepresentative() {
		return representative;
	}

	/**
	 * Sets the representative material. Need not correspond to any material in actual population, it just represents a point in genome space. The material is cloned.
	 */
	public void setRepresentative(ChromosomeMaterial material) {
		representative = material.clone(null);
	}

	/**
	 * Add a chromosome to this species. Updates the species reference in the chromosome.
	 * @param aChromosome
	 * @return true if chromosome is added, false if chromosome already is a member of this species. 
	 * 
	 */
	public boolean add(Chromosome aChromosome) {
		if (aChromosome.getSpecie() != null) {
			throw new IllegalArgumentException("Chromosome is already a member of another species. Try moveFromCurrentSpecies() instead?");
		}
		// if (chromosomes.isEmpty() && !match(aChromosome))
		// throw new IllegalArgumentException("chromosome does not match specie: " + aChromosome);
		if (chromosomes.contains(aChromosome))
			return false;
		aChromosome.setSpecie(this);
		bestPerforming = null; // Set to null rather than test as the performance comparator checks ID as well as performance
		return chromosomes.add(aChromosome);
	}
	
	/**
	 * @param aChromosome
	 * @return true if chromosome was removed, false if chromosome not a member of this specie
	 */
	public boolean remove(Chromosome aChromosome) {
		int index = chromosomes.indexOf(aChromosome);
		if (index == -1) {
			return false;
		}
		aChromosome.resetSpecie();
		if (aChromosome == bestPerforming)
			bestPerforming = null;
		chromosomes.remove(index);
		return true;
	}
	
	/**
	 * Move the given Chromosome from it's current species to this species. An IllegalArgumentException is thrown if the chromosome is not currently in a species.
	 * @return false if the chromosome's current species is this species, otherwise true.
	 */
	public boolean moveFromCurrentSpecies(Chromosome c) {
		if (c.getSpecie() == null) {
			throw new IllegalArgumentException("Can't move from null species.");
		}
		
		if (c.getSpecie().equals(this)) return false;
		
		c.getSpecie().chromosomes.remove(c);
		if (c == c.getSpecie().bestPerforming) {
			c.getSpecie().bestPerforming = null;
		}
		
		chromosomes.add(c);
		c.resetSpecie();
		c.setSpecie(this);
		
		return true;
	}
	
	/**
	 * Add the given Chromosome to this species, removing it from it's current species if necessary. 
	 * @return false if the chromosome's current species is this species, otherwise true.
	 */
	public boolean addOrMoveFromCurrentSpecies(Chromosome c) {
		if (c.getSpecie() != null && c.getSpecie().equals(this)) return false;

		if (c.getSpecie() != null) {
			c.getSpecie().chromosomes.remove(c);
			if (c == c.getSpecie().bestPerforming) {
				c.getSpecie().bestPerforming = null;
			}
		}
		
		chromosomes.add(c);
		c.resetSpecie();
		c.setSpecie(this);
		
		return true;
	}

	/**
	 * @return all chromosomes in specie as an immutable list.
	 */
	public List<Chromosome> getChromosomes() {
		return Collections.unmodifiableList(chromosomes);
	}

	/**
	 * Remove all chromosomes from this species. NOTE: this method does not update the species field in the removed
	 * Chromosomes.
	 */
	public void clear() {
		chromosomes.clear();
		bestPerforming = null;
	}

	/**
	 * Remove all chromosomes from this specie except <code>keepers</code> NOTE: this method does not update the species
	 * field in the removed Chromosomes.
	 * 
	 * @param keepers <code>Collection</code> contains chromosome objects
	 */
	public void cull(Collection<Chromosome> keepers) {
		Iterator<Chromosome> it = chromosomes.iterator();
		while (it.hasNext()) {
			Chromosome e = it.next();
			if (!keepers.contains(e)) {
				e.resetSpecie();
				it.remove();
			}
		}
		bestPerforming = null;
	}

	/**
	 * remove all non-elite chromosomes from this species, except for population-wide bestPerforming
	 */
	public void cullToElites(Chromosome popBestPerforming) {
		Iterator<Chromosome> it = chromosomes.iterator();
		while (it.hasNext()) {
			Chromosome e = it.next();
			if (!e.isElite && e != popBestPerforming) {
				e.resetSpecie();
				it.remove();
			}
		}
		bestPerforming = null;
	}

	/**
	 * update internal variables (bestPerforming, stagnantGenerationsCount) to begin new generation
	 */
	public void newGeneration() {
		age++;

		if (!chromosomes.isEmpty())
			getBestPerforming();

		if (bestPerforming != null) {
			// if performance hasn't improved increase stagnant generations count
			if (bestPerforming.getPerformanceValue() <= bestPerformanceEver) {
				stagnantGenerationsCount++;
				// System.out.println("Stagnant generations increased to " +
				// stagnantGenerationsCount);
			} else {
				stagnantGenerationsCount = 0;
				bestPerformanceEver = bestPerforming.getPerformanceValue();
				// System.out.println("Stagnant generations reset");
			}
		}
		bestPerforming = null;
		averageFitness = Double.NaN;
		previousOriginalSize = originalSize;
		originalSize = 0;
	}

	/**
	 * @return true iff specie contains no active chromosomes in population
	 */
	public boolean isEmpty() {
		return chromosomes.isEmpty();
	}

	/**
	 * @return number of chomosomes in species
	 */
	public int size() {
		return chromosomes.size();
	}

	public int getStagnantGenerationsCount() {
		return stagnantGenerationsCount;
	}

	public void setStagnantGenerationsCount(int stagnantGenerationsCount) {
		this.stagnantGenerationsCount = stagnantGenerationsCount;
	}

	/**
	 * @param aChromosome
	 * @return Adjusted fitness for aChromosome relative to this species
	 * @throws IllegalArgumentException if chromosome is not a member if this specie
	 */
	public double getChromosomeSpeciatedFitnessValue(Chromosome aChromosome) {
		if (aChromosome.getFitnessValue() == Double.NaN)
			throw new IllegalArgumentException("chromosome's fitness has not been set: " + aChromosome.toString());
		/*
		 * removed check for performance if (chromosomes.contains(aChromosome) == false) throw new
		 * IllegalArgumentException( "chromosome not a member of this specie: " + aChromosome.toString());
		 */
		return ((double) aChromosome.getFitnessValue()) / originalSize;
	}

	/**
	 * @return Average fitness over all chromosomes in this species. This is equivalent to the sum of calling {@link #getChromosomeSpeciatedFitnessValue(Chromosome)} over all chromosomes in this species.
	 */
	public double getAverageFitnessValue() {
		if (averageFitness == Double.NaN)
			throw new IllegalStateException("Average fitness has not yet been calculated for the species.");
		return averageFitness;
	}

	/**
	 * @return Chromosome bestPerforming in this specie
	 */
	public synchronized Chromosome getBestPerforming() {
		if (bestPerforming == null && !chromosomes.isEmpty()) {
			Collections.sort(chromosomes, new ChromosomePerformanceComparator(false));
			bestPerforming = chromosomes.get(0);
		}
		return bestPerforming;
	}

	public Chromosome getPreviousBestPerforming() {
		return previousBestPerforming;
	}
	
	public double getBestPerformanceEver() {
		return bestPerformanceEver;
	}
	public boolean performanceIncreasedLastGen() {
		return stagnantGenerationsCount == 0 && age > 0;
	}

	public void setPreviousBestPerforming(Chromosome previousBestPerforming) {
		this.previousBestPerforming = previousBestPerforming;
	}

	/**
	 * Determine and return the top specified proportion of chromosomes in this species. The returned chromosomes are
	 * marked as being elite and all other chromosomes in this species are marked as not elite.
	 * 
	 * @param proportion The proportion of Chromosomes to select, range (0, 1).
	 * @param minToSelect The minimum number of Chromosomes to select. Use 0 for no minimum.
	 * @param bestPerforming If not null this is the population-wide best performing and should be added to the list of elites.
	 * @return top proportion (or minToSelect, which ever is greater) of fittest Chromosomes in this species.
	 */
	public List<Chromosome> getElite(double proportion, int minToSelect, Chromosome bestPerforming) {
		eliteCount = 0;
		int numToSelect = Math.max(0, Math.max(minToSelect, (int) Math.round(proportion * size())) - (bestPerforming != null ? 1 : 0));
		
		Collections.sort(chromosomes, new ChromosomeFitnessComparator(false /* asc */, false /* speciated fitness */));
		List<Chromosome> result = new ArrayList<Chromosome>(numToSelect);
		
		Iterator<Chromosome> it = chromosomes.iterator();
		// get numToSelect elites
		while (it.hasNext() && (result.size() < numToSelect)) {
			Chromosome e = it.next();
			e.isElite = true;
			result.add(e);
			eliteCount++;
		}
		// mark remaining as not elite
		while (it.hasNext()) {
			Chromosome c = it.next();
			c.isElite = false;
		}
		
		// Make sure we include the population-wide best performing in the elites.
		if (bestPerforming != null) {
			bestPerforming.isElite=true;
			result.add(bestPerforming);
			eliteCount++;
		}
		
		return result;
	}

	/**
	 * @param numToSelect The number of Chromosomes to select.
	 * @param includeElites If true then elites will be included in the returned list, otherwise elites will be ignored.
	 * @return the numToSelect fittest Chromosomes in this species.
	 */
	public List<Chromosome> getTop(int numToSelect, boolean includeElites) {
		Collections.sort(chromosomes, new ChromosomeFitnessComparator(false /* asc */, false /* speciated fitness */));
		List<Chromosome> result = new ArrayList<Chromosome>(numToSelect);
		Iterator<Chromosome> it = chromosomes.iterator();
		// get numToSelect parents
		while (it.hasNext() && (result.size() < numToSelect)) {
			Chromosome c = it.next();
			if (includeElites || !c.isElite) {
				result.add(c);
			}
		}
		return result;
	}


	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer result = new StringBuffer("Specie ");
		result.append(getID());
		return result.toString();
	}

	/**
	 * @return String XML representation of object according to <a href="http://nevt.sourceforge.net/">NEVT </a>.
	 */
	public String toXml() {
		StringBuffer result = new StringBuffer();
		result.append("<").append(SPECIE_TAG).append(" ").append(ID_TAG).append("=\"");
		//result.append(getRepresentativeId()).append("\" ").append(COUNT_TAG).append("=\"");
		result.append(getChromosomes().size()).append("\">\n");
		Iterator<Chromosome> chromIter = getChromosomes().iterator();
		while (chromIter.hasNext()) {
			Chromosome chromToStore = chromIter.next();
			result.append("<").append(CHROMOSOME_TAG).append(" ").append(ID_TAG).append("=\"");
			result.append(chromToStore.getId()).append("\" ").append(FITNESS_TAG).append("=\"");
			result.append(chromToStore.getFitnessValue()).append("\" />\n");
		}
		result.append("</").append(SPECIE_TAG).append(">\n");
		return result.toString();
	}

	public int getEliteCount() {
		return eliteCount;
	}

	/**
	 * for hibernate
	 * 
	 * @return unique id
	 */
	public Long getID() {
		return id;
	}

	/**
	 * for hibernate
	 * 
	 * @param aId
	 */
	private void setID(Long aId) {
		id = aId;
	}

	public int getAge() {
		return age;
	}

	public void setElites(List<Chromosome> elites) {
		eliteCount = 0;
		for (Chromosome c : chromosomes) {
			if (c.getSpecie() != this) {
				throw new IllegalStateException("The Chromosome to set as elite in a species is not a member of the species.");
			}
			
			if (elites.contains(c)) {
				c.isElite = true;
				eliteCount++;
			}
			else {
				c.isElite = false;
			}
		}
	}

	public void calculateAverageFitness() {
		if (chromosomes.size() != originalSize) {
			throw new IllegalStateException("Should not be calculating average fitness for species as the number of chromosomes it has does not match the species original size");
		}
		double totalRawFitness = 0;
		for (Chromosome aChromosome : chromosomes) {
			if (aChromosome.getFitnessValue() == Double.NaN)
				throw new IllegalStateException("chromosome's fitness has not been set: " + aChromosome.toString());
			totalRawFitness += aChromosome.getFitnessValue();
		}
		averageFitness = totalRawFitness / originalSize;
	}

	/**
	 * Remove the clones from this species, as determined by {@link org.jgapcustomised.Chromosome#isEquivalent(Chromosome)}.
	 */
	@SuppressWarnings("unchecked")
	public List<Chromosome> cullClones() {
		ArrayList<Chromosome> chromosomesArr = new ArrayList<Chromosome>(chromosomes);
		
		/*
		// Sort in descending order of fitness so we can't remove fittest (elites haven't been set when this is called so don't worry about them).
		Collections.sort(chromosomesArr, new ChromosomeFitnessComparator(false, false));
		*/
		
		// Make sure best performing is first
		getBestPerforming();
		if (bestPerforming != null && chromosomesArr.get(0) != bestPerforming) {
			chromosomesArr.remove(bestPerforming);
			chromosomesArr.add(0, bestPerforming);
		}
		
		List<Chromosome> toRemove = new ArrayList<Chromosome>();
		for (int i = 0; i < chromosomesArr.size(); i++) {
			Chromosome c1 = chromosomesArr.get(i);
			for (int j = i+1; j < chromosomesArr.size(); j++) {
				Chromosome c2 = chromosomesArr.get(j);
				if (!toRemove.contains(c2) && c1.isEquivalent(c2)) {
					assert (c2 != bestPerforming) : "shouldn't remove best performing, index is " + j + "\n" + chromosomesArr + "\n" + chromosomes;
					toRemove.add(c2);
					originalSize--;
				}
			}
		}
		
		for (Chromosome c : toRemove) {
			c.resetSpecie();
			chromosomes.remove(c);
		}
		return toRemove;
	}
}
