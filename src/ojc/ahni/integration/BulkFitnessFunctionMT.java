package ojc.ahni.integration;

import java.util.*;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.*;
import com.anji.util.*;
import com.anji.util.Properties;
import com.anji.neat.Evolver;

/**
 * Provides a base for multi-threaded bulk fitness functions. Provides a multi-threaded framework for performing evaluations on multiple genomes. The methods
 * {@link #getMaxFitnessValue()} and {@link #evaluate(Chromosome, Activator, int)} must be implemented in subclasses. Subclasses may also need to override the
 * methods {@link #init(Properties)}, {@link #initialiseEvaluation()}, {@link #postEvaluate(Chromosome, Activator, int)} and {@link #dispose()}. See
 * {@link ojc.ahni.integration.TargetFitnessFunctionMT} for an example.
 * 
 * @author Oliver Coleman
 */
public abstract class BulkFitnessFunctionMT implements BulkFitnessFunction, Configurable {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(BulkFitnessFunctionMT.class);
	
	/**
	 * Property key for minimum number of threads to use for fitness evaluation (including transcription of genotype/cppn to phenotype/substrate).
	 */
	public static final String MIN_THREADS_KEY = "fitness.min_threads";
	/**
	 * Property key for maximum number of threads to use for fitness evaluation (including transcription of genotype/cppn to phenotype/substrate).
	 * If value is <= 0 then which ever is lower of the detected number of processor cores and the specified minimum will be used.
	 */
	public static final String MAX_THREADS_KEY = "fitness.max_threads";

	
	protected Properties props;
	protected Transcriber transcriber;
	protected int numThreads;
	protected int evaluatorsFinishedCount;
	protected Evaluator[] evaluators;
	protected Iterator<Chromosome> chromosomesIterator;
	/**
	 * The current generation of the evolutionary algorithm.
	 */
	protected int generation;
	
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
		transcriber = (Transcriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);

		targetPerformance = props.getFloatProperty(Evolver.PERFORMANCE_TARGET_KEY, 1);
		targetPerformanceType = props.getProperty(Evolver.PERFORMANCE_TARGET_TYPE_KEY, "higher").toLowerCase().trim().equals("higher") ? 1 : 0;
	
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
	 * Evaluate a set of chromosomes.
	 * 
	 * @param genotypes The set of chromosomes to evaluate.
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
		// If we've reached the target performance, end the run.
		if ((targetPerformanceType == 1 && bestPerformance >= targetPerformance) || (targetPerformanceType == 0 && bestPerformance <= targetPerformance)) {
			System.out.println("End run, solution found. bestPerformance: " + bestPerformance + ", targetPerformance: " + targetPerformance);
			endRun = true;
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

	
	protected class Evaluator extends Thread {
		private volatile boolean go = false;
		private int id;
		private Activator substrate;
		
		protected Evaluator(int id) {
			this.id = id;
			substrate = null;
		}
		
		/**
		 * Deletes the current substrate, a completely new one will be generated.
		 * This is useful for when a substrate can be reused by the Transcriber, but sometimes needs to be completely regenerated.
		 * @see Transcriber#transcribe(Chromosome, Activator) 
		 */
		public void resetSubstrate() {
			substrate = null;
		}
		
		/**
		 * Internal use only
		 */
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
							
							postEvaluate(chrom, substrate, id);
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
	
	/**
	 * Sub-classes may override this method to dispose of resources upon disposal of this object.
	 */
	public void dispose() {
	}
	
	/**
	 * Sub-classes may override this method to perform operations after a Chromosome has been evaluated.
	 * @param genotype the Chromosome that has been evaluated. It's fitness and performance will have been set.
	 * @param substrate the network (activator), or phenotype, of the evaluated Chromosome.
	 * @param evalThreadIndex The index of the evaluator thread. This is not usually required but may be useful in some cases.
	 */
	protected void postEvaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {}
}
