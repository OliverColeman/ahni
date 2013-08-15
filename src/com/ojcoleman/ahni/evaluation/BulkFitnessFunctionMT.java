package com.ojcoleman.ahni.evaluation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.neat.Evolver;
import com.anji.util.Randomizer;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;
import com.ojcoleman.ahni.evaluation.novelty.NoveltySearch;
import com.ojcoleman.ahni.evaluation.novelty.RealVectorBehaviour;
import com.ojcoleman.ahni.experiments.csb.SimpleNavigationEnvironment;
import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.CircularFifoBuffer;

/**
 * <p>
 * Provides a base for multi-threaded bulk fitness functions. Provides a multi-threaded framework for performing
 * evaluations on multiple genomes. The methods {@link #evaluate(Chromosome, Activator, int)} must be implemented in
 * subclasses. Subclasses may also need to override the methods {@link #init(Properties)},
 * {@link #initialiseEvaluation()}, {@link #finaliseEvaluation()}, {@link #postEvaluate(Chromosome, Activator, int)},
 * {@link #fitnessObjectivesCount()}, {@link #noveltyObjectiveCount()} and {@link #dispose()}. Subclasses may also
 * need to override {@link #getLayerDimensions(int, int)} or {@link #getNeuronPositions(int, int)} to specify required
 * layer dimensions or neuron positions, for example for input or output layers.
 * </p>
 * 
 * <p>
 * See {@link com.ojcoleman.ahni.evaluation.TargetFitnessFunctionMT} for an example.
 * </p>
 * 
 * @author Oliver Coleman
 */
public abstract class BulkFitnessFunctionMT extends AHNIFitnessFunction implements Configurable {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(BulkFitnessFunctionMT.class);

	/**
	 * Property key for minimum number of threads to use for fitness evaluation (including transcription of
	 * genotype/cppn to phenotype/substrate).
	 */
	public static final String MIN_THREADS_KEY = "fitness.min_threads";
	/**
	 * Property key for maximum number of threads to use for fitness evaluation (including transcription of
	 * genotype/cppn to phenotype/substrate). If value is <= 0 then which ever is lower of the detected number of
	 * processor cores and the specified minimum will be used.
	 */
	public static final String MAX_THREADS_KEY = "fitness.max_threads";

	/**
	 * Property key for specifying additional fitness function classes in a multi-objective evaluation. Note that these
	 * fitness functions must extend BulkFitnessFunctionMT.
	 */
	public static final String MULTI_KEY = "fitness.function.multi.class";

	/**
	 * The overall fitness will be assigned to each individual according to a weighted sum of the fitness values
	 * provided by each fitness function, according to the weightings provided here. If not specified then each
	 * objective will have equal weighting. Must be a comma-separated list of weight values. The first value should
	 * correspond to the primary fitness function specified by fitness_function.class followed by a weight value for
	 * each function specified by fitness.function.multi.class. For all fitness functions that define a novelty
	 * behaviour the weighting for these must be included at the end of this list in the same order as the functions are
	 * declared by fitness.function.multi.class. Note that a NaturalSelector (eg NSGA-II selector) may set the overall
	 * fitness to something else based on the multiple objectives, in which case the weightings here will be ignored.
	 */
	public static final String MULTI_WEIGHTING_KEY = "fitness.function.multi.weighting";

	protected Properties props;
	protected Transcriber transcriber;
	protected int numThreads;
	protected int evaluatorsFinishedCount;
	protected Evaluator[] evaluators;
	protected Iterator<Chromosome> chromosomesIterator;
	protected int logChampPerGens = -1;
	protected BulkFitnessFunctionMT[] multiFitnessFunctions;
	protected double[] multiFitnessFunctionWeights;
	protected boolean isRealMultiObjective;
	protected int objectiveCount;
	protected int noveltyObjectiveCount;
	protected NoveltySearch[] noveltyArchives;

	/**
	 * This RNG should be used by all sub-classes for all randomness.
	 */
	protected Random random;

	protected boolean endRun = false;

	protected double bestPerformance;
	/**
	 * The best performance in the previous generation.
	 */
	protected double lastBestPerformance;
	/**
	 * The chromosome with the best performance in the previous generation.
	 */
	protected Chromosome lastBestChrom;
	/**
	 * The chromosome with the current best performance.
	 */
	protected Chromosome newBestChrom;

	/**
	 * Whether the best performance is lower of higher. If 1 then the best performance is higher, if 0 then the best
	 * performance is lower.
	 */
	protected int targetPerformanceType = 1;
	/**
	 * The performance being aimed for.
	 */
	protected double targetPerformance;

	/**
	 * If greater than 1 then use an average of the best performance over this many generations.
	 */
	protected int targetPerformanceAverageCount;

	protected CircularFifoBuffer<Double> bestPerformances;

	private boolean renderedNoveltyArchivesThisGeneration;

	/**
	 * Subclasses may override this method to perform initialise tasks. <strong>Make sure to call this method from the
	 * overriding method.</strong>
	 * 
	 * @param props Configuration parameters, typically read from the/a properties file.
	 */
	public void init(Properties props) {
		this.props = props;
		random = ((Randomizer) props.singletonObjectProperty(Randomizer.class)).getRand();

		// If this is not the primary fitness function, skip everything else.
		if (props.getBooleanProperty("fitness.function.multi.addingsub", false)) {
			return;
		}

		targetPerformance = props.getFloatProperty(HyperNEATEvolver.PERFORMANCE_TARGET_KEY, 1);
		targetPerformanceType = props.getProperty(HyperNEATEvolver.PERFORMANCE_TARGET_TYPE_KEY, "higher").toLowerCase().trim().equals("higher") ? 1 : 0;
		targetPerformanceAverageCount = props.getIntProperty(HyperNEATEvolver.PERFORMANCE_TARGET_AVERAGE_KEY, 1);
		if (targetPerformanceAverageCount < 1)
			targetPerformanceAverageCount = 1;
		bestPerformances = new CircularFifoBuffer<Double>(targetPerformanceAverageCount);

		numThreads = Runtime.getRuntime().availableProcessors();
		int minThreads = props.getIntProperty(MIN_THREADS_KEY, 0);
		int maxThreads = props.getIntProperty(MAX_THREADS_KEY, 0);
		if (numThreads < minThreads)
			numThreads = minThreads;
		if (maxThreads > 0 && numThreads > maxThreads)
			numThreads = maxThreads;

		logger.info("Using " + numThreads + " threads for transcription and evaluation.");
		evaluators = new Evaluator[numThreads];
		for (int i = 0; i < numThreads; i++) {
			evaluators[i] = new Evaluator(i);
			evaluators[i].start();
		}

		noveltyObjectiveCount = noveltyObjectiveCount();
		objectiveCount = fitnessObjectivesCount();

		multiFitnessFunctions = new BulkFitnessFunctionMT[0];
		if (props.containsKey(MULTI_KEY)) {
			if (props.containsKey(MULTI_KEY)) {
				String[] mffs = props.getProperty(MULTI_KEY).split(",");
				multiFitnessFunctions = new BulkFitnessFunctionMT[mffs.length];
				// Prevent recursively loading multi fitness functions that extend this class.
				String multiKeyValue = (String) props.remove(MULTI_KEY);
				props.put("fitness.function.multi.addingsub", "true");
				for (int i = 0; i < mffs.length; i++) {
					String tempKey = MULTI_KEY + "." + i;
					props.put(tempKey + ".class", mffs[i].trim());
					multiFitnessFunctions[i] = (BulkFitnessFunctionMT) props.newObjectProperty(tempKey);
					props.remove(tempKey + ".class");

					noveltyObjectiveCount += multiFitnessFunctions[i].noveltyObjectiveCount();
					objectiveCount += multiFitnessFunctions[i].fitnessObjectivesCount();
				}
				props.remove("fitness.function.multi.addingsub");
				props.put(MULTI_KEY, multiKeyValue);
			}
		}

		objectiveCount += noveltyObjectiveCount;

		multiFitnessFunctionWeights = props.getDoubleArrayProperty(MULTI_WEIGHTING_KEY, ArrayUtil.newArray(objectiveCount, 1.0));
		ArrayUtil.normaliseSum(multiFitnessFunctionWeights);
		if (multiFitnessFunctionWeights.length != objectiveCount) {
			throw new IllegalArgumentException("The number of weighting values for the (multiple) fitness functions does not match the number of fitness functions (note that if novelty search is enabled and is not the only fitness value returned by the primary fitness function then it must be included seperately in the weights at the first position).");
		}
		logger.info("Number of objectives is " + objectiveCount + ".");
		logger.info("Normalised fitness function weightings: " + Arrays.toString(multiFitnessFunctionWeights));

		if (noveltyObjectiveCount > 0) {
			logger.info("Enabling novelty search with " + noveltyObjectiveCount + " archive(s).");
			noveltyArchives = new NoveltySearch[noveltyObjectiveCount];
			for (int n = 0; n < noveltyObjectiveCount; n++) {
				noveltyArchives[n] = props.newObjectProperty(NoveltySearch.class);
			}
		}
	}

	/**
	 * If required, initialise data for the current evaluation run. This method is called at the beginning of
	 * {@link #evaluate(List)}. It should be overridden if data (eg input and/or output patterns) need to be set-up
	 * before every evaluation run.
	 * */
	public void initialiseEvaluation() {
	}

	/**
	 * If required, perform cleanup after the current evaluation run. This method is called at the end of
	 * {@link #evaluate(List)}.
	 * */
	public void finaliseEvaluation() {
	}

	/**
	 * This is akin to {@link #getNoveltyObjectiveCount()} but allows for handling multiple fitness functions, some of
	 * which may define multiple novelty objectives. Subclasses that support novelty search should override this method
	 * and return the number of behaviours this fitness function defines. This default implementation returns 0.
	 * 
	 * @see com.ojcoleman.ahni.evaluation.novelty.NoveltySearch
	 * @see #fitnessObjectivesCount()
	 */
	public int noveltyObjectiveCount() {
		return 0;
	}

	/**
	 * This is akin to {@link #getObjectiveCount()} but allows for handling multiple fitness functions, some of which
	 * may define multiple objectives. This default implementation returns 1. If the fitness function defines more than
	 * one fitness objective, or no fitness objectives (eg only a novelty objective), then this method must be
	 * overridden to return the number of fitness objectives defined. NOTE: unlike {@link #getObjectiveCount()} this
	 * method should not include the number of novelty objectives.
	 * 
	 * @see #noveltyObjectiveCount()
	 */
	public int fitnessObjectivesCount() {
		return 1;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation is set as final to allow this class to make use of multiple separate fitness functions for a
	 * multi-objective setup.
	 * </p>
	 * 
	 * @see #fitnessObjectivesCount()
	 * @see #noveltyObjectiveCount()
	 */
	@Override
	public final int getObjectiveCount() {
		return objectiveCount;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation is set as final to allow this class to make use of multiple separate fitness functions for a
	 * multi-objective setup.
	 * </p>
	 * 
	 * @see #fitnessObjectivesCount()
	 * @see #noveltyObjectiveCount()
	 */
	@Override
	public final int getNoveltyObjectiveCount() {
		return noveltyObjectiveCount;
	}

	/**
	 * If the fitness value(s) and behaviour(s) for novelty search (if used) for this fitness function do not change
	 * between generations then subclasses should override this method to return true in order to avoid unnecessarily
	 * recalculating the fitness value(s) or behaviour(s). This default implementation returns false.
	 * 
	 * @see #fitnessObjectivesCount()
	 */
	public boolean fitnessValuesStable() {
		return false;
	}

	/**
	 * Evaluate a set of chromosomes.
	 * 
	 * @param genotypes The set of chromosomes to evaluate.
	 */
	public void evaluate(List<Chromosome> genotypes) {
		transcriber = (Transcriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);

		initialiseEvaluation();
		for (BulkFitnessFunctionMT f : multiFitnessFunctions) {
			f.initialiseEvaluation();
		}

		bestPerformance = targetPerformanceType == 1 ? 0 : Float.MAX_VALUE;

		// Evaluate fitness/performance over all individuals.
		chromosomesIterator = genotypes.iterator();
		evaluatorsFinishedCount = 0;
		for (Evaluator ev : evaluators)
			ev.go();
		while (true) {
			try {
				synchronized (this) {
					if (evaluatorsFinishedCount == evaluators.length)
						break;
					wait();
				}
			} catch (InterruptedException ignore) {
				System.out.println(ignore);
			}
		}
		
		if (noveltyArchives != null) {
			// Evaluate novelty over all individuals (this must be done after fitness/performance evaluation so that
			// we have the behaviour record of every individual in the population.
			chromosomesIterator = genotypes.iterator();
			evaluatorsFinishedCount = 0;
			for (Evaluator ev : evaluators)
				ev.goNovelty();
			while (true) {
				try {
					synchronized (this) {
						if (evaluatorsFinishedCount == evaluators.length)
							break;
						wait();
					}
				} catch (InterruptedException ignore) {
					System.out.println(ignore);
				}
			}

			// double avgArchiveSize = 0;
			for (int n = 0; n < noveltyObjectiveCount; n++) {
				noveltyArchives[n].finishedEvaluation();
				// avgArchiveSize += noveltyArchives[n].getArchiveSize();
			}
			// avgArchiveSize /= noveltyArchives.length;
			// logger.info("aas: " + avgArchiveSize);
		}

		lastBestChrom = newBestChrom;
		lastBestPerformance = bestPerformance;

		bestPerformances.add(bestPerformance);
		double avgBestPerformance = ArrayUtil.average(ArrayUtils.toPrimitive(bestPerformances.toArray(new Double[0])));

		endRun = false;
		// If enough generations have been finished to get an average.
		if (bestPerformances.isFull()) {
			// If we've reached the target performance, end the run.
			if ((targetPerformanceType == 1 && avgBestPerformance >= targetPerformance) || (targetPerformanceType == 0 && avgBestPerformance <= targetPerformance)) {
				endRun = true;
			}
		}

		finaliseEvaluation();
		for (BulkFitnessFunctionMT f : multiFitnessFunctions) {
			f.finaliseEvaluation();
		}

		renderedNoveltyArchivesThisGeneration = false;
	}

	/**
	 * Evaluate an individual genotype on one fitness objective (if more than one fitness objective is defined by this
	 * fitness function and/or it defines a behaviour for novelty search then
	 * {@link #evaluate(Chromosome, Activator, int, double[], Behaviour[])} must be overridden instead). This method is
	 * called from {@link #evaluate(List)} and must be overridden in order to evaluate the genotypes. If the fitness
	 * function defines a performance value then this method should call {@link Chromosome#setPerformanceValue(double)}.
	 * In a multi-objective set-up only the performance defined by the primary fitness function will be used. If a
	 * performance value isn't defined it will be set to the overall fitness value. This default implementation returns
	 * 0.
	 * 
	 * @param genotype the genotype being evaluated. This is not usually required but may be useful in some cases.
	 * @param substrate the phenotypic substrate of the genotype being evaluated.
	 * @param evalThreadIndex The index of the evaluator thread. This is not usually required but may be useful in some
	 *            cases.
	 * @return The fitness value, in the range [0, 1]. Note that if novelty is the only objective then the returned
	 *         value is ignored.
	 */
	protected double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		return 0;
	}

	/**
	 * For fitness functions that define multiple fitness objectives (see {@link #fitnessObjectivesCount()}) and/or
	 * behaviours for novelty search (see {@link #noveltyObjectiveCount()}), evaluate an individual genotype over all
	 * of these objectives. This default implementation simply calls {@link #evaluate(Chromosome, Activator, int)}. If
	 * the fitness function defines a performance value then this method should call
	 * {@link Chromosome#setPerformanceValue(double)}. In a multi-objective set-up (see {@link #MULTI_KEY}) only the
	 * performance defined by the primary fitness function will be used. If a performance value isn't defined it will be
	 * set to the (first) fitness value given by the primary fitness function.
	 * 
	 * @param genotype the genotype being evaluated. This is not usually required but may be useful in some cases.
	 * @param substrate the phenotypic substrate of the genotype being evaluated.
	 * @param evalThreadIndex The index of the evaluator thread. This is not usually required but may be useful in some
	 *            cases.
	 * @param fitnessValues An array to hold the fitness value(s) as determined by this fitness function. The array will
	 *            have length {@link #fitnessObjectivesCount()}.
	 * @param behaviours An array to hold the behaviour(s) for novelty search as determined by this fitness function.
	 *            The array will have length {@link #noveltyObjectiveCount()}, and can be safely ignored if this method
	 *            returns 0.
	 */
	protected void evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, double[] fitnessValues, Behaviour[] behaviours) {
		double f = evaluate(genotype, substrate, evalThreadIndex);
		if (fitnessObjectivesCount() > 0) {
			fitnessValues[0] = f;
		}
	}

	public int getNumThreads() {
		return numThreads;
	}

	public boolean endRun() {
		return endRun;
	}

	private synchronized Chromosome getNextChromosome() {
		if (chromosomesIterator.hasNext())
			return chromosomesIterator.next();
		else
			return null;
	}

	private synchronized void finishedEvaluating() {
		evaluatorsFinishedCount++;
		notifyAll();
	}

	protected class Evaluator extends Thread {
		private volatile boolean go = false;
		private volatile boolean finish = false;
		private volatile boolean testingNovelty = false;
		private int id;
		private Activator substrate;

		protected Evaluator(int id) {
			this.id = id;
			substrate = null;
		}

		/**
		 * Deletes the current substrate, a completely new one will be generated. This is useful for when a substrate
		 * can be reused by the Transcriber, but sometimes needs to be completely regenerated.
		 * 
		 * @see Transcriber#transcribe(Chromosome, Activator)
		 */
		protected void resetSubstrate() {
			if (substrate != null) {
				// Dispose of the old substrate.
				substrate.dispose();
			}
			substrate = null;
		}

		/**
		 * Internal use only
		 */
		public void run() {
			double[][] fitnessValues = null;
			Behaviour[][] behaviours = null;

			while (!finish) {
				while (go) {
					if (fitnessValues == null) {
						fitnessValues = new double[multiFitnessFunctions.length + 1][];
						behaviours = new Behaviour[multiFitnessFunctions.length + 1][];
						fitnessValues[0] = new double[fitnessObjectivesCount()];
						behaviours[0] = new Behaviour[noveltyObjectiveCount()];
						for (int i = 0; i < multiFitnessFunctions.length; i++) {
							fitnessValues[i + 1] = new double[multiFitnessFunctions[i].fitnessObjectivesCount()];
							behaviours[i + 1] = new Behaviour[multiFitnessFunctions[i].noveltyObjectiveCount()];
						}
					}

					Chromosome chrom;
					while ((chrom = getNextChromosome()) != null) {
						if (!testingNovelty) {
							try {
								Activator previousSubstrate = substrate;
								substrate = generateSubstrate(chrom, substrate);

								// If a valid substrate could be generated.
								if (substrate != null) {
									// Pull any stable (fixed) fitness values from chromosome.
									for (int i = 0, fs = 0; i < fitnessValues.length; i++) {
										for (int f = 0; f < fitnessValues[i].length; f++, fs++) {
											fitnessValues[i][f] = chrom.getFitnessValue(fs);
										}
									}
									for (int i = 0, fs = 0; i < behaviours.length; i++) {
										for (int f = 0; f < behaviours[i].length; f++, fs++) {
											behaviours[i][f] = chrom.behaviours[fs];
										}
									}
									// Do secondary fitness functions first.
									for (int i = 0; i < multiFitnessFunctions.length; i++) {
										BulkFitnessFunctionMT func = multiFitnessFunctions[i];
										// If the fitness values aren't stable for this function or they haven't been
										// calculated yet for this chrom.
										if (!func.fitnessValuesStable() || Double.isNaN(ArrayUtil.sum(fitnessValues[i + 1])) || ArrayUtils.contains(behaviours[i + 1], null)) {
											func.evaluate(chrom, substrate, id, fitnessValues[i + 1], behaviours[i + 1]);
										}
										if (func.fitnessValuesStable()) {
											// At least some fitness values stable (this doesn't prevent the non-stable
											// ones from being updated).
											chrom.setEvaluationDataStable();
										}
									}

									// If the fitness values aren't stable for the primary function or they haven't been
									// calculated yet for this chrom.
									if (!fitnessValuesStable() || Double.isNaN(ArrayUtil.sum(fitnessValues[0]))) {
										// Do primary fitness function.
										evaluate(chrom, substrate, id, fitnessValues[0], behaviours[0]);
									}
									if (fitnessValuesStable()) {
										chrom.setEvaluationDataStable();
									}

									// Assign fitness values to chromosome.
									for (int i = 0, fs = 0; i < fitnessValues.length; i++) {
										for (int f = 0; f < fitnessValues[i].length; f++, fs++) {
											if (!Double.isNaN(fitnessValues[i][f])) {
												chrom.setFitnessValue(fitnessValues[i][f], fs);
											}
										}
									}
									for (int i = 0, fs = 0; i < behaviours.length; i++) {
										for (int f = 0; f < behaviours[i].length; f++, fs++) {
											if (behaviours[i][f] != null) {
												chrom.behaviours[fs] = behaviours[i][f];
											}
										}
									}

									postEvaluate(chrom, substrate, id);

									// We just set the overall fitness value according to the weightings. A different
									// selector (eg NSGA-II selector) may set the overall fitness to something else
									// based on the multiple objectives.
									// If novelty is to be assessed wait until this is done for all chromosomes before
									// calculating overall fitness.
									if (noveltyArchives == null)
										calculateOverallFitness(chrom);

									synchronized (this) {
										if ((targetPerformanceType == 1 && chrom.getPerformanceValue() > bestPerformance) || (targetPerformanceType == 0 && chrom.getPerformanceValue() < bestPerformance)) {
											bestPerformance = chrom.getPerformanceValue();
											newBestChrom = chrom;
										}
									}
									if (noveltyArchives != null) {
										for (int n = 0; n < noveltyObjectiveCount; n++) {
											noveltyArchives[n].addToCurrentPopulation(chrom.behaviours[n]);
										}
									}
								}
								// If the transcriber decided the substrate decoding was a dud then still allow reusing
								// the old substrate.
								else {
									substrate = previousSubstrate;
								}
							} catch (TranscriberException e) {
								logger.warn("transcriber error: " + e.getMessage());
								e.printStackTrace();
							}
						} else { // testingNovelty
							int fitnessSlot = objectiveCount - noveltyArchives.length;
							// May be empty if substrate decoding was a dud (see above).
							if (chrom.behaviours != null) {
								for (int n = 0; n < noveltyArchives.length; n++) {
									chrom.setFitnessValue(noveltyArchives[n].testNovelty(chrom.behaviours[n]), fitnessSlot++);
								}
								calculateOverallFitness(chrom);
							}
						}
					}

					go = false;
					finishedEvaluating();
				}
				try {
					synchronized (this) {
						while (!go && !finish)
							wait();
					}
				} catch (InterruptedException e) {
					System.out.println("Exception: " + e);
				}
			}
		}

		private void calculateOverallFitness(Chromosome c) {
			double overallFitness = 0;
			for (int i = 0; i < objectiveCount; i++) {
				overallFitness += c.getFitnessValue(i) * multiFitnessFunctionWeights[i];
			}
			c.setFitnessValue(overallFitness);

			// If the fitness function hasn't explicitly set a performance value, just set it to
			// overall fitness value.
			if (Double.isNaN(c.getPerformanceValue()) && !Double.isNaN(c.getFitnessValue())) {
				c.setPerformanceValue(c.getFitnessValue());
			}
		}

		protected synchronized void go() {
			go = true;
			testingNovelty = false;
			notifyAll();
		}

		protected synchronized void goNovelty() {
			go = true;
			testingNovelty = true;
			notifyAll();
		}

		protected synchronized void dispose() {
			if (substrate != null)
				substrate.dispose();
			finish = true;
			notifyAll();
		}
	}

	/**
	 * A convenience method to generate a substrate from the given Chromosome using the configured transcriber for this
	 * fitness function.
	 * 
	 * @param chrom The Chromosome to generate the substrate from.
	 * @param substrate If a substrate can be reused for efficiency reasons it can be supplied here, otherwise null may
	 *            be given.
	 * @throws TranscriberException
	 */
	public Activator generateSubstrate(Chromosome chrom, Activator substrate) throws TranscriberException {
		Activator previousSubstrate = substrate;
		substrate = transcriber.transcribe(chrom, substrate);

		// If the previous substrate was not reused, dispose of it.
		if (previousSubstrate != null && previousSubstrate != substrate) {
			// Dispose of the old substrate.
			previousSubstrate.dispose();
		}
		return substrate;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * This implementation will generate images for the novelty archive(s) if possible. If called multiple times in the
	 * same generation it will only generate images the first time. If overridden, subclasses should call this method
	 * from their own implementation.
	 */
	@Override
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		if (!logImage || noveltyArchives == null)
			return;

		synchronized (this) {
			if (!renderedNoveltyArchivesThisGeneration) {
				renderedNoveltyArchivesThisGeneration = true;
			} else {
				return;
			}
		}

		for (int n = 0; n < noveltyArchives.length; n++) {
			if (noveltyArchives[n].archive.isEmpty())
				continue;

			Behaviour tmp = noveltyArchives[n].archive.get(0);
			if (tmp instanceof RealVectorBehaviour) {
				int dims = ((RealVectorBehaviour) tmp).p.getDimension();
				// If only 2 dimensions render as scatter plot.
				if (dims == 2) {
					int imageSize = 254;
					BufferedImage image = new BufferedImage(imageSize + 2, imageSize + 2, BufferedImage.TYPE_BYTE_GRAY);
					Graphics2D g = image.createGraphics();

					g.setColor(Color.WHITE);
					for (Behaviour b : noveltyArchives[n].archive) {
						RealVectorBehaviour brv = (RealVectorBehaviour) b;
						g.fillRect((int) Math.round(brv.p.getEntry(0) * imageSize), (int) Math.round(brv.p.getEntry(1) * imageSize), 1, 1);
					}
					String fileName = props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "novelty_archive-" + props.getEvolver().getGeneration() + "-" + n + ".png";
					File outputfile = new File(fileName);
					try {
						ImageIO.write(image, "png", outputfile);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else { // Render as intensity plot.
					int imageScale = 1;
					int size = noveltyArchives[n].archive.size();
					BufferedImage image = new BufferedImage(size * imageScale, dims * imageScale, BufferedImage.TYPE_BYTE_GRAY);
					Graphics2D g = image.createGraphics();

					for (int i = 0; i < size; i++) {
						RealVectorBehaviour brv = (RealVectorBehaviour) noveltyArchives[n].archive.get(i);
						for (int j = 0; j < dims; j++) {
							float c = (float) brv.p.getEntry(j);
							g.setColor(new Color(c, c, c));
							g.fillRect(i * imageScale, j * imageScale, imageScale, imageScale);
						}
					}
					String fileName = props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "novelty_archive-" + n + ".png";
					File outputfile = new File(fileName);
					try {
						ImageIO.write(image, "png", outputfile);
					} catch (IOException e) {
						e.printStackTrace();
					}

				}
			}

		}
	}

	/**
	 * Sub-classes may override this method to dispose of resources upon disposal of this object.
	 */
	public void dispose() {
		if (evaluators != null) {
			for (Evaluator e : evaluators) {
				e.dispose();
			}
		}
		if (multiFitnessFunctions != null) {
			for (BulkFitnessFunctionMT f : multiFitnessFunctions) {
				f.dispose();
			}
		}
	}

	/**
	 * Sub-classes may override this method to perform operations after a Chromosome has been evaluated.
	 * 
	 * @param genotype the Chromosome that has been evaluated. It's fitness and performance will have been set.
	 * @param substrate the network (activator), or phenotype, of the evaluated Chromosome.
	 * @param evalThreadIndex The index of the evaluator thread. This is not usually required but may be useful in some
	 *            cases.
	 */
	protected void postEvaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
	}

	/**
	 * {@inheritDoc} This default implementation does nothing.
	 */
	@Override
	@Deprecated
	public void evolutionFinished(HyperNEATEvolver evolver) {
	}
}
