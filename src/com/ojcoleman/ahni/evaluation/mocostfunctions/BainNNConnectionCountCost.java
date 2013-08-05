package com.ojcoleman.ahni.evaluation.mocostfunctions;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.bain.base.SynapseCollection;

public class BainNNConnectionCountCost extends BulkFitnessFunctionMT {
	/**
	 * The target proportion of synapses based on maximum possible number of synapses (calculated as number of neurons squared).
	 * Default is 0.1.
	 */
	public static String TARGET = "fitness.function.connection_count_cost.target";
	
	double target;
	
	@Override
	public void init(Properties props) {
		target = props.getDoubleProperty(TARGET, 0);
	}

	@Override
	public boolean fitnessValuesStable() {
		return true;
	}
	
	@Override
	protected double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		if (substrate instanceof BainNN) {
			BainNN nn = (BainNN) substrate;
			double[] weights = nn.getNeuralNetwork().getSynapses().getEfficacies();
			int count = 0;
			for (int c = 0; c < weights.length; c++) {
				if (weights[c] != 0)
					count++;
			}
			double targetCount = nn.getNeuronCount() * nn.getNeuronCount() * target;
			return 1.0 / (1.0 + Math.abs(count - targetCount));
		}
		return 0;
	}
}
