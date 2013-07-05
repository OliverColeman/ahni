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

import java.util.Random;

import org.jgapcustomised.Allele;

/**
 * Gene corresponding to NEAT connection gene according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>
 * 
 * @author Philip Tucker
 */
public class ConnectionAllele extends Allele {
	private ConnectionGene connectionGene;

	/**
	 * default connection weight
	 */
	public final static double DEFAULT_WEIGHT = 0;
	/**
	 * Standard deviation of perturbations to weight values. This is generally set by WeightMutationOperator according to the loaded properties file.
	 */
	public static double RANDOM_STD_DEV = 1;
	private double weight = DEFAULT_WEIGHT;

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return connectionGene.toString() + " [" + weight + "]";
	}

	/**
	 * for hibernate
	 */
	private ConnectionAllele() {
		super();
	}

	/**
	 * @param aConnectionGene
	 */
	public ConnectionAllele(ConnectionGene aConnectionGene) {
		super(aConnectionGene);
		connectionGene = aConnectionGene;
	}

	/**
	 * @see org.jgapcustomised.Allele#cloneAllele()
	 */
	public Allele cloneAllele() {
		ConnectionAllele allele = new ConnectionAllele(connectionGene);
		allele.setWeight(weight);
		return allele;
	}

	/**
	 * @param target should be <code>ConnectionAllele</code> with same gene
	 * @return <code>double</code> compatibility distance based on weight; always positive
	 * @see Allele#distance(Allele)
	 */
	public double distance(Allele target) {
		// TODO - removed to help performance
		// if ( target.getInnovationId().equals( getInnovationId() ) == false )
		// throw new Exception( "should not compute distance for alleles of different gene" );
		return Math.abs(weight - ((ConnectionAllele) target).getWeight());
	}

	/**
	 * Set weight to random value from a Gaussian distribution determined by {@link #RANDOM_STD_DEV}
	 * 
	 * @param a_numberGenerator
	 * @param onlyPerturbFromCurrentValue if true then the weight is perturbed from its current value.
	 */
	public void setToRandomValue(Random a_numberGenerator, boolean onlyPerturbFromCurrentValue) {
		if (onlyPerturbFromCurrentValue)
			weight += a_numberGenerator.nextGaussian() * RANDOM_STD_DEV;
		else
			weight = a_numberGenerator.nextGaussian() * RANDOM_STD_DEV;
	}

	/**
	 * @return connection weight
	 */
	public double getWeight() {
		return weight;
	}

	/**
	 * @param aWeight new connection weight
	 */
	public void setWeight(double aWeight) {
		weight = aWeight;
	}

	/**
	 * @return src neuron ID
	 * @see ConnectionGene#getSrcNeuronId()
	 */
	public Long getSrcNeuronId() {
		return connectionGene.getSrcNeuronId();
	}

	/**
	 * @return dest neuron ID
	 * @see ConnectionGene#getDestNeuronId()
	 */
	public Long getDestNeuronId() {
		return connectionGene.getDestNeuronId();
	}

	@Override
	public boolean isEquivalent(Allele otherAllele) {
		if (!(otherAllele instanceof ConnectionAllele))
			return false;
		ConnectionAllele other = (ConnectionAllele) otherAllele;
		return getSrcNeuronId() == other.getSrcNeuronId() && getDestNeuronId() == other.getDestNeuronId() && weight == other.weight;
	}
}
