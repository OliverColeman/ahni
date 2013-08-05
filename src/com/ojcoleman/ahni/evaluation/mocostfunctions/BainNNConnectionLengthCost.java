package com.ojcoleman.ahni.evaluation.mocostfunctions;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.bain.base.SynapseCollection;

public class BainNNConnectionLengthCost extends BulkFitnessFunctionMT {
	@Override
	public boolean fitnessValuesStable() {
		return true;
	}
	
	@Override
	protected double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		if (substrate instanceof BainNN) {
			double tcl = ((BainNN) substrate).getSumOfSquaredConnectionLengths();
			return 1.0 / (1+tcl);
		}
		return 0;
	}
}
