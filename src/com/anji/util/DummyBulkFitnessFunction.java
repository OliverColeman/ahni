/**
 * ----------------------------------------------------------------------------| Created on Apr
 * 12, 2003
 */
package com.anji.util;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;


import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;

import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;

/**
 * @author Philip Tucker
 */
public class DummyBulkFitnessFunction implements BulkFitnessFunction {

	private Random rand = null;

	/**
	 * ctor
	 * 
	 * @param newRand
	 */
	public DummyBulkFitnessFunction(Random newRand) {
		rand = newRand;
	}

	/**
	 * ctor
	 */
	public DummyBulkFitnessFunction() {
		rand = new Random();
	}

	private void evaluate(Chromosome a_subject) {
		a_subject.setFitnessValue(rand.nextInt(100));
	}

	/**
	 * @see org.jgapcustomised.BulkFitnessFunction#evaluate(java.util.List)
	 */
	public void evaluate(List<Chromosome> aSubjects) {
		Iterator it = aSubjects.iterator();
		while (it.hasNext()) {
			Chromosome c = (Chromosome) it.next();
			evaluate(c);
		}
	}

	/**
	 * @see org.jgapcustomised.BulkFitnessFunction#getMaxFitnessValue()
	 */
	public int getMaxFitnessValue() {
		return 100;
	}

	public double getPerformanceFromFitnessValue(int fitness) {
		return (double) fitness / getMaxFitnessValue();
	}

	public boolean endRun() {
		return false;
	}

	@Override
	public void dispose() {
	}

	@Override
	public void evolutionFinished(HyperNEATEvolver evolver) {
	}
}
