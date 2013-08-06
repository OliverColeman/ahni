package com.anji.neat;

import java.util.Random;

import org.jgapcustomised.MutationOperator;

/**
 * <p>For mutation operators that use a normal (Gaussian) distribution to determine the probability and/or number of mutations to perform.</p>
 */
public abstract class MutationOperatorNormalDistribution extends MutationOperator {
	public MutationOperatorNormalDistribution(double aMutationRate) {
		super(aMutationRate);
	}

	/**
	 * @return int the number of mutations that should occur (given the mutation rate for this operator).
	 * @param rand The RNG to use.
	 * @param numOpportunities Not used (see {@link MutationOperator#numMutations(Random, int)}.
	 */
	@Override
	protected int numMutations(Random rand, int numOpportunities) {
		return (int) Math.round(Math.abs(rand.nextGaussian()) * this.getMutationRate());
	}
}
