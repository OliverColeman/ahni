/*
 * Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of ANJI (Another NEAT Java Implementation).
 * 
 * ANJI is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Created on Apr 4, 2004 by Philip Tucker
 */
package com.anji.integration;

import java.util.Iterator;

import org.jgapcusomised.Chromosome;
import org.jgapcusomised.Genotype;
import org.jgapcusomised.Species;

import com.anji.util.XmlPersistable;

/**
 * Converts generation data between <code>Genotype</code> and XML.
 * 
 * @author Philip Tucker
 */
public class Generation implements XmlPersistable {

/**
 * XML base tag
 */
public final static String GENERATION_TAG = "generation";

private Genotype genotype;

private Long id;

private String cachedXml;

/**
 * @see java.lang.Object#hashCode()
 */
public int hashCode() {
	return id.hashCode();
}

/**
 * @see java.lang.Object#equals(java.lang.Object)
 */
public boolean equals( Object o ) {
	Generation other = (Generation) o;
	return id.equals( other.id );
}

/**
 * for hibernate
 */
private Generation() {
	super();
}

/**
 * @param aGenotype chromosomes from this object make up generation.
 * @param anId of generation
 */
public Generation( Genotype aGenotype, long anId ) {
	genotype = aGenotype;
	id = new Long( anId );
	cacheXml();
}

/**
 * @see com.anji.util.XmlPersistable#toXml()
 */
public String toXml() {
	return cachedXml;
}

private void cacheXml() {
	int maxFitness = Integer.MIN_VALUE;
	int minFitness = Integer.MAX_VALUE;
	int maxComplexity = Integer.MIN_VALUE;
	int minComplexity = Integer.MAX_VALUE;

	StringBuffer result = new StringBuffer();
	result.append( "<" ).append( GENERATION_TAG ).append( " id=\"" ).append( id ).append(
			"\" >\n" );

	Iterator iter = genotype.getChromosomes().iterator();
	long runningFitnessTotal = 0;
	int popSize = 0;
	long runningComplexityTotal = 0;

	while ( iter.hasNext() ) {
		Chromosome chrom = (Chromosome) iter.next();
		int thisChromFitness = chrom.getFitnessValue();
		runningFitnessTotal += thisChromFitness;
		int thisChromComplexity = chrom.size();
		runningComplexityTotal += thisChromComplexity;
		popSize++;
		if ( thisChromFitness > maxFitness )
			maxFitness = thisChromFitness;
		if ( thisChromFitness < minFitness )
			minFitness = thisChromFitness;
		if ( thisChromComplexity > maxComplexity )
			maxComplexity = thisChromComplexity;
		if ( thisChromComplexity < minComplexity )
			minComplexity = thisChromComplexity;
	}
	result.append( "<fitness>\n" );
	result.append( "<max>" ).append( maxFitness );
	result.append( "</max>\n" );
	result.append( "<min>" ).append( minFitness );
	result.append( "</min>\n" );
	result.append( "<avg>" );
	result.append( runningFitnessTotal / popSize );
	result.append( "</avg>\n" );
	result.append( "</fitness>\n" );

	result.append( "<complexity>\n" );
	result.append( "<champ>" ).append( genotype.getFittestChromosome().size() );
	result.append( "</champ>\n" );
	result.append( "<max>" ).append( maxComplexity );
	result.append( "</max>\n" );
	result.append( "<min>" ).append( minComplexity );
	result.append( "</min>\n" );
	result.append( "<avg>" );
	result.append( (float) runningComplexityTotal / popSize );
	result.append( "</avg>\n" );
	result.append( "</complexity>\n" );

	Iterator speciesIter = genotype.getSpecies().iterator();
	while ( speciesIter.hasNext() ) {
		Species specie = (Species) speciesIter.next();
		result.append( specie.toXml() );
	}
	result.append( "</" ).append( GENERATION_TAG ).append( ">\n" );

	cachedXml = result.toString();
}

/**
 * @see com.anji.util.XmlPersistable#getXmlRootTag()
 */
public String getXmlRootTag() {
	return GENERATION_TAG;
}

/**
 * @see com.anji.util.XmlPersistable#getXmld()
 */
public String getXmld() {
	return null;
}

/**
 * for hibernate
 * @return unique id
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
}
