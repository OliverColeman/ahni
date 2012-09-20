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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jgap.ChromosomeMaterial;
import org.jgap.Configuration;
import org.jgap.MutationOperator;

import com.anji.integration.AnjiRequiredException;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Implements remove connection mutation, using one of 3 strategies:
 * 
 * SKEWED - Probability of connection being removed ranges from 0 (if weight magnitude >= max
 * weight removed) to mutation rate (if weight is 0)
 * 
 * ALL - All connections have equal likelihood of being removed, regardless of weight.
 * 
 * SMALL - Mutation occurs according to James and Tucker's "A Comparative Analysis of
 * Simplification and Complexification in the Evolution of Neural Network Topologies" paper for
 * <a href="http://gal4.ge.uiuc.edu:8080/GECCO-2004/">GECCO 2004 </a>.
 * 
 * @author Philip Tucker
 */
public class RemoveConnectionMutationOperator extends MutationOperator implements Configurable {

/**
 * Enumerated type for remove connection strategies.
 * @author Philip Tucker
 */
public static class Strategy {

private String name;

/**
 * all connections have equal likelihood of being removed
 */
public final static Strategy ALL = new Strategy( "all" );

/**
 * likelihood of connection being removed is inversely proprtional to weight
 */
public final static Strategy SKEWED = new Strategy( "skewed" );

/**
 * number of connections removed (n) is calculated based on total number of connections, and the
 * n smallest connections are removed, excepting any whose weight is larger than
 * <code>maxWeightRemoved</code>
 */
public final static Strategy SMALL = new Strategy( "small" );

private final static HashMap strategies = new HashMap();
static {
	strategies.put( ALL.toString(), ALL );
	strategies.put( SKEWED.toString(), SKEWED );
	strategies.put( SMALL.toString(), SMALL );
}

private Strategy( String aName ) {
	super();
	name = aName;
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return name;
}

/**
 * @param s
 * @return <code>Strategy</code> object identified by <code>s</code>, or null if no
 * strategy matches <code>s</code>
 */
public static Strategy valueOf( String s ) {
	return (Strategy) strategies.get( s );
}
}

private static final String STRATEGY_KEY = "remove.connection.strategy";

/**
 * properties key, max weight that can be removed by remove connection mutation
 */
public static final String REMOVE_CONN_MAX_WEIGHT_KEY = "remove.connection.max.weight";

/**
 * properties key, remove connection mutation rate
 */
private static final String REMOVE_CONN_MUTATE_RATE_KEY = "remove.connection.mutation.rate";

/**
 * default maximum weight a connection can have and still be removed
 */
public final static float DEFAULT_MAX_WEIGHT_REMOVED = 0.10f;

/**
 * default mutation rate
 */
public final static float DEFAULT_MUTATE_RATE = 0.01f;

private float maxWeightRemoved = DEFAULT_MAX_WEIGHT_REMOVED;

private Strategy strategy = Strategy.SKEWED;

/**
 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
 */
public void init( Properties props ) throws Exception {
	setMutationRate( props.getFloatProperty( REMOVE_CONN_MUTATE_RATE_KEY,
			RemoveConnectionMutationOperator.DEFAULT_MUTATE_RATE ) );
	maxWeightRemoved = props.getFloatProperty( REMOVE_CONN_MAX_WEIGHT_KEY,
			DEFAULT_MAX_WEIGHT_REMOVED );
	strategy = Strategy.valueOf( props.getProperty( STRATEGY_KEY, Strategy.SKEWED.toString() ) );
}

/**
 * @see RemoveConnectionMutationOperator#RemoveConnectionMutationOperator(float)
 */
public RemoveConnectionMutationOperator() {
	this( DEFAULT_MUTATE_RATE );
}

/**
 * @see MutationOperator#MutationOperator(float)
 */
public RemoveConnectionMutationOperator( float aMutationRate ) {
	super( aMutationRate );
}

/**
 * @param aMutationRate
 * @param aMaxWeightRemoved weights larger than this can not be removed
 * @see MutationOperator#MutationOperator(float)
 */
public RemoveConnectionMutationOperator( float aMutationRate, float aMaxWeightRemoved ) {
	super( aMutationRate );
	maxWeightRemoved = aMaxWeightRemoved;
}

/**
 * @param aMutationRate
 * @param aMaxWeightRemoved weights larger than this can not be removed
 * @param aStrategy
 * @see MutationOperator#MutationOperator(float)
 */
public RemoveConnectionMutationOperator( float aMutationRate, float aMaxWeightRemoved,
		Strategy aStrategy ) {
	super( aMutationRate );
	maxWeightRemoved = aMaxWeightRemoved;
	strategy = aStrategy;
}

/**
 * Removes, in ascending order of weight magnitude, those connections whose weight magnitude is
 * less than the maximum weight to be removed. Maximum number of connections that can be removed
 * is determined by mutation rate.
 * 
 * @param jgapConfig must be <code>NeatConfiguration</code>
 * @param target chromosome material to mutate
 * @param allelesToAdd <code>Set</code> contains <code>Allele</code> objects
 * @param allelesToRemove <code>Set</code> contains <code>Allele</code> objects
 * @see org.jgap.MutationOperator#mutate(org.jgap.Configuration, org.jgap.ChromosomeMaterial,
 * java.util.Set, java.util.Set)
 */
protected void mutate( Configuration jgapConfig, final ChromosomeMaterial target,
		Set allelesToAdd, Set allelesToRemove ) {
	if ( ( jgapConfig instanceof NeatConfiguration ) == false )
		throw new AnjiRequiredException( "com.anji.neat.NeatConfiguration" );
	NeatConfiguration config = (NeatConfiguration) jgapConfig;
	List allConns = NeatChromosomeUtility.getConnectionList( target.getAlleles() );

	if ( Strategy.SMALL.equals( strategy ) )
		mutateSmall( config, allConns, allelesToRemove );
	else if ( Strategy.ALL.equals( strategy ) )
		mutateAll( config, allConns, allelesToRemove );
	else if ( Strategy.SKEWED.equals( strategy ) )
		mutateSkewed( config, allConns, allelesToRemove );
	else
		throw new IllegalStateException( "invalid remove connection operator strategy: " + strategy );
}

private void mutateSkewed( NeatConfiguration config, List allConns, Set allelesToRemove ) {
	Iterator it = allConns.iterator();
	while ( it.hasNext() ) {
		ConnectionAllele connAllele = (ConnectionAllele) it.next();
		float absWeight = (float) Math.abs( connAllele.getWeight() );

		// probability of connection being removed ranges from 0 (if weight magnitude >= max
		// weight removed) to mutation rate (if weight is 0)
		float smallWeightFactor = 0;
		if ( absWeight < maxWeightRemoved )
			smallWeightFactor = ( maxWeightRemoved - absWeight ) / maxWeightRemoved;
		smallWeightFactor *= smallWeightFactor;
		if ( doesMutationOccur( config.getRandomGenerator(), smallWeightFactor * getMutationRate() ) )
			allelesToRemove.add( connAllele );
	}
}

private void mutateAll( NeatConfiguration config, List allConns, Set allelesToRemove ) {
	Iterator it = allConns.iterator();
	while ( it.hasNext() ) {
		ConnectionAllele connAllele = (ConnectionAllele) it.next();
		if ( ( Math.abs( connAllele.getWeight() ) <= maxWeightRemoved )
				&& doesMutationOccur( config.getRandomGenerator() ) )
			allelesToRemove.add( connAllele );
	}
}

private void mutateSmall( NeatConfiguration config, List allConns, Set allelesToRemove ) {
	// get maximum connections removed
	int maxConnsRemoved = numMutations( config.getRandomGenerator(), allConns.size() );

	// skip connections whose weight is too large to remove
	List candidateConns = new ArrayList( allConns );
	Iterator iter = candidateConns.iterator();
	while ( iter.hasNext() ) {
		ConnectionAllele conn = (ConnectionAllele) iter.next();
		if ( Math.abs( conn.getWeight() ) > maxWeightRemoved )
			iter.remove();
	}

	// remove candidate connections, up to maxConnsRemoved
	Collections.sort( candidateConns, WeightMagnitudeComparator.getInstance() );
	iter = candidateConns.iterator();
	int i = 0;
	while ( iter.hasNext() && ( i++ < maxConnsRemoved ) ) {
		ConnectionAllele connAllele = (ConnectionAllele) iter.next();
		allelesToRemove.add( connAllele );
	}
}

/**
 * @return float threshold above which no weight is removed
 */
public float getMaxWeightRemoved() {
	return maxWeightRemoved;
}

}
