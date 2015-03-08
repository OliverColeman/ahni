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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.thoughtworks.xstream.XStream;

/**
 * This is the guts of the original Chromosome object, pulled out so the genes can be modified by genetic operators
 * before creating the Chromosome object. Also enables us to handle special cases, like sample chromosome, where you
 * don't need a Configuration or fitness value. Also, made methods not synchronized, since only Genotype.evolve() should
 * be modifying this object.
 */
public class ChromosomeMaterial implements Comparable, Serializable {
	private static final long serialVersionUID = 1L;
	
	private Long primaryParentId = null;
	private Long secondaryParentId = null;
	private SortedSet<Allele> m_alleles = null;
	private boolean shouldMutate = true;
	
	public boolean pruned;
	
	/**
	 * Create chromosome with two parents. Used for crossover.
	 * 
	 * @param a_initialAlleles
	 * @param aPrimaryParentId
	 * @param aSecondaryParentId
	 */
	public ChromosomeMaterial(Collection<Allele> a_initialAlleles, Long aPrimaryParentId, Long aSecondaryParentId) {
		// Sanity checks: make sure the genes array isn't null and
		// that none of the genes contained within it are null.
		// -------------------------------------------------------
		if (a_initialAlleles == null) {
			throw new IllegalArgumentException("The given List of alleles cannot be null.");
		}

		setPrimaryParentId(aPrimaryParentId);
		setSecondaryParentId(aSecondaryParentId);

		// sanity check
		Iterator iter = a_initialAlleles.iterator();
		while (iter.hasNext()) {
			if (iter.next() == null) {
				throw new IllegalArgumentException("The given List of alleles cannot contain nulls.");
			}
		}

		m_alleles = new TreeSet<Allele>(a_initialAlleles);
	}

	/**
	 * Create chromosome with one parents. Used for cloning.
	 * 
	 * @param a_initialGenes
	 * @param aPrimaryParentId
	 * @see ChromosomeMaterial#ChromosomeMaterial(Collection, Long, Long)
	 */
	public ChromosomeMaterial(Collection<Allele> a_initialGenes, Long aPrimaryParentId) {
		this(a_initialGenes, aPrimaryParentId, null);
	}

	/**
	 * Create chromosome with no parents. Used for startup sample chromosome material.
	 * 
	 * @param a_initialAlleles
	 * @see ChromosomeMaterial#ChromosomeMaterial(Collection, Long, Long)
	 */
	public ChromosomeMaterial(Collection<Allele> a_initialAlleles) {
		this(a_initialAlleles, null, null);
	}

	/**
	 * for hibernate
	 */
	ChromosomeMaterial() {
		this(new TreeSet<Allele>(), null, null);
	}

	/**
	 * Returns a copy of this ChromosomeMaterial. The returned instance can evolve independently of this instance.
	 * 
	 * @param parentId represents ID of chromosome that was cloned. If this is initial chromosome material without a
	 *            parent, or is the clone of material only (e.g., before the it has become a Chromosome), parentId ==
	 *            null.
	 * @return copy of this object
	 */
	public ChromosomeMaterial clone(Long parentId) {
		// First we make a copy of each of the Genes. We explicity use the Gene
		// at each respective gene location (locus) to create the new Gene that
		// is to occupy that same locus in the new Chromosome.
		// -------------------------------------------------------------------
		List<Allele> copyOfAlleles = new ArrayList<Allele>(m_alleles.size());

		for (Allele orig : m_alleles) {
			copyOfAlleles.add(orig.cloneAllele());
		}

		// Now construct a new Chromosome with the copies of the genes and return it.
		// ---------------------------------------------------------------
		Long cloneParentId = (parentId == null) ? getPrimaryParentId() : parentId;
		return new ChromosomeMaterial(copyOfAlleles, cloneParentId);
	}
	
	/**
	 * Returns the size of this ChromosomeMaterial (the number of alleles it contains).
	 * 
	 * @return The number of alleles contained within this ChromosomeMaterial instance.
	 */
	public int size() {
		return m_alleles.size();
	}

	/**
	 * Retrieves the set of genes. This method exists primarily for the benefit of GeneticOperators that require the
	 * ability to manipulate Chromosomes at a low level.
	 * 
	 * @return an array of the Genes contained within this Chromosome.
	 */
	public SortedSet<Allele> getAlleles() {
		return m_alleles;
	}

	/**
	 * Returns a string representation of this Chromosome, useful for some display purposes.
	 * 
	 * @return A string representation of this Chromosome.
	 */
	public String toString() {
		StringBuffer representation = new StringBuffer();
		//representation.append("[ ");

		// Append the representations of each of the gene Alleles.
		// -------------------------------------------------------
		Iterator iter = m_alleles.iterator();
		if (iter.hasNext()) {
			Allele allele = (Allele) iter.next();
			representation.append(allele.toString());
		}
		while (iter.hasNext()) {
			Allele allele = (Allele) iter.next();
			representation.append("\n");
			representation.append(allele.toString());
		}
		//representation.append(" ]");

		return representation.toString();
	}
	
	/**
	 * Returns an XML representation of this Chromosome. Useful for exporting.
	 */
	public String toXML() {
		XStream xstream = new XStream();
		return xstream.toXML(this);
	}
	
	public static ChromosomeMaterial fromXML(String xml) {
		XStream xstream = new XStream();
		return (ChromosomeMaterial) xstream.fromXML(xml);
	}
	
	/**
	 * Convenience method that returns a new Chromosome instance with its genes values (alleles) randomized. Note that,
	 * if possible, this method will acquire a Chromosome instance from the active ChromosomePool (if any) and then
	 * randomize its gene values before returning it. If a Chromosome cannot be acquired from the pool, then a new
	 * instance will be constructed and its gene values randomized before returning it.
	 * 
	 * @param a_activeConfiguration The current active configuration.
	 * @return new <code>ChromosomeMaterial</code>
	 * 
	 * @throws InvalidConfigurationException if the given Configuration instance is invalid.
	 * @throws IllegalArgumentException if the given Configuration instance is null.
	 */
	public static ChromosomeMaterial randomInitialChromosomeMaterial(Configuration a_activeConfiguration) throws InvalidConfigurationException {
		// Sanity check: make sure the given configuration isn't null.
		// -----------------------------------------------------------
		if (a_activeConfiguration == null) {
			throw new IllegalArgumentException("Configuration instance must not be null");
		}

		// Lock the configuration settings so that they can't be changed
		// from now on.
		// -------------------------------------------------------------
		a_activeConfiguration.lockSettings();

		// If we got this far, then we weren't able to get a Chromosome from
		// the pool, so we have to construct a new instance and build it from
		// scratch.
		// ------------------------------------------------------------------
		ChromosomeMaterial newMaterial = a_activeConfiguration.getSampleChromosomeMaterial().clone(null);

		Iterator iter = newMaterial.getAlleles().iterator();
		while (iter.hasNext()) {
			Allele newAllele = (Allele) iter.next();

			// Perturb the gene's value (allele) a random amount.
			// ------------------------------------------------
			//if (newAllele instanceof ConnectionAllele)
				newAllele.setToRandomValue(a_activeConfiguration.getRandomGenerator(), true);
		}

		return newMaterial;
	}

	/**
	 * Compares this Chromosome against the specified object. The result is true if and the argument is an instance of
	 * the Chromosome class and has a set of genes equal to this one.
	 * 
	 * @param other The object to compare against.
	 * @return true if the objects are the same, false otherwise.
	 */
	public boolean equals(Object other) {
		return compareTo(other) == 0;
	}
	
	/**
	 * Compares this Chromosome against the specified object. The result is true if and the argument is an instance of
	 * the Chromosome class and has a set of genes with equal values (eg connection weight, activation type) to this one.
	 * 
	 * @param other The object to compare against.
	 * @return true if the objects are the same, false otherwise.
	 */
	public boolean isEquivalent(ChromosomeMaterial other) {
		// First, if the other ChromosomeMaterial is null, then this chromosome
		// is automatically the "greater" Chromosome.
		// ---------------------------------------------------------------
		if (other == null) {
			return false;
		}

		ChromosomeMaterial otherChromosome = (ChromosomeMaterial) other;
		SortedSet otherAlleles = otherChromosome.m_alleles;

		// If the other Chromosome doesn't have the same number of genes,
		// then whichever has more is the "greater" Chromosome.
		// --------------------------------------------------------------
		if (otherAlleles.size() != m_alleles.size()) {
			return false;
		}

		// Next, compare the gene values (alleles) for differences. If
		// one of the genes is not equal, then we return the result of its
		// comparison.
		// ---------------------------------------------------------------
		Iterator iter = m_alleles.iterator();
		Iterator otherIter = otherAlleles.iterator();
		while (iter.hasNext() && otherIter.hasNext()) {
			Allele allele = (Allele) iter.next();
			Allele otherAllele = (Allele) otherIter.next();
			if (!allele.isEquivalent(otherAllele))
				return false;
		}
		return true;
	}

	/**
	 * Compares the given Chromosome to this Chromosome. This chromosome is considered to be "less than" the given
	 * chromosome if it has a fewer number of genes or if any of its gene values (alleles) are less than their
	 * corresponding gene values in the other chromosome.
	 * 
	 * @param other The Chromosome against which to compare this chromosome.
	 * @return a negative number if this chromosome is "less than" the given chromosome, zero if they are equal to each
	 *         other, and a positive number if this chromosome is "greater than" the given chromosome.
	 * @see Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object other) {
		// First, if the other ChromosomeMaterial is null, then this chromosome
		// is automatically the "greater" Chromosome.
		// ---------------------------------------------------------------
		if (other == null) {
			return 1;
		}

		ChromosomeMaterial otherChromosome = (ChromosomeMaterial) other;
		SortedSet otherAlleles = otherChromosome.m_alleles;

		// If the other Chromosome doesn't have the same number of genes,
		// then whichever has more is the "greater" Chromosome.
		// --------------------------------------------------------------
		if (otherAlleles.size() != m_alleles.size()) {
			return m_alleles.size() - otherAlleles.size();
		}

		// Next, compare the gene values (alleles) for differences. If
		// one of the genes is not equal, then we return the result of its
		// comparison.
		// ---------------------------------------------------------------
		Iterator iter = m_alleles.iterator();
		Iterator otherIter = otherAlleles.iterator();
		while (iter.hasNext() && otherIter.hasNext()) {
			Allele allele = (Allele) iter.next();
			Allele otherAllele = (Allele) otherIter.next();
			Class srcClass = allele.getClass();
			Class targetClass = otherAllele.getClass();
			if (srcClass != targetClass) {
				return srcClass.getName().compareTo(targetClass.getName());
			}

			int comparison = allele.compareTo(otherAllele);

			if (comparison != 0) {
				return comparison;
			}
		}

		// Everything is equal. Return zero.
		// ---------------------------------
		return 0;
	}

	/**
	 * @return primary parent ID; dominant parent if chromosome spawned by crossover
	 */
	public Long getPrimaryParentId() {
		return primaryParentId;
	}

	/**
	 * @return primary parent ID; recessive parent if chromosome spawned by crossover
	 */
	public Long getSecondaryParentId() {
		return secondaryParentId;
	}

	/**
	 * for hibernate
	 * 
	 * @param id ID of dominant parent
	 */
	void setPrimaryParentId(Long id) {
		if (primaryParentId != null) {
			throw new IllegalStateException("can not set primary parent ID twice");
		}
		primaryParentId = id;
	}

	/**
	 * @param id ID of recessive parent
	 */
	public void setSecondaryParentId(Long id) {
		if (secondaryParentId != null) {
			throw new IllegalStateException("can not set secondary parent ID twice");
		}
		secondaryParentId = id;
	}

	private static long getMaxInnovationId(Collection someAlleles) {
		long result = -1;
		Iterator iter = someAlleles.iterator();
		while (iter.hasNext()) {
			Allele allele = (Allele) iter.next();
			if (allele.getInnovationId().longValue() > result) {
				result = allele.getInnovationId().longValue();
			}
		}
		return result;
	}
	
	public long getMinInnovationID() {
		if (m_alleles.isEmpty()) return -1;
		return m_alleles.first().getInnovationId();
	}
	
	public long getMaxInnovationID() {
		if (m_alleles.isEmpty()) return -1;
		return m_alleles.last().getInnovationId();
	}

	/**
	 * returns excess alleles, defined as those alleles whose innovation ID is greater than <code>threshold</code>;
	 * also, removes excess alleles from <code>alleles</code>
	 * 
	 * @param alleles <code>List</code> contains <code>Allele</code> objects
	 * @param threshold
	 * @return excess alleles, <code>List</code> contains <code>Allele</code> objects
	 */
	private List<Allele> extractExcessAlleles(Collection<Allele> alleles, long threshold) {
		List<Allele> result = new ArrayList<Allele>();
		Iterator<Allele> iter = alleles.iterator();
		while (iter.hasNext()) {
			Allele allele = iter.next();
			if (allele.getInnovationId().longValue() > threshold) {
				iter.remove();
				result.add(allele);
			}
		}
		return result;
	}

	/**
	 * Calculates compatibility distance between this and <code>target</code> according to <a
	 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT </a> speciation methodology. Made it
	 * generic enough that the genes do not have to be nodes and connections.
	 * 
	 * @param target
	 * @param speciationParms
	 * @return distance between this object and <code>target</code>
	 * @see Allele#distance(Allele)
	 */
	public double distance(ChromosomeMaterial target, SpeciationParms speciationParms) {
		//boolean log = Math.random() < 0.01;
		boolean log = false;
		
		boolean useValues = speciationParms.specieCompatMismatchUseValues();
		double disjointCountOrValueSum = 0, excessCountOrValueSum = 0, commonCount = 0;
		double weightDifference = 0;
		int maxSize = Math.max(this.getAlleles().size(), target.getAlleles().size());
		
		if (m_alleles.isEmpty() || target.m_alleles.isEmpty()) {
			if (log) System.err.println("empty");
			
			ChromosomeMaterial m = m_alleles.isEmpty() ? target : this;
			for (Allele a : m.getAlleles()) {
				excessCountOrValueSum += getMismatchValue(a, useValues);
			}
		}
		else {
			Iterator<Allele> thisIter = m_alleles.iterator(), targetIter = target.m_alleles.iterator();
			Allele thisCurrent = thisIter.next(), targetCurrent = targetIter.next();
			long thisMaxInnoID = this.getMaxInnovationID(), targetMaxInnoID = target.getMaxInnovationID();
			// Iterate through this and target alleles counting up common and disjoint genes as we go.
			do {
				if (thisCurrent.getInnovationId() == targetCurrent.getInnovationId()) {
					commonCount++;
					weightDifference += thisCurrent.distance(targetCurrent);
					thisCurrent = thisIter.hasNext() ? thisIter.next() : null;
					targetCurrent = targetIter.hasNext() ? targetIter.next() : null;
				}
				else {
					Allele a = thisCurrent.getInnovationId() < targetCurrent.getInnovationId() ? thisCurrent : targetCurrent;
					disjointCountOrValueSum += getMismatchValue(a, useValues);
					
					if (thisCurrent.getInnovationId() < targetCurrent.getInnovationId()) {
						thisCurrent = thisIter.hasNext() ? thisIter.next() : null;
					}
					else {
						targetCurrent = targetIter.hasNext() ? targetIter.next() : null;
					}
				}
			} while (thisCurrent != null && targetCurrent != null);
			
			// If the last gene pulled from this set of genes is out of the range of innovation IDs of the target, add it to excess.
			if (thisCurrent != null && thisCurrent.getInnovationId() > targetMaxInnoID) {
				excessCountOrValueSum += getMismatchValue(thisCurrent, useValues);
			}
			// If the last gene pulled from the target set of genes is out of the range of innovation IDs of this set of genes, add it to excess.
			if (targetCurrent != null && targetCurrent.getInnovationId() > thisMaxInnoID) {	
				excessCountOrValueSum += getMismatchValue(targetCurrent, useValues);
			}
		
			// Iterate over and count up any remaining excess genes.
			while (thisIter.hasNext()) {
				excessCountOrValueSum += getMismatchValue(thisIter.next(), useValues);
			}
			while (targetIter.hasNext()) {
				excessCountOrValueSum += getMismatchValue(targetIter.next(), useValues);
			}
		}
		
		if (speciationParms.specieCompatNormalise()) {
			excessCountOrValueSum /= maxSize;
			disjointCountOrValueSum /= maxSize;
			if (commonCount > 0) weightDifference /= commonCount;
		}
		
		double result2 = (speciationParms.getSpecieCompatExcessCoeff() * excessCountOrValueSum) + (speciationParms.getSpecieCompatDisjointCoeff() * disjointCountOrValueSum) + (speciationParms.getSpecieCompatCommonCoeff() * weightDifference);
		
		if (log) {
			System.err.println("\nexcessCount " + excessCountOrValueSum);
			System.err.println("disjointCount " + disjointCountOrValueSum);
			System.err.println("commonCount " + commonCount);
			System.err.println("weightDifference " + weightDifference);
			System.err.println("result2 " + result2);
			System.err.println(this.toString());
			System.err.println(target.toString());
		}
		
		//if (result != result2) {
		//	System.err.println("result " + result  + " != " + result2 + "     " + (result - result2));
		//}
		return result2;
	}
	
	// Distance value of a mismatched allele as determined by whether we're using a constant value (1), 
	// or the stored value of the allele (eg the connection weight or neuron bias).
	private double getMismatchValue(Allele a, boolean useValues) {
		return useValues ? a.getValue() : 1;
	}

	/**
	 * for hibernate
	 * 
	 * @param aAlleles
	 */
	public void setAlleles(SortedSet<Allele> aAlleles) {
		m_alleles = aAlleles;
	}
	

	/**
	 * Indicates whether this individual is a candidate for mutations (after reproduction). Default value is true.
	 * @return the mutateProbability
	 */
	public boolean shouldMutate() {
		return shouldMutate;
	}

	/**
	 * May be used by reproduction operators to indicate that this individual is a candidate for mutations.
	 */
	public void setShouldMutate(boolean mutate) {
		this.shouldMutate = mutate;
	}
}
