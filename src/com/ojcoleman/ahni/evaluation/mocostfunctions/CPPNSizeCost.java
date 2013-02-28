package com.ojcoleman.ahni.evaluation.mocostfunctions;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;

public class CPPNSizeCost extends BulkFitnessFunctionMT {
	@Override
	public int getMaxFitnessValue() {
		return 1000000;
	}

	@Override
	protected int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return getMaxFitnessValue() / (1 + (genotype.getAlleles().size() * genotype.getAlleles().size()));
	}
}
