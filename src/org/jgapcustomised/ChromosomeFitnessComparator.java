/*
 * Copyright (C) 2004 Derek James and Philip Tucker
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
 * Created on Feb 3, 2003 by Philip Tucker
 */
package org.jgapcustomised;

import java.util.Comparator;

import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * Enables sorting of chromosomes by their fitness.
 * 
 * @author Philip Tucker
 */
public class ChromosomeFitnessComparator implements Comparator<Chromosome> {

	private boolean isAscending = true;

	private boolean isSpeciated = true;

	/**
	 * @see ChromosomeFitnessComparator#ChromosomeFitnessComparator(boolean, boolean)
	 */
	public ChromosomeFitnessComparator() {
		this(true, true);
	}

	/**
	 * Enables sorting of chromosomes in order of fitness. Ascending order if <code>ascending</code> is true, descending
	 * otherwise. Uses fitness adjusted for species fitness sharing if <code>speciated</code> is true, raw fitness
	 * otherwise.
	 * 
	 * @param ascending
	 * @param speciated
	 */
	public ChromosomeFitnessComparator(boolean ascending, boolean speciated) {
		super();
		isAscending = ascending;
		isSpeciated = speciated;
	}

	/**
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(Chromosome c1, Chromosome c2) {
		double fitness1 = (isSpeciated ? c1.getSpeciatedFitnessValue() : c1.getFitnessValue());
		double fitness2 = (isSpeciated ? c2.getSpeciatedFitnessValue() : c2.getFitnessValue());
		
		if (Double.isNaN(fitness1)) fitness1 = -1; // Use -1 as NaN should be lower than 0.
		if (Double.isNaN(fitness2)) fitness2 = -1;
		
		// If the overall fitness is the same (eg because it's based on a ranking produced by NSGA-II), 
		// then we might be able to differentiate based on the performance value.
		if (fitness1 == fitness2) {
			fitness1 = c1.getPerformanceValue();
			fitness2 = c2.getPerformanceValue();
			if (Double.isNaN(fitness1)) fitness1 = -1;
			if (Double.isNaN(fitness2)) fitness2 = -1;
		}
		/*
		// If the overall fitness is the same (eg because it's based on a ranking produced by NSGA-II), 
		// then we might be able to differentiate based on the average over multiple objectives.
		if (fitness1 == fitness2) {
			fitness1 = ArrayUtil.sum(c1.getFitnessValues());
			fitness2 = ArrayUtil.sum(c2.getFitnessValues());
			if (Double.isNaN(fitness1)) fitness1 = -1;
			if (Double.isNaN(fitness2)) fitness2 = -1;
		}
		*/
		
		// If the fitness is still identical then use ID to sort in order to maintain a stable sorting.
		if (fitness1 == fitness2) {
			fitness1 = c1.getId();
			fitness2 = c2.getId();
		}
		return (int) (isAscending ? Math.signum(fitness1 - fitness2) : Math.signum(fitness2 - fitness1));
	}

}
