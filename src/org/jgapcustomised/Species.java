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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Species are reproductively isolated segments of a population. They are used
 * to ensure diversity in the population. This can protect innovation, and also
 * serve to maintain a broader search space, avoiding being trapped in local
 * optima.
 * 
 * Modified by Oliver Coleman 2010-09-11 to use fittest chromosome as representative
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
	 * chromosomes active in current population; these logically should be a
	 * <code>Set</code>, but we use a <code>List</code> to make random selection
	 * easier, specifically in <code>ReproductionOperator</code>
	 */
	private List<Chromosome> chromosomes = new ArrayList<Chromosome>();

	//private Chromosome representative = null;

	private SpeciationParms speciationParms = null;

	private Chromosome fittest = null, previousFittest = null;

	private int stagnantGenerationsCount = 0;
	private int previousGenBestFitness = 0;
	private int age = 0;

	private int eliteCount;
	
	public int originalSize;
	
	

	/**
	 * for hibernate
	 */
	private Long id;

	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		//return representative.hashCode();
		return getFittest().hashCode();
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object o) {
		Species other = (Species) o;
		//return representative.equals(other.representative);
		return getFittest().equals(other.getFittest());
	}

	/**
	 * @return unique ID; this is chromosome ID of representative
	 */
	public Long getRepresentativeId() {
		//return representative.getId();
		if (getFittest() == null) {
			System.out.println("species empty!");
			return -1l;
		}
		return getFittest().getId();
	}

	/**
	 * Create new specie from representative. Representative is first member of
	 * specie, and all other members of specie are determined by compatibility
	 * with representative. Even if representative dies from population, a
	 * reference is kept here to determine specie membership.
	 * 
	 * @param aSpeciationParms
	 * @param aRepresentative
	 */
	public Species(SpeciationParms aSpeciationParms, Chromosome aRepresentative) {
		//representative = aRepresentative;
		fittest = aRepresentative;
		aRepresentative.setSpecie(this);
		chromosomes.add(aRepresentative);
		speciationParms = aSpeciationParms;
		id = idCount;
		idCount++;
	}

	/**
	 * @return representative chromosome
	 */
	protected Chromosome getRepresentative() {
		//return representative;
		return getFittest();
	}

	/**
	 * @param aChromosome
	 * @return true if chromosome is added, false if chromosome already is a
	 *         member of this specie
	 */
	public boolean add(Chromosome aChromosome) {
		//if (chromosomes.isEmpty() && !match(aChromosome))
		//	throw new IllegalArgumentException("chromosome does not match specie: " + aChromosome);
		if (chromosomes.contains(aChromosome))
			return false;
		aChromosome.setSpecie(this);
		if (fittest != null && aChromosome.m_fitnessValue > fittest.m_fitnessValue)
			fittest = aChromosome;
		return chromosomes.add(aChromosome);
	}
	
	
	/**
	 * @param aChromosome
	 * @return true if chromosome was removed, false if chromosome not a
	 *         member of this specie
	 */
	public boolean remove(Chromosome aChromosome) {
		aChromosome.resetSpecie();
		if (aChromosome == fittest)
			fittest = null;
		return chromosomes.remove(aChromosome);
	}
	

	/**
	 * @return all chromosomes in specie
	 */
	public List<Chromosome> getChromosomes() {
		return Collections.unmodifiableList(chromosomes);
	}
	
	/**
	 * Remove all chromosomes from this species.
	 * NOTE: this method does not update the species field in the removed Chromosomes.
	 */
	public void clear() {
		chromosomes.clear();
		fittest = null;
	}

	/**
	 * Remove all chromosomes from this specie except <code>keepers</code>
	 * NOTE: this method does not update the species field in the removed Chromosomes. 
	 * 
	 * @param keepers
	 *            <code>Collection</code> contains chromosome objects
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
		fittest = null;
	}

	/**
	 * remove all non-elite chromosomes from this species, except for population-wide fittest
	 */
	public void cullToElites(Chromosome popFittest) {
		Iterator<Chromosome> it = chromosomes.iterator();
		while (it.hasNext()) {
			Chromosome e = it.next();
			if (!e.isElite && e != popFittest) {
				e.resetSpecie();
				it.remove();
			}
		}
		fittest = null;
	}

	/**
	 * update internal variables (fittest, stagnantGenerationsCount) to begin
	 * new generation
	 * 
	 * @param keepers
	 *            <code>Collection</code> contains chromosome objects
	 */
	public void newGeneration() {
		age++;
		
		if (!chromosomes.isEmpty())
			getFittest();

		if (fittest != null) {
			//if fitness hasn't improved increase stagnant generations count
			if (fittest.getFitnessValue() <= previousGenBestFitness) {
				stagnantGenerationsCount++;
				// System.out.println("Stagnant generations increased to " +
				// stagnantGenerationsCount);
			} else {
				stagnantGenerationsCount = 0;
				previousGenBestFitness = fittest.getFitnessValue();
				// System.out.println("Stagnant generations reset");
			}
		}

		fittest = null;
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
	 * @return double adjusted fitness for aChromosome relative to this specie
	 * @throws IllegalArgumentException
	 *             if chromosome is not a member if this specie
	 */
	public double getChromosomeFitnessValue(Chromosome aChromosome) {
		if (aChromosome.getFitnessValue() < 0)
			throw new IllegalArgumentException("chromosome's fitness has not been set: " + aChromosome.toString());
		/*
		 * removed check for performance if (chromosomes.contains(aChromosome)
		 * == false) throw new IllegalArgumentException(
		 * "chromosome not a member of this specie: " + aChromosome.toString());
		 */
		return ((double) aChromosome.getFitnessValue()) / chromosomes.size();
	}

	/**
	 * @return average raw fitness (i.e., not adjusted for specie size) of all
	 *         chromosomes in specie
	 */
	public double getFitnessValue() {
		long totalRawFitness = 0;
		Iterator<Chromosome> iter = chromosomes.iterator();
		while (iter.hasNext()) {
			Chromosome aChromosome = iter.next();
			if (aChromosome.getFitnessValue() < 0)
				throw new IllegalStateException("chromosome's fitness has not been set: " + aChromosome.toString());
			totalRawFitness += aChromosome.getFitnessValue();
		}

		return (double) totalRawFitness / chromosomes.size();
	}

	/**
	 * @return Chromosome fittest in this specie
	 */
	public synchronized Chromosome getFittest() {
		if (fittest == null && !chromosomes.isEmpty()) {
			Iterator<Chromosome> it = chromosomes.iterator();
			fittest = it.next();
			while (it.hasNext()) {
				Chromosome next = it.next();
				if (next.getFitnessValue() > fittest.getFitnessValue())
					fittest = next;
			}
		}
		return fittest;
	}

	public Chromosome getPreviousFittest() {
		return previousFittest;
	}

	public void setPreviousFittest(Chromosome previousFittest) {
		this.previousFittest = previousFittest;
	}

	/**
	 * @param proportion
	 *            The proportion of Chromosomes to select, range (0, 1).
	 * @param minToSelect
	 *            The minimum number of Chromosomes to select. Use 0 for no
	 *            minimum.
	 * @return top proportion (or minToSelect, which ever is greater) of fittest
	 *         Chromosomes in this species.
	 */
	public List<Chromosome> getElite(double proportion, int minToSelect) {
		eliteCount = 0;

		int numToSelect = Math.max(minToSelect, (int) Math.round(proportion * size()));
		if (numToSelect == 0)
			return new ArrayList<Chromosome>(0);

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

		// System.out.println("selected " + result.size() +
		// " chroms as elite from species of size " + chromosomes.size());

		return result;
	}

	/**
	 * @param aChromosome
	 * @return boolean true iff compatibility difference between
	 *         <code>aChromosome</code? and representative is less than
	 *         speciation threshold
	 */
	public boolean match(Chromosome aChromosome) {
		if (isEmpty()) {
			System.err.println("Attempt to determine chromosome match for empty species");
			return false;
		}
		//return (representative.distance(aChromosome, speciationParms) < speciationParms.getSpeciationThreshold());
		return (getFittest().distance(aChromosome, speciationParms) < speciationParms.getSpeciationThreshold());
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer result = new StringBuffer("Specie ");
		result.append(getRepresentativeId());
		return result.toString();
	}

	/**
	 * @return String XML representation of object according to <a
	 *         href="http://nevt.sourceforge.net/">NEVT </a>.
	 */
	public String toXml() {
		StringBuffer result = new StringBuffer();
		result.append("<").append(SPECIE_TAG).append(" ").append(ID_TAG).append("=\"");
		result.append(getRepresentativeId()).append("\" ").append(COUNT_TAG).append("=\"");
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
}
