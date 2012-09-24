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
package org.jgapcustomised.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.jgapcustomised.Chromosome;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.NaturalSelector;

/**
 * A basic implementation of NaturalSelector that models a roulette wheel. When a Chromosome is
 * added, it gets a number of "slots" on the wheel equal to its fitness value. When the select
 * method is invoked, the wheel is "spun" and the Chromosome occupying the spot on which it
 * lands is selected. Then the wheel is spun again and again until the requested number of
 * Chromosomes have been selected. Since Chromosomes with higher fitness values get more slots
 * on the wheel, there's a higher statistical probability that they'll be chosen, but it's not
 * guaranteed.
 */
public class WeightedRouletteSelector extends NaturalSelector {

	/**
	 * Represents the "roulette wheel". Each key in the Map is a Chromosome and each value is an
	 * instance of the SlotCounter inner class, which keeps track of how many slots on the wheel
	 * each Chromosome is occupying.
	 */
	private Map m_wheel = new HashMap();

	/**
	 * Keeps track of the total number of slots that are in use on the roulette wheel. This is
	 * equal to the combined fitness values of all Chromosome instances that have been added to
	 * this wheel.
	 */
	private long m_totalNumberOfUsedSlots = 0;

	/**
	 * An internal pool in which discarded SlotCounter instances can be stored so that they can be
	 * reused over and over again, thus saving memory and the overhead of constructing new ones
	 * each time.
	 */
	private Pool m_counterPool = new Pool();

	/**
	 * Add a Chromosome instance to this selector's working pool of Chromosomes.
	 * 
	 * @param a_activeConfigurator The current active Configuration to be used during the add
	 * process.
	 * @param a_chromosomeToAdd The specimen to add to the pool.
	 */
	protected synchronized void add( Configuration a_activeConfigurator,
			Chromosome a_chromosomeToAdd ) {

		// The "roulette wheel" is represented by a Map. Each key is a
		// Chromosome and each value is an instance of the SlotCounter inner
		// class. The counter keeps track of the total number of slots that
		// each chromosome is occupying on the wheel (which is equal to the
		// combined total of their fitness values). If the Chromosome is
		// already in the Map, then we just increment its number of slots
		// by its fitness value. Otherwise we add it to the Map.
		// -----------------------------------------------------------------
		SlotCounter counter = (SlotCounter) m_wheel.get( a_chromosomeToAdd );

		if ( counter != null ) {
			// The Chromosome is already in the map.
			// -------------------------------------
			counter.incrementByFitness();
		}
		else {
			// We need to add this Chromosome and an associated SlotCounter
			// to the map. First, we reset the Chromosome's
			// isSelectedForNextGeneration flag to false. Later, if the
			// Chromosome is actually selected to move on to the next
			// generation population by the select() method, then it will
			// be set to true.
			// ------------------------------------------------------------
			a_chromosomeToAdd.setIsSelectedForNextGeneration( false );

			// We're going to need a SlotCounter. See if we can get one
			// from the pool. If not, construct a new one.
			// --------------------------------------------------------
			counter = (SlotCounter) m_counterPool.acquirePooledObject();
			if ( counter == null ) {
				counter = new SlotCounter();
			}
            int fitness = speciatedFitness ? a_chromosomeToAdd.getSpeciatedFitnessValue() : a_chromosomeToAdd.getFitnessValue();
			counter.reset(fitness);
			m_wheel.put( a_chromosomeToAdd, counter );
		}

		m_totalNumberOfUsedSlots += a_chromosomeToAdd.getSpeciatedFitnessValue();
	}

	/**
	 * Select a given number of Chromosomes from the pool that will move on to the next generation
	 * population. This selection should be guided by the fitness values, but fitness should be
	 * treated as a statistical probability of survival, not as the sole determining factor. In
	 * other words, Chromosomes with higher fitness values should be more likely to be selected
	 * than those with lower fitness values, but it should not be guaranteed.
	 * 
	 * @param a_activeConfiguration The current active Configuration that is to be used during
	 * the selection process.
	 * @param a_howManyToSelect The number of Chromosomes to select.
	 * 
	 * @return List contains Chromosome objects.
	 */
	protected synchronized List select( Configuration a_activeConfiguration, int a_howManyToSelect ) {
		List result = new ArrayList();

		if ( m_wheel.isEmpty() == false ) {
			Random generator = a_activeConfiguration.getRandomGenerator();

			// Build three arrays from the key/value pairs in the wheel map: one
			// that contains the fitness values for each chromosome, one that
			// contains the total number of occupied slots on the wheel for each
			// chromosome, and one that contains the chromosomes themselves. The
			// array indices are used to associate the values of the three arrays
			// together (eg, if a chromosome is at index 5, then its fitness value
			// and counter values are also at index 5 of their respective arrays).
			// -------------------------------------------------------------------
			Set entries = m_wheel.entrySet();
			int numberOfEntries = entries.size();
			int[] fitnessValues = new int[ numberOfEntries ];
			long[] counterValues = new long[ numberOfEntries ];
			Chromosome[] chromosomes = new Chromosome[ numberOfEntries ];

			Iterator entryIterator = entries.iterator();
			for ( int i = 0; i < numberOfEntries; i++ ) {
				Map.Entry chromosomeEntry = (Map.Entry) entryIterator.next();

				Chromosome currentChromosome = (Chromosome) chromosomeEntry.getKey();

				SlotCounter currentCounter = (SlotCounter) chromosomeEntry.getValue();

				fitnessValues[ i ] = currentCounter.getFitnessValue();
				counterValues[ i ] = currentCounter.getCounterValue();
				chromosomes[ i ] = currentChromosome;
			}

			// To select each chromosome, we just "spin" the wheel and grab
			// whichever chromosome it lands on.
			// ------------------------------------------------------------
			while ( result.size() < a_howManyToSelect ) {
				Chromosome selectedChromosome = spinWheel( generator, fitnessValues, counterValues,
						chromosomes );
				selectedChromosome.setIsSelectedForNextGeneration( true );
				result.add( selectedChromosome );
			}
		}

		return result;
	}

	/**
	 * This method "spins" the wheel and returns the Chromosome that is "landed upon". Each time a
	 * chromosome is selected, one instance of it is removed from the wheel so that it can not be
	 * selected again.
	 * 
	 * @param a_generator The random number generator to be used during the spinning process.
	 * @param a_fitnessValues
	 * @param a_counterValues
	 * @param a_chromosomes
	 * @return selected chromosome
	 */
	private Chromosome spinWheel( Random a_generator, int[] a_fitnessValues,
			long[] a_counterValues, Chromosome[] a_chromosomes ) 
{
		// Randomly choose a slot on the wheel.
		// ------------------------------------
		long selectedSlot = Math.abs( a_generator.nextLong() % m_totalNumberOfUsedSlots );

		// Loop through the wheel until we find our selected slot. Here's
		// how this works: we have three arrays, one with the fitness values
		// of the chromosomes, one with the total number of slots on the
		// wheel that each chromosome occupies (its counter value), and
		// one with the chromosomes themselves. The array indices associate
		// each of the three together (eg, if a chromosome is at index 5,
		// then its fitness value and counter value are also at index 5 of
		// their respective arrays).
		//
		// We've already chosen a random slot number on the wheel from which
		// we want to select the Chromosome. We loop through each of the
		// array indices and, for each one, we add the number of occupied slots
		// (the counter value) to an ongoing total until that total
		// reaches or exceeds the chosen slot number. When that happenes,
		// we've found the chromosome sitting in that slot and we return it.
		// ------------------------------------------------------------------
		long currentSlot = 0;

		for ( int i = 0; i < a_counterValues.length; i++ ) {
			// Increment our ongoing total and see if we've landed on the
			// selected slot.
			// ----------------------------------------------------------
			currentSlot += a_counterValues[ i ];

			if ( currentSlot > selectedSlot ) {
				// Remove one instance of the chromosome from the wheel by
				// decrementing the slot counter by the fitness value.
				// --------------------------------------------------------
				a_counterValues[ i ] -= a_fitnessValues[ i ];
				m_totalNumberOfUsedSlots -= a_fitnessValues[ i ];

				// Now return our selected Chromosome
				// ----------------------------------
				return a_chromosomes[ i ];
			}
		}

		// If we have reached here, it means we have not found any chromosomes
		// to select and something is wrong with our logic. For some reason
		// the selected slot has exceeded the slots on our wheel. To help
		// with debugging, we tally up the total number of slots left on
		// the wheel and report it along with the chosen slot number that we
		// couldn't find.
		// -------------------------------------------------------------------
		long totalSlotsLeft = 0;
		for ( int i = 0; i < a_counterValues.length; i++ ) {
			totalSlotsLeft += a_counterValues[ i ];
		}

		throw new RuntimeException( "Logic Error. This code should never "
				+ "be reached. Please report this as a bug to the " + "JGAP team: selected slot "
				+ selectedSlot + " " + "exceeded " + totalSlotsLeft + " number of slots left." );
	}

	/**
	 * Empty out the working pool of Chromosomes.
	 */
	protected synchronized void emptyImpl() {
		// Put all of the old SlotCounters into the pool so that we can
		// reuse them later instead of constructing new ones.
		// ------------------------------------------------------------
		m_counterPool.releaseAllObjects( m_wheel.values() );

		// Now clear the wheel and reset the internal state.
		// -------------------------------------------------
		m_wheel.clear();
		m_totalNumberOfUsedSlots = 0;
	}
}

/**
 * Implements a counter that is used to keep track of the total number of slots that a single
 * Chromosome is occupying in the roulette wheel. Since all equal copies of a chromosome have
 * the same fitness value, the increment method always adds the fitness value of the chromosome.
 * Following construction of this class, the reset() method must be invoked to provide the
 * initial fitness value of the Chromosome for which this SlotCounter is to be associated. The
 * reset() method may be reinvoked to begin counting slots for a new Chromosome.
 */

class SlotCounter {

	/**
	 * The fitness value of the Chromosome for which we are keeping count of roulette wheel slots.
	 * Although this value is constant for a Chromosome, it's not declared final here so that the
	 * slots can be reset and later reused for other Chromosomes, thus saving some memory and the
	 * overhead of constructing them from scratch.
	 */
	private int m_fitnessValue = 0;

	/**
	 * The current number of slots occupied by our associated Chromosome.
	 */
	private long m_count = 0;

	/**
	 * Resets the internal state of this SlotCounter instance so that it can be used to count
	 * slots for a new Chromosome.
	 * 
	 * @param a_initialFitness The fitness value of the Chromosome for which this instance is
	 * acting as a counter.
	 */
	public void reset( int a_initialFitness ) {
		m_fitnessValue = a_initialFitness;
		m_count = a_initialFitness;
	}

	/**
	 * Retrieves the fitness value of the chromosome for which this instance is acting as a
	 * counter.
	 * 
	 * @return The fitness value that was passed in at reset time.
	 */
	public int getFitnessValue() {
		return m_fitnessValue;
	}

	/**
	 * Increments the value of this counter by the fitness value that was passed in at reset time.
	 */
	public void incrementByFitness() {
		m_count += m_fitnessValue;
	}

	/**
	 * Retrieves the current value of this counter: ie, the number of slots on the roulette wheel
	 * that are currently occupied by the Chromosome associated with this SlotCounter instance.
	 * 
	 * @return the current value of this counter.
	 */
	public long getCounterValue() {
		return m_count;
	}
}


