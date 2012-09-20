/*
 * Copyright 2001-2003 Neil Rotstan Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of JGAP.
 * 
 * JGAP is free software; you can redistribute it and/or modify it under the terms of the GNU
 * Lesser Public License as published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * JGAP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License along with JGAP; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Modified on Feb 3, 2003 by Philip Tucker
 */
package org.jgap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.jgap.event.EventManager;

/**
 * The Configuration class represents the current configuration of plugins and flags necessary
 * to execute the genetic algorithm (such as fitness function, natural selector, genetic
 * operators, and so on).
 * <p>
 * Note that, while during setup, the settings, flags, and other values may be set multiple
 * times. But once the lockSettings() method is invoked, they cannot be changed. The default
 * behavior of the Genotype constructor is to invoke this method, meaning that once a
 * Configuration object is passed to a Genotype, it cannot be subsequently modified. There is no
 * mechanism for unlocking the settings once they are locked.
 * <p>
 * Not all configuration options are required. See the documentation for each of the respective
 * mutator methods to determine whether it is required to provide a value for that setting, and
 * what the setting will default to if not.
 */
public class Configuration implements java.io.Serializable {

	private IdFactory idFactory = new IdFactory();

	/**
	 * @return next unique chromosome ID
	 */
	public Long nextChromosomeId() {
		return new Long( idFactory.next() );
	}

	/**
	 * @return next unique innovation ID
	 */
	public Long nextInnovationId() {
		return new Long( idFactory.next() );
	}

	/**
	 * References the current fitness function that will be used to evaluate chromosomes during
	 * the natural selection process. Note that only this or the bulk fitness function may be
	 * set--the two are mutually exclusive.
	 */
	private FitnessFunction m_objectiveFunction = null;

	/**
	 * References the current bulk fitness function that will be used to evaluate chromosomes (in
	 * bulk) during the natural selection process. Note that only this or the normal fitness
	 * function may be set--the two are mutually exclusive.
	 */
	private BulkFitnessFunction m_bulkObjectiveFunction = null;

	/**
	 * References the NaturalSelector implementation that will be used to determine which
	 * chromosomes are chosen to be a part of the next generation population.
	 */
	private NaturalSelector m_populationSelector = null;

	private SpeciationParms m_speciationParms = new SpeciationParms();
	
	/**
	 * set selector
	 * 
	 * @param selector
	 * @throws InvalidConfigurationException
	 */
	public synchronized void setNaturalSelector( NaturalSelector selector )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		m_populationSelector = selector;
	}

	/**
	 * @return selector
	 */
	public NaturalSelector getNaturalSelector() {
		return m_populationSelector;
	}

	/**
	 * References ChromosomeMaterial that serves as a sample of the Gene setup that is to be used.
	 * Each gene in ChromosomeMaterial should be represented with the desired Gene type. Added by
	 * Tucker and James.
	 */
	private ChromosomeMaterial m_sampleChromosomeMaterial = null;

	/**
	 * References the random number generator implementation that is to be used for the generation
	 * of any random numbers during the various genetic operations and processes.
	 */
	private Random m_randomGenerator = null;

	/**
	 * References the EventManager that is to be used for the notification of genetic events and
	 * the management of event subscribers.
	 */
	private EventManager m_eventManager = null;

	/**
	 * Stores all of the ReproductionOperator implementations that are to be used to operate upon
	 * the chromosomes of a population after natural selection. In general, operators will be
	 * executed in the order that they are added to this list. Added by Tucker and James.
	 */
	private List reproductionOperators = new ArrayList();

	/**
	 * Stores all of the MutationOperator implementations that are to be used to operate upon the
	 * chromosomes of a population after natural selection. In general, operators will be executed
	 * in the order that they are added to this list. Added by Tucker and James.
	 */
	private List mutationOperators = new ArrayList();

	/**
	 * Add a reproduction operator for use in this algorithm.
	 * 
	 * At least one reproduction operator must be provided.
	 * 
	 * @param a_operatorToAdd The reproduction operator to be added.
	 * 
	 * @throws InvalidConfigurationException if the reproduction operator is null a_operatorToAdd
	 * this object is locked.
	 */
	public synchronized void addReproductionOperator( ReproductionOperator a_operatorToAdd )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		if ( a_operatorToAdd == null )
				throw new InvalidConfigurationException(
						"ReproductionOperator instance may not be null." );
		reproductionOperators.add( a_operatorToAdd );
	}

	/**
	 * Retrieve the reproduction operators added for this genetic algorithm.
	 * 
	 * @return The list of reproduction operators added to this Configuration
	 */
	public List getReproductionOperators() {
		return reproductionOperators;
	}

	/**
	 * Add a mutation operator for use in this algorithm.
	 * 
	 * @param a_operatorToAdd The mutation operator to be added.
	 * 
	 * @throws InvalidConfigurationException if the mutation operator is null a_operatorToAdd this
	 * object is locked.
	 */
	public synchronized void addMutationOperator( MutationOperator a_operatorToAdd )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		if ( a_operatorToAdd == null ) { throw new InvalidConfigurationException(
				"ReproductionOperator instance may not be null." ); }
		mutationOperators.add( a_operatorToAdd );
	}

	/**
	 * Retrieve the mutation operators added for this genetic algorithm.
	 * 
	 * @return The list of mutation operators added to this Configuration
	 */
	public List getMutationOperators() {
		return mutationOperators;
	}

	/**
	 * The number of chromosomes that will be stored in the Genotype.
	 */
	private int m_populationSize = 0;

	/**
	 * Indicates whether the settings of this Configuration instance have been locked. Prior to
	 * locking, the settings may be set and reset as desired. Once this flag is set to true, no
	 * settings may be altered.
	 */
	private boolean m_settingsLocked = false;

	/**
	 * Sets the fitness function to be used for this genetic algorithm. The fitness function is
	 * responsible for evaluating a given Chromosome and returning a positive integer that
	 * represents its worth as a candidate solution. These values are used as a guide by the
	 * natural to determine which Chromosome instances will be allowed to move on to the next
	 * round of evolution, and which will instead be eliminated.
	 * <p>
	 * Note that it is illegal to set both this fitness function and a bulk fitness function.
	 * Although one or the other must be set, the two are mutually exclusive.
	 * 
	 * @param a_functionToSet The fitness function to be used.
	 * 
	 * @throws InvalidConfigurationException if the fitness function is null, a bulk fitness
	 * function has already been set, or if this Configuration object is locked.
	 */
	public synchronized void setFitnessFunction( FitnessFunction a_functionToSet )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		// Sanity check: Make sure that the given fitness function isn't null.
		// -------------------------------------------------------------------
		if ( a_functionToSet == null ) { throw new InvalidConfigurationException(
				"The FitnessFunction instance may not be null." ); }
		// Make sure the bulk fitness function hasn't already been set.
		// ------------------------------------------------------------
		if ( m_bulkObjectiveFunction != null ) { throw new InvalidConfigurationException(
				"The bulk fitness function and normal fitness function " + "may not both be set." ); }
		m_objectiveFunction = a_functionToSet;
	}

	/**
	 * Retrieves the fitness function previously setup in this Configuration object.
	 * 
	 * @return The fitness function.
	 */
	public FitnessFunction getFitnessFunction() {
		return m_objectiveFunction;
	}

	/**
	 * Sets the bulk fitness function to be used for this genetic algorithm. The bulk fitness
	 * function may be used to evaluate and assign fitness values to the entire group of candidate
	 * Chromosomes in a single batch. This can be useful in cases where it's difficult to assign
	 * fitness values to a Chromosome in isolation from the other candidate Chromosomes.
	 * <p>
	 * Note that it is illegal to set both a bulk fitness function and a normal fitness function.
	 * Although one or the other is required, the two are mutually exclusive.
	 * 
	 * @param a_functionToSet The bulk fitness function to be used.
	 * 
	 * @throws InvalidConfigurationException if the bulk fitness function is null, the normal
	 * fitness function has already been set, or if this Configuration object is locked.
	 */
	public synchronized void setBulkFitnessFunction( BulkFitnessFunction a_functionToSet )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		// Sanity check: Make sure that the given bulk fitness function
		// isn't null.
		// ------------------------------------------------------------
		if ( a_functionToSet == null ) { throw new InvalidConfigurationException(
				"The BulkFitnessFunction instance may not be null." ); }
		// Make sure a normal fitness function hasn't already been set.
		// ------------------------------------------------------------
		if ( m_objectiveFunction != null ) { throw new InvalidConfigurationException(
				"The bulk fitness function and normal fitness function " + "may not both be set." ); }
		m_bulkObjectiveFunction = a_functionToSet;
	}

	/**
	 * Retrieves the bulk fitness function previously setup in this Configuration object.
	 * 
	 * @return The bulk fitness function.
	 */
	public BulkFitnessFunction getBulkFitnessFunction() {
		return m_bulkObjectiveFunction;
	}

	/**
	 * Sets sample ChromosomeMaterial that is to be used as a guide for the construction of other
	 * Chromosomes. ChromosomeMaterial should be setup with each gene represented by the desired
	 * concrete Gene implementation for that gene position (locus). Anytime a new Chromosome is
	 * created, it will be constructed with the same Gene setup as that provided in this sample
	 * ChromosomeMaterial.
	 * 
	 * @param a_sampleChromosomeMaterial ChromosomeMaterial to be used as the sample.
	 * @throws InvalidConfigurationException if the given Chromosome is null or this Configuration
	 * object is locked.
	 */
	public void setSampleChromosomeMaterial( ChromosomeMaterial a_sampleChromosomeMaterial )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		// Sanity check: Make sure that the given chromosome isn't null.
		// -----------------------------------------------------------
		if ( a_sampleChromosomeMaterial == null )
				throw new InvalidConfigurationException(
						"The sample Chromosome instance may not be null." );
		m_sampleChromosomeMaterial = a_sampleChromosomeMaterial;
	}

	/**
	 * Retrieves sample ChromosomeMaterial that contains the desired Gene setup for each
	 * respective gene position (locus).
	 * 
	 * @return the sample Chromosome instance.
	 */
	public ChromosomeMaterial getSampleChromosomeMaterial() {
		return m_sampleChromosomeMaterial;
	}

	/**
	 * Sets the random generator to be used for this genetic algorithm. The random generator is
	 * responsible for generating random numbers, which are used throughout the process of genetic
	 * evolution and selection. This setting is required.
	 * 
	 * @param a_generatorToSet The random generator to be used.
	 * 
	 * @throws InvalidConfigurationException if the random generator is null or this object is
	 * locked.
	 */
	public synchronized void setRandomGenerator( Random a_generatorToSet )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		// Sanity check: Make sure that the given random generator isn't null.
		// -------------------------------------------------------------------
		if ( a_generatorToSet == null ) { throw new InvalidConfigurationException(
				"The RandomGenerator instance may not be null." ); }
		m_randomGenerator = a_generatorToSet;
	}

	/**
	 * Retrieves the random generator setup in this Configuration instance.
	 * 
	 * @return The random generator.
	 */
	public Random getRandomGenerator() {
		return m_randomGenerator;
	}

	/**
	 * Sets the population size to be used for this genetic algorithm. The population size is a
	 * fixed value that represents the number of Chromosomes contained within the Genotype
	 * (population). This setting is required.
	 * 
	 * @param a_sizeOfPopulation The population size to be used.
	 * 
	 * @throws InvalidConfigurationException if the population size is not positive or this object
	 * is locked.
	 */
	public synchronized void setPopulationSize( int a_sizeOfPopulation )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		// Sanity check: Make sure the population size is positive.
		// --------------------------------------------------------
		if ( a_sizeOfPopulation < 1 ) { throw new InvalidConfigurationException(
				"The population size must be positive." ); }
		m_populationSize = a_sizeOfPopulation;
	}

	/**
	 * Retrieves the population size setup in this Configuration instance.
	 * 
	 * @return The population size.
	 */
	public int getPopulationSize() {
		return m_populationSize;
	}

	/**
	 * Sets the EventManager that is to be associated with this configuration. The EventManager is
	 * responsible for the management of event subscribers and event notifications.
	 * 
	 * @param a_eventManagerToSet the EventManager instance to use in this configuration.
	 * 
	 * @throws InvalidConfigurationException if the event manager is null or this Configuration
	 * object is locked.
	 */
	public void setEventManager( EventManager a_eventManagerToSet )
			throws InvalidConfigurationException {
		verifyChangesAllowed();
		// Sanity check: Make sure that the given event manager isn't null.
		// ----------------------------------------------------------------
		if ( a_eventManagerToSet == null ) { throw new InvalidConfigurationException(
				"The EventManager instance may not be null." ); }
		m_eventManager = a_eventManagerToSet;
	}

	/**
	 * Retrieves the EventManager associated with this configuration. The EventManager is
	 * responsible for the management of event subscribers and event notifications.
	 * 
	 * @return the actively configured EventManager instance.
	 */
	public EventManager getEventManager() {
		return m_eventManager;
	}

	/**
	 * Locks all of the settings in this configuration object. Once this method is successfully
	 * invoked, none of the settings may be changed. There is no way to unlock this object once it
	 * is locked.
	 * <p>
	 * Prior to returning successfully, this method will first invoke the verifyStateIsValid()
	 * method to make sure that any required configuration options have been properly set. If it
	 * detects a problem, it will throw an InvalidConfigurationException and leave the object
	 * unlocked.
	 * <p>
	 * It's possible to test whether is object is locked through the isLocked() method.
	 * <p>
	 * It is ok to lock an object more than once. In that case, this method does nothing and
	 * simply returns.
	 * 
	 * @throws InvalidConfigurationException if this Configuration object is in an invalid state
	 * at the time of invocation.
	 */
	public synchronized void lockSettings() throws InvalidConfigurationException {
		if ( !m_settingsLocked ) {
			verifyStateIsValid();
			// Make genetic operators lists immutable.
			// --------------------------------------
			reproductionOperators = Collections.unmodifiableList( reproductionOperators );
			mutationOperators = Collections.unmodifiableList( mutationOperators );
			m_settingsLocked = true;
		}
	}

	/**
	 * Retrieves the lock status of this object.
	 * 
	 * @return true if this object has been locked by a previous successful call to the
	 * lockSettings() method, false otherwise.
	 */
	public boolean isLocked() {
		return m_settingsLocked;
	}

	/**
	 * Tests the state of this Configuration object to make sure it's valid. This generally
	 * consists of verifying that required settings have, in fact, been set. If this object is not
	 * in a valid state, then an exception will be thrown detailing the reason the state is not
	 * valid.
	 * 
	 * @throws InvalidConfigurationException if the state of this Configuration is not valid. The
	 * error message in the exception will detail the reason for invalidity.
	 */
	public synchronized void verifyStateIsValid() throws InvalidConfigurationException {
		// First, make sure all of the required fields have been set to
		// appropriate values.
		// ------------------------------------------------------------
		if ( m_objectiveFunction == null && m_bulkObjectiveFunction == null ) { throw new InvalidConfigurationException(
				"A desired fitness function or bulk fitness function must "
						+ "be specified in the active configuration." ); }
		if ( m_sampleChromosomeMaterial == null )
				throw new InvalidConfigurationException(
						"Sample ChromosomeMaterial setup must be specified in the "
								+ "active configuration." );
		if ( m_populationSelector == null ) { throw new InvalidConfigurationException(
				"A desired natural selector must be specified in the active " + "configuration." ); }
		if ( m_randomGenerator == null ) { throw new InvalidConfigurationException(
				"A desired random number generator must be specified in the " + "active configuration." ); }
		if ( m_eventManager == null ) { throw new InvalidConfigurationException(
				"A desired event manager must be specified in the active " + "configuration." ); }
		if ( reproductionOperators.isEmpty() )
				throw new InvalidConfigurationException(
						"At least one reproduction operator must be specified in the " + "configuration." );
		// added by Tucker and James
		// make sure our slice of population add up to 1.0
		//float totalSlices = getNaturalSelector().getSurvivalRate();
		float totalSlices = 0; //only elite survive
		Iterator it = getReproductionOperators().iterator();
		while ( it.hasNext() ) {
			ReproductionOperator oper = (ReproductionOperator) it.next();
			totalSlices += oper.getSlice();
		}
		if ( totalSlices != 1.0f )
				throw new InvalidConfigurationException(
						"Survival rate and reproduction rates are more than 1.0: " + totalSlices);
		if ( m_populationSize <= 0 ) { throw new InvalidConfigurationException(
				"A genotype size greater than zero must be specified in " + "the active configuration." ); }
	}

	/**
	 * Makes sure that this Configuration object isn't locked. If it is, then an exception is
	 * thrown with an appropriate message indicating that settings in this object may not be
	 * altered. This method should be invoked by any mutator method in this object prior to making
	 * any state alterations.
	 * 
	 * @throws InvalidConfigurationException if this Configuration object is locked.
	 */
	protected void verifyChangesAllowed() throws InvalidConfigurationException {
		if ( m_settingsLocked ) { throw new InvalidConfigurationException(
				"This Configuration object is locked. Settings may not be " + "altered." ); }
	}

	/**
	 * @return factory for generating unique IDs
	 */
	public IdFactory getIdFactory() {
		return idFactory;
	}

	/**
	 * @param factory factory for generating unique IDs
	 */
	public void setIdFactory( IdFactory factory ) {
		idFactory = factory;
	}
	
	/**
	 * @return Returns the m_speciationParms.
	 */
	public SpeciationParms getSpeciationParms() {
		return m_speciationParms;
	}
}
