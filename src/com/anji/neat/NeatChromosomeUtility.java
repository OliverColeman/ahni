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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.jgap.Allele;
import org.jgap.ChromosomeMaterial;

/**
 * Utility class capturing functionality pertaining to NEAT neuron and connection genes.
 * 
 * @author Philip Tucker
 */
public class NeatChromosomeUtility {

private static Logger logger = Logger.getLogger( NeatChromosomeUtility.class );

/**
 * factory method to construct chromosome material for neural net with specified input and
 * output dimension, JGAP/NEAT configuration, and amount of connectivity
 * 
 * @param newNumInputs
 * @param newNumHidden
 * @param newNumOutputs
 * @param config
 * @param fullyConnected all layers fully connected if true, not connected at all otherwise
 * @return ChromosomeMaterial
 */
public static ChromosomeMaterial newSampleChromosomeMaterial( short newNumInputs,
		short newNumHidden, short newNumOutputs, NeatConfiguration config, boolean fullyConnected ) {
	return new ChromosomeMaterial( initAlleles( newNumInputs, newNumHidden, newNumOutputs,
			config, fullyConnected ) );
}

/**
 * @param connAlleles <code>Collection</code> contains <code>ConnectionA</code> llele
 * objects
 * @param destNeuronInnovationIds <code>Collection</code> contains Long objects
 * @return <code>Collection</code> containing <code>ConnectionAllele</code> objects, those
 * in <code>connAlleles</code> whose destination neuron is in
 * <code>destNeuronInnovationIds</code>
 */
public static Collection extractConnectionAllelesForDestNeurons( Collection connAlleles,
		Collection destNeuronInnovationIds ) {
	Collection result = new ArrayList();

	// for every connection ...
	Iterator connIter = connAlleles.iterator();
	while ( connIter.hasNext() ) {
		ConnectionAllele cAllele = (ConnectionAllele) connIter.next();
		if ( destNeuronInnovationIds.contains( cAllele.getDestNeuronId() ) )
			result.add( cAllele );
	}
	return result;
}

/**
 * @param connAlleles <code>Collection</code> contains ConnectionGene objects
 * @param srcNeuronInnovationIds <code>Collection</code> contains Long objects
 * @return <code>Collection</code> containing ConnectionGene objects, those in connGenes whose
 * source neuron is srcNeuronGene
 */
public static Collection extractConnectionAllelesForSrcNeurons( Collection connAlleles,
		Collection srcNeuronInnovationIds ) {
	Collection result = new ArrayList();

	// for every connection ...
	Iterator connIter = connAlleles.iterator();
	while ( connIter.hasNext() ) {
		ConnectionAllele connAllele = (ConnectionAllele) connIter.next();
		if ( srcNeuronInnovationIds.contains( connAllele.getSrcNeuronId() ) )
			result.add( connAllele );
	}
	return result;
}

/**
 * constructs genes for neural net with specified input and output dimension, JGAP/NEAT
 * configuration, and amount of connectivity
 * 
 * @param numInputs
 * @param numHidden
 * @param numOutputs
 * @param config
 * @param fullyConnected all layers fully connected if true, not connected at all otherwise
 * @return List contains Allele objects
 */
private static List initAlleles( short numInputs, short numHidden, short numOutputs,
		NeatConfiguration config, boolean fullyConnected ) {
	List inNeurons = new ArrayList( numInputs );
	List outNeurons = new ArrayList( numOutputs );
	List hidNeurons = new ArrayList( numHidden );
	List conns = new ArrayList();

	// input neurons
	for ( int i = 0; i < numInputs; ++i )
		inNeurons.add( config.newNeuronAllele( NeuronType.INPUT ) );

	// output neurons
	for ( int j = 0; j < numOutputs; ++j ) {
		NeuronAllele outNeuron = config.newNeuronAllele( NeuronType.OUTPUT );
		outNeurons.add( outNeuron );

		if ( fullyConnected && ( numHidden == 0 ) ) {
			// in->out connections
			for ( int i = 0; i < numInputs; ++i ) {
				NeuronAllele srcNeuronAllele = (NeuronAllele) inNeurons.get( i );
				ConnectionAllele c = config.newConnectionAllele( srcNeuronAllele.getInnovationId(),
						outNeuron.getInnovationId() );
				if ( config != null )
					c.setToRandomValue( config.getRandomGenerator() );
				conns.add( c );
			}
		}
	}

	// hidden neurons
	if ( fullyConnected ) {
		for ( int k = 0; k < numHidden; ++k ) {
			NeuronAllele hidNeuron = config.newNeuronAllele( NeuronType.HIDDEN );
			hidNeurons.add( hidNeuron );

			// in->hid connections
			for ( int i = 0; i < numInputs; ++i ) {
				NeuronAllele srcNeuronAllele = (NeuronAllele) inNeurons.get( i );
				ConnectionAllele c = config.newConnectionAllele( srcNeuronAllele.getInnovationId(),
						hidNeuron.getInnovationId() );
				if ( config != null )
					c.setToRandomValue( config.getRandomGenerator() );
				conns.add( c );
			}

			// hid->out connections
			for ( int j = 0; j < numOutputs; ++j ) {
				NeuronAllele destNeuronAllele = (NeuronAllele) outNeurons.get( j );
				ConnectionAllele c = config.newConnectionAllele( hidNeuron.getInnovationId(),
						destNeuronAllele.getInnovationId() );
				if ( config != null )
					c.setToRandomValue( config.getRandomGenerator() );
				conns.add( c );
			}
		}
	}
	else if ( numHidden > 0 ) {
		logger.warn( "ignoring intial topology hidden neurons, not fully connected" );
	}

	List result = new ArrayList();
	result.addAll( inNeurons );
	result.addAll( outNeurons );
	result.addAll( hidNeurons );
	result.addAll( conns );
	Collections.sort( result );
	return result;
}

/**
 * return all neurons in <code>alleles</code> as <code>SortedMap</code>
 * 
 * @param alleles <code>Collection</code> contains <code>Allele</code> objects
 * @return <code>SortedMap</code> containing key <code>Long</code> innovation id, value
 * <code>NeuronGene</code> objects
 * @see NeatChromosomeUtility#getNeuronMap(Collection, NeuronType)
 */
public static SortedMap getNeuronMap( Collection alleles ) {
	return getNeuronMap( alleles, null );
}

/**
 * return all neurons in <code>genes</code> as <code>List</code>
 * 
 * @param alleles <code>Collection</code> contains <code>Allele</code> objects
 * @return <code>List</code> containing <code>NeuronGene</code> objects
 * @see NeatChromosomeUtility#getNeuronList(Collection, NeuronType)
 */
public static List getNeuronList( Collection alleles ) {
	return getNeuronList( alleles, null );
}

/**
 * if type == null, returns all neurons in <code>alleles</code>; otherwise, returns only
 * neurons of <code>type</code>
 * 
 * @param alleles <code>Collection</code> contains <code>Allele</code> objects
 * @param type
 * @return SortedMap contains key Long innovation id, value NeuronGene objects
 */
public static SortedMap getNeuronMap( Collection alleles, NeuronType type ) {
	TreeMap result = new TreeMap();
	Iterator iter = alleles.iterator();
	while ( iter.hasNext() ) {
		Allele allele = (Allele) iter.next();

		if ( allele instanceof NeuronAllele ) {
			NeuronAllele neuronAllele = (NeuronAllele) allele;
			Long id = neuronAllele.getInnovationId();

			// sanity check
			if ( result.containsKey( id ) )
				throw new IllegalArgumentException( "chromosome contains duplicate neuron gene: "
						+ allele.toString() );

			if ( ( type == null ) || neuronAllele.isType( type ) )
				result.put( id, allele );
		}
	}
	return result;
}

/**
 * if type == null, returns all neuron genes in <code>genes</code>; otherwise, returns only
 * neuron genes of type
 * 
 * @param alleles <code>Collection</code> contains gene objects
 * @param type
 * @return <code>List</code> contains <code>NeuronAllele</code> objects
 */
public static List getNeuronList( Collection alleles, NeuronType type ) {
	List result = new ArrayList();
	Iterator iter = alleles.iterator();
	while ( iter.hasNext() ) {
		Allele allele = (Allele) iter.next();

		if ( allele instanceof NeuronAllele ) {
			NeuronAllele nAllele = (NeuronAllele) allele;

			// sanity check
			if ( result.contains( nAllele ) )
				throw new IllegalArgumentException( "chromosome contains duplicate neuron gene: "
						+ allele.toString() );

			if ( ( type == null ) || nAllele.isType( type ) )
				result.add( allele );
		}
	}
	return result;
}

/**
 * returns all connections in <code>alleles</code> as <code>SortedMap</code>
 * 
 * @param alleles <code>SortedSet</code> contains <code>Allele</code> objects
 * @return <code>SortedMap</code> containing key <code>Long</code> innovation id, value
 * <code>ConnectionAllele</code> objects
 */
public static SortedMap getConnectionMap( Set alleles ) {
	TreeMap result = new TreeMap();
	Iterator iter = alleles.iterator();
	while ( iter.hasNext() ) {
		Allele allele = (Allele) iter.next();

		if ( allele instanceof ConnectionAllele ) {
			ConnectionAllele connAllele = (ConnectionAllele) allele;
			Long id = connAllele.getInnovationId();

			// sanity check
			if ( result.containsKey( id ) )
				throw new IllegalArgumentException( "chromosome contains duplicate connection gene: "
						+ allele.toString() );

			result.put( id, allele );
		}
	}
	return result;
}

/**
 * returns all connection genes in <code>genes</code> as <code>List</code>
 * 
 * @param alleles <code>Collection</code> contains gene objects
 * @return <code>List</code> containing <code>ConnectionGene</code> objects
 */
public static List getConnectionList( Collection alleles ) {
	List result = new ArrayList();
	Iterator iter = alleles.iterator();
	while ( iter.hasNext() ) {
		Allele allele = (Allele) iter.next();

		if ( allele instanceof ConnectionAllele ) {
			// sanity check
			if ( result.contains( allele ) )
				throw new IllegalArgumentException( "chromosome contains duplicate connection gene: "
						+ allele.toString() );
			result.add( allele );
		}
	}
	return result;
}

/**
 * non-recursive starting point for recursive search
 * 
 * @param srcNeuronId
 * @param destNeuronId
 * @param connGenes
 * @return true if <code>srcNeuronId</code> and <code>destNeuronId</code> are connected
 * @see NeatChromosomeUtility#neuronsAreConnected(Long, Long, Collection, Set)
 */
public static boolean neuronsAreConnected( Long srcNeuronId, Long destNeuronId,
		Collection connGenes ) {
	return neuronsAreConnected( srcNeuronId, destNeuronId, connGenes, new HashSet() );
}

/**
 * Recursively searches <code>allConnGenes</code> to determines if the network contains a
 * directed path from <code>srcNeuronId</code> to <code>destNeuronId</code> are connected.
 * For efficiency, we pass in <code>alreadyTraversedConnGeneIds</code> to eliminate redundant
 * searching.
 * 
 * @param srcNeuronId
 * @param destNeuronId
 * @param allConnAlleles <code>Collection</code> contains <code>ConnectionGene</code>
 * objects
 * @param alreadyTraversedConnIds <code>Set</code> contains <code>Long</code> connection ID
 * objects
 * @return returns true if neurons are the same, or a path lies between src and dest in
 * connGenes connected graph
 */
private static boolean neuronsAreConnected( Long srcNeuronId, Long destNeuronId,
		Collection allConnAlleles, Set alreadyTraversedConnIds ) {
	// TODO - make connGenes Map key on srcNeuronId

	// Recursively searches connections to see if src and dest are connected
	if ( alreadyTraversedConnIds.contains( srcNeuronId ) )
		return false;
	alreadyTraversedConnIds.add( srcNeuronId );

	if ( srcNeuronId.equals( destNeuronId ) )
		return true;

	Iterator connIter = allConnAlleles.iterator();
	while ( connIter.hasNext() ) {
		ConnectionAllele connAllele = (ConnectionAllele) connIter.next();
		if ( ( connAllele.getSrcNeuronId().equals( connAllele.getDestNeuronId() ) == false )
				&& ( connAllele.getSrcNeuronId().equals( srcNeuronId ) ) ) {
			if ( neuronsAreConnected( connAllele.getDestNeuronId(), destNeuronId, allConnAlleles,
					alreadyTraversedConnIds ) )
				return true;
		}
	}

	return false;
}

}
