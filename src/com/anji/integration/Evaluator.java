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
 * Created on Jul 23, 2005 by Philip Tucker
 */
package com.anji.integration;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.jgap.BulkFitnessFunction;
import org.jgap.Chromosome;
import org.jgap.Configuration;

import com.anji.Copyright;
import com.anji.neat.Evolver;
import com.anji.persistence.Persistence;
import com.anji.util.DummyConfiguration;
import com.anji.util.Properties;

/**
 * @author Philip Tucker
 */
public class Evaluator {

private final static Logger logger = Logger.getLogger( Evaluator.class );

private Evaluator() {
	super();
}

/**
 * main method
 * @param args
 * @throws Exception
 */
public static void main( String[] args ) throws Exception {
	System.out.println( Copyright.STRING );
	if ( args.length < 2 ) {
		System.err
				.println( "usage: <cmd> <properties-file> <chromosome-id1> [<chromosome-id2> <chromosome-id3> ...]" );
		System.exit( -1 );
	}

	// load fitness function from properties
	Properties props = new Properties();
	props.loadFromResource( args[ 0 ] );
	BulkFitnessFunction fitnessFunc = (BulkFitnessFunction) props
			.singletonObjectProperty( Evolver.FITNESS_FUNCTION_CLASS_KEY );

	// load chromosomes
	Persistence db = (Persistence) props.newObjectProperty( Persistence.PERSISTENCE_CLASS_KEY );
	Configuration config = new DummyConfiguration();
	ArrayList chroms = new ArrayList();
	for ( int i = 1; i < args.length; ++i ) {
		Chromosome chrom = db.loadChromosome( args[ i ], config );
		if ( chrom == null )
			throw new IllegalArgumentException( "no chromosome found: " + args[ i ] );
		chroms.add( chrom );
	}

	// evaluate
	fitnessFunc.evaluate( chroms );

	Iterator it = chroms.iterator();
	while ( it.hasNext() ) {
		Chromosome chrom = (Chromosome) it.next();
		logger.info( chrom.toString() + ": fitness = " + chrom.getFitnessValue() + "/"
				+ fitnessFunc.getMaxFitnessValue() );
	}
}

}
