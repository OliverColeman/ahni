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
package org.jgapcustomised;

import java.io.Serializable;
import java.util.List;

import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;


/**
 * Bulk fitness functions are used to determine how optimal a group of solutions are relative to each other. Bulk
 * fitness functions can be useful (vs. normal fitness functions) when fitness of a particular solution cannot be easily
 * computed in isolation, but instead is dependent upon the fitness of its fellow solutions that are also under
 * consideration. This abstract class should be extended and the <code>evaluate(List)</code> method implemented to
 * evaluate each of the Chromosomes given in an array and set their fitness values prior to returning.
 */
public abstract class BulkFitnessFunction implements Serializable {
	/**
	 * Calculates and sets the fitness values on each of the given Chromosomes via their setFitnessValue() method. May also
	 * set the performance of a Chromosome if this is calculated independently of fitness. 
	 * @param subjects {@link Chromosome} objects for which the fitness values must be computed and set.
	 */
	public abstract void evaluate(List<Chromosome> subjects);

	/**
	 * @return Return true when an evolutionary run should be completed before the maximum number of generations is complete, false otherwise.
	 */
	public abstract boolean endRun();
	
	/**
	 * @return Returns the number of objectives being employed. This is only valid for fitness functions and 
	 * {@link NaturalSelector}s that handle multiple objectives. This default implementation returns 1. NOTE:
	 * the returned value should include the number of regular fitness objectives PLUS the number of novelty
	 * objectives (see {@link #getNoveltyObjectiveCount()}).
	 */
	public int getObjectiveCount() {
		return 1;
	}
	
	/**
	 * @return Returns the number of novelty objectives being employed. This is only valid for fitness functions 
	 * that define novelty behaviours. This default implementation returns 0.
	 */
	public int getNoveltyObjectiveCount() {
		return 0;
	}

	/**
	 * @return Labels for each objective defined by this fitness function, with regular fitness objectives followed by novelty objectives. 
	 * This default implementation returns an array containing {@link #getObjectiveCount()} strings 
	 * constructed of the class name of the sub-class prefixed with either F# or N# where F denotes a regular fitness objective, N denotes a novelty objective and
	 * # is the index of the objective.
	 */
	public String[] getObjectiveLabels() {
		String[] labels = new String[getObjectiveCount() + getNoveltyObjectiveCount()];
		int index = 0;
		for (int f = 0; f < getObjectiveCount() - getNoveltyObjectiveCount(); f++)
			labels[index++] = "F" + f + this.getClass().getSimpleName();
		for (int f = 0; f < getNoveltyObjectiveCount(); f++)
			labels[index++] = "N" + f + this.getClass().getSimpleName();
		return labels;
	}
	
	public abstract void dispose();

	/**
	 * <p><em>Deprecated in favour of event listeners such as {@link org.jgapcustomised.event.GeneticEventListener}
	 * or {@link com.ojcoleman.ahni.event.AHNIEventListener}.</em></p>
	 * <p>This method is called when an evolutionary run has finished. It can be used to perform testing or other analysis
	 * on the fittest and/or best performing Chromosomes evolved during the run.</p>
	 * @param evolver Methods on this object can be used to obtain the fittest and best performing Chromosomes evolved during the run.
	 */
	@Deprecated
	public abstract void evolutionFinished(HyperNEATEvolver evolver);
}
