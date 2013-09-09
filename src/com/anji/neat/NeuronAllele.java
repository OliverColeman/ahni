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
 * 
 * Modified by Oliver Coleman 2013-04-25 - Added bias
 */
package com.anji.neat;

import java.text.DecimalFormat;
import java.util.Random;

import org.jgapcustomised.Allele;

import com.anji.nn.activationfunction.ActivationFunction;

/**
 * Gene corresponding to NEAT node gene according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>
 * 
 * @author Philip Tucker
 */
public class NeuronAllele extends Allele {
	private static final DecimalFormat nf = new DecimalFormat(" 0.0000;-0.0000");

	private NeuronGene neuronGene;
	
	private double bias = ConnectionAllele.DEFAULT_WEIGHT;

	/**
	 * for hibernate
	 */
	private NeuronAllele() {
		super();
	}

	/**
	 * @param aNeuronGene
	 */
	public NeuronAllele(NeuronGene aNeuronGene, double bias) {
		super(aNeuronGene);
		
		neuronGene = aNeuronGene;
		this.bias = bias;
	}

	/**
	 * @see org.jgapcustomised.Allele#cloneAllele()
	 */
	public Allele cloneAllele() {
		NeuronAllele allele = new NeuronAllele(neuronGene, bias);
		return allele;
	}

	/**
	 * Set bias to random value from a Gaussian distribution determined by {@link ConnectionAllele#RANDOM_STD_DEV}
	 * 
	 * @param a_numberGenerator
	 * @param onlyPerturbFromCurrentValue if true then the bias is perturbed from its current value.
	 */
	public void setToRandomValue(Random a_numberGenerator, boolean onlyPerturbFromCurrentValue) {
		if (onlyPerturbFromCurrentValue)
			bias += a_numberGenerator.nextGaussian() * ConnectionAllele.RANDOM_STD_DEV;
			//bias += (a_numberGenerator.nextBoolean() ? 1 : -1) * a_numberGenerator.nextDouble() * ConnectionAllele.RANDOM_STD_DEV;
		else
			bias = a_numberGenerator.nextGaussian() * ConnectionAllele.RANDOM_STD_DEV;
			//bias = (a_numberGenerator.nextBoolean() ? 1 : -1) * a_numberGenerator.nextDouble() * ConnectionAllele.RANDOM_STD_DEV;
	}

	/**
	 * @param target should be <code>NeuronAllele</code> with same gene TODO - activation type and slope
	 * @see org.jgapcustomised.Allele#distance(org.jgapcustomised.Allele)
	 */
	public double distance(Allele target) {
		assert target.getInnovationId().equals(getInnovationId()) : "Should not compute distance for alleles of different gene.";
		return Math.abs(bias - ((NeuronAllele) target).getBias());
	}

	/**
	 * @return neuron type
	 * @see NeuronGene#getType()
	 */
	public NeuronType getType() {
		return neuronGene.getType();
	}

	/**
	 * @param aType
	 * @return true if <code>aType</code> matches
	 * @see NeuronGene#isType(NeuronType)
	 */
	public boolean isType(NeuronType aType) {
		return neuronGene.isType(aType);
	}

	/**
	 * @return activation type
	 * @see NeuronGene#getActivationType()
	 */
	public String getActivationType() {
		return neuronGene.getActivationType();
	}
	
	/**
	 * @return connection bias
	 */
	public double getBias() {
		return bias;
	}

	/**
	 * @param aBias new connection bias
	 */
	public void setBias(double aBias) {
		bias = aBias;
	}

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return "N-" + neuronGene.toString() + " [" + nf.format(bias) + "]";
	}
	
	@Override
	public boolean isEquivalent(Allele otherAllele) {
		if (!(otherAllele instanceof NeuronAllele))
			return false;
		NeuronAllele other = (NeuronAllele) otherAllele;
		return getType().equals(other.getType()) && getActivationType().equals(other.getActivationType()) && bias == other.bias;
	}

	/**
	 * Gets the bias value.
	 */
	@Override
	public double getValue() {
		return bias;
	}

	/**
	 * Sets the bias value.
	 */
	@Override
	public void setValue(double aValue) {
		this.bias = aValue;
	}
}
