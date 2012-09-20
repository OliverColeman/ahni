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
 * Created on Apr 20, 2004 by Philip Tucker
 */
package com.anji.integration;

import org.apache.log4j.Logger;
import org.jgap.Chromosome;

import com.anji.Copyright;
import com.anji.persistence.FilePersistence;
import com.anji.util.Configurable;
import com.anji.util.DummyConfiguration;
import com.anji.util.Properties;

/**
 * Converts chromosome to activator, which it then uses to write network XML according to <a
 * href="http://nevt.sourceforge.net/">NEVT </a> data model.
 * 
 * @author Philip Tucker
 */
public class ChromosomeToNetworkXml implements Configurable {

private static Logger logger = Logger.getLogger( ChromosomeToNetworkXml.class );

private FilePersistence db = new FilePersistence();

private AnjiNetTranscriber transcriber;

/**
 * See <a href=" {@docRoot}/params.htm" target="anji_params">Parameter Details </a> for
 * specific property settings.
 * @param props configuration parameters
 */
public void init( Properties props ) {
	try {
		db.init( props );
		transcriber = (AnjiNetTranscriber) props.singletonObjectProperty( AnjiNetTranscriber.class );
	}
	catch ( Exception e ) {
		throw new IllegalArgumentException( "invalid properties: " + e.getClass() + ": "
				+ e.getMessage() );
	}
}

/**
 * Transcribes chromosome to network and generates XML.
 * 
 * @param chromId persistence ID of chromosome
 * @return XML representation of network
 * @throws Exception
 */
public String toNetworkXml( String chromId ) throws Exception {
	Chromosome chrom = db.loadChromosome( chromId, new DummyConfiguration() );
	AnjiActivator activator = new AnjiActivator( transcriber.newAnjiNet( chrom ), 1 );

	String result = activator.toXml();
	db.store( activator );
	return result;
}

/**
 * Saves to persistence network XML representation of chromosomes.
 * 
 * @param args list of chromosome IDs
 * @throws Exception
 */
public static void main( String[] args ) throws Exception {
	System.out.println( Copyright.STRING );
	if ( args.length < 2 ) {
		printUsage();
		System.exit( -1 );
	}

	ChromosomeToNetworkXml ctnx = new ChromosomeToNetworkXml();
	Properties props = new Properties( args[ 0 ] );
	ctnx.init( props );

	for ( int i = 1; i < args.length; ++i ) {
		String result = ctnx.toNetworkXml( args[ i ] );
		logger.info( result );
	}
}

/**
 * command line usage
 */
private static void printUsage() {
	System.err.println( "<cmd> properties-file-name chromosome-id" );
}

}
