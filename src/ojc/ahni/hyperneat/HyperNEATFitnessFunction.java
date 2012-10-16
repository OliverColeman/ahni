package ojc.ahni.hyperneat;

import java.util.*;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.*;
import com.anji.util.*;
import com.anji.util.Properties;
import com.anji.neat.Evolver;

/**
 * Provides a base for fitness functions for use with HyperNEAT. Provides a multi-threaded framework for performing evaluations on multiple genomes. The methods
 * {@link #getMaxFitnessValue()} and {@link #evaluate(Chromosome, Activator, int)} must be implemented in subclasses. Subclasses may also need to override the
 * methods {@link #init(Properties)}, {@link #initialiseEvaluation()}, {@link #scale(int, int)} and {@link #dispose()}. See
 * {@link ojc.ahni.experiments.TestTargetFitnessFunction} and {@link ojc.ahni.experiments.objectrecognition.ObjectRecognitionFitnessFunction3} for examples.
 * 
 * @author Oliver Coleman
 */
public abstract class HyperNEATFitnessFunction implements BulkFitnessFunction, Configurable {
	private static final long serialVersionUID = 1L;

	/**
	 * Property key for minimum number of threads to use for fitness evaluation (including transcription of genotype/cppn to phenotype/substrate).
	 */
	public static final String MIN_THREADS_KEY = "fitness.hyperneat.min_threads";
	/**
	 * Property key for maximum number of threads to use for fitness evaluation (including transcription of genotype/cppn to phenotype/substrate).
	 * If value is <= 0 then which ever is lower of the detected number of processor cores and the specified minimum will be used.
	 */
	public static final String MAX_THREADS_KEY = "fitness.hyperneat.max_threads";

	/**
	 * Property key for multiplicative amount to scale substrate by when the performance of the best individual in the population has reached the specified level.
	 */
	public static final String SCALE_FACTOR_KEY = "fitness.hyperneat.scale.factor";
	
	/**
	 * Property key for the maximum number of rescalings to perform.
	 */
	public static final String SCALE_COUNT_KEY = "fitness.hyperneat.scale.times";
	/**
	 * Property key for the performance level required before a scaling is performed.
	 */
	public static final String SCALE_PERFORMANCE_KEY = "fitness.hyperneat.scale.performance";
	
	/**
	 * Property key for whether the performance values should be recorded before the final scaling has been performed. If false them the performance of individuals (Chromosomes) is set to 0 after each evaluation.
	 */
	public static final String SCALE_RIP_KEY = "fitness.hyperneat.scale.recordintermediateperformance";

	
	protected Properties props;
	protected static Logger logger = Logger.getLogger(HyperNEATFitnessFunction.class);
	private HyperNEATTranscriber transcriber;
	private int numThreads;
	private int evaluatorsFinishedCount;
	private Evaluator[] evaluators;
	private Iterator<Chromosome> chromosomesIterator;
	/**
	 * The current generation of the evolutionary algorithm.
	 */
	protected int generation;
	
	protected Random random;
	/**
	 * Width of each layer in the substrate network.
	 */
	protected int width[];
	/**
	 * Height of each layer in the substrate network.
	 */
	protected int height[];
	/**
	 * Number of layers in the substrate network.
	 */
	protected int depth;
	/**
	 * Limits the incoming connections to a target neuron to include those from source neurons within the specified range of the target neuron. This is optional.
	 */ 
	protected int connectionRange;

	private int scaleFactor = 2;
	private int scaleTimes = 2;
	/**
	 * The performance level required before a scaling is performed.
	 * @see #SCALE_PERFORMANCE_KEY
	 */
	protected double scalePerformance = 0.98f;
	/**
	 * The maximum number of rescalings to perform.
	 * @see #SCALE_COUNT_KEY
	 */
	protected int scaleCount = 0;
	private boolean endRun = false;
	private boolean scaleRecordIntermediatePerf = true;

	private double bestPerformance;
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
	 * Whether the best performance is lower of higher. If 1 then the best performance is higher, if 0 then the best performance is lower.
	 */
	protected int targetPerformanceType = 1;
	/**
	 * The performance being aimed for.
	 */
	protected double targetPerformance;

	/**
	 * Subclasses may override this method to perform initialise tasks. <strong>Make sure to call this method from the overriding method.</strong>
	 * 
	 * @param props Configuration parameters, typically read from the/a properties file.
	 */
	public void init(Properties props) {
		this.props = props;
		random = ((Randomizer) props.singletonObjectProperty(Randomizer.class)).getRand();
		transcriber = (HyperNEATTranscriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
		depth = transcriber.getDepth();
		height = transcriber.getHeight();
		width = transcriber.getWidth();
		connectionRange = transcriber.getConnectionRange();

		targetPerformance = props.getFloatProperty(Evolver.PERFORMANCE_TARGET_KEY, 1);
		targetPerformanceType = props.getProperty(Evolver.PERFORMANCE_TARGET_TYPE_KEY, "higher").toLowerCase().trim().equals("higher") ? 1 : 0;
		scalePerformance = props.getDoubleProperty(SCALE_PERFORMANCE_KEY, scalePerformance);
		scaleTimes = Math.max(0, props.getIntProperty(SCALE_COUNT_KEY, scaleTimes));
		scaleRecordIntermediatePerf = props.getBooleanProperty(SCALE_RIP_KEY, scaleRecordIntermediatePerf);

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

		generation = 0;
	}

	/**
	 * @return maximum possible fitness value for this function.
	 */
	abstract public int getMaxFitnessValue();

	/**
	 * If required, initialise data for the current evaluation run. This method is called at the beginning of {@link #evaluate(List)}. It should be overridden
	 * if data (eg input and/or output patterns) need to be set-up before every evaluation run.
	 * */
	public void initialiseEvaluation() {
	}

	/**
	 * Evaluate each chromosome in genotypes.
	 * 
	 * @param genotypes <code>List</code> contains <code>Chromosome</code> objects.
	 */
	public void evaluate(List<Chromosome> genotypes) {
		initialiseEvaluation();

		bestPerformance = targetPerformanceType == 1 ? 0 : Float.MAX_VALUE;

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

		lastBestChrom = newBestChrom;
		lastBestPerformance = bestPerformance;
		
		endRun = false;
		// If we've completed all scalings and reached the target performance, end the run.
		if (scaleCount >= 0 && scaleCount == scaleTimes && scaleFactor > 1 && ((targetPerformanceType == 1 && bestPerformance >= targetPerformance) || (targetPerformanceType == 0 && bestPerformance <= targetPerformance))) {
			System.out.println("End run, solution found. bestPerformance: " + bestPerformance + ", targetPerformance: " + targetPerformance);
			endRun = true;
		}

		// if we should scale the substrate
		if (scaleCount < scaleTimes && scaleFactor > 1 && ((targetPerformanceType == 1 && bestPerformance >= scalePerformance) || (targetPerformanceType == 0 && bestPerformance <= scalePerformance))) {
			// allow sub-class to make necessary changes
			scale(scaleCount, scaleFactor);
			for (Evaluator ev : evaluators)
				ev.resetSubstrate(); // don't reuse old size substrate
			transcriber.resize(width, height, connectionRange);

			scaleCount++;
		}
		
		generation++;
	}

	/**
	 * Evaluate an individual genotype. This method is called from {@link #evaluate(List)} , and must be overridden in order to evaluate the
	 * genotypes.
	 * 
	 * @param genotype the genotype being evaluated. This is not usually required but may be useful in some cases.
	 * @param substrate the phenotypic substrate of the genotype being evaluated.
	 * @param evalThreadIndex The index of the evaluator thread. This is not usually required but may be useful in some cases.
	 */
	protected abstract int evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex);

	/**
	 * Allow sub-class to make the necessary changes when a substrate scale occurs. If implemented, then at a minimum this method will usually need to set new
	 * values for the {@link #width} and {#link height} array fields, and the {@link #connectionRange} field if applicable.
	 * 
	 * @param scaleCount A count of how many times a scale has previously occurred. In the first call this has value 0.
	 * @param scaleFactor The amount the substrate is being scaled by.
	 */
	protected void scale(int scaleCount, int scaleFactor) {

	}

	public int getConnectionRange() {
		return transcriber.getConnectionRange();
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
		// System.out.println("finishedEvaluating: " + evaluatorsFinishedCount);
		notifyAll();
		// System.out.println("finishedEvaluating exit");
	}

	
	private class Evaluator extends Thread {
		private volatile boolean go = false;
		private int id;
		private Activator substrate;
		

		public Evaluator(int id) {
			this.id = id;
			substrate = null;
		}

		public void resetSubstrate() {
			substrate = null;
		}

		public void run() {
			for (;;) {
				while (go) {
					Chromosome chrom;
					while ((chrom = getNextChromosome()) != null) {
						try {
							substrate = transcriber.transcribe(chrom, substrate);
							int fitness = evaluate(chrom, substrate, id);
							chrom.setFitnessValue(fitness);
							if (chrom.getPerformanceValue() == -1) {
								chrom.setPerformanceValue((double) fitness / getMaxFitnessValue());
							}
							synchronized (this) {
								if ((targetPerformanceType == 1 && chrom.getPerformanceValue() > bestPerformance) || (targetPerformanceType == 0 && chrom.getPerformanceValue() < bestPerformance)) {
									bestPerformance = chrom.getPerformanceValue();
									newBestChrom = chrom;
								}
							}
							
							// only record performance when all scales have been performed
							if (!scaleRecordIntermediatePerf && scaleCount < scaleTimes)
								chrom.setPerformanceValue(0);
						} catch (TranscriberException e) {
							logger.warn("transcriber error: " + e.getMessage());
							chrom.setFitnessValue(1);
							chrom.setPerformanceValue(0);
						}
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

	public void dispose() {
	}
}
