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

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;

import org.jgapcustomised.Allele;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;

import com.anji.integration.AnjiRequiredException;
import com.anji.nn.RecurrencyPolicy;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Implements NEAT add connection mutation inspired by <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>.
 * 
 * @author Philip Tucker, Oliver Coleman
 */
public class AddConnectionMutationOperator extends MutationOperatorMultiple implements Configurable {

	/**
	 * properties key, add connection mutation rate
	 */
	public static final String ADD_CONN_MUTATE_RATE_KEY = "add.connection.mutation.rate";

	/**
	 * default mutation rate
	 */
	public static final double DEFAULT_MUTATE_RATE = 0.01f;
	
	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	@Override
	public void init(Properties props) throws Exception {
		setMutationRate(props.getDoubleProperty(ADD_CONN_MUTATE_RATE_KEY, DEFAULT_MUTATE_RATE));
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
	protected void mutate(Configuration jgapConfig, final ChromosomeMaterial target, Set<Allele> allelesToAdd, Set<Allele> allelesToRemove) {
		if ((jgapConfig instanceof NeatConfiguration) == false)
			throw new AnjiRequiredException("com.anji.neat.NeatConfiguration");
		NeatConfiguration config = (NeatConfiguration) jgapConfig;
		List<NeuronAllele> neuronList = NeatChromosomeUtility.getNeuronList(target.getAlleles());
		
		int numConnectionsToAdd = numMutations(config.getRandomGenerator(), 0);
		
		int maxConnectionsPossible = (neuronList.size() + (config.biasViaInput() ? 0 : 1)) * neuronList.size();
		if (numConnectionsToAdd > maxConnectionsPossible) {
			numConnectionsToAdd = maxConnectionsPossible;
		}

		if (numConnectionsToAdd > 0) {
			// connection can mutate between any 2 neurons, excluding those neurons already removed
			SortedMap<Long, ConnectionAllele> conns = NeatChromosomeUtility.getConnectionMap(target.getAlleles());

			addConnections(numConnectionsToAdd, config, neuronList, conns, allelesToAdd, allelesToRemove);
		}
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
	public void addConnections(int numConnectionsToAdd, NeatConfiguration config, List<NeuronAllele> neuronList, SortedMap<Long, ConnectionAllele> conns, Set<Allele> allelesToAdd, Set<Allele> allelesToRemove) {
		HashSet<Long> rejectedConnIds = new HashSet<Long>();
		HashSet<Long> neuronsWithBiasAdded = new HashSet<Long>();
		boolean bvi = config.biasViaInput();

		for (int i = 0; i < numConnectionsToAdd; ++i) {
			Allele newAllele = null;
			NeuronAllele src = null;
			NeuronAllele dest = null;

			// ... until we find a new src and destination neuron that aren't connected and that we
			// haven't searched yet ...
			int attempts = 0;
			while (newAllele == null && attempts < 10000) {
				// If we're not using a real bias input then we add an extra 
				// index here to simulate the possibility of adding a connection from a bias input neuron.
				int srcIdx = config.getRandomGenerator().nextInt(neuronList.size() + (bvi ? 0 : 1));
				int destIdx = config.getRandomGenerator().nextInt(neuronList.size());
				dest = neuronList.get(destIdx);
				
				// If src is not a simulated bias input neuron.
				if (srcIdx < neuronList.size()) {
					src = neuronList.get(srcIdx);
					
					newAllele = config.newConnectionAllele(src.getInnovationId(), dest.getInnovationId());
					if (conns.containsKey(newAllele.getInnovationId()) || rejectedConnIds.contains(newAllele.getInnovationId())) {
						newAllele = null;
					}
					else {
						// ... for which a mutation can occur
						if (NeatChromosomeUtility.connectionAllowed(config, src, dest, conns.values())) {
							conns.put(newAllele.getInnovationId(), (ConnectionAllele) newAllele);
							newAllele.setToRandomValue(config.getRandomGenerator(), false);
							allelesToAdd.add(newAllele);
						} else {
 							rejectedConnIds.add(newAllele.getInnovationId());
 							newAllele = null;
						}
					}
				}
				// src is a simulated bias input neuron.
				else  {
					// If dest isn't an input neuron and doesn't already have a bias value set (equivalent to a connection from a bias neuron).
					if (!dest.getType().equals(NeuronType.INPUT) && dest.getBias() == 0 && !neuronsWithBiasAdded.contains(dest.getInnovationId())) {
						newAllele = dest.cloneAllele();
						newAllele.setToRandomValue(config.getRandomGenerator(), false);
						
						neuronsWithBiasAdded.add(dest.getInnovationId());
						
						allelesToRemove.add(dest);
						allelesToAdd.add(newAllele);
					}
				}
				
				attempts++;
			}
		}
	}
}
