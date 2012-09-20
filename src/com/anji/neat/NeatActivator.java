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
 * created by Philip Tucker on May 17, 2003
 */
package com.anji.neat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.jgapcusomised.Chromosome;
import org.jgapcusomised.Configuration;

import com.anji.Copyright;
import com.anji.integration.Activator;
import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.TargetFitnessFunction;
import com.anji.persistence.Persistence;
import com.anji.util.Configurable;
import com.anji.util.DummyConfiguration;
import com.anji.util.Properties;
import com.anji.util.Randomizer;

/**
 * Transcribe <code>Chromosome</code> object (loaded from persistence if necessary) into
 * <code>Activator</code> object and activate it with specified stimuli.
 * 
 * @author Philip Tucker
 */
public class NeatActivator implements Configurable {

private static Logger logger = Logger.getLogger( NeatActivator.class );

private Persistence db;

private List idxs = new ArrayList();

//	dimension # activation sets by dim stimuli
private float[][] stimuli;

//	dimension # activation sets by dim response
private float[][] targets;

private ActivatorTranscriber activatorFactory;

private Randomizer randomizer;

/**
 * See <a href=" {@docRoot}/params.htm" target="anji_params">Parameter Details </a> for
 * specific property settings.
 * @param props configuration parameters.
 */
public void init( Properties props ) {
	try {
		randomizer = (Randomizer) props.singletonObjectProperty( Randomizer.class );
		db = (Persistence) props.singletonObjectProperty( Persistence.PERSISTENCE_CLASS_KEY );
		activatorFactory = (ActivatorTranscriber) props
				.singletonObjectProperty( ActivatorTranscriber.class );

		stimuli = Properties.loadArrayFromFile( props
				.getResourceProperty( TargetFitnessFunction.STIMULI_FILE_NAME_KEY ) );
		targets = Properties.loadArrayFromFile( props
				.getResourceProperty( TargetFitnessFunction.TARGETS_FILE_NAME_KEY ) );

		if ( stimuli.length == 0 || targets.length == 0 )
			throw new IllegalArgumentException( "require at least 1 training set for stimuli ["
					+ stimuli.length + "] and targets [" + targets.length + "]" );
		if ( stimuli.length != targets.length )
			throw new IllegalArgumentException( "# training sets does not match for stimuli ["
					+ stimuli.length + "] and targets [" + targets.length + "]" );

		for ( int i = 0; i < stimuli.length; ++i )
			idxs.add( new Integer( i ) );
		Collections.sort( idxs );
		reset();
	}
	catch ( Exception e ) {
		throw new IllegalArgumentException( "invalid properties: " + e.getClass().toString() + ": "
				+ e.getMessage() );
	}
}

/**
 * reshuffle stimuli
 */
public void reset() {
	Collections.shuffle( idxs, randomizer.getRand() );
}

/**
 * Load chromosome from persistence and activate it.
 * 
 * @param chromId persistence ID of chromosome
 * @return SortedMap contains key Integer index, value float[] response
 * @throws Exception
 * @see NeatActivator#activate(Activator)
 */
public SortedMap activate( String chromId ) throws Exception {
	Configuration config = new DummyConfiguration();
	Chromosome chrom = db.loadChromosome( chromId, config );

	Activator activator = activatorFactory.newActivator( chrom );
	db.store( activator );
	return activate( activator );
}

/**
 * Activate <code>activator</code> with stimuli, and return results
 * 
 * @param activator
 * @return SortedMap contains key Integer index, value float[] response
 * @throws Exception
 */
public SortedMap activate( Activator activator ) throws Exception {
	SortedMap result = new TreeMap();

	float[][] response = activator.nextSequence( stimuli );
	Iterator it = idxs.iterator();
	while ( it.hasNext() ) {
		Integer idx = (Integer) it.next();
		result.put( idx, response[ idx.intValue() ] );
	}

	return result;
}

/**
 * Load chromosome from persistencem transcribe it into activator, and activate it.
 * 
 * @param chromId persistence ID of chromosome
 * @return String representation of activation results
 * @throws Exception
 */
public String displayActivation( String chromId ) throws Exception {
	StringBuffer result = new StringBuffer();

	Map responses = activate( chromId );
	Iterator it = responses.keySet().iterator();
	while ( it.hasNext() ) {
		Integer idx = (Integer) it.next();
		int i = idx.intValue();
		float[] response = (float[]) responses.get( idx );

		result.append( i ).append( ": IN (" ).append( stimuli[ i ][ 0 ] );
		for ( int j = 1; j < stimuli[ i ].length; ++j )
			result.append( ", " ).append( stimuli[ i ][ j ] );
		result.append( ")   OUT (" ).append( response[ 0 ] );
		for ( int j = 1; j < response.length; ++j )
			result.append( ", " ).append( response[ j ] );
		result.append( ")   TARGET (" ).append( targets[ i ][ 0 ] );
		for ( int j = 1; j < targets[ i ].length; ++j )
			result.append( ", " ).append( targets[ i ][ j ] );
		result.append( ")\n" );
	}

	return result.toString();
}

/**
 * command line usage
 */
private static void printUsage() {
	System.err.println( "activator <properties-file> <chromosome-id>" );
}

/**
 * Loads chromosome from persistence, transcribes it into activator, and displays activation.
 * 
 * @param args args[0] is properties file name, args[1] is chromosome ID
 * @throws Exception
 */
public static void main( String[] args ) throws Exception {
	System.out.println( Copyright.STRING );
	if ( args.length < 2 )
		printUsage();
	else {
		NeatActivator na = new NeatActivator();
		Properties props = new Properties( args[ 0 ] );
		na.init( props );
		logger.info( "\n" + na.displayActivation( args[ 1 ] ) );
	}
}

}
