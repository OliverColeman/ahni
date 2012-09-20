/**
 * ----------------------------------------------------------------------------| Created on Apr
 * 12, 2003
 */
package com.anji.util;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.jgapcusomised.BulkFitnessFunction;
import org.jgapcusomised.Chromosome;

/**
 * @author Philip Tucker
 */
public class DummyBulkFitnessFunction implements BulkFitnessFunction {

	private Random rand = null;
	
	/**
	 * ctor
	 * @param newRand
	 */
	public DummyBulkFitnessFunction( Random newRand ) {
		rand = newRand;
	}
	
	/**
	 * ctor
	 */
	public DummyBulkFitnessFunction() {
		rand = new Random();
	}
	
	private void evaluate( Chromosome a_subject ) {
		a_subject.setFitnessValue( rand.nextInt( 100 ) );
	}
	
	/**
	 * @see org.jgapcusomised.BulkFitnessFunction#evaluate(java.util.List)
	 */
	public void evaluate( List aSubjects ) {
		Iterator it = aSubjects.iterator();
		while ( it.hasNext() ) {
			Chromosome c = (Chromosome) it.next();
			evaluate( c );
		}
	}
	
	/**
	 * @see org.jgapcusomised.BulkFitnessFunction#getMaxFitnessValue()
	 */
	public int getMaxFitnessValue() {
		return 100;
	}
	
	
	public float getPerformanceFromFitnessValue(int fitness) {
		return (float) fitness / getMaxFitnessValue();
	}
	
	public boolean endRun() {
		return false;
	}
	@Override
    public void dispose() {
	}
}
