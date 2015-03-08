package com.ojcoleman.ahni.evaluation;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.commons.collections.primitives.ArrayDoubleList;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.util.Randomizer;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;
import com.ojcoleman.ahni.evaluation.novelty.NoveltySearch;
import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.CircularFifoBuffer;
import com.ojcoleman.ahni.util.Exec;
import com.ojcoleman.ahni.util.Parallel;
import com.ojcoleman.ahni.util.Parallel.Operation;

/**
 * <p>
 * Provides a base for multi-threaded bulk fitness functions. Provides a multi-threaded framework for performing
 * evaluations on multiple genomes. The methods {@link #evaluate(Chromosome, Activator, int)} must be implemented in
 * subclasses. Subclasses may also need to override the methods {@link #init(Properties)},
 * {@link #initialiseEvaluation()}, {@link #finaliseEvaluation()}, {@link #postEvaluate(Chromosome, Activator, int)},
 * {@link #fitnessObjectivesCount()}, {@link #noveltyObjectiveCount()} and {@link #dispose()}. Subclasses may also need
 * to override {@link #getLayerDimensions(int, int)} or {@link #getNeuronPositions(int, int)} to specify required layer
 * dimensions or neuron positions, for example for input or output layers.
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
	private static NumberFormat nf2 = new DecimalFormat("0.00");

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

	/**
	 * Property key for specifying whether the performance for an individual should be forced to the overall fitness.
	 * Default is false.
	 */
	public static final String FORCE_PERF_FITNESS = "fitness.function.performance.force.fitness";
	
	/**
	 * properties keys, minion config (see {@link com.ojcoleman.ahni.evaluation.Minion})
	 */
	public static final String MINION_HOSTS = "minion.hosts";
	public static final String MINION_AUTOSTART = "minion.autostart";
	public static final String MINION_DEFAULT_PORT = "minion.default_port";

	protected Properties props;
	protected Transcriber<Activator> transcriber;
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
	protected String[] objectiveLabels;
	protected NoveltySearch[] noveltyArchives;
	protected boolean forcePerfFitness;
	protected ArrayList<MinionHandler> minions = null;
	
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
	 * Indicates if this instantiation is for in a minion instance.
	 * @see Minion
	 */
	protected boolean isMinionInstance=false;

	/**
	 * Subclasses may override this method to perform initialise tasks. <strong>Make sure to call this method from the
	 * overriding method, typically at the end of it</strong>.
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
		
		EvaluatorGroup eg = new EvaluatorGroup(this.getClass().getSimpleName() + " evaluators");
		logger.info("Using " + numThreads + " threads for transcription and evaluation.");
		evaluators = new Evaluator[numThreads];
		for (int i = 0; i < numThreads; i++) {
			evaluators[i] = new Evaluator(i, eg);
			evaluators[i].start();
		}
		
		String[] minionHosts = props.getStringArrayProperty(MINION_HOSTS, null);
		if (minionHosts != null) {
			int defaultPort = props.getIntProperty(MINION_DEFAULT_PORT, 5000);
			minions = new ArrayList<MinionHandler>();
			for (String hostDef : minionHosts) {
				try {
					MinionHandler inst = new MinionHandler(hostDef, defaultPort, props.getBooleanProperty(MINION_AUTOSTART, false));
					minions.add(inst);
				} catch (Exception e) {
					logger.error("Unable to create Minion instance for host " + hostDef, e);
					e.printStackTrace();
				}
			}
			logger.info("Using " + minions.size() + " Minions for transcription and evaluation.");
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
	            @Override
	            public void run() {
	            	logger.info("Sending terminate signal to all minions.");
	            	for (MinionHandler minion : minions) {
	            		if (minion.isConnected()) {
	            			minion.dispose();
	            		}
	            	}
	            }
	        });
		}
	
		int fitnessObjectiveCount = fitnessObjectivesCount();
		noveltyObjectiveCount = noveltyObjectiveCount();

		ArrayList<String> fitnessLabels = new ArrayList<String>();
		ArrayList<String> noveltyLabels = new ArrayList<String>();
		String[] labels = objectiveLabels();
		for (int i = 0; i < fitnessObjectiveCount; i++)
			fitnessLabels.add(labels[i]);
		for (int i = 0; i < noveltyObjectiveCount; i++)
			noveltyLabels.add(labels[fitnessObjectiveCount + i]);

		multiFitnessFunctions = new BulkFitnessFunctionMT[0];
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

				int mffObjCount = multiFitnessFunctions[i].fitnessObjectivesCount();
				int mffNovCount = multiFitnessFunctions[i].noveltyObjectiveCount();
				fitnessObjectiveCount += mffObjCount;
				noveltyObjectiveCount += mffNovCount;

				labels = multiFitnessFunctions[i].objectiveLabels();
				for (int j = 0; j < mffObjCount; j++)
					fitnessLabels.add(labels[j]);
				for (int j = 0; j < mffNovCount; j++)
					noveltyLabels.add(labels[mffObjCount + j]);

			}
			props.remove("fitness.function.multi.addingsub");
			props.put(MULTI_KEY, multiKeyValue);
		}

		objectiveCount = fitnessObjectiveCount + noveltyObjectiveCount;

		fitnessLabels.addAll(noveltyLabels);
		objectiveLabels = new String[fitnessLabels.size()];
		fitnessLabels.toArray(objectiveLabels);

		multiFitnessFunctionWeights = props.getDoubleArrayProperty(MULTI_WEIGHTING_KEY, ArrayUtil.newArray(objectiveCount, 1.0));
		ArrayUtil.normaliseSum(multiFitnessFunctionWeights);
		if (multiFitnessFunctionWeights.length != objectiveCount) {
			throw new IllegalArgumentException("The number of weighting values for the (multiple) fitness functions does not match the number of fitness functions (note that if novelty search is enabled and is not the only fitness value returned by the primary fitness function then it must be included seperately in the weights at the first position).");
		}
		logger.info("Number of objectives is " + objectiveCount + ".");
		logger.info("Normalised fitness function weightings: " + Arrays.toString(multiFitnessFunctionWeights));

		isMinionInstance = props.getBooleanProperty(Minion.MINION_INSTANCE, false);
		
		if (!isMinionInstance && noveltyObjectiveCount > 0) {
			logger.info("Creating " + noveltyObjectiveCount + " novelty search archive(s).");
			noveltyArchives = new NoveltySearch[noveltyObjectiveCount];
			for (int n = 0; n < noveltyObjectiveCount; n++) {
				noveltyArchives[n] = props.newObjectProperty(NoveltySearch.class);
			}
		}

		forcePerfFitness = props.getBooleanProperty(FORCE_PERF_FITNESS, false);
	}
	
	/**
	 * Calls {@link #initialiseEvaluation()} on this and all secondary fitness functions.
	 */
	void initialiseEvaluationOnAll() {
		transcriber = (Transcriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
		bestPerformance = targetPerformanceType == 1 ? 0 : Float.MAX_VALUE;
		
		initialiseEvaluation();
		for (BulkFitnessFunctionMT f : multiFitnessFunctions) {
			f.initialiseEvaluation();
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
	 * This is akin to {@link #getObjectiveLabels()} but allows for handling multiple fitness functions, some of which
	 * may define multiple fitness or novelty objectives. This default implementation returns an array containing
	 * {@link #fitnessObjectivesCount()} + {@link #noveltyObjectiveCount()} strings constructed of the class name of the
	 * sub-class prefixed with either F# or N# where F denotes a regular fitness objective, N denotes a novelty
	 * objective and # is the index of the objective.
	 */
	public String[] objectiveLabels() {
		String[] labels = new String[fitnessObjectivesCount() + noveltyObjectiveCount()];
		int index = 0;
		for (int f = 0; f < fitnessObjectivesCount(); f++)
			labels[index++] = "F" + f + this.getClass().getSimpleName();
		for (int f = 0; f < getNoveltyObjectiveCount(); f++)
			labels[index++] = "N" + f + this.getClass().getSimpleName();
		return labels;
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
	public final String[] getObjectiveLabels() {
		return objectiveLabels;
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
	@Override
	public void evaluate(List<Chromosome> genotypes) {
		assert !isMinionInstance;
		
		initialiseEvaluationOnAll();
		
		if (minions != null) {
			evaluateFitnessViaMinions(genotypes);
		}
		else {
			evaluateFitnessMT(genotypes);
		}
		
		if (noveltyArchives != null) {
			evaluateNoveltyMT(genotypes);
		}
		
		for (Chromosome chrom : genotypes) {
			finaliseEvaluation(chrom);
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
	
	void evaluateFitnessMT(List<Chromosome> genotypes) {
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
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void evaluateFitnessViaMinions(List<Chromosome> genotypes) {
		assert !isMinionInstance;
		
		List<Chromosome> stillToEvaluate = new ArrayList<Chromosome>(genotypes);
		while (!stillToEvaluate.isEmpty()) {
			// For each minion, update generation number, initialise evaluation in fitness functions, and check if it's still active. 
			ArrayList<MinionHandler> activeMinions = new ArrayList<MinionHandler>();
			ArrayDoubleList relativePerformanceList = new ArrayDoubleList();
			double avgPerformance = 0;
			int avgPerformanceCount = 0;
			while (activeMinions.isEmpty()) {
				for (MinionHandler minion : minions) {
					if (!activeMinions.contains(minion) && minion.isConnected() && minion.initialiseEvaluation()) {
						activeMinions.add(minion);
						if (minion.getAverageEvalTimePerChrom() != 0) {
							double perf = 1.0 / minion.getAverageEvalTimePerChrom();
							relativePerformanceList.add(perf);
							avgPerformance += perf;
							avgPerformanceCount++;
						}
						else {
							relativePerformanceList.add(0);
						}
					}
				}
			}
			avgPerformance /= avgPerformanceCount;
			
			// Assign chromosomes to Minions proportional to their observed performance.
			double[] relativePerformance = relativePerformanceList.toArray();
			if (avgPerformanceCount == 0) avgPerformance = 1;
			// If any Minions have not yet had their performance calculated, assign them an average performance value. 
			for (int i = 0; i < relativePerformance.length; i++) {
				if (relativePerformance[i] == 0) {
					relativePerformance[i] = avgPerformance;
				}
			}
			ArrayUtil.normaliseSum(relativePerformance);
			int minionIndex = 0;
			double chromIndex = 0;
			for (MinionHandler minion : activeMinions) {
				double nextChromIndex = chromIndex + relativePerformance[minionIndex] * stillToEvaluate.size();
				int chromIndexRnd = (int) Math.round(chromIndex);
				int nextChromIndexRnd = (int) Math.round(nextChromIndex);
				List<Chromosome> chroms = stillToEvaluate.subList(chromIndexRnd, nextChromIndexRnd);
				minion.setChromosomesToEvaluate(chroms);
				chromIndex = nextChromIndex;
				minionIndex++;
			}
			
			// Start evaluation on all active minions.
			Parallel.foreach(activeMinions, activeMinions.size(), new Operation<MinionHandler>() {
				@Override
				public void perform(MinionHandler minion) {
					minion.evaluateChroms();
				}
			});
			
			// Check if evaluation was successful for each minion, compile 
			// list of chromosomes still to evaluate if any minions failed.
			stillToEvaluate.clear();
			int failCount = 0, activeMinionCount = activeMinions.size();
			for (MinionHandler minion : activeMinions) {
				if (minion.lastEvalFailed()) {
					stillToEvaluate.addAll(minion.getChromosomesToEvaluate());
					failCount++;
				}
			}
			// If they all failed it might be because the evaluation time has suddenly increased.
			if (failCount == activeMinionCount) {
				for (MinionHandler minion : minions) {
					// Increases the allowed eval time by 10.
					minion.increaseAverageMinionEvalTime();
				}
			}
		}
	}
	
	// Evaluate novelty over all individuals (this must be done after fitness/performance evaluation is complete for 
	// all members of the population so that we have the behaviour record of every individual in the population.
	// For this reason novelty is not evaluated via Minions.
	private void evaluateNoveltyMT(List<Chromosome> genotypes) {
		assert !isMinionInstance;
		
		for (Chromosome chrom : genotypes) {
			for (int n = 0; n < noveltyObjectiveCount; n++) {
				assert chrom.behaviours[n] != null;
				noveltyArchives[n].addToCurrentPopulation(chrom.behaviours[n]);
			}
		}
		
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
			} catch (InterruptedException e) {
				e.printStackTrace();
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
	
	private void finaliseEvaluation(Chromosome chrom) {
		assert !isMinionInstance;
		
		// We just set the overall fitness value according to the weightings. A different
		// selector (eg NSGA-II selector) may set the overall fitness to something else
		// based on the multiple objectives.
		// If novelty is to be assessed wait until this is done for all chromosomes before
		// calculating overall fitness.
		double overallFitness = 0;
		for (int i = 0; i < objectiveCount; i++) {
			overallFitness += chrom.getFitnessValue(i) * multiFitnessFunctionWeights[i];
		}
		chrom.setFitnessValue(overallFitness);

		// If the fitness function hasn't explicitly set any performance values, just set it to
		// overall fitness value.
		// if (chrom.getAllPerformanceValues().isEmpty() && !Double.isNaN(chrom.getFitnessValue())) {
		// chrom.setPerformanceValue(chrom.getFitnessValue());
		// }

		if (forcePerfFitness) {
			chrom.setPerformanceValue(chrom.getFitnessValue());
		}

		synchronized (this) {
			if ((targetPerformanceType == 1 && chrom.getPerformanceValue() > bestPerformance) || (targetPerformanceType == 0 && chrom.getPerformanceValue() < bestPerformance)) {
				bestPerformance = chrom.getPerformanceValue();
				newBestChrom = chrom;
			}
		}
	}

	/**
	 * <p>
	 * Evaluate an individual genotype on one fitness objective (if more than one fitness objective is defined by this
	 * fitness function and/or it defines a behaviour for novelty search then
	 * {@link #evaluate(Chromosome, Activator, int, double[], Behaviour[])} must be overridden instead). This method is
	 * called from {@link #evaluate(List)} and must be overridden in order to evaluate the genotypes.
	 * </p>
	 * <p>
	 * If the fitness function defines a performance value then this method should call
	 * {@link Chromosome#setPerformanceValue(double)}. In a multi-objective set-up only the performance defined by the
	 * primary fitness function will be used. If a performance value isn't defined it will be set to the overall fitness
	 * value. This default implementation returns 0.
	 * </p>
	 * <p>
	 * <em>Note:</em>This method may be called simultaneously by multiple threads (see {@link #MAX_THREADS_KEY}), thus
	 * any state variables that may change during the course of an evaluation must be independent for each thread that
	 * calls this method. The parameter evalThreadIndex may be used to determine which thread is calling this method or
	 * more simply state variables can be instantiated within the method. The total number of threads is
	 * {@link #numThreads}.
	 * <p>
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
	 * <p>
	 * For fitness functions that define multiple fitness objectives (see {@link #fitnessObjectivesCount()}) and/or
	 * behaviours for novelty search (see {@link #noveltyObjectiveCount()}), evaluate an individual genotype over all of
	 * these objectives. This default implementation simply calls {@link #evaluate(Chromosome, Activator, int)}. If the
	 * fitness function defines a performance value then this method should call
	 * {@link Chromosome#setPerformanceValue(double)}. In a multi-objective set-up (see {@link #MULTI_KEY}) only the
	 * performance defined by the primary fitness function will be used. If a performance value isn't defined it will be
	 * set to the (first) fitness value given by the primary fitness function.
	 * </p>
	 * <p>
	 * <em>Note:</em>This method may be called simultaneously by multiple threads (see {@link #MAX_THREADS_KEY}), thus
	 * any state variables that may change during the course of an evaluation must be independent for each thread that
	 * calls this method. The parameter evalThreadIndex may be used to determine which thread is calling this method or
	 * more simply state variables can be instantiated within the method. The total number of threads is
	 * {@link #numThreads}.
	 * <p>
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

	/**
	 * This implementation of
	 * {@link AHNIFitnessFunction#evaluateGeneralisation(Chromosome, Activator, String, boolean, boolean)} is marked as
	 * final to allow this class to handle multiple fitness functions, some of which may define multiple objectives.
	 * {@link BulkFitnessFunctionMT#evaluateGeneralisation(Chromosome, Activator, String, boolean, boolean, double[])}
	 * may be used instead.
	 */
	@Override
	public final boolean evaluateGeneralisation(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		assert !isMinionInstance;
		
		boolean atLeastOneGeneralisationEval = false;
		// Do secondary fitness functions first.
		double[][] fitnessValues = new double[multiFitnessFunctions.length + 1][];
		fitnessValues[0] = new double[fitnessObjectivesCount()];
		for (int i = 0; i < multiFitnessFunctions.length; i++) {
			fitnessValues[i + 1] = new double[multiFitnessFunctions[i].fitnessObjectivesCount()];
			BulkFitnessFunctionMT func = multiFitnessFunctions[i];
			atLeastOneGeneralisationEval |= func.evaluateGeneralisation(genotype, substrate, baseFileName, logText, logImage, fitnessValues[i + 1]);
		}
		// Do primary fitness function.
		atLeastOneGeneralisationEval |= evaluateGeneralisation(genotype, substrate, baseFileName, logText, logImage, fitnessValues[0]);

		if (atLeastOneGeneralisationEval) {
			// Assign fitness values to chromosome.
			for (int i = 0, fs = 0; i < fitnessValues.length; i++) {
				for (int f = 0; f < fitnessValues[i].length; f++, fs++) {
					genotype.setFitnessValue(fitnessValues[i][f], fs);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * This method is akin to
	 * {@link AHNIFitnessFunction#evaluateGeneralisation(Chromosome, Activator, String, boolean, boolean)} but allows
	 * for handling multiple fitness functions, some of which may define multiple objectives. This method may optionally
	 * be overridden to perform an evaluation that performs a generalisation test of an individual genotype and output
	 * the results to a log. The generalisation test should use training examples or environments that are not used for
	 * fitness evaluations. The evaluation is not used for any purpose other than generating a record of the
	 * generalisation performance of the given genotype. The function should set the performance values and fitness
	 * values of the given Chromosome using the functions setFitnessValue(...) and setPerformanceValue(...).
	 * 
	 * @param genotype the genotype being evaluated. This is not usually required but may be useful in some cases.
	 * @param substrate the phenotypic substrate of the genotype being evaluated.
	 * @param baseFileName The base/prefix of the names of log file(s) that should be created.
	 * @param logText If true then text representations should be output.
	 * @param logImage If true then image representations should be output.
	 * @param fitnessValues An array to hold the fitness value(s) as determined by this fitness function. The array will
	 *            have length {@link #fitnessObjectivesCount()}.
	 * @return Implementations of this method must return true. This default implementation returns false to indicate
	 *         that it's a dummy implementation.
	 */
	public boolean evaluateGeneralisation(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues) {
		assert !isMinionInstance;
		return false;
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

		protected Evaluator(int id, ThreadGroup tg) {
			super(tg, "FF Evaluator " + id);
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
								}
								// If the transcriber decided the substrate decoding was a dud then still allow reusing
								// the old substrate.
								else {
									substrate = previousSubstrate;
								}
							} catch (Exception e) {
								logger.warn("Exception during transcription or evaluation: " + e.getMessage());
								e.printStackTrace();
							}
						} else { // testingNovelty
							int fitnessSlot = objectiveCount - noveltyArchives.length;
							// May be empty if substrate decoding was a dud (see above).
							if (chrom.behaviours != null) {
								for (int n = 0; n < noveltyArchives.length; n++) {
									chrom.setFitnessValue(noveltyArchives[n].testNovelty(chrom.behaviours[n]), fitnessSlot++);
								}
								finaliseEvaluation(chrom);
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
					e.printStackTrace();
				}
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

	private class EvaluatorGroup extends ThreadGroup {
		public EvaluatorGroup(String name) {
			super(name);
		}

		public void uncaughtException(Thread t, Throwable e) {
			super.uncaughtException(t, e);
			System.exit(1);
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

			String fileName = props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + props.getProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, "") + "novelty_archive-" + n + ".png";
			noveltyArchives[n].archive.get(0).renderArchive(noveltyArchives[n].archive, fileName, this);
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
	
	

	private class MinionHandler extends Thread {
		private static final int RETRY_COUNT = 3;
		
		private int autoLaunchAttempts;
		private String host, user;
		private InetAddress address;
		private int port;
		private Socket socket;
		private ObjectInputStream in;
		private ObjectOutputStream out;
		private List<Chromosome> chromsToEval;
		private boolean lastEvalFailed = false;
		private int failCount = 0;
		private int averageMinionEvalTimePerChrom = 0;
		
		private volatile boolean finish = false;
		private volatile boolean connected = false;
		
		MinionHandler(String hostDef, int defaultPort, boolean autoLaunch) throws UnknownHostException, URISyntaxException {
			assert !isMinionInstance;
			
			// URI class wants a protocol before it will parse a URI, so just prepend ssh://
			URI uri = new URI("ssh://" + hostDef);
			this.host = uri.getHost();
			this.port = uri.getPort() == -1 ? defaultPort : uri.getPort();
			this.user = uri.getUserInfo();
			address = InetAddress.getByName(host);
			autoLaunchAttempts = autoLaunch ? 5 : 0;
			
			if (autoLaunch)
				launch();
			else
				connect();
			this.start();
		}
		
		/**
		 * Internal use only
		 */
		public void run() {
			while (!finish && failCount < RETRY_COUNT) {
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (!isConnected()) connect();
				
				if (failCount == RETRY_COUNT && autoLaunchAttempts > 0) {
					autoLaunchAttempts--;
					launch();
				}
			}
			if (failCount == RETRY_COUNT) {
				logger.error("Permanently gave up on " + this + ", retry limit exceeded.");
			}
		}
		
		/**
		 * Start a Minion instance on a remote machine. Assumes shared file system.
		 */
		public synchronized void launch() {
			try {
				File launchScript = new File("ahni-start-minion.sh");
				if (!launchScript.exists()) {
					String classpath = System.getProperty("java.class.path");
					boolean assertsEnabled = false;
					assert assertsEnabled = true;
					DataOutputStream dos = new DataOutputStream(new FileOutputStream(launchScript));
					dos.writeBytes("#!/bin/bash\n");
					dos.writeBytes("ssh $1 \"nohup java " + (assertsEnabled ? "-ea" : "") + " -cp \"" + classpath + "\" com.ojcoleman.ahni.evaluation.Minion --port $2 --log $3 > $4 &\"\n");
					dos.close();
					launchScript.setExecutable(true);
				}

				String minionLog = props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + props.getProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, "") + "minion." + host + ".log";
				String minionLaunchLog = props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + props.getProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, "") + "minion." + host + ".launch.log";
				Exec exec = new Exec("./ahni-start-minion.sh", (user != null ? user + "@" : "") + host, ""+port, minionLog, minionLaunchLog);
				
				if (exec.getExitStatus() == 0 && !exec.exceptionOccurred()) {
					failCount = 0; // reset
				}
				else {
					logger.error("Error starting " + this + ". Exit status: " + exec.getExitStatus() + ". " + (exec.exceptionOccurred() ? "Exception: " + exec.getException().getMessage() : ""));
				}
			} catch (Exception e) {
				logger.error("Error starting " + this + ": ", e);
				e.printStackTrace();
			}
		}
		
		private synchronized boolean connect() {
			connected = false;
			try {
				// Attempt to close old connection.
				if (socket != null) socket.close();
				// Connect to the instance.
				socket = new Socket(address, port);
				out = new ObjectOutputStream(socket.getOutputStream());
				in = new ObjectInputStream(socket.getInputStream());
				socket.setSoTimeout(Minion.DEFAULT_READ_TIMEOUT);
				// Configure the instance.
				StringWriter sw = new StringWriter();
				props.store(sw, "");
				String propsStr = sw.toString();
				propsStr += "\n" + Minion.MINION_INSTANCE + "=true";
				out.writeObject(new Minion.Request(Minion.Request.Type.CONFIGURE, propsStr));
				if (((Boolean) readFromMinion()).booleanValue()) {
					logger.info("Connected to " + this);
					connected = true;
					return true;
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			failCount++;
			logger.error("Unable to connect to or configure " + this);
			return false;
		}
		
		public boolean isConnected() {
			return connected;
		}
		
		private Object readFromMinion() throws Exception {
			Object response = in.readObject();
			if (response instanceof Exception) {
				connected = false;
				logger.error(this + " threw an exception: " + ((Exception) response).getMessage());
				throw (Exception) response;
			}
			return response;
		}
		
		public synchronized boolean initialiseEvaluation() {
			try {
				out.writeObject(new Minion.Request(Minion.Request.Type.INITIALISE_EVALUATION, props.getEvolver().getGeneration()));
				return ((Boolean) readFromMinion()).booleanValue();
			} catch (Exception e) {
				e.printStackTrace();
			}
			connected = false;
			logger.error("Initialise evaluation failed for " + this);
			return false;
		}
		
		public synchronized void setChromosomesToEvaluate(List<Chromosome> chroms) {
			chromsToEval = chroms;
		}
		public List<Chromosome> getChromosomesToEvaluate() {
			return chromsToEval;
		}
		
		private synchronized boolean evaluateChroms() {
			try {
				List<Chromosome> dummies = new ArrayList<Chromosome>();
				// Create dummy chromosomes that don't reference a Species to avoid 
				// serialisation of the Species and all the Chromosomes, etc that they contain.
				for (Chromosome c : chromsToEval) {
					Chromosome dummy = (Chromosome) c.clone();
					dummy.resetSpecie();
					dummies.add(dummy);
				}
				out.writeObject(new Minion.Request(Minion.Request.Type.EVALUATE, dummies));
				
				// Can take a while for evaluations to complete.
				// Wait twice as long as the longest average time for this minion.
				int avgTimePerChrom = averageMinionEvalTimePerChrom == 0 ? 10 * 60 * 1000 : averageMinionEvalTimePerChrom;
				socket.setSoTimeout(avgTimePerChrom * chromsToEval.size() * 2);
				try {
					long evalStart = System.currentTimeMillis();
					Object response = readFromMinion();
					long evalEnd = System.currentTimeMillis();
					socket.setSoTimeout(Minion.DEFAULT_READ_TIMEOUT);
					
					Iterator<Chromosome> chromsEvaluated = ((List<Chromosome>) response).iterator();
					for (Chromosome chrom : chromsToEval) {
						Chromosome evaluated = chromsEvaluated.next();
						assert chrom.getId() == evaluated.getId(); 
						chrom.setFitnessValue(evaluated.getFitnessValue());
						chrom.setFitnessValues(evaluated.getFitnessValues());
						chrom.setPerformanceValue(evaluated.getPerformanceValue());
						chrom.setPerformanceValues(evaluated.getAllPerformanceValues());
						chrom.behaviours = evaluated.behaviours;
					}
					lastEvalFailed = false;
					updateAverageMinionEvalTimePerChrom((int) (evalEnd - evalStart) / chromsToEval.size());
					return true;
				} catch (SocketTimeoutException e) {
					lastEvalFailed = true;
					socket.setSoTimeout(Minion.DEFAULT_READ_TIMEOUT);
					return false;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			lastEvalFailed = true;
			connected = false;
			failCount++;
			logger.error("Evaluation failed on " + this);
			return false;
		}
				
		public String toString() {
			return "Minion " + host + ":" + port + " (IP " + address.getHostAddress() + ")";
		}

		public synchronized boolean lastEvalFailed() {
			return lastEvalFailed;
		}
				
		public synchronized void dispose() {
			try {
				if (connected) {
					socket.setSoTimeout(500);
					out.writeObject(new Minion.Request(Minion.Request.Type.TERMINATE, null));
					socket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			finish = true;
			notifyAll();
		}
		
		private synchronized int getAverageEvalTimePerChrom() {
			return averageMinionEvalTimePerChrom;
		}
		
		private synchronized void updateAverageMinionEvalTimePerChrom(int time) {
			if (averageMinionEvalTimePerChrom == 0) {
				averageMinionEvalTimePerChrom = time;
			} else {
				averageMinionEvalTimePerChrom = (int) Math.round(averageMinionEvalTimePerChrom * 0.95 + time * 0.05);
			}
		}
		public synchronized void increaseAverageMinionEvalTime() {
			averageMinionEvalTimePerChrom *= 10;
		}
	}
}
