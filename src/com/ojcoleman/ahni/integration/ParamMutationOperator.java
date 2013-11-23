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
package com.ojcoleman.ahni.integration;

import java.util.Set;

import org.jgapcustomised.Allele;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.MutationOperator;

import com.anji.integration.AnjiRequiredException;
import com.anji.neat.NeatConfiguration;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Implements NEAT perturb connection weight mutation according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>.
 * 
 * @author Philip Tucker
 */
public class ParamMutationOperator extends MutationOperator implements Configurable {
	/**
	 * properties key, perturb weight mutation rate
	 */
	public static final String PARAM_MUTATE_RATE_KEY = "param.mutation.rate";
	/**
	 * properties key, standard deviation of perturb weight mutation, default is 1.
	 */
	public static final String PARAM_MUTATE_STD_DEV_KEY = "param.mutation.std.dev";
	/**
	 * properties key, the amount to perturb weights by when generating the initial population. Default is
	 * weight.mutation.std.dev.
	 */
	public static final String PARAM_MUTATE_STD_DEV_INITIAL_KEY = "param.mutation.std.dev.initial";
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
		setMutationRate(props.getDoubleProperty(PARAM_MUTATE_RATE_KEY, DEFAULT_MUTATE_RATE));
		stdDev = props.getDoubleProperty(PARAM_MUTATE_STD_DEV_KEY, DEFAULT_STD_DEV);
		ParamAllele.RANDOM_STD_DEV = props.getDoubleProperty(PARAM_MUTATE_STD_DEV_INITIAL_KEY, stdDev);
	}

	/**
	 * @see MutationOperator#MutationOperator(double)
	 */
	public ParamMutationOperator() {
		super(DEFAULT_MUTATE_RATE);
	}

	/**
	 * Mutates (some of) the {@link ParamAllele}s in the given target.
	 */
	protected void mutate(Configuration jgapConfig, final ChromosomeMaterial target, Set<Allele> allelesToAdd, Set<Allele> allelesToRemove) {
		if ((jgapConfig instanceof NeatConfiguration) == false) {
			throw new AnjiRequiredException(NeatConfiguration.class.toString());
		}
		NeatConfiguration config = (NeatConfiguration) jgapConfig;
		for (Allele a : target.getAlleles()) {
			if (a instanceof ParamAllele && doesMutationOccur(config.getRandomGenerator())) {
				ParamAllele allele = (ParamAllele) a;
				ParamGene gene = allele.getGene();
				double nextValue = allele.getValue() + config.getRandomGenerator().nextGaussian() * getStdDev();
				// double nextValue = currentValue + (config.getRandomGenerator().nextBoolean() ? 1 : -1) *
				// config.getRandomGenerator().nextDouble() * getStdDev();

				if (nextValue > gene.getMaxValue()) {
					nextValue = gene.getMaxValue();
				} else if (nextValue < gene.getMinValue()) {
					nextValue = gene.getMinValue();
				}
				
				//if (Math.random() < 0.01) System.err.println(gene.getCollection().getLabel() + " [" + gene.getMinValue() + ", " + gene.getMaxValue() + "] " + nextValue);

				Allele newAllele = allele.cloneAllele();
				newAllele.setValue(nextValue);

				allelesToRemove.add(allele);
				allelesToAdd.add(newAllele);
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
