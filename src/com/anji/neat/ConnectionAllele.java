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
 * created by Philip Tucker on Feb 16, 2003
 */
package com.anji.neat;

import java.util.Random;

import org.jgapcusomised.Allele;

/**
 * Gene corresponding to NEAT connection gene according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks
 * through Augmenting Topologies </a>
 * 
 * @author Philip Tucker
 */
public class ConnectionAllele extends Allele {
		private ConnectionGene connectionGene;
	
/**
 * default connection weight
 */
public final static double DEFAULT_WEIGHT = 0;

private final static double MIN_INIT_WEIGHT = -0.5f;

private final static double MAX_INIT_WEIGHT = 0.5f;

private double weight = DEFAULT_WEIGHT;


/**
 * @see Object#toString()
 */
public String toString() {
	return connectionGene.toString() + " [" + weight + "]";
}

/**
 * for hibernate
 */
private ConnectionAllele() {
	super();
}

/**
 * @param aConnectionGene
 */
public ConnectionAllele( ConnectionGene aConnectionGene ) {
	super( aConnectionGene );
	connectionGene = aConnectionGene;
}

/**
 * @see org.jgapcusomised.Allele#cloneAllele()
 */
public Allele cloneAllele() {
	ConnectionAllele allele = new ConnectionAllele( connectionGene );
	allele.setWeight( weight );
	return allele;
}

/**
 * @param target should be <code>ConnectionAllele</code> with same gene
 * @return <code>double</code> compatibility distance based on weight; always positive
 * @see Allele#distance(Allele)
 */
public double distance( Allele target ) {
	// TODO - removed to help performance
	//		if ( target.getInnovationId().equals( getInnovationId() ) == false )
	//			throw new Exception( "should not compute distance for alleles of different gene" );
	return Math.abs( weight - ( (ConnectionAllele) target ).getWeight() );
}

/**
 * set weight to random value distributed uniformly between <code>MIN_INIT_WEIGHT</code> and
 * <code>MAX_INIT_WEIGHT</code>
 * 
 * @param a_numberGenerator
 */
public void setToRandomValue( Random a_numberGenerator ) {
	weight = MIN_INIT_WEIGHT + ( a_numberGenerator.nextFloat() * ( MAX_INIT_WEIGHT - MIN_INIT_WEIGHT ) );
}

/**
 * @return connection weight
 */
public double getWeight() {
	return weight;
}

/**
 * @param aWeight new connection weight
 */
public void setWeight( double aWeight ) {
	weight = aWeight;
}

/**
 * @return src neuron ID
 * @see ConnectionGene#getSrcNeuronId()
 */
public Long getSrcNeuronId() {
	return connectionGene.getSrcNeuronId();
}


/**
 * @return dest neuron ID
 * @see ConnectionGene#getDestNeuronId()
 */
public Long getDestNeuronId() {
	return connectionGene.getDestNeuronId();
}
}
