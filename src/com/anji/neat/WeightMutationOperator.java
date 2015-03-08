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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


import org.jgapcustomised.Allele;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.MutationOperator;

import com.anji.integration.AnjiRequiredException;
import com.anji.util.Configurable;
import com.anji.util.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * Implements NEAT perturb connection weight mutation according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>.
 * 
 * @author Philip Tucker
 */
public class WeightMutationOperator extends MutationOperator implements Configurable {
	/**
	 * properties key, perturb weight mutation rate
	 */
	public static final String WEIGHT_MUTATE_RATE_KEY = "weight.mutation.rate";
	/**
	 * properties key, standard deviation of perturb weight mutation, default is 1.
	 */
	public static final String WEIGHT_MUTATE_STD_DEV_KEY = "weight.mutation.std.dev";
	/**
	 * properties key, the amount to perturb weights by when generating the initial population. Default is weight.mutation.std.dev.
	 */
	public static final String WEIGHT_MUTATE_STD_DEV_INITIAL_KEY = "weight.mutation.std.dev.initial";
	/**
	 * default mutation rate
	 */
	public static final double DEFAULT_MUTATE_RATE = 0.1;
	/**
	 * default standard deviation for weight delta
	 */
	public final static double DEFAULT_STD_DEV = 1.0;
	private double stdDev = DEFAULT_STD_DEV;

	/**
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init(Properties props) throws Exception {
		setMutationRate(props.getDoubleProperty(WEIGHT_MUTATE_RATE_KEY, DEFAULT_MUTATE_RATE));
		stdDev = props.getDoubleProperty(WEIGHT_MUTATE_STD_DEV_KEY, DEFAULT_STD_DEV);
		
		ConnectionAllele.RANDOM_STD_DEV = props.getDoubleProperty(WEIGHT_MUTATE_STD_DEV_INITIAL_KEY, stdDev);
		ConnectionAllele.RANDOM_STD_DEV_INITIAL = props.getDoubleProperty(WEIGHT_MUTATE_STD_DEV_INITIAL_KEY, stdDev * 0.1);
	}

	/**
	 * @see MutationOperator#MutationOperator(double)
	 */
	public WeightMutationOperator() {
		super(DEFAULT_MUTATE_RATE);
	}
	
	static boolean dbg = false;
	
	/**
	 * Removes from <code>genesToAdd</code> and adds to <code>genesToRemove</code> all connection genes that are
	 * modified.
	 * 
	 * @param jgapConfig The current active genetic configuration.
	 * @param target chromosome material to mutate
	 * @param genesToAdd <code>Set</code> contains <code>Gene</code> objects
	 * @param genesToRemove <code>Set</code> contains <code>Gene</code> objects
	 */
	protected void mutate(Configuration jgapConfig, final ChromosomeMaterial target, Set<Allele> genesToAdd, Set<Allele> genesToRemove) {
		if ((jgapConfig instanceof NeatConfiguration) == false) {
			throw new AnjiRequiredException(NeatConfiguration.class.toString());
		}
		NeatConfiguration config = (NeatConfiguration) jgapConfig;
		
		// If bias is provided via an input to the network then just get connection alleles, otherwise get connection and neuron alleles.
		List<Allele> alleles = config.biasViaInput() ? NeatChromosomeUtility.getConnectionList(target.getAlleles()) : new ArrayList(target.getAlleles());
		Collections.shuffle(alleles, config.getRandomGenerator());

		if (!dbg) {
			dbg=true;
			System.out.println((config.biasViaInput() ? "" : "not ") + "bias via input");
		}


		int numMutations = numMutations(config.getRandomGenerator(), alleles.size());
		Iterator<Allele> iter = alleles.iterator();
		int i = 0;
		
		while ((i < numMutations) && iter.hasNext()) {
			Allele origAllele = iter.next();
			
			// TODO CHECK IF THIS IS A NEW CONNECTION AND DON'T MUTATE IF SO.
			
			boolean isNeuron = origAllele instanceof NeuronAllele;
			boolean isConnection = origAllele instanceof ConnectionAllele;
			if (isNeuron || isConnection) {
				double currentValue = isConnection ? ((ConnectionAllele) origAllele).getWeight() : ((NeuronAllele) origAllele).getBias();
				// Treat neuron bias values of 0 like a connection that doesn't exist. 
				if (!isNeuron || currentValue != 0) {
					double nextValue = currentValue + config.getRandomGenerator().nextGaussian() * getStdDev();
					//double nextValue = currentValue + (config.getRandomGenerator().nextBoolean() ? 1 : -1) * config.getRandomGenerator().nextDouble() * getStdDev();
		
					if (nextValue > config.getMaxConnectionWeight()) {
						nextValue = config.getMaxConnectionWeight();
					} else if (nextValue < config.getMinConnectionWeight()) {
						nextValue = config.getMinConnectionWeight();
					}
		
					Allele newAllele = origAllele.cloneAllele();
					if (isConnection) {
						((ConnectionAllele) newAllele).setWeight(nextValue);
					}
					else {
						((NeuronAllele) newAllele).setBias(nextValue);
					}
					
					genesToRemove.add(origAllele);
					genesToAdd.add(newAllele);
					
					i++;
				}
			}
		}
	}

	/**
	 * @return standard deviation for weight delta
	 */
	public double getStdDev() {
		return stdDev;
	}
}
