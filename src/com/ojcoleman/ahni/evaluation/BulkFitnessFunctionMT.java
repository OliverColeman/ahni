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
 * {@link #definesFitness()}, {@link #definesNovelty()} and {@link #dispose()}. Subclasses may also need to override
 * {@link #getLayerDimensions(int, int)} or {@link #getNeuronPositions(int, int)} to specify required layer dimensions
 * or neuron positions, for example for input or output layers.
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

		objectiveCount = 0;
		int noveltyCount = 0;
		if (definesFitness())
			objectiveCount++;
		if (definesNovelty()) {
			objectiveCount++;
			noveltyCount++;
		}

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
					multiFitnessFunctions[i] = (BulkFitnessFunctionMT) props.singletonObjectProperty(tempKey);
					props.remove(tempKey + ".class");

					if (multiFitnessFunctions[i].definesFitness())
						objectiveCount++;
					if (multiFitnessFunctions[i].definesNovelty()) {
						objectiveCount++;
						noveltyCount++;
					}
				}
				props.remove("fitness.function.multi.addingsub");
				props.put(MULTI_KEY, multiKeyValue);
			}
		}

		multiFitnessFunctionWeights = props.getDoubleArrayProperty(MULTI_WEIGHTING_KEY, ArrayUtil.newArray(objectiveCount, 1.0));
		ArrayUtil.normaliseSum(multiFitnessFunctionWeights);
		if (multiFitnessFunctionWeights.length != objectiveCount) {
			throw new IllegalArgumentException("The number of weighting values for the (multiple) fitness functions does not match the number of fitness functions (note that if novelty search is enabled and is not the only fitness value returned by the primary fitness function then it must be included seperately in the weights at the first position).");
		}
		logger.info("Number of objectives is " + objectiveCount + ".");
		logger.info("Normalised fitness function weightings: " + Arrays.toString(multiFitnessFunctionWeights));

		if (noveltyCount > 0) {
			logger.info("Enabling novelty search with " + noveltyCount + " archive(s).");
			noveltyArchives = new NoveltySearch[noveltyCount];
			for (int n = 0; n < noveltyCount; n++) {
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
	 * Superclasses that support novelty search should override this method and return true. A {@link Behaviour} should
	 * then be appended to {@link Chromosome#behaviour}(s) of each Chromosome in the implementation of
	 * {@link BulkFitnessFunctionMT#evaluate(Chromosome, Activator, int)}. This default implementation returns false.
	 * 
	 * @see com.ojcoleman.ahni.evaluation.novelty.NoveltySearch
	 */
	public boolean definesNovelty() {
		return false;
	}

	/**
	 * Superclasses that do not provide a fitness value (and instead only define a novelty behaviour or performance
	 * value (for primary fitness functions only)) should override this method and return false.
	 */
	public boolean definesFitness() {
		return true;
	}

	/**
	 * Evaluate a set of chromosomes.
	 * 
	 * @param genotypes The set of chromosomes to evaluate.
	 */
	public void evaluate(List<Chromosome> genotypes) {
		transcriber = (Transcriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);

		initialiseEvaluation();

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
			for (int n = 0; n < noveltyArchives.length; n++) {
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

		renderedNoveltyArchivesThisGeneration = false;
	}

	/**
	 * Evaluate an individual genotype. This method is called from {@link #evaluate(List)} and must be overridden in
	 * order to evaluate the genotypes.
	 * 
	 * @param genotype the genotype being evaluated. This is not usually required but may be useful in some cases.
	 * @param substrate the phenotypic substrate of the genotype being evaluated.
	 * @param evalThreadIndex The index of the evaluator thread. This is not usually required but may be useful in some
	 *            cases.
	 * @return The fitness value, in the range [0, 1]. Note that if novelty is the only objective then the returned
	 *         value is ignored.
	 */
	protected abstract double evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex);

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
		private int id;
		private Activator substrate;
		private boolean testingNovelty = false;

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
			while (!finish) {
				while (go) {
					Chromosome chrom;
					while ((chrom = getNextChromosome()) != null) {
						if (!testingNovelty) {
							try {
								Activator previousSubstrate = substrate;
								substrate = generateSubstrate(chrom, substrate);

								// If a valid substrate could be generated.
								if (substrate != null) {
									double primaryFitness = evaluate(chrom, substrate, id);
									double performance = chrom.getPerformanceValue();
									int fitnessSlot = 0;

									// If this (the primary) fitness function defines a regular objective.
									if (definesFitness()) {
										chrom.setFitnessValue(primaryFitness, fitnessSlot++);
										// If the subclass didn't set the fitness of the individual then do it here.
										// Note this will be overwritten if multiple fitness functions are used.
										if (Double.isNaN(chrom.getFitnessValue()) && !Double.isNaN(primaryFitness)) {
											chrom.setFitnessValue(primaryFitness);
										}
									}

									for (int i = 0; i < multiFitnessFunctions.length; i++) {
										BulkFitnessFunctionMT func = multiFitnessFunctions[i];
										double f = func.evaluate(chrom, substrate, id);
										if (func.definesFitness()) {
											chrom.setFitnessValue(f, fitnessSlot++);
										}
									}

									// Reinstate performance value as determined by primary fitness function in case any
									// secondary ones overwrite it.
									chrom.setPerformanceValue(performance);

									// We just set the overall fitness value according to the weightings. A different
									// selector (eg NSGA-II selector) may set the overall fitness to something else
									// based on the multiple objectives.
									// If novelty is to be assessed wait until this is done for all chromosomes before
									// calculating overall fitness.
									if (noveltyArchives != null)
										calculateOverallFitness(chrom);

									// If the fitness function hasn't explicitly set a performance value, just set it to
									// (first) fitness value.
									if (chrom.getPerformanceValue() == Double.NaN && chrom.getFitnessValue() != Double.NaN) {
										chrom.setPerformanceValue(chrom.getFitnessValue());
									}
									synchronized (this) {
										if ((targetPerformanceType == 1 && chrom.getPerformanceValue() > bestPerformance) || (targetPerformanceType == 0 && chrom.getPerformanceValue() < bestPerformance)) {
											bestPerformance = chrom.getPerformanceValue();
											newBestChrom = chrom;
										}
									}

									if (noveltyArchives != null && chrom.getFitnessValue() != Double.NaN && chrom.behaviour != null) {
										for (int n = 0; n < noveltyArchives.length; n++) {
											noveltyArchives[n].addToCurrentPopulation(chrom.behaviour.get(n));
										}
									}
								}
								// If the transcriber decided the substrate decoding was a dud then still allow reusing
								// the old substrate.
								else {
									substrate = previousSubstrate;
								}

								postEvaluate(chrom, substrate, id);
							} catch (TranscriberException e) {
								logger.warn("transcriber error: " + e.getMessage());
								e.printStackTrace();
							}
						} else { // testingNovelty
							int fitnessSlot = objectiveCount - noveltyArchives.length;
							// May be empty if substrate decoding was a dud (see above).
							if (!chrom.behaviour.isEmpty()) {
								for (int n = 0; n < noveltyArchives.length; n++) {
									chrom.setFitnessValue(noveltyArchives[n].testNovelty(chrom.behaviour.get(n)), fitnessSlot++);
								}
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
	public double evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		if (!logImage || noveltyArchives == null)
			return 0;

		synchronized (this) {
			if (!renderedNoveltyArchivesThisGeneration) {
				renderedNoveltyArchivesThisGeneration = true;
			} else {
				return 0;
			}
		}

		for (int n = 0; n < noveltyArchives.length; n++) {
			Behaviour tmp = noveltyArchives[n].archive.get(0);
			boolean noveltyImageable = (tmp instanceof RealVectorBehaviour) && ((RealVectorBehaviour) tmp).p.getDimension() == 2;
			if (noveltyImageable) {
				// Log novelty archive in 2D map.
				int imageSize = 254;
				BufferedImage image = new BufferedImage(imageSize + 2, imageSize + 2, BufferedImage.TYPE_3BYTE_BGR);
				Graphics2D g = image.createGraphics();

				g.setColor(Color.WHITE);
				for (Behaviour b : noveltyArchives[n].archive) {
					RealVectorBehaviour brv = (RealVectorBehaviour) b;
					g.drawRect((int) Math.round(brv.p.getEntry(0) * imageSize), (int) Math.round(brv.p.getEntry(1) * imageSize), 1, 1);
				}
				String fileName = props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "novelty_archive-" + props.getEvolver().getGeneration() + "-" + n + ".png";
				File outputfile = new File(fileName);
				try {
					ImageIO.write(image, "png", outputfile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return 0;
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

	@Override
	public int getObjectiveCount() {
		return objectiveCount;
	}

	/**
	 * {@inheritDoc} This default implementation does nothing.
	 */
	@Override
	@Deprecated
	public void evolutionFinished(HyperNEATEvolver evolver) {
	}
}
