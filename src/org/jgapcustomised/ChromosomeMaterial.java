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
		representation.append("[ ");

		// Append the representations of each of the gene Alleles.
		// -------------------------------------------------------
		Iterator iter = m_alleles.iterator();
		if (iter.hasNext()) {
			Allele allele = (Allele) iter.next();
			representation.append(allele.toString());
		}
		while (iter.hasNext()) {
			Allele allele = (Allele) iter.next();
			representation.append(", ");
			representation.append(allele.toString());
		}
		representation.append(" ]");

		return representation.toString();
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
		/*
		 * Old method, not very efficient, new method below.
		 * 
		// get genes I have target does not
		List<Allele> myUnmatchedAlleles = new ArrayList<Allele>(m_alleles);
		myUnmatchedAlleles.removeAll(target.getAlleles());

		// get genes target has I do not
		List<Allele> targetUnmatchedAlleles = new ArrayList<Allele>(target.getAlleles());
		targetUnmatchedAlleles.removeAll(m_alleles);

		// extract excess genes
		long targetMax = getMaxInnovationId(target.getAlleles());
		long thisMax = getMaxInnovationId(m_alleles);
		List excessAlleles = (targetMax > thisMax) ? extractExcessAlleles(targetUnmatchedAlleles, thisMax) : extractExcessAlleles(myUnmatchedAlleles, targetMax);

		// all other extras are disjoint
		List<Allele> disjointAlleles = new ArrayList<Allele>(myUnmatchedAlleles);
		disjointAlleles.addAll(targetUnmatchedAlleles);

		// get common connection genes
		List<Allele> myCommonAlleles = new ArrayList<Allele>(this.getAlleles());
		myCommonAlleles.retainAll(target.getAlleles());
		List<Allele> targetCommonAlleles = new ArrayList<Allele>(target.getAlleles());
		targetCommonAlleles.retainAll(this.getAlleles());

		// sanity test
		if (myCommonAlleles.size() != targetCommonAlleles.size()) {
			throw new IllegalStateException("sizes of my common genes and target common genes differ");
		}

		// calculate distance for common genes
		double avgCommonDiff = 0;
		int numComparableCommonAlleles = 0;
		if (myCommonAlleles.size() > 0) {
			double totalCommonDiff = 0;
			Iterator<Allele> myIter = myCommonAlleles.iterator();
			Iterator<Allele> targetIter = targetCommonAlleles.iterator();
			while (myIter.hasNext() && targetIter.hasNext() && totalCommonDiff < Double.MAX_VALUE) {
				Allele myAllele = myIter.next();
				Allele targetAllele = targetIter.next();
				if (myAllele.getInnovationId().equals(targetAllele.getInnovationId()) == false) {
					throw new IllegalStateException("corresponding genes do not have same innovation ids");
				}
				try {
					double aDistance = myAllele.distance(targetAllele);
					if (totalCommonDiff + aDistance > Float.MAX_VALUE) {
						totalCommonDiff = Float.MAX_VALUE;
					} else {
						totalCommonDiff += aDistance;
					}
					++numComparableCommonAlleles;
				} catch (UnsupportedOperationException e) {
					// do nothing
				}
			}
			avgCommonDiff = totalCommonDiff / numComparableCommonAlleles;
		}

		// formula from "Evolving Neural Networks Through Augmenting Topologies",
		// Stanley/Miikkulainen
		long maxChromSize = Math.max(this.getAlleles().size(), target.getAlleles().size());
		double result = 0;
		if (maxChromSize > 0) // should never be 0
		{
			result = ((speciationParms.getSpecieCompatExcessCoeff() * excessAlleles.size()) / maxChromSize) + ((speciationParms.getSpecieCompatDisjointCoeff() * disjointAlleles.size()) / maxChromSize) + (speciationParms.getSpecieCompatCommonCoeff() * avgCommonDiff);
		}
		//return result;
		*/
		
		
		if (m_alleles.isEmpty() || target.m_alleles.isEmpty()) {
			return 1; // disjointCount / Math.max(m_alleles.size(), target.m_alleles.size()) will always equal 1 (no excess and no weight diff between common genes).
		}
		int disjointCount = 0, excessCount = 0, commonCount = 0;
		double avgWeightDifference = 0;
		Iterator<Allele> thisIter = m_alleles.iterator(), targetIter = target.m_alleles.iterator();
		Allele thisCurrent = thisIter.next(), targetCurrent = targetIter.next();
		long thisMaxInnoID = this.getMaxInnovationID(), targetMaxInnoID = target.getMaxInnovationID();
		// Iterate through this and target alleles counting up common and disjoint genes as we go.
		do {
			if (thisCurrent.getInnovationId() == targetCurrent.getInnovationId()) {
				commonCount++;
				avgWeightDifference += thisCurrent.distance(targetCurrent);
				thisCurrent = thisIter.hasNext() ? thisIter.next() : null;
				targetCurrent = targetIter.hasNext() ? targetIter.next() : null;
			}
			else {
				disjointCount++;
				if (thisCurrent.getInnovationId() < targetCurrent.getInnovationId()) {
					thisCurrent = thisIter.hasNext() ? thisIter.next() : null;
				}
				else {
					targetCurrent = targetIter.hasNext() ? targetIter.next() : null;
				}
			}
		} while (thisCurrent != null && targetCurrent != null);
		
		// If the last gene pulled from this set of genes is out of the range of innovation IDs of the target, add it to excess.
		if (thisCurrent != null && thisCurrent.getInnovationId() > targetMaxInnoID) excessCount++;
		// If the last gene pulled from the target set of genes is out of the range of innovation IDs of this set of genes, add it to excess.
		if (targetCurrent != null && targetCurrent.getInnovationId() > thisMaxInnoID) excessCount++;
		// Iterate over and count up any remaining excess genes.
		while (thisIter.hasNext()) {
			thisIter.next();
			excessCount++;
		}
		while (targetIter.hasNext()) {
			targetIter.next();
			excessCount++;
		}
		
		if (commonCount > 0) avgWeightDifference /= commonCount;
		/* testing
		if (
				excessAlleles.size() != excessCount || 
				disjointAlleles.size() != disjointCount ||
				numComparableCommonAlleles != commonCount ||
				avgCommonDiff != avgWeightDifference) {
			System.err.println("\nexcessCount " + excessAlleles.size()  + " != " + excessCount);
			System.err.println("disjointCount " + disjointAlleles.size()  + " != " + disjointCount);
			System.err.println("commonCount " + numComparableCommonAlleles  + " != " + commonCount);
			System.err.println("avgWeightDifference " + avgCommonDiff  + " != " + avgWeightDifference + "     " + (avgCommonDiff - avgWeightDifference));
			
			thisIter = m_alleles.iterator();
			while (thisIter.hasNext())
				System.err.print(thisIter.next().getInnovationId() + ", ");
			System.err.println();
			targetIter = target.m_alleles.iterator();
			while (targetIter.hasNext())
				System.err.print(targetIter.next().getInnovationId() + ", ");
			System.err.println();
			System.err.println("thisCurrent " + thisCurrent);
			System.err.println("targetCurrent " + targetCurrent);
		}
		*/
		
		int maxSize = Math.max(this.getAlleles().size(), target.getAlleles().size());
		double result2 = ((speciationParms.getSpecieCompatExcessCoeff() * excessCount) / maxSize) + ((speciationParms.getSpecieCompatDisjointCoeff() * disjointCount) / maxSize) + (speciationParms.getSpecieCompatCommonCoeff() * avgWeightDifference);
		
		//if (result != result2) {
		//	System.err.println("result " + result  + " != " + result2 + "     " + (result - result2));
		//}
		return result2;
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
