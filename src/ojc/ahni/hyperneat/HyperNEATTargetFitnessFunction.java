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
 * created by Philip Tucker
 */
package ojc.ahni.hyperneat;

import java.util.*;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.*;
import com.anji.util.*;

/**
 * Determines fitness based on how close <code>Activator</code> output is to a target.
 * 
 * @author Philip Tucker
 */
public class HyperNEATTargetFitnessFunction implements BulkFitnessFunction, Configurable {

	private static Logger logger = Logger.getLogger(HyperNEATTargetFitnessFunction.class);

	private final static String ADJUST_FOR_NETWORK_SIZE_FACTOR_KEY = "fitness.function.adjust.for.network.size.factor";

	private double adjustForNetworkSizeFactor = 0.0f;

	/**
	 * properties key, file containing stimuli
	 */
	public final static String STIMULI_FILE_NAME_KEY = "stimuli.file";

	/**
	 * properties key, file containing output targets
	 */
	public final static String TARGETS_FILE_NAME_KEY = "targets.file";

	private final static String TARGETS_RANGE_KEY = "targets.range";

	/**
	 * dimension # training sets by dim stimuli
	 */
	private double[][][] stimuli;

	/**
	 * dimension # training sets by dim response
	 */
	private double[][][] targets;

	/**
	 * dimensions of each layer in the network
	 */
	private int width, height;

	private double targetRange = 0;

	private int maxFitnessValue;

	private ActivatorTranscriber activatorFactory;

	private Random random;

	private final static boolean SUM_OF_SQUARES = false;

	private final static int MAX_FITNESS = 1000000;

	private int numThreads;

	private Evaluator[] evaluators;
	private int evaluatorsFinishedCount;

	private Iterator<Chromosome> chromosomesIterator;

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param props configuration parameters
	 */
	public void init(com.anji.util.Properties props) {
		try {
			random = ((Randomizer) props.singletonObjectProperty(Randomizer.class)).getRand();
			activatorFactory = (ActivatorTranscriber) props.singletonObjectProperty(ActivatorTranscriber.class);
			height = props.getIntProperty(HyperNEATTranscriberGridNet.HYPERNEAT_HEIGHT);
			width = props.getIntProperty(HyperNEATTranscriberGridNet.HYPERNEAT_WIDTH);

			// stimuli = Properties.loadArrayFromFile( props.getResourceProperty( STIMULI_FILE_NAME_KEY ) );
			// targets = Properties.loadArrayFromFile( props.getResourceProperty( TARGETS_FILE_NAME_KEY ) );

			targetRange = props.getFloatProperty(TARGETS_RANGE_KEY, 0);

			adjustForNetworkSizeFactor = props.getFloatProperty(ADJUST_FOR_NETWORK_SIZE_FACTOR_KEY, 0.0f);

			ErrorFunction.getInstance().init(props);
			setMaxFitnessValue(MAX_FITNESS);

			/*
			 * if ( stimuli.length == 0 || targets.length == 0 ) throw new IllegalArgumentException( "require at least 1 training set for stimuli [" +
			 * stimuli.length + "] and targets [" + targets.length + "]" ); if ( stimuli.length != targets.length ) throw new IllegalArgumentException(
			 * "# training sets does not match for stimuli [" + stimuli.length + "] and targets [" + targets.length + "]" );
			 */

			numThreads = Runtime.getRuntime().availableProcessors();
			logger.info("Using " + numThreads + " threads for evaluation.");

			evaluators = new Evaluator[numThreads];
			for (int i = 0; i < numThreads; i++) {
				evaluators[i] = new Evaluator(i);
				evaluators[i].start();
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("invalid properties: " + e.getClass().toString() + ", message: " + e.getMessage());
		}
	}

	/**
	 * @param aMaxFitnessValue maximum raw fitness this function will return
	 */
	protected void setMaxFitnessValue(int aMaxFitnessValue) {
		int minGenes = width * height * 2; // stimuli + targets
		maxFitnessValue = aMaxFitnessValue - (int) (adjustForNetworkSizeFactor * minGenes);
	}

	/**
	 * Iterates through chromosomes. For each, transcribe it to an <code>Activator</code> and present the stimuli to the activator. The stimuli are presented in
	 * random order to ensure the underlying network is not memorizing the sequence of inputs. Calculation of the fitness based on error is delegated to the
	 * subclass. This method adjusts fitness for network size, based on configuration.
	 * 
	 * @param genotypes <code>List</code> contains <code>Chromosome</code> objects.
	 * @see TargetFitnessFunction#calculateErrorFitness(double[][], double, double)
	 */
	public void evaluate(List genotypes) {
		int stimCount = 100;
		stimuli = new double[stimCount][height][width];
		targets = new double[stimCount][height][width];
		// System.out.println("stimuli,targets:");
		for (int s = 0; s < stimCount; s++) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					stimuli[s][y][x] = Math.round(random.nextDouble());
					// stimuli[s][y][x] = random.nextDouble();
					targets[s][y][x] = stimuli[s][y][x];
					// System.out.print("\t" + ((int) (100*stimuli[s][y][x]) / 100.0));
				}
				// System.out.println();
			}
			// System.out.println();
		}

		chromosomesIterator = genotypes.iterator();
		evaluatorsFinishedCount = 0;

		for (Evaluator ev : evaluators) {
			ev.go();
		}

		while (true) {
			try {
				synchronized (this) {
					if (evaluatorsFinishedCount == evaluators.length)
						break;
					// System.out.println("wait");
					wait();
					// System.out.println("stopped waiting");
				}
			} catch (InterruptedException ignore) {
				System.out.println(ignore);
			}
			// System.out.println("done");
		}

		/*
		 * //long start = System.currentTimeMillis(); Iterator it = genotypes.iterator(); while ( it.hasNext() ) { Chromosome genotype = (Chromosome) it.next();
		 * evaluate(genotype); } //long end = System.currentTimeMillis(); //System.out.println("Eval time: " + (end-start)/1000.0);
		 */
	}

	private void evaluate(Chromosome genotype) {
		if (genotype == null)
			return;

		try {
			if (genotype.size() > 100) {
				genotype.setFitnessValue(0);
			} else {
				Activator activator = activatorFactory.newActivator(genotype);

				double[][][] responses = activator.nextSequence(stimuli);
				/*
				 * System.out.println("responses:"); for (int s = 0; s < responses.length; s++) { for (int y = 0; y < height; y++) { for (int x = 0; x < width;
				 * x++) { System.out.print("\t" + responses[s][y][x]); } System.out.println(); } System.out.println(); } System.out.println();
				 */

				double minResponse = activator.getMinResponse();
				double maxResponse = activator.getMaxResponse();
				int fitness = calculateErrorFitness(targets, responses, minResponse, maxResponse) - (int) (adjustForNetworkSizeFactor * genotype.size());
				// System.out.println("fitness: " + fitness);
				// System.out.println();
				// System.out.println();
				genotype.setFitnessValue(fitness);
				genotype.setPerformanceValue((double) fitness / maxFitnessValue);
			}
		} catch (TranscriberException e) {
			logger.warn("transcriber error: " + e.getMessage());
			genotype.setFitnessValue(1);
		}
	}

	/**
	 * Subtract <code>responses</code> from targets, sum all differences, subtract from max fitness, and square result.
	 * 
	 * @param responses output top be compared to targets
	 * @param minResponse
	 * @param maxResponse
	 * @return result of calculation
	 */
	private int calculateErrorFitness(double[][][] targets, double[][][] responses, double minResponse, double maxResponse) {
		ErrorFunction ef = ErrorFunction.getInstance();
		if (ef == null)
			throw new IllegalStateException("couldn't get instance of error function");
		double maxSumDiff = ef.getMaxError(targets.length * width * height, (maxResponse - minResponse), SUM_OF_SQUARES);
		double maxRawFitnessValue = (double) Math.pow(maxSumDiff, 2);
		double sumDiff = ef.calculateError(targets, responses, SUM_OF_SQUARES);
		if (sumDiff > maxSumDiff)
			throw new IllegalStateException("sum diff > max sum diff");
		double rawFitnessValue = (double) Math.pow(maxSumDiff - sumDiff, 2);
		double skewedFitness = (rawFitnessValue / maxRawFitnessValue) * MAX_FITNESS;
		int result = (int) skewedFitness;
		return result;
	}

	protected synchronized Chromosome getNextChromosome() {
		if (chromosomesIterator.hasNext())
			return chromosomesIterator.next();
		else
			return null;
	}

	protected synchronized void finishedEvaluating() {
		evaluatorsFinishedCount++;
		// System.out.println("finishedEvaluating: " + evaluatorsFinishedCount);
		notifyAll();
		// System.out.println("finishedEvaluating exit");
	}

	/**
	 * @return if response is within this range of the target, error is 0
	 */
	protected double getTargetRange() {
		return targetRange;
	}

	/**
	 * @return sequence of stimuli activation patterns
	 */
	protected double[][][] getStimuli() {
		return stimuli;
	}

	/**
	 * @return sequence of target values
	 */
	protected double[][][] getTargets() {
		return targets;
	}

	/**
	 * @return maximum possible fitness value for this function
	 */
	public int getMaxFitnessValue() {
		return maxFitnessValue;
	}

	public boolean endRun() {
		return false;
	}

	private class Evaluator extends Thread {
		private volatile boolean go = false;
		private int id;

		public Evaluator(int id) {
			this.id = id;
		}

		public void run() {
			for (;;) {
				while (go) {
					Chromosome chrom;
					while ((chrom = getNextChromosome()) != null) {
						evaluate(chrom);
					}

					go = false;
					// System.out.println("ev " + id + " finished");
					finishedEvaluating();
				}
				try {
					synchronized (this) {
						// System.out.println("ev " + id + " wait");
						while (!go)
							wait();
					}
				} catch (InterruptedException e) {
					System.out.println("Exception: " + e);
				}
			}
		}

		public synchronized void go() {
			go = true;
			// System.out.println("ev " + id + " go");
			notifyAll();
		}
	}

	@Override
	public void dispose() {
	}
}
