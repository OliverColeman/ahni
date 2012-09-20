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
package org.jgapcusomised;

import java.io.Serializable;
import java.util.List;

/**
 * Bulk fitness functions are used to determine how optimal a group of solutions are relative to
 * each other. Bulk fitness functions can be useful (vs. normal fitness functions) when fitness
 * of a particular solution cannot be easily computed in isolation, but instead is dependent
 * upon the fitness of its fellow solutions that are also under consideration. This abstract
 * class should be extended and the <code>evaluate(List)</code> method implemented to evaluate
 * each of the Chromosomes given in an array and set their fitness values prior to returning.
 */
public interface BulkFitnessFunction extends Serializable {
	/**
	 * Calculates and sets the fitness values on each of the given Chromosomes via their
	 * setFitnessValue() method.
	 * 
	 * @param subjects <code>List</code> contains <code>Chromosome</code> objects for which
	 * the fitness values must be computed and set.
	 */
	public void evaluate(List<Chromosome> subjects);

	/**
	 * @return int maximum possible fitness value this function will return
	 */
	public int getMaxFitnessValue();
	
	public boolean endRun();
	
	public void dispose(); 
}
