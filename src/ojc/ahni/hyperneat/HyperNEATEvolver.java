package ojc.ahni.hyperneat;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.imageio.ImageIO;

import ojc.ahni.integration.AHNIEvent;
import ojc.ahni.integration.AHNIEventListener;
import ojc.ahni.integration.AHNIRunProperties;
import ojc.ahni.integration.BainNN;

import org.apache.log4j.Logger;
import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.Genotype;
import org.jgapcustomised.Species;
import org.jgapcustomised.event.GeneticEvent;
import org.jgapcustomised.event.GeneticEventListener;

import com.anji.integration.*;
import com.anji.persistence.Persistence;
import com.anji.run.Run;
import com.anji.util.*;
import com.anji.neat.NeatChromosomeUtility;
import com.anji.neat.NeuronType;
import com.anji.nn.*;

/**
 * Configures and performs an AHNI evolutionary run.
 * @see Run
 * 
 * @author Oliver Coleman
 */
public class HyperNEATEvolver implements Configurable, GeneticEventListener {
	private static Logger logger = Logger.getLogger(HyperNEATEvolver.class);
	private static final Runtime runtime = Runtime.getRuntime();

	/**
	 * properties key, # generations in run
	 */
	public static final String NUM_GENERATIONS_KEY = "num.generations";
	public static final String PERFORMANCE_TARGET_TYPE_KEY = "performance.target.type";
	public static final String PERFORMANCE_TARGET_KEY = "performance.target";
	/**
	 * properties key, fitness function class
	 */
	public static final String FITNESS_FUNCTION_CLASS_KEY = "fitness_function";
	// private static final String FITNESS_THRESHOLD_KEY = "fitness.threshold";
	private static final String RESET_KEY = "run.reset";
	private static final String HIBERNATE_ENABLE_KEY = "hibernate.enable";
	private static final String LOGGING_ENABLE_KEY = "logging.enable";
	private static final String LOAD_GENOTYPE_KEY = "persist.load.genotype";
	private static final String PERSIST_ENABLE_KEY = "persist.enable";
	private static final String PRESENTATION_GENERATE_KEY = "presentation.generate";
	private static final String LOG_PER_GENERATIONS_KEY = "log.pergenerations";
	private static final String LOG_CHAMP_TOSTRING_KEY = "log.champ.tostring";
	private static final String LOG_CHAMP_TOIMAGE_KEY = "log.champ.toimage";
	
	private HyperNEATConfiguration config = null;
	private List<AHNIEventListener> listeners = new ArrayList<AHNIEventListener>();
	private AHNIRunProperties properties = null;
	private Genotype genotype = null;
	private int numEvolutions = 0;
	private double targetPerformance = 1;
	private int maxFitness = 0;
	private Persistence db = null;
	private boolean loadGenotypeFromDB = false;
	private BulkFitnessFunction bulkFitnessFunc;
	private int logPerGenerations = 1;
	int logChampToString = -1;
	int logChampToImage = -1;
	
	protected int generation = 0;
	protected Chromosome fittest = null;
	protected Chromosome bestPerforming = null;

	/**
	 * The fittest Chromosome from each generation.
	 */
	protected Chromosome[] fittestChromosomes;
	/**
	 * The best performing Chromosome from each generation. Sometimes this is equivalent to fittest depending on whether the fitness function defines
	 * performance independently.
	 */
	protected Chromosome[] bestPerformingChromosomes;
	/**
	 * The highest fitness achieved in each generation, as a fraction of the maximum possible fitness. This is maintained as well as fittestChromosomes because the fitness achieved can vary when the fitness function
	 * is stochastic.
	 */
	protected double[] bestFitnesses;
	/**
	 * The highest performance achieved in each generation. This is maintained as well as bestPerformingChromosomes because the performance achieved can vary when the fitness function
	 * is stochastic.
	 */
	protected double[] bestPerformances;

	
	/**
	 * ctor; must call {@link #init(Properties)} before using this object.
	 * Generally the 
	 */
	public HyperNEATEvolver() {
	}

	/**
	 * Construct new evolver with given properties. See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter
	 * Details </a> for specific property settings.
	 * 
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init(Properties props) throws Exception {
		properties = (AHNIRunProperties) props;
		properties.setEvolver(this);
		
		boolean doReset = props.getBooleanProperty(RESET_KEY, false);
		if (doReset) {
			// logger.warn( "Resetting previous run !!!" );
			Reset resetter = new Reset(props);
			resetter.setUserInteraction(false);
			resetter.reset();
		}

		config = (HyperNEATConfiguration) props.singletonObjectProperty(HyperNEATConfiguration.class);

		// peristence
		db = (Persistence) props.singletonObjectProperty(Persistence.PERSISTENCE_CLASS_KEY);

		loadGenotypeFromDB = props.getBooleanProperty(LOAD_GENOTYPE_KEY);

		numEvolutions = props.getIntProperty(NUM_GENERATIONS_KEY);
		targetPerformance = props.getFloatProperty(PERFORMANCE_TARGET_KEY, 1);
		// targetFitness = props.getFloatProperty(FITNESS_TARGET_KEY, 1);
		// thresholdFitness = props.getFloatProperty(FITNESS_THRESHOLD_KEY, targetFitness);
		logPerGenerations = props.getIntProperty(LOG_PER_GENERATIONS_KEY, 1);
		logChampToString = props.getIntProperty(LOG_CHAMP_TOSTRING_KEY, -1);
		logChampToImage = props.getIntProperty(LOG_CHAMP_TOIMAGE_KEY, -1);

		//
		// event listeners
		//

		// run
		// TODO - hibernate
		Run run = (Run) props.singletonObjectProperty(Run.class);
		if (props.getBooleanProperty(HIBERNATE_ENABLE_KEY, true)) {
			db.startRun(run.getName());
			config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_EVALUATED_EVENT, run);
		}

		if (props.getBooleanProperty(LOGGING_ENABLE_KEY, true)) {
			// logging
			LogEventListener logListener = new LogEventListener(config);
			config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_EVOLVED_EVENT, logListener);
			config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_EVALUATED_EVENT, logListener);
		}

		// persistence
		if (props.getBooleanProperty(PERSIST_ENABLE_KEY, false)) {
			System.out.println("\n\nhere2\n");
			PersistenceEventListener dbListener = new PersistenceEventListener(config, run);
			dbListener.init(props);
			config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_START_GENETIC_OPERATORS_EVENT, dbListener);
			config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_FINISH_GENETIC_OPERATORS_EVENT, dbListener);
			config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_EVALUATED_EVENT, dbListener);
		}
		// else {
		// config.load();
		// }

		// presentation
		if (props.getBooleanProperty(PRESENTATION_GENERATE_KEY, false)) {
			PresentationEventListener presListener = new PresentationEventListener(run);
			presListener.init(props);
			config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_EVALUATED_EVENT, presListener);
			config.getEventManager().addEventListener(GeneticEvent.RUN_COMPLETED_EVENT, presListener);
		}
		
		config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_START_EVALUATION_EVENT, this);
		config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_EVALUATED_EVENT, this);
		config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_START_GENETIC_OPERATORS_EVENT, this);
		config.getEventManager().addEventListener(GeneticEvent.GENOTYPE_FINISH_GENETIC_OPERATORS_EVENT, this);

		// fitness function
		bulkFitnessFunc = (BulkFitnessFunction) props.singletonObjectProperty(FITNESS_FUNCTION_CLASS_KEY);
		config.setBulkFitnessFunction(bulkFitnessFunc);
		maxFitness = bulkFitnessFunc.getMaxFitnessValue();
	}
	
	/**
	 * Get the configuration object, primarily used by JGAP and ANJI. 
	 * @see org.jgapcustomised.Configuration
	 */
	public HyperNEATConfiguration getConfig() {
		return config;
	}


	/**
	 * Add the given event listener to this evolver.
	 */
	public void addEventListener(AHNIEventListener listener) {
		listeners.add(listener);
	}
	/**
	 * Remove the given event listener from this evolver.
	 */
	public void removeEventListener(AHNIEventListener listener) {
		listeners.remove(listener);
	}
	protected void fireEvent(AHNIEvent event) {
		for (AHNIEventListener listener : listeners) {
			listener.ahniEventOccurred(event);
		}
	}


	/**
	 * Perform a single run. This is synchronised as the object contains 
	 * state variables (other than configurable properties) and so multiple runs should not be performed in parallel.
	 * This is in contrast to most {@link com.anji.util.Configurable} objects that do not contain state variables other than configurable properties.
	 * @return An array containing the best performance achieved in each generation.
	 * @throws Exception
	 */
	public synchronized double[] run() throws Exception {
		DecimalFormat nf4 = new DecimalFormat("0.0000");
		DecimalFormat nf1 = new DecimalFormat("0.0");

		fittestChromosomes = new Chromosome[numEvolutions];
		bestPerformingChromosomes = new Chromosome[numEvolutions];
		bestFitnesses = new double[numEvolutions];
		bestPerformances = new double[numEvolutions];
		
		if (loadGenotypeFromDB) {
			// load population, either from previous run or random
			genotype = db.loadGenotype(config);
			if (genotype != null) {
			} else {
				genotype = Genotype.randomInitialGenotype(properties, config);
			}
		} else {
			genotype = Genotype.randomInitialGenotype(properties, config);
		}

		// initialize result data
		int generationOfFirstSolution = -1;
		fittest = genotype.getFittestChromosome();

		File dirFile = new File(properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY));
		if (!dirFile.exists())
			dirFile.mkdirs();
		BufferedWriter speciesInfoWriter = new BufferedWriter(new FileWriter(properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "species-history-size.txt"));
		StringBuffer output = new StringBuffer();
		output.append("Gen,\tTSE,\tTS,\tNew,\tExt");
		for (int i = 0; i < 100; i++)
			output.append(",\t" + i);
		output.append("\n");
		speciesInfoWriter.write(output.toString());
		speciesInfoWriter.flush();

		double avgGenTime = 0;
		int previousSpeciesCount = 0;
		TreeMap<Long, Species> allSpeciesEver = new TreeMap<Long, Species>();
		long start = System.currentTimeMillis();
		
		fireEvent(new AHNIEvent(AHNIEvent.Type.RUN_START, this, this));

		for (generation = 0; generation < numEvolutions && !bulkFitnessFunc.endRun(); generation++) {
			fireEvent(new AHNIEvent(AHNIEvent.Type.GENERATION_START, this, this));

			previousSpeciesCount = genotype.getSpecies().size();

			// nextSequence generation
			fittest = genotype.evolve();
			bestPerforming = genotype.getBestPerforming();

			// result data
			if (bulkFitnessFunc.endRun())
				generationOfFirstSolution = generation;

			fittestChromosomes[generation] = fittest;
			bestPerformingChromosomes[generation] = bestPerforming;
			bestFitnesses[generation] = (double) fittest.getFitnessValue() / maxFitness;
			bestPerformances[generation] = bestPerforming.getPerformanceValue();

			int numSpecies = genotype.getSpecies().size();
			int minSpeciesSize = Integer.MAX_VALUE;
			int maxSpeciesSize = 0;
			int numSpeciesWithNewFittest = 0;
			int numNewSpecies = 0;
			int maxSpeciesAge = 0;
			int minSpeciesAge = Integer.MAX_VALUE;
			double avgBestSpeciesFitness = 0;
			Iterator<Species> speciesIter = genotype.getSpecies().iterator();
			while (speciesIter.hasNext()) {
				Species species = speciesIter.next();

				if (species.originalSize > maxSpeciesSize)
					maxSpeciesSize = species.originalSize;
				if (species.originalSize < minSpeciesSize)
					minSpeciesSize = species.originalSize;

				if (species.getAge() > maxSpeciesAge)
					maxSpeciesAge = species.getAge();
				if (species.getAge() < minSpeciesAge)
					minSpeciesAge = species.getAge();

				if (species.getFittest() != null)
					avgBestSpeciesFitness += species.getFittest().getFitnessValue();

				Long speciesKey = new Long(species.getID());
				if (allSpeciesEver.containsKey(speciesKey)) { // if existing species
					if (species.getFittest() != species.getPreviousFittest())
						numSpeciesWithNewFittest++;
				} else {
					numNewSpecies++;
					allSpeciesEver.put(speciesKey, species);
				}
			}
			avgBestSpeciesFitness /= ((long) numSpecies * maxFitness);
			int numExtinctSpecies = previousSpeciesCount - numSpecies + numNewSpecies;

			// write out some info about species history
			speciesIter = allSpeciesEver.values().iterator();
			output = new StringBuffer(generation + ",\t" + allSpeciesEver.size() + ",\t" + numSpecies + ",\t" + numNewSpecies + ",\t" + numExtinctSpecies);
			while (speciesIter.hasNext()) {
				Species species = speciesIter.next();
				output.append(",\t");
				output.append(species.originalSize);
			}
			output.append("\n");
			speciesInfoWriter.write(output.toString());
			speciesInfoWriter.flush();

			if (generation % logPerGenerations == 0) {
				double speciationCompatThreshold = genotype.getParameters().getSpeciationThreshold();

				long memTotal = Math.round(runtime.totalMemory() / 1048576);
				long memFree = Math.round(runtime.freeMemory() / 1048576);
				long memUsed = memTotal - memFree;

				double duration = (System.currentTimeMillis() - start) / 1000d;
				if (avgGenTime == 0)
					avgGenTime = duration;
				else
					avgGenTime = avgGenTime * 0.9 + duration * 0.1;
				int eta = (int) Math.round(avgGenTime * (numEvolutions - generation));

				logger.info("Gen: " + generation + "  Fittest: " + fittest.getId() + "  (F: " + nf4.format((double) fittest.getFitnessValue() / bulkFitnessFunc.getMaxFitnessValue()) + "  P: " + nf4.format(fittest.getPerformanceValue()) + ")" + "  Best perf: " + bestPerforming.getId() + "  (F: " + nf4.format((double) bestPerforming.getFitnessValue() / bulkFitnessFunc.getMaxFitnessValue()) + "  P: " + nf4.format(bestPerforming.getPerformanceValue()) + ")" + "  ZFC: " + genotype.getNumberOfChromosomesWithZeroFitnessFromLastGen() + "  ABSF: " + nf4.format(avgBestSpeciesFitness) + "  S: " + numSpecies + "  NS/ES: " + numNewSpecies + "/" + numExtinctSpecies + "  SCT: " + nf1.format(speciationCompatThreshold) + "  Min/Max SS: " + minSpeciesSize + "/" + maxSpeciesSize + "  Min/Max SA: " + minSpeciesAge + "/" + maxSpeciesAge + "  SNF: " + numSpeciesWithNewFittest + "  Time: " + (int) Math.round(duration) + "s  ETA: " + Misc.formatTimeInterval(eta) + "  Mem: " + memUsed + "MB");
				start = System.currentTimeMillis();
			}
			
			logChamp(bestPerforming);
			
			fireEvent(new AHNIEvent(AHNIEvent.Type.GENERATION_END, this, this));
		}
		
		fireEvent(new AHNIEvent(AHNIEvent.Type.RUN_END, this, this));
		
		speciesInfoWriter.close();

		// if evolution was terminated before the max number of gens was
		// performed (eg because solution was found sooner)
		if (generation != numEvolutions) {
			// fill in rest of array with last fitness and performance values (for stats generation later)
			double lastPerf = bestPerformances[generation - 1];
			double lastFitness = bestFitnesses[generation - 1];
			for (int i = generation; i < numEvolutions; i++) {
				bestPerformances[i] = lastPerf;
				bestFitnesses[i] = lastFitness;
			}
		}

		// run finish
		config.getEventManager().fireGeneticEvent(new GeneticEvent(GeneticEvent.RUN_COMPLETED_EVENT, genotype));
		logConclusion(generationOfFirstSolution, bestPerforming);
		
		bulkFitnessFunc.evolutionFinished(this);

		return bestPerformances;
	}
	
	
	/**
	 * Returns an array containing the fittest Chromosome from each generation.
	 */
	public Chromosome[] getFittestChromosomesForEachGen() {
		return fittestChromosomes;
	}
	/**
	 * Returns an array containing the best performing Chromosome from each generation. This may be equivalent to the fittest Chromosomes depending on whether the fitness function defines
	 * performance independently.
	 */
	public Chromosome[] getBesttPerformingChromosomesForEachGen() {
		return bestPerformingChromosomes;
	}
	/**
	 * Returns an array of the highest fitness achieved, as a fraction of the maximum possible fitness, in each generation. This is maintained as well as the fittest Chromosomes because the fitness achieved can vary when the fitness function
	 * is stochastic.
	 */
	public double[] getBestFitness() {
		return bestFitnesses;
	}
	/**
	 * Returns an array of the highest performance achieved in each generation. This is maintained as well as best performing Chromosomes because the performance achieved can vary when the fitness function
	 * is stochastic.
	 */
	public double[] getBestPerformance() {
		return bestPerformances;
	}

	/**
	 * Log summary data of run including generation in which the first solution occurred, and the champion of the final
	 * generation.
	 * 
	 * @param generationOfFirstSolution
	 * @param champ
	 */
	private void logConclusion(int generationOfFirstSolution, Chromosome champ) {
		logger.info("    Generation of first solution: " + generationOfFirstSolution);
		logger.info("    Final species compatability threshold: " + config.getSpeciationParms().getSpeciationThreshold());
		
		DecimalFormat nf3 = new DecimalFormat("0.000");
		logger.info("Best performance for this run (average per 10 gens): ");
		int i;
		String bpAvg = "";
		for (int generation = 0; generation < numEvolutions;) {
			double v = 0;
			for (i = 0; i < 10 && generation < numEvolutions; i++, generation++)
				v += bestPerformances[generation];
			v /= i;
			bpAvg += nf3.format(v) + ", ";
		}
		logger.info(bpAvg);
		logger.info("Best fitness for this run (average per 10 gens): ");
		String bfAvg = "";
		for (int generation = 0; generation < numEvolutions;) {
			double v = 0;
			for (i = 0; i < 10 && generation < numEvolutions; i++, generation++)
				v += bestFitnesses[generation];
			v /= i;
			bfAvg += nf3.format(v) + ", ";
		}
		logger.info(bfAvg);
		
		try {
			// Write out all the properties to make sure we can refer back to them later or re-run the experiment exactly (including random seed).
			BufferedWriter propsWriter = new BufferedWriter(new FileWriter(properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "run.properties"));
			propsWriter.write("# " + (new Date()).toString() + "\n");
			SortedSet<String> propNames = new TreeSet<String>(properties.stringPropertyNames());
			for (String key : propNames) {
				propsWriter.write(key + "=" + properties.getProperty(key) + "\n");
			}
			if (!properties.containsKey("random.seed")) {
				propsWriter.write("random.seed=" + ((Randomizer) properties.singletonObjectProperty(Randomizer.class)).getSeed() + "\n");
			}
			propsWriter.close();
		} catch (IOException e) {
			System.err.println("Error saving run properties to file.");
			e.printStackTrace();
		}
	}
	
	private void logChamp(Chromosome champ) {
		boolean finished = evolutionFinished();
		boolean logString = (finished && logChampToString >= 0) || (logChampToString > 0 && generation % logChampToString == 0);
		boolean logImage = (finished && logChampToImage >= 0) || (logChampToImage > 0 && generation % logChampToImage == 0);
		String msg = "best performing substrate from " + (finished ? "final generation" : "from generation " + generation);
		if (logString || logImage) {
			try {
				Transcriber transcriber = (Transcriber) properties.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
				Activator substrate = transcriber.transcribe(champ, null);
				
				if (logString) {
					BufferedWriter outputfile = new BufferedWriter(new FileWriter(properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "best_performing-" + (finished ? "final" : generation) + "-" + champ.getId() + ".txt"));
					outputfile.write("String representation of " + msg + ":\n" + substrate);
					outputfile.close();
				}
				
				if (logImage) {
					// Performance improvement for BainNN, don't create a buffered image unless the BainNN has recorded neuron coords.
					if (!(substrate instanceof BainNN) || ((BainNN) substrate).coordsEnabled()) { 
						BufferedImage image = new BufferedImage(800, 800, BufferedImage.TYPE_3BYTE_BGR);
						boolean success = substrate.render(image.createGraphics(), image.getWidth(), image.getHeight(), 30);
						if (success) {
							File outputfile = new File(properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "best_performing-" + (finished ? "final" : generation) + "-" + champ.getId() + ".png");
							ImageIO.write(image, "png", outputfile);
							logger.info("Rendered " + msg + " to " + outputfile);
						}
					}
				}
			} catch (TranscriberException e) {
				System.err.println("Error transcribing best performing individual.");
				e.printStackTrace();
			} catch (IOException e) {
				System.err.println("Error saving image of best performing network.");
				e.printStackTrace();
			}
		}
	}

	/**
	 * @return The fittest Chromosome from the last generation.
	 */
	public Chromosome getFittestFromLastGen() {
		return fittest;
	}
	
	/**
	 * @return The best performing Chromosome from the last generation. This may be equivalent to the fittest Chromosome depending on whether the fitness function defines
	 * performance independently.
	 */
	public Chromosome getBestPerformingFromLastGen() {
		return bestPerforming;
	}


	/**
	 * Fitness of given Chromosome in proportion to maximum possible fitness.
	 * @return maximum fitness value
	 */
	public double getProportionalFitness(Chromosome c) {
		return (c == null) ? 0 : (double) c.getFitnessValue() / config.getBulkFitnessFunction().getMaxFitnessValue();
	}

	/**
	 * @return target fitness value, 0 ... 1
	 */
	public double getTargetPerformance() {
		return targetPerformance;
	}

	public void dispose() {
		bulkFitnessFunc.dispose();
	}

	/**
	 * Provides a translation from and passes through {@link org.jgapcustomised.event.GeneticEvent}s
	 * to {@link ojc.ahni.integration.AHNIEvent}s via {@link ojc.ahni.integration.AHNIEventListener}s.
	 */ 
	@Override
	public void geneticEventFired(GeneticEvent event) {
		switch (event.getEventName()) {
		case GeneticEvent.GENOTYPE_START_EVALUATION_EVENT:
			fireEvent(new AHNIEvent(AHNIEvent.Type.EVALUATION_START, this, this));
			break;
		case GeneticEvent.GENOTYPE_EVALUATED_EVENT:
			fireEvent(new AHNIEvent(AHNIEvent.Type.EVALUATION_END, this, this));
			break;
		case GeneticEvent.GENOTYPE_START_GENETIC_OPERATORS_EVENT:
			fireEvent(new AHNIEvent(AHNIEvent.Type.REGENERATE_POPULATION_START, this, this));
			break;
		case GeneticEvent.GENOTYPE_FINISH_GENETIC_OPERATORS_EVENT:
			fireEvent(new AHNIEvent(AHNIEvent.Type.REGENERATE_POPULATION_END, this, this));
			break;
		}	
	}
	
	/**
	 * Returns the current generation number.
	 */
	public int getGeneration() {
		return generation;
	}
	
	/**
	 * Returns true iff evolution has finished or iff the current generation is the last one.
	 */
	public boolean evolutionFinished() {
		return generation >= numEvolutions-1 || bulkFitnessFunc.endRun();
	}
}
