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
 * created by Philip Tucker on Feb 16, 2003
 */
package com.anji.neat;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.MutationOperator;

import com.anji.integration.AnjiRequiredException;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Implements NEAT add node mutation inspired by <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving
 * Neural Networks through Augmenting Topologies </a>. In ANJI, mutation rate
 * refers to the likelihood of a new node being created on any existing
 * connection. In traditional NEAT, it is the likelihood of a chromosome
 * experiencing a mutation, and each chromosome can not have more than one
 * topological mutation per generation.
 * 
 * @author Philip Tucker
 */
public class AddNeuronMutationOperator extends MutationOperator implements Configurable {

	/**
	 * properties key, add neuron mutation rate
	 */
	public static final String ADD_NEURON_MUTATE_RATE_KEY = "add.neuron.mutation.rate";

	/**
	 * default mutation rate
	 */
	public static final double DEFAULT_MUTATE_RATE = 0.01f;

	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init(Properties props) throws Exception {
		setMutationRate(props.getDoubleProperty(ADD_NEURON_MUTATE_RATE_KEY, DEFAULT_MUTATE_RATE));
	}

	/**
	 * @see AddNeuronMutationOperator#AddNeuronMutationOperator(double)
	 */
	public AddNeuronMutationOperator() {
		this(DEFAULT_MUTATE_RATE);
	}

	/**
	 * @see MutationOperator#MutationOperator(double)
	 */
	public AddNeuronMutationOperator(double newMutationRate) {
		super(newMutationRate);
	}

	/**
	 * Adds connections according to <a
	 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT
	 * </a> add node mutation.
	 * 
	 * @see org.jgapcustomised.MutationOperator#mutate(org.jgapcustomised.Configuration,
	 *      org.jgapcustomised.ChromosomeMaterial, java.util.Set, java.util.Set)
	 */
	protected void mutate(Configuration jgapConfig, final ChromosomeMaterial target, Set allelesToAdd, Set allelesToRemove) {
		if ((jgapConfig instanceof NeatConfiguration) == false)
			throw new AnjiRequiredException("com.anji.neat.NeatConfiguration");
		NeatConfiguration config = (NeatConfiguration) jgapConfig;

		Map neurons = NeatChromosomeUtility.getNeuronMap(target.getAlleles());

		// neuron can be mutated on any connection
		List connList = NeatChromosomeUtility.getConnectionList(target.getAlleles());
		Collections.shuffle(connList, config.getRandomGenerator());

		int numConnections = numMutations(config.getRandomGenerator(), connList.size());
		Iterator iter = connList.iterator();
		int count = 0;
		while (iter.hasNext() && (count++ < numConnections)) {
			ConnectionAllele oldConnectAllele = (ConnectionAllele) iter.next();
			addNeuronAtConnection(config, neurons, oldConnectAllele, allelesToAdd, allelesToRemove);
		}
	}

	/**
	 * @param config
	 * @param neurons
	 *            <code>Map</code> contains <code>NeuronAllele</code> objects
	 * @param oldConnectAllele
	 *            connection allele to be replaced by neuron
	 * @param allelesToAdd
	 *            <code>Set</code> contains <code>Allele</code> objects
	 * @param allelesToRemove
	 *            <code>Set</code> contains <code>Allele</code> objects
	 * @return true iff neuron added
	 */
	public boolean addNeuronAtConnection(NeatConfiguration config, Map neurons, ConnectionAllele oldConnectAllele, Set allelesToAdd, Set allelesToRemove) {
		NeuronAllele newNeuronAllele = config.newNeuronAllele(oldConnectAllele.getInnovationId());

		// check for dupes
		if (neurons.containsKey(newNeuronAllele.getInnovationId()) == false) {
			neurons.put(newNeuronAllele.getInnovationId(), newNeuronAllele);

			// and add 2 new connections ...
			ConnectionAllele newConnectAllele1 = config.newConnectionAllele(oldConnectAllele.getSrcNeuronId(), newNeuronAllele.getInnovationId());
			newConnectAllele1.setWeight(1);

			ConnectionAllele newConnectAllele2 = config.newConnectionAllele(newNeuronAllele.getInnovationId(), oldConnectAllele.getDestNeuronId());
			newConnectAllele2.setWeight(oldConnectAllele.getWeight());

			allelesToRemove.add(oldConnectAllele);
			allelesToAdd.add(newNeuronAllele);
			allelesToAdd.add(newConnectAllele1);
			allelesToAdd.add(newConnectAllele2);

			return true;
		}

		return false;
	}
}
