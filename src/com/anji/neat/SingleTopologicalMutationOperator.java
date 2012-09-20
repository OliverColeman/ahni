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
 * created by Philip Tucker on Apr 21, 2004
 */
package com.anji.neat;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import org.jgapcusomised.ChromosomeMaterial;
import org.jgapcusomised.Configuration;
import org.jgapcusomised.MutationOperator;

import com.anji.integration.AnjiRequiredException;
import com.anji.nn.RecurrencyPolicy;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * @author Philip
 */
public class SingleTopologicalMutationOperator extends MutationOperator implements Configurable {

	private final static float DEFAULT_MUTATION_RATE = calculateMutationRate(AddConnectionMutationOperator.DEFAULT_MUTATE_RATE,
	        AddNeuronMutationOperator.DEFAULT_MUTATE_RATE);

	private AddConnectionMutationOperator addConnOp;

	private AddNeuronMutationOperator addNeuronOp;

	private float addConnRatio = calculateAddConnRatio(AddConnectionMutationOperator.DEFAULT_MUTATE_RATE, AddNeuronMutationOperator.DEFAULT_MUTATE_RATE);

	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init(Properties props) throws Exception {
		addConnOp = (AddConnectionMutationOperator) props.singletonObjectProperty(AddConnectionMutationOperator.class);
		addNeuronOp = (AddNeuronMutationOperator) props.singletonObjectProperty(AddNeuronMutationOperator.class);
		float addConnRate = addConnOp.getMutationRate();
		float addNeuronRate = addNeuronOp.getMutationRate();
		addConnRatio = calculateAddConnRatio(addConnRate, addNeuronRate);
		setMutationRate(calculateMutationRate(addConnRate, addNeuronRate));
	}

	private static float calculateAddConnRatio(float addConnMutationRate, float addNeuronMutationRate) {
		return (addConnMutationRate - (addConnMutationRate * addNeuronMutationRate * 0.5f)) / (addConnMutationRate + addNeuronMutationRate);
	}

	private static float calculateMutationRate(float addConnMutationRate, float addNeuronMutationRate) {
		return addConnMutationRate + addNeuronMutationRate - (addConnMutationRate * addNeuronMutationRate);
	}
	
	/**
	 * should call <code>init()</code> after this constructor
	 */
	public SingleTopologicalMutationOperator() {
		super(DEFAULT_MUTATION_RATE);
	}

	/**
	 * ctor
	 * 
	 * @param addConnMutationRate
	 * @param addNeuronMutationRate
	 * @param aPolicy
	 */
	public SingleTopologicalMutationOperator(float addConnMutationRate, float addNeuronMutationRate, RecurrencyPolicy aPolicy) {
		super(calculateMutationRate(addConnMutationRate, addNeuronMutationRate));
		addConnRatio = calculateAddConnRatio(addConnMutationRate, addNeuronMutationRate);
		addConnOp = new AddConnectionMutationOperator(addConnMutationRate, aPolicy);
		addNeuronOp = new AddNeuronMutationOperator(addNeuronMutationRate);
	}

	/**
	 * @see org.jgapcusomised.MutationOperator#mutate(org.jgapcusomised.Configuration,
	 *      org.jgapcusomised.ChromosomeMaterial, java.util.Set, java.util.Set)
	 */
	protected void mutate(Configuration jgapConfig, ChromosomeMaterial target, Set allelesToAdd, Set allelesToRemove) {
		if ((jgapConfig instanceof NeatConfiguration) == false)
			throw new AnjiRequiredException("com.anji.neat.NeatConfiguration");
		
		NeatConfiguration config = (NeatConfiguration) jgapConfig;

		Random rand = config.getRandomGenerator();
		if (doesMutationOccur(rand)) {
			SortedSet alleles = target.getAlleles();
			if (rand.nextDouble() < addConnRatio) {
				List neuronList = NeatChromosomeUtility.getNeuronList(alleles);
				SortedMap conns = NeatChromosomeUtility.getConnectionMap(alleles);
				addConnOp.addSingleConnection(config, neuronList, conns, allelesToAdd);
			} else {
				List connList = NeatChromosomeUtility.getConnectionList(alleles);
				Collections.shuffle(connList, rand);
				Iterator iter = connList.iterator();
				boolean isAdded = false;
				while (iter.hasNext() && !isAdded) {
					ConnectionAllele oldConnectAllele = (ConnectionAllele) iter.next();
					isAdded = addNeuronOp.addNeuronAtConnection(config, NeatChromosomeUtility.getNeuronMap(alleles), oldConnectAllele, allelesToAdd,
					        allelesToRemove);
				}
			}
		}
	}
}
