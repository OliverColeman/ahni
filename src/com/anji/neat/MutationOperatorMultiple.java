package com.anji.neat;

import java.util.Random;

import org.jgapcustomised.MutationOperator;

/**
 * <p>
 * For mutation operators that may perform one or potentially more mutations (if the mutation rate is > 1) based solely
 * on the mutation rate (rather than some notion of the number of opportunities for mutation in a given individual).
 * </p>
 */
public abstract class MutationOperatorMultiple extends MutationOperator {
	public MutationOperatorMultiple(double aMutationRate) {
		super(aMutationRate);
	}

	/**
	 * @return int the number of mutations that should occur (given the mutation rate for this operator).
	 * @param rand The RNG to use.
	 * @param numOpportunities Not used (see {@link MutationOperator#numMutations(Random, int)}.
	 */
	@Override
	protected int numMutations(Random rand, int numOpportunities) {
		double m = this.getMutationRate();
		int i = (int) Math.floor(m);
		double f = m - i;
		double r = rand.nextDouble();
		int count = (int) Math.floor(r * (i + 1)) + (r < f ? 1 : 0);
		return count;
	}
}
