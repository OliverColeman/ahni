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
 * Created on Apr 6, 2004 by Philip Tucker
 */
package com.anji.neat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgapcustomised.Allele;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.MutationOperator;

import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Removes neurons and connections that do not affect the activation of the network. This includes hidden neurons
 * without inputs or outputs, connections missing source or destination neurons, or sub-structures of neurons and
 * connections that are stranded. Allows additive and subtractive mutation operators to be less careful about what they
 * do and require less coordination among them, since this operator can follow them and "clean up the mess". For this
 * reason, this operator generally should be the last executed in the sequence of mutation operators. This operator was
 * necessary with the addition of simplification dynamics for James and Tucker's "A Comparative Analysis of
 * Simplification and Complexification in the Evolution of Neural Network Topologies" paper for <a
 * href="http://gal4.ge.uiuc.edu:8080/GECCO-2004/">GECCO 2004 </a>.
 * 
 * This class forces the mutation rate to be 1 as any other mutation rate doesn't make sense.
 * 
 * @author Philip Tucker
 */
public class PruneMutationOperator extends MutationOperator implements Configurable {

	/**
	 * properties key, prune network mutation rate
	 */
	private static final String PRUNE_MUTATE_RATE_KEY = "prune.mutation.rate";

	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init(Properties props) throws Exception {
		setMutationRate(1);
	}

	public PruneMutationOperator() {
		super(1);
	}

	/**
	 * Traverse network flowing forward and backward to identify unvisited connections and neurons. Then, remove a
	 * number of those depending on mutation rate.
	 * 
	 * @param config
	 * @param target chromosome material to mutate
	 * @param genesToAdd <code>Set</code> contains <code>Gene</code> objects
	 * @param genesToRemove <code>Set</code> contains <code>Gene</code> objects
	 * @see org.jgapcustomised.MutationOperator#mutate(org.jgapcustomised.Configuration,
	 *      org.jgapcustomised.ChromosomeMaterial, java.util.Set, java.util.Set)
	 */
	protected void mutate(Configuration config, ChromosomeMaterial target, Set<Allele> genesToAdd, Set<Allele> genesToRemove) {
		genesToRemove.addAll(getAllelesToRemove(target));
	}
	
	public static Set<Allele> getAllelesToRemove(ChromosomeMaterial target) {
		target.pruned = true;
		List<Allele> candidatesToRemove = new ArrayList<Allele>();
		findUnvisitedAlleles(target, candidatesToRemove, true);
		findUnvisitedAlleles(target, candidatesToRemove, false);
		return new HashSet<Allele>(candidatesToRemove);
	}

	/**
	 * @param material target from which to remove stranded nodes and connections
	 * @param unvisitedAlleles <code>List</code> contains <code>Gene</code> objects, unvisited nodes and connections
	 * @param isForward traverse the network from input to output if true, output to input if false
	 */
	private static void findUnvisitedAlleles(ChromosomeMaterial material, List<Allele> unvisitedAlleles, boolean isForward) {
		// initialize unvisited connections
		List<ConnectionAllele> unvisitedConnAlleles = NeatChromosomeUtility.getConnectionList(material.getAlleles());

		// initialize unvisited neurons (input and output neurons are always part of the activation,
		// and therefore considered "visited")
		Map<Long, NeuronAllele> hiddenNeuronAlleles = NeatChromosomeUtility.getNeuronMap(material.getAlleles(), NeuronType.HIDDEN);
		Set<Long> unvisitedNeuronInnovationIds = new HashSet<Long>(hiddenNeuronAlleles.keySet());

		// currentNeuronInnovationIds and nextNeuronInnovationIds keep track of where we are as we
		// traverse the network
		Map<Long, NeuronAllele> initialNeuronAlleles = NeatChromosomeUtility.getNeuronMap(material.getAlleles(), (isForward ? NeuronType.INPUT : NeuronType.OUTPUT));
		Set<Long> currentNeuronInnovationIds = new HashSet<Long>(initialNeuronAlleles.keySet());
		Set<Long> nextNeuronInnovationIds = new HashSet<Long>();
		while (!unvisitedConnAlleles.isEmpty() && !currentNeuronInnovationIds.isEmpty()) {
			nextNeuronInnovationIds.clear();
			Collection connAlleles = isForward ? NeatChromosomeUtility.extractConnectionAllelesForSrcNeurons(unvisitedConnAlleles, currentNeuronInnovationIds) : NeatChromosomeUtility.extractConnectionAllelesForDestNeurons(unvisitedConnAlleles, currentNeuronInnovationIds);
			Iterator it = connAlleles.iterator();
			while (it.hasNext()) {
				ConnectionAllele connAllele = (ConnectionAllele) it.next();
				nextNeuronInnovationIds.add(isForward ? connAllele.getDestNeuronId() : connAllele.getSrcNeuronId());
			}
			unvisitedNeuronInnovationIds.removeAll(nextNeuronInnovationIds);
			currentNeuronInnovationIds.clear();
			currentNeuronInnovationIds.addAll(nextNeuronInnovationIds);
			unvisitedConnAlleles.removeAll(connAlleles);
		}

		// return all unvisited neurons and connections
		unvisitedAlleles.addAll(unvisitedConnAlleles);
		Iterator it = unvisitedNeuronInnovationIds.iterator();
		while (it.hasNext()) {
			Long id = (Long) it.next();
			NeuronAllele neuronAllele = (NeuronAllele) hiddenNeuronAlleles.get(id);
			unvisitedAlleles.add(neuronAllele);
		}
	}

	/**
	 * @param aMutationRate
	 */
	@Override
	public void setMutationRate(double aMutationRate) {
	}
}
