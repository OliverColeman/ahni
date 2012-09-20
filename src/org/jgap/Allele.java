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
package org.jgap;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

/**
 * Allele contains gene data that can be different for multiple chromosomes with the same gene.
 * @author Philip Tucker
 */
public abstract class Allele implements Comparable {

private Long id;

private Gene gene;

private Chromosome chromosome = null;

/**
 * for hibernate
 */
protected Allele() {
	super();
}

/**
 * ctor
 * @param aGene
 */
protected Allele( Gene aGene ) {
	gene = aGene;
}

/**
 * @param target should be same implementation class and same innovation ID as this gene
 * @return positive distance between genes, where a value closer to 0 represents more similar
 * genes; used in computing distance between chromosomes, which in turn is used to compute
 * speciation compatibility.
 * @see Chromosome#distance(Chromosome, SpeciationParms)
 */
public abstract float distance( Allele target );

/**
 * Sets the value of this Gene to a random legal value for the implementation. This method
 * exists for the benefit of mutation and other operations that simply desire to randomize the
 * value of a gene.
 * 
 * @param a_numberGenerator The random number generator that should be used to create any random
 * values. It's important to use this generator to maintain the user's flexibility to configure
 * the genetic engine to use the random number generator of their choice.
 */
public abstract void setToRandomValue( Random a_numberGenerator );

/**
 * @return Gene clone of this object
 */
public abstract Allele cloneAllele();

/**
 * @return gene
 */
protected Gene getGene() {
	return gene;
}

/**
 * for hibernate
 * @param aGene
 */
private void setGene( Gene aGene ) {
	gene = aGene;
}

/**
 * for hibernate
 * @return persistence id
 */
private Long getId() {
	return id;
}

/**
 * for hibernate
 * @param aId
 */
private void setId( Long aId ) {
	id = aId;
}

/**
 * @see java.lang.Comparable#compareTo(java.lang.Object)
 */
public int compareTo( Object o ) {
	Allele other = (Allele) o;
	return gene.compareTo( other.gene );
}

/**
 * @see java.lang.Object#equals(java.lang.Object)
 */
public boolean equals( Object o ) {
	return ( compareTo( o ) == 0 );
}

/**
 * @see java.lang.Object#hashCode()
 */
public int hashCode() {
	return gene.hashCode();
}

/**
 * @see Object#toString()
 */
public String toString() {
	StringBuffer result = new StringBuffer();
	result.append( gene.toString() );
	if ( id != null )
		result.append( "[" ).append( id ).append( "]" );
	return result.toString();
}

/**
 * @param alleles <code>Collection</code> contains <code>Allele</code> objects
 * @return <code>Set</code> contains <code>Gene</code> objects
 */
public static Set getGenes( Collection alleles ) {
	HashSet result = new HashSet();
	Iterator it = alleles.iterator();
	while ( it.hasNext() ) {
		Allele allele = (Allele) it.next();
		result.add( allele.getGene() );
	}
	return result;
}

/**
 * @return innovation ID
 * @see Gene#getInnovationId()
 */
public Long getInnovationId() {
	return gene.getInnovationId();
}

/**
 * for hibernate
 * @return chromosome containnig this allele - can be null
 */
private Chromosome getChromosome() {
	return chromosome;
}

/**
 * for hibernate
 * @param aChromosome
 */
void setChromosome( Chromosome aChromosome ) {
	chromosome = aChromosome;
}

}
