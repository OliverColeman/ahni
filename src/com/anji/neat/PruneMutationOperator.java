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
 * Created on Apr 6, 2004 by Philip Tucker
 */
package com.anji.neat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgap.ChromosomeMaterial;
import org.jgap.Configuration;
import org.jgap.MutationOperator;

import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Removes neurons and connections that do not affect the activation of the network. This
 * includes hidden neurons without inputs or outputs, connections missing source or destination
 * neurons, or sub-structures of neurons and connections that are stranded. Allows additive and
 * subtractive mutation operators to be less careful about what they do and require less
 * coordination among them, since this operator can follow them and "clean up the mess". For
 * this reason, this operator generally should be the last executed in the sequence of mutation
 * operators. This operator was necessary with the addition of simplification dynamics for James
 * and Tucker's "A Comparative Analysis of Simplification and Complexification in the Evolution
 * of Neural Network Topologies" paper for <a
 * href="http://gal4.ge.uiuc.edu:8080/GECCO-2004/">GECCO 2004 </a>.
 * 
 * TODO - mutation rate less than 1.0 might yield unexpected results - maybe should handle nodes
 * and connections differently in that case
 * 
 * @author Philip Tucker
 */
public class PruneMutationOperator extends MutationOperator implements Configurable {

/**
 * properties key, prune network mutation rate
 */
private static final String PRUNE_MUTATE_RATE_KEY = "prune.mutation.rate";

/**
 * default mutation rate
 */
public final static float DEFAULT_MUTATE_RATE = 1.00f;

/**
 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
 */
public void init( Properties props ) throws Exception {
	setMutationRate( props.getFloatProperty( PRUNE_MUTATE_RATE_KEY, DEFAULT_MUTATE_RATE ) );
}

/**
 * @see PruneMutationOperator#PruneMutationOperator(float)
 */
public PruneMutationOperator() {
	this( DEFAULT_MUTATE_RATE );
}

/**
 * @see MutationOperator#MutationOperator(float)
 */
public PruneMutationOperator( float newMutationRate ) {
	super( newMutationRate );
}

/**
 * Traverse network flowing forward and backward to identify unvisited connections and neurons.
 * Then, remove a number of those depending on mutation rate.
 * 
 * @param config
 * @param target chromosome material to mutate
 * @param genesToAdd <code>Set</code> contains <code>Gene</code> objects
 * @param genesToRemove <code>Set</code> contains <code>Gene</code> objects
 * @see org.jgap.MutationOperator#mutate(org.jgap.Configuration, org.jgap.ChromosomeMaterial,
 * java.util.Set, java.util.Set)
 */
protected void mutate( Configuration config, ChromosomeMaterial target, Set genesToAdd,
		Set genesToRemove ) {
	List candidatesToRemove = new ArrayList();
	findUnvisitedAlleles( target, candidatesToRemove, true );
	findUnvisitedAlleles( target, candidatesToRemove, false );
	Collections.shuffle( candidatesToRemove, config.getRandomGenerator() );
	for ( int i = 0; i < numMutations( config.getRandomGenerator(), candidatesToRemove.size() ); ++i )
		genesToRemove.add( candidatesToRemove.get( i ) );
}

/**
 * @param material target from which to remove stranded nodes and connections
 * @param unvisitedAlleles <code>List</code> contains <code>Gene</code> objects, unvisited
 * nodes and connections
 * @param isForward traverse the network from input to output if true, output to input if false
 */
private void findUnvisitedAlleles( ChromosomeMaterial material, List unvisitedAlleles,
		boolean isForward ) {
	// initialize unvisited connections
	List unvisitedConnAlleles = NeatChromosomeUtility.getConnectionList( material.getAlleles() );

	// initialize unvisited neurons (input and output neurons are always part of the activation,
	// and therefore considered "visited")
	Map hiddenNeuronAlleles = NeatChromosomeUtility.getNeuronMap( material.getAlleles(),
			NeuronType.HIDDEN );
	Set unvisitedNeuronInnovationIds = new HashSet( hiddenNeuronAlleles.keySet() );

	// currentNeuronInnovationIds and nextNeuronInnovationIds keep track of where we are as we
	// traverse the network
	Map initialNeuronAlleles = NeatChromosomeUtility.getNeuronMap( material.getAlleles(),
			( isForward ? NeuronType.INPUT : NeuronType.OUTPUT ) );
	Set currentNeuronInnovationIds = new HashSet( initialNeuronAlleles.keySet() );
	Set nextNeuronInnovationIds = new HashSet();
	while ( !unvisitedConnAlleles.isEmpty() && !currentNeuronInnovationIds.isEmpty() ) {
		nextNeuronInnovationIds.clear();
		Collection connAlleles = isForward ? NeatChromosomeUtility
				.extractConnectionAllelesForSrcNeurons( unvisitedConnAlleles,
						currentNeuronInnovationIds ) : NeatChromosomeUtility
				.extractConnectionAllelesForDestNeurons( unvisitedConnAlleles,
						currentNeuronInnovationIds );
		Iterator it = connAlleles.iterator();
		while ( it.hasNext() ) {
			ConnectionAllele connAllele = (ConnectionAllele) it.next();
			nextNeuronInnovationIds.add( isForward ? connAllele.getDestNeuronId() : connAllele
					.getSrcNeuronId() );
		}
		unvisitedNeuronInnovationIds.removeAll( nextNeuronInnovationIds );
		currentNeuronInnovationIds.clear();
		currentNeuronInnovationIds.addAll( nextNeuronInnovationIds );
		unvisitedConnAlleles.removeAll( connAlleles );
	}

	// return all unvisited neurons and connections
	unvisitedAlleles.addAll( unvisitedConnAlleles );
	Iterator it = unvisitedNeuronInnovationIds.iterator();
	while ( it.hasNext() ) {
		Long id = (Long) it.next();
		NeuronAllele neuronAllele = (NeuronAllele) hiddenNeuronAlleles.get( id );
		unvisitedAlleles.add( neuronAllele );
	}
}

}
