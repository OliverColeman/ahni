/*
 * Copyright 2001-2003 Neil Rotstan
 * Copyright (C) 2004  Derek James and Philip Tucker
 *
 * This file is part of JGAP.
 *
 * JGAP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * JGAP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with JGAP; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Created on Feb 3, 2003 by Philip Tucker
 */
package org.jgapcustomised.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeFitnessComparator;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.ReproductionOperator;

/**
 * Produces offspring be creating clones of parents.
 * 
 * @author Philip Tucker
 */
public class CloneReproductionOperator extends ReproductionOperator {
	/**
	 * Adds new children of <code>parents</code> to <code>offspring</code>.
	 * 
	 * @param config
	 * @param parents <code>List</code> contains Chromosome objects
	 * @param numOffspring
	 * @param offspring <code>List</code> contains ChromosomeMaterial objects
	 * @see org.jgapcustomised.ReproductionOperator#reproduce(Configuration, List, int, List)
	 */
	protected void reproduce(final Configuration config, final List<Chromosome> parents, int numOffspring, List<ChromosomeMaterial> offspring) {
		reproduce(parents, numOffspring, offspring);
	}

	/**
	 * Adds new children of <code>parents</code> to <code>offspring</code>.
	 * 
	 * @param parents <code>List</code> contains <code>Chromosome</code> objects
	 * @param numOffspring
	 * @param offspring <code>List</code> contains <code>ChromosomeMaterial</code> objects
	 */
	@SuppressWarnings("unchecked")
	public static void reproduce(final List<Chromosome> parents, int numOffspring, List<ChromosomeMaterial> offspring) {
		// Sort fittest first to ensure we include these (and more than once if numOffspring is greater than number of parents).
		List<Chromosome> parentsSorted = new ArrayList<Chromosome>(parents);
		Collections.sort(parentsSorted, new ChromosomeFitnessComparator(false, false));
		for (int i = 0; i < numOffspring; i++) {
			Chromosome parent = parentsSorted.get(i % parentsSorted.size());
			offspring.add(parent.cloneMaterial());
		}
	}

}
