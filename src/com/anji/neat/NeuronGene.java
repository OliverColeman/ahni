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

import org.jgapcustomised.Gene;

import com.anji.nn.activationfunction.ActivationFunctionType;

/**
 * Gene corresponding to NEAT node gene according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks
 * through Augmenting Topologies </a>
 * 
 * @author Philip Tucker
 */
public class NeuronGene extends Gene {

/**
 * for hibernate
 */
private Long id;

private NeuronType type = NeuronType.HIDDEN;

private ActivationFunctionType activationType;

/**
 * @see Object#toString()
 */
public String toString() {
	return getInnovationId().toString();
}

/**
 * for hibernate
 */
private NeuronGene() {
	super();
}

/**
 * Construct new NeuronGene with given type and ID. Protected since this should only be
 * constructed via factory methods in <code>NeuronGene</code> and
 * <code>NeatChromosomeUtility</code>, and persistence objects.
 * 
 * @param newType
 * @param newInnovationId
 * @param anActivationType
 */
public NeuronGene( NeuronType newType, Long newInnovationId, ActivationFunctionType anActivationType ) {
	super( newInnovationId );

    //if (anActivationType.equals(ActivationFunctionType.RANDOM))
	//	throw new IllegalArgumentException( "activation function can not be random for NeuronGene" );

	type = newType;
	activationType = anActivationType;
}

/**
 * @return type of neuron
 * @see NeuronType
 */
NeuronType getType() {
	return type;
}

/**
 * @param aType
 * @return true iff neuron is of type <code>aType</code>
 */
boolean isType( NeuronType aType ) {
	return type.equals( aType );
}

/**
 * @return gets activation function type
 */
ActivationFunctionType getActivationType() {
	return activationType;
}

/**
 * for hibernate
 * @param anActivationType The activationType to set.
 */
private void setActivationType( ActivationFunctionType anActivationType ) {
	activationType = anActivationType;
}

/**
 * for hibernate
 * @param aType
 */
private void setType( NeuronType aType ) {
	type = aType;
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
