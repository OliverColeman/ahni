package com.ojcoleman.ahni.evaluation.mocostfunctions;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.bain.base.SynapseCollection;

public class BainNNConnectionCountCost extends BulkFitnessFunctionMT {
	@Override
	public int getMaxFitnessValue() {
		return 1000000;
	}

	@Override
	protected int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		if (substrate instanceof BainNN) {
			BainNN nn = (BainNN) substrate;
			double[] weights = nn.getNeuralNetwork().getSynapses().getEfficacies();
			int count = 0;
			for (int c = 0; c < weights.length; c++) {
				if (weights[c] != 0)
					count++;
			}
			return getMaxFitnessValue() / (1 + (count * count));
		}
		return 0;
	}
}
