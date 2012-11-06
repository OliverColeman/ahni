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

import com.anji.nn.activationfunction.ActivationFunction;

/**
 * Gene corresponding to NEAT node gene according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>
 * 
 * @author Philip Tucker
 */
public class NeuronAllele extends Allele {

	private NeuronGene neuronGene;

	/**
	 * for hibernate
	 */
	private NeuronAllele() {
		super();
	}

	/**
	 * @param aNeuronGene
	 */
	public NeuronAllele(NeuronGene aNeuronGene) {
		super(aNeuronGene);

		neuronGene = aNeuronGene;
	}

	/**
	 * @see org.jgapcustomised.Allele#cloneAllele()
	 */
	public Allele cloneAllele() {
		return new NeuronAllele(neuronGene);
	}

	/**
	 * @see Allele#setToRandomValue(Random)
	 */
	public void setToRandomValue(Random a_numberGenerator) {
		// noop
	}

	/**
	 * @param aTarget should be <code>NeuronAllele</code> with same gene TODO - activation type and slope
	 * @see org.jgapcustomised.Allele#distance(org.jgapcustomised.Allele)
	 */
	public double distance(Allele aTarget) {
		return 0;
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

}
