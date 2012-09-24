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
 * created by Philip Tucker
 */
package com.anji.integration;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.Genotype;
import org.jgapcustomised.event.GeneticEvent;
import org.jgapcustomised.event.GeneticEventListener;

import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Writes log events to log4j framework.
 * @author Philip Tucker
 */
public class LogEventListener implements GeneticEventListener, Configurable {

private static Logger logger = Logger.getLogger( LogEventListener.class );

private Configuration config = null;

/**
 * @param newConfig JGAP configuration.
 */
public LogEventListener( Configuration newConfig ) {
	config = newConfig;
}

/**
 * no initialization parameters
 * @param p
 */
public void init( Properties p ) {
	// noop
}

/**
 * @param event <code>GeneticEvent.GENOTYPE_EVOLVED_EVENT</code> is the only event handled;
 * writes species count and stats of all fittest chromosomes.
 */
public void geneticEventFired( GeneticEvent event ) {
	if ( GeneticEvent.GENOTYPE_EVOLVED_EVENT.equals( event.getEventName() ) ) {
		Genotype genotype = (Genotype) event.getSource();
		Chromosome fittest = genotype.getFittestChromosome();
		double maxFitnessValue = ( config.getBulkFitnessFunction() != null ) ? config
				.getBulkFitnessFunction().getMaxFitnessValue() : config.getFitnessFunction()
				.getMaxFitnessValue();
		double fitness = ( maxFitnessValue == 0 ) ? fittest.getFitnessValue() : ( fittest
				.getFitnessValue() / maxFitnessValue );
		//logger.info( "species count: " + genotype.getSpecies().size() );
		List chroms = genotype.getChromosomes();
		Iterator iter = chroms.iterator();
		int maxFitnessCount = 0;
		while ( iter.hasNext() ) {
			Chromosome c = (Chromosome) iter.next();
			if ( c.getFitnessValue() == maxFitnessValue ) {
				//logger.info( "max: id=" + c.getId() + " score=" + fitness + " size=" + c.size() );
				++maxFitnessCount;
			}
		}
// TODO		if ( maxFitnessCount > 0 )
			//logger.info( "# chromosomes with max fitness: " + maxFitnessCount );
		//logger.info( "champ: id=" + fittest.getId() + " score=" + fitness + " size=" + fittest.size() );
	}
}
}
