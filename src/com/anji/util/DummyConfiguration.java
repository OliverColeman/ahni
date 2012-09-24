/**
 * ----------------------------------------------------------------------------| Created on Apr
 * 12, 2003
 */
package com.anji.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.InvalidConfigurationException;
import org.jgapcustomised.InvalidConfigurationRuntimeException;
import org.jgapcustomised.NaturalSelector;
import org.jgapcustomised.ReproductionOperator;
import org.jgapcustomised.event.EventManager;
import org.jgapcustomised.impl.IntegerAllele;
import org.jgapcustomised.impl.WeightedRouletteSelector;

/**
 * @author Philip Tucker
 */
public class DummyConfiguration extends Configuration {

private final static int DEFAULT_POPULATION_SIZE = 100;

private final static double DEFAULT_SURVIVAL_RATE = 0.20f;

private final static double DEFAULT_REPRODUCTION_RATE = 0.80f;

/**
 * ctor
 */
public DummyConfiguration() {
	super();

	try {
		setEventManager( new EventManager() );
		setRandomGenerator( new Random() );
		setBulkFitnessFunction( new DummyBulkFitnessFunction( getRandomGenerator() ) );
		NaturalSelector selector = new WeightedRouletteSelector();
		selector.setSurvivalRate( DEFAULT_SURVIVAL_RATE );
		setNaturalSelector( selector );
		setPopulationSize( DEFAULT_POPULATION_SIZE );

		List initAlleles = new ArrayList( 1 );
		IntegerAllele initAllele = new IntegerAllele( this, 0, 10 );
		initAllele.setValue( new Integer( 1 ) );
		initAlleles.add( initAllele );
		setSampleChromosomeMaterial( new ChromosomeMaterial( initAlleles ) );

		ReproductionOperator repro = new DummyReproductionOperator();
		repro.setSlice( DEFAULT_REPRODUCTION_RATE );
		addReproductionOperator( repro );
	}
	catch ( InvalidConfigurationException e ) {
		throw new InvalidConfigurationRuntimeException( "error in DummyConfiguration: " + e );
	}
}

}
