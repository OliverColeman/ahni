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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.MutationOperator;

import com.anji.integration.AnjiRequiredException;
import com.anji.nn.RecurrencyPolicy;
import com.anji.nn.activationfunction.ActivationFunction;
import com.anji.nn.activationfunction.LinearActivationFunction;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Implements NEAT add connection mutation inspired by <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>. In ANJI, mutation rate refers to the likelihood of any candidate new mutation (i.e., any 2
 * unconnected nodes, not counting those that would create a loop if recurrency is disabled) occurring. In traditional
 * NEAT, it is the likelihood of a chromosome experiencing a mutation, and each chromosome can not have more than one
 * topological mutation per generation.
 * 
 * @author Philip Tucker
 */
public class AddConnectionMutationOperator extends MutationOperator implements Configurable {

	/**
	 * properties key, add connection mutation rate
	 */
	public static final String ADD_CONN_MUTATE_RATE_KEY = "add.connection.mutation.rate";

	/**
	 * default mutation rate
	 */
	public static final double DEFAULT_MUTATE_RATE = 0.01f;

	private RecurrencyPolicy policy;

	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	@Override
	public void init(Properties props) throws Exception {
		setMutationRate(props.getDoubleProperty(ADD_CONN_MUTATE_RATE_KEY, DEFAULT_MUTATE_RATE));
		policy = RecurrencyPolicy.load(props);
	}

	/**
	 * @see AddConnectionMutationOperator#AddConnectionMutationOperator(double)
	 */
	public AddConnectionMutationOperator() {
		this(DEFAULT_MUTATE_RATE, RecurrencyPolicy.BEST_GUESS);
	}

	/**
	 * @param newMutationRate
	 * @see AddConnectionMutationOperator#AddConnectionMutationOperator(double, RecurrencyPolicy)
	 */
	public AddConnectionMutationOperator(double newMutationRate) {
		this(newMutationRate, RecurrencyPolicy.BEST_GUESS);
	}

	/**
	 * Creates new operator with specified recurrency policy.
	 * 
	 * @param aPolicy
	 * @see RecurrencyPolicy
	 */
	public AddConnectionMutationOperator(RecurrencyPolicy aPolicy) {
		this(DEFAULT_MUTATE_RATE, aPolicy);
	}

	/**
	 * Creates new operator with specified mutation rate and recurrency policy.
	 * 
	 * @param aMutationRate
	 * @param aPolicy
	 * @see RecurrencyPolicy
	 */
	public AddConnectionMutationOperator(double aMutationRate, RecurrencyPolicy aPolicy) {
		super(aMutationRate);
		policy = aPolicy;
	}

	/**
	 * Adds connections according to <a href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT </a> add
	 * connection mutation.
	 * 
	 * @param jgapConfig
	 * @param target chromosome material to mutate
	 * @param allelesToAdd <code>Set</code> contains <code>Allele</code> objects
	 * @param allelesToRemove <code>Set</code> contains <code>Allele</code> objects
	 * @see org.jgapcustomised.MutationOperator#mutate(org.jgapcustomised.Configuration,
	 *      org.jgapcustomised.ChromosomeMaterial, java.util.Set, java.util.Set)
	 */
	@Override
	protected void mutate(Configuration jgapConfig, final ChromosomeMaterial target, Set allelesToAdd, Set allelesToRemove) {
		if ((jgapConfig instanceof NeatConfiguration) == false)
			throw new AnjiRequiredException("com.anji.neat.NeatConfiguration");
		NeatConfiguration config = (NeatConfiguration) jgapConfig;

		// connection can mutate between any 2 neurons, excluding those neurons already removed
		List neuronList = NeatChromosomeUtility.getNeuronList(target.getAlleles());
		SortedMap conns = NeatChromosomeUtility.getConnectionMap(target.getAlleles());

		// Determine # connections to add and iterate randomly through alleles ...
		int maxConnectionsToAdd = (neuronList.size() * neuronList.size()) - conns.size();
		int numConnectionsToAdd = numMutations(config.getRandomGenerator(), maxConnectionsToAdd);

		addConnections(numConnectionsToAdd, config, neuronList, conns, allelesToAdd);
	}

	/**
	 * Given the collections of neurons and connections, returns the new connections that should be added, up to a max
	 * of numConnectionsToAdd.
	 * 
	 * @param numConnectionsToAdd
	 * @param config
	 * @param neuronList <code>List</code> contains <code>NeuronAllele</code> objects
	 * @param conns <code>SortedMap</code> contains <code>ConnectionAllele</code> objects; contains original alleles
	 *            plus new connection alleles added
	 * @param allelesToAdd <code>Set</code> contains <code>Allele</code> objects; contains new connection alleles added
	 */
	private void addConnections(int numConnectionsToAdd, NeatConfiguration config, List neuronList, SortedMap conns, Set allelesToAdd) {
		HashSet rejectedConnIds = new HashSet();

		for (int i = 0; i < numConnectionsToAdd; ++i) {
			ConnectionAllele newConn = null;
			NeuronAllele src = null;
			NeuronAllele dest = null;

			// ... until we find a new src and destination neuron that aren't connected and that we
			// haven't searched yet ...
			while (newConn == null) {
				int srcIdx = config.getRandomGenerator().nextInt(neuronList.size());
				int destIdx = config.getRandomGenerator().nextInt(neuronList.size());
				src = (NeuronAllele) neuronList.get(srcIdx);
				dest = (NeuronAllele) neuronList.get(destIdx);

				newConn = config.newConnectionAllele(src.getInnovationId(), dest.getInnovationId());
				if (conns.containsKey(newConn.getInnovationId()) || rejectedConnIds.contains(newConn.getInnovationId()))
					newConn = null;
			}

			// ... for which a mutation can occur
			if (connectionAllowed(src, dest, conns.values())) {
				conns.put(newConn.getInnovationId(), newConn);
				newConn.setToRandomValue(config.getRandomGenerator(), false);
				allelesToAdd.add(newConn);
			} else
				rejectedConnIds.add(newConn.getInnovationId());
		}
	}

	/**
	 * Given the collections of neurons and connections, returns the new connection that should be added.
	 * 
	 * @param config
	 * @param neuronList <code>List</code> contains <code>NeuronAllele</code> objects
	 * @param conns <code>SortedMap</code> contains <code>ConnectionAllele</code> objects; contains new connection
	 *            allele added
	 * @param allelesToAdd <code>Set</code> contains <code>Allele</code> objects; contains new connection allele added
	 *            TOTO - allele (callers)
	 */
	public void addSingleConnection(NeatConfiguration config, List neuronList, SortedMap conns, Set allelesToAdd) {
		HashSet rejectedConnIds = new HashSet();
		boolean isAdded = false;
		int maxConnections = (neuronList.size() * neuronList.size()) - conns.size();
		while (!isAdded && (rejectedConnIds.size() < maxConnections)) {
			ConnectionAllele newConn = null;
			NeuronAllele src = null;
			NeuronAllele dest = null;

			// ... until we find a new src and destination neuron that aren't connected and that we
			// haven't searched yet ...
			while (newConn == null) {
				int srcIdx = config.getRandomGenerator().nextInt(neuronList.size());
				int destIdx = config.getRandomGenerator().nextInt(neuronList.size());
				src = (NeuronAllele) neuronList.get(srcIdx);
				dest = (NeuronAllele) neuronList.get(destIdx);

				newConn = config.newConnectionAllele(src.getInnovationId(), dest.getInnovationId());
				if (conns.containsKey(newConn.getInnovationId()) || rejectedConnIds.contains(newConn.getInnovationId()))
					newConn = null;
			}

			// ... for which a mutation can occur
			if (connectionAllowed(src, dest, conns.values())) {
				conns.put(newConn.getInnovationId(), newConn);
				newConn.setToRandomValue(config.getRandomGenerator(), false);
				allelesToAdd.add(newConn);
				isAdded = true;
			} else
				rejectedConnIds.add(newConn.getInnovationId());
		}
	}

	/**
	 * @param src
	 * @param dest
	 * @param conns <code>SortedMap</code> contains key <code>Long</code> id, value <code>ConnectionAllele</code>
	 *            objects
	 * @return true of connection between <code>src</code> and <code>dest</code> is allowed according to recurrency
	 *         policy; false otherwise.
	 * @see NeatChromosomeUtility#neuronsAreConnected(Long, Long, Collection)
	 */
	public boolean connectionAllowed(NeuronAllele src, NeuronAllele dest, Collection<ConnectionAllele> conns) {
		if (RecurrencyPolicy.DISALLOWED.equals(policy)) {
			if (dest.isType(NeuronType.INPUT) || src.isType(NeuronType.OUTPUT))
				return false;
			boolean connected = NeatChromosomeUtility.neuronsAreConnected(dest.getInnovationId(), src.getInnovationId(), conns);
			return !connected;
		}
		//return (dest.getActivationType().equals(LinearActivationFunction.NAME));
		return true;
	}
}
