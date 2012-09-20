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
 * created by Philip Tucker on Feb 16, 2003
 */
package com.anji.neat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;


import org.apache.log4j.Logger;
import org.jgapcusomised.BulkFitnessFunction;
import org.jgapcusomised.Chromosome;
import org.jgapcusomised.Genotype;
import org.jgapcusomised.Species;
import org.jgapcustomised.event.GeneticEvent;

import com.anji.integration.*;
import com.anji.persistence.Persistence;
import com.anji.run.Run;
import com.anji.util.*;
import com.anji.nn.*;

/**
 * Configures and performs an ANJI evolutionary run.
 * 
 * @author Philip Tucker
 */
public class Evolver implements Configurable {

	private static Logger logger = Logger.getLogger(Evolver.class);

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

	//private static final String FITNESS_THRESHOLD_KEY = "fitness.threshold";

	private static final String RESET_KEY = "run.reset";

	private static final String HIBERNATE_ENABLE_KEY = "hibernate.enable";
	
	private static final String LOGGING_ENABLE_KEY = "logging.enable";
	
	private static final String LOAD_GENOTYPE_KEY = "persist.load.genotype";

	private static final String PERSIST_ENABLE_KEY = "persist.enable";

	private static final String PRESENTATION_GENERATE_KEY = "presentation.generate";
	
	private static final String LOG_PER_GENERATIONS_KEY = "log.pergenerations";
	
	
	///**
	// * properties key, target fitness value - after reaching this run will halt
	// */
	//public static final String FITNESS_TARGET_KEY = "fitness.target";

	private NeatConfiguration config = null;
	private Properties props = null;

	private Chromosome fittest = null;
	private Chromosome bestPerforming = null;

	private Genotype genotype = null;

	private int numRuns = 0;

	private int numEvolutions = 0;

	//private float targetFitness = 0;

	//private float thresholdFitness = 0;
	
	private float targetPerformance = 1;

	private int maxFitness = 0;

	private Persistence db = null;

	private boolean loadGenotypeFromDB = false;

	static final Runtime runtime = Runtime.getRuntime();

	private BulkFitnessFunction bulkFitnessFunc;
	
	private int logPerGenerations = 1;
	
	float[] bestPerformance;
	float[] bestFitness;
	float[] bestPC;

	/**
	 * ctor; must call <code>init()</code> before using this object
	 */
	public Evolver() {
		super();
	}

	/**
	 * Construct new evolver with given properties. See <a href=" {@docRoot}
	 * /params.htm" target="anji_params">Parameter Details </a> for specific
	 * property settings.
	 * 
	 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
	 */
	public void init(Properties props) throws Exception {
		boolean doReset = props.getBooleanProperty(RESET_KEY, false);
		if (doReset) {
			// logger.warn( "Resetting previous run !!!" );
			Reset resetter = new Reset(props);
			resetter.setUserInteraction(false);
			resetter.reset();
		}
		
		this.props = props;
		config = new NeatConfiguration(props);

		// peristence
		db = (Persistence) props.singletonObjectProperty(Persistence.PERSISTENCE_CLASS_KEY);

		loadGenotypeFromDB = props.getBooleanProperty(LOAD_GENOTYPE_KEY);

		numEvolutions = props.getIntProperty(NUM_GENERATIONS_KEY);
		targetPerformance = props.getFloatProperty(PERFORMANCE_TARGET_KEY, 1);
		//targetFitness = props.getFloatProperty(FITNESS_TARGET_KEY, 1);
		//thresholdFitness = props.getFloatProperty(FITNESS_THRESHOLD_KEY, targetFitness);
		logPerGenerations = props.getIntProperty(LOG_PER_GENERATIONS_KEY, 1);
		

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
		

		// fitness function
		bulkFitnessFunc = (BulkFitnessFunction) props.singletonObjectProperty(FITNESS_FUNCTION_CLASS_KEY);
		config.setBulkFitnessFunction(bulkFitnessFunc);
		maxFitness = bulkFitnessFunc.getMaxFitnessValue();
	}

	/**
	 * command line usage
	 */
	private static void usage() {
		System.err.println("usage: <cmd> <properties-file>");
	}

	/**
	 * Perform a single run.
	 * 
	 * @throws Exception
	 */
	public float[] run() throws Exception {

		DecimalFormat nf4 = new DecimalFormat("0.0000");
		DecimalFormat nf3 = new DecimalFormat("0.000");
		DecimalFormat nf1 = new DecimalFormat("0.0");

		bestPerformance = new float[numEvolutions];
		bestFitness = new float[numEvolutions];
		bestPC = new float[numEvolutions];
		
		if (loadGenotypeFromDB) {
			// load population, either from previous run or random
			genotype = db.loadGenotype(config);
			if (genotype != null) {
				// logger.info( "genotype from previous run" );
			} else {
				genotype = Genotype.randomInitialGenotype(props, config);
				// logger.info( "random genotype (unable to load from DB)" );
			}
		} else {
			genotype = Genotype.randomInitialGenotype(props, config);
			// logger.info( "random genotype" );
		}

		// run start time
		Date runStartDate = Calendar.getInstance().getTime();
		// logger.info( "----- Run start -----  (RAM: total: " +
		// (runtime.totalMemory() / (1024*1024)) + "  free: " +
		// (runtime.freeMemory() / (1024*1024)) + "  used: " +
		// ((runtime.totalMemory() - runtime.freeMemory()) / (1024*1024)) +
		// ")");
		DateFormat fmt = new SimpleDateFormat("HH:mm:ss");

		// System.out.println("targetFitness = " + targetFitness);
		// System.out.println("maxFitness = " + maxFitness);

		// initialize result data
		int generationOfFirstSolution = -1;
		fittest = genotype.getFittestChromosome();
		
		
		File dirFile = new File(props.getProperty("output.dir"));
		if (!dirFile.exists())
			dirFile.mkdirs();
        BufferedWriter speciesInfoWriter = new BufferedWriter(new FileWriter(props.getProperty("output.dir") + "species-history-size.txt"));
        StringBuffer output = new StringBuffer();
        output.append("Gen,\tTSE,\tTS,\tNew,\tExt");
        for (int i = 0; i < 100; i++)
            output.append(",\t" + i);
        output.append("\n");
        speciesInfoWriter.write(output.toString());
        speciesInfoWriter.flush();
        
		double avgGenTime = 0;
		int previousSpeciesCount = 0;
		int generation;
		TreeMap<Long, Species> allSpeciesEver = new TreeMap<Long, Species>();
		long start = System.currentTimeMillis();
		
		//for (generation = 0; (generation < numEvolutions && (adjustedFitness < targetFitness || genotype.preventRunEnd())); ++generation) {
		for (generation = 0; generation < numEvolutions && !bulkFitnessFunc.endRun(); ++generation) {
			previousSpeciesCount = genotype.getSpecies().size();
			
			// generation start time
			// Date generationStartDate = Calendar.getInstance().getTime();
			// logger.info( "Generation " + generation + ": start" );

			// nextSequence generation
			fittest = genotype.evolve();
			
			bestPerforming = genotype.getBestPerforming();

			// result data
			//if (bestPerforming.getPerformanceValue() >= targetPerformance && generationOfFirstSolution == -1)
			if (bulkFitnessFunc.endRun())
				generationOfFirstSolution = generation;

			// champFitnesses[generation] = adjustedFitness;
			
			bestPerformance[generation] = bestPerforming.getPerformanceValue();
			bestFitness[generation] = (float)fittest.getFitnessValue() / bulkFitnessFunc.getMaxFitnessValue();
			
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
	            
	            if (species.originalSize > maxSpeciesSize) maxSpeciesSize = species.originalSize;
	            if (species.originalSize < minSpeciesSize) minSpeciesSize = species.originalSize;
	            
	            if (species.getAge() > maxSpeciesAge) maxSpeciesAge = species.getAge();
	            if (species.getAge() < minSpeciesAge) minSpeciesAge = species.getAge();
	            
	            if (species.getFittest() != null)
	            	avgBestSpeciesFitness += species.getFittest().getFitnessValue();
	            
	            Long speciesKey = new Long(species.getID());
	            if (allSpeciesEver.containsKey(speciesKey)) { //if existing species
	            	if (species.getFittest() != species.getPreviousFittest())
	            		numSpeciesWithNewFittest++;
	            }
	            else {
	            	numNewSpecies++;
	            	allSpeciesEver.put(speciesKey, species);
	            }
	        }
	        avgBestSpeciesFitness /= ((long) numSpecies * bulkFitnessFunc.getMaxFitnessValue());
	        int numExtinctSpecies = previousSpeciesCount - numSpecies + numNewSpecies;
			
	        
	    	//write out some info about species history
			speciesIter = allSpeciesEver.values().iterator();
			output = new StringBuffer(generation + ",\t" + allSpeciesEver.size() + ",\t" + numSpecies + ",\t" + numNewSpecies + ",\t" + numExtinctSpecies);
	        while (speciesIter.hasNext()) {
	            Species species = speciesIter.next();
	            //output += ",\t" + species.getID() + ":" + species.size();
	            output.append(",\t");
	            output.append(species.originalSize);
	        }
	        output.append("\n");
	        speciesInfoWriter.write(output.toString());
	        speciesInfoWriter.flush();
			
			if (generation % logPerGenerations == 0) {
				float speciationCompatThreshold = genotype.getParameters().getSpeciationThreshold();
				
				long memTotal = Math.round(runtime.totalMemory() / 1048576);
				long memFree = Math.round(runtime.freeMemory() / 1048576);
				long memUsed = memTotal - memFree;
				
				long duration = (System.currentTimeMillis() - start) / 1000;
				if (avgGenTime == 0)
					avgGenTime = duration;
				else
					avgGenTime = avgGenTime * 0.9 + duration * 0.1;
				int eta = (int) Math.round(avgGenTime * (numEvolutions - generation));
				
				// System.out.print(generation+"(" + (int)(adjustedFitness*100) + "," + genotype.getSpecies().size() + "), ");
				//System.out.println(generation + "(" + nf.format((float)fittest.getFitnessValue() / bulkFitnessFunc.getMaxFitnessValue()) + ", " + nf.format(bestPerformance[generation]) + ", " + duration + "s, " + memUsed + "M), ");
				// System.out.print((int)(adjustedFitness*100)+",");
				// System.out.println(generation+"(" + (int)(adjustedFitness*100) + " : " + champ.getFitnessValue() + "), ");
				
				logger.info("Gen: " + generation + 
						"  Fittest: " + fittest.getId() + "  (F: " + nf4.format((float)fittest.getFitnessValue() / bulkFitnessFunc.getMaxFitnessValue()) + "  P: " + nf4.format(fittest.getPerformanceValue()) + ")" + 
						"  Best perf: " + bestPerforming.getId() + "  (F: " + nf4.format((float)bestPerforming.getFitnessValue() / bulkFitnessFunc.getMaxFitnessValue()) + "  P: " + nf4.format(bestPerforming.getPerformanceValue()) + ")" + "  ABSF: " + nf4.format(avgBestSpeciesFitness) + 
						"  S: " + numSpecies + "  NS/ES: " + numNewSpecies + "/" + numExtinctSpecies + "  SCT: " + nf1.format(speciationCompatThreshold) + "  Min/Max SS: " + minSpeciesSize + "/" + maxSpeciesSize + "  Min/Max SA: " + minSpeciesAge + "/" + maxSpeciesAge + "  SNF: " + numSpeciesWithNewFittest + "  Time: " + duration + "s  ETA: " + Misc.formatTimeInterval(eta) + "  Mem: " + memUsed + "MB");
				
				
				start = System.currentTimeMillis();
			}
			
		
			// generation finish
			// Date generationEndDate = Calendar.getInstance().getTime();
			// long durationMillis = generationEndDate.getTime() -
			// generationStartDate.getTime();
			// logger.info( "Generation " + generation + ": end [" + fmt.format(
			// generationStartDate ) + " - " + fmt.format( generationEndDate ) +
			// "] [" + durationMillis + "]" );
		}
		System.out.println();

		speciesInfoWriter.close();

		// if evolution was terminated before the max number of gens was
		// performed (eg because solution was found sooner)
		if (generation != numEvolutions) {
			// fill in rest of array with last fitness and performance values (for stats generation later)
			float lastPerf = bestPerformance[generation - 1];
			for (int i = generation; i < numEvolutions; i++)
				bestPerformance[i] = lastPerf;
			
			float lastFitness = bestFitness[generation - 1];
			for (int i = generation; i < numEvolutions; i++)
				bestFitness[i] = lastFitness;
			
			float lastPC = bestPC[generation - 1];
			for (int i = generation; i < numEvolutions; i++)
				bestPC[i] = lastPC;
			
		}

		// run finish
		config.getEventManager().fireGeneticEvent(new GeneticEvent(GeneticEvent.RUN_COMPLETED_EVENT, genotype));
		logConclusion(generationOfFirstSolution, fittest);
		Date runEndDate = Calendar.getInstance().getTime();
		long durationMillis = runEndDate.getTime() - runStartDate.getTime();
		// logger.info( "Run: end [" + fmt.format( runStartDate ) + " - " +
		// fmt.format( runEndDate ) + "] [" + durationMillis +
		// "]  Best fitness: " + adjustedFitness);
		
		System.out.println();
		System.out.println();
		System.out.println("Best performance for this run (average per 10 gens): ");
		float v;
		int i;
		for (generation = 0; generation < numEvolutions;) {
			v = 0;
			for (i = 0; i < 10 && generation < numEvolutions; i++, generation++)
				v += bestPerformance[generation];
			v /= i;
			System.out.print(nf3.format(v) + ", ");
		}
		System.out.println();
		System.out.println("Best fitness for this run (average per 10 gens): ");
		for (generation = 0; generation < numEvolutions;) {
			v = 0;
			for (i = 0; i < 10 && generation < numEvolutions; i++, generation++)
				v += bestFitness[generation];
			v /= i;
			System.out.print(nf3.format(v) + ", ");
		}
		System.out.println();
		System.out.println();
		System.out.println();
		
		return bestPerformance;
	}
	
	public float[] getBestFitness() {
		return bestFitness;
	}
	
	public float[] getBestPerformance() {
		return bestPerformance;
	}

	public float[] getBestPC() {
		return bestPC;
	}

	/**
	 * Log summary data of run including generation in which the first solution
	 * occurred, and the champion of the final generation.
	 * 
	 * @param generationOfFirstSolution
	 * @param champ
	 */
	private void logConclusion(int generationOfFirstSolution, Chromosome champ) {
		logger.info("    generation of first solution: " + generationOfFirstSolution);
		logger.info("    champ # connections: " + NeatChromosomeUtility.getConnectionList(champ.getAlleles()).size());
		logger.info("    champ # hidden nodes: " + NeatChromosomeUtility.getNeuronList(champ.getAlleles(), NeuronType.HIDDEN).size());
		logger.info("    final species compat threshold: " + config.getSpeciationParms().getSpeciationThreshold());
	}

	/**
	 * Main program used to perform an evolutionary run.
	 * 
	 * @param args
	 *            command line arguments; args[0] used as properties file
	 * @throws Throwable
	 */
	/*
	 * public static void main( String[] args ) throws Throwable { try {
	 * //System.out.println( Copyright.STRING );
	 * 
	 * if ( args.length != 1 ) { usage(); System.exit( -1 ); }
	 * 
	 * Properties props = new Properties( args[ 0 ] ); Evolver evolver = new
	 * Evolver(); evolver.init( props ); evolver.run();
	 * 
	 * 
	 * 
	 * // HyperNEATTranscriber transcriber = new HyperNEATTranscriber(props); //
	 * GridNet net = transcriber.newGridNet(evolver.getChamp()); //
	 * System.out.println("Champ weights:"); //
	 * System.out.println(net.toString());
	 * 
	 * 
	 * // NeatActivator na = new NeatActivator(); // na.init( props ); //
	 * logger.info( "\n" + na.displayActivation( "" + evolver.getChamp().getId()
	 * ) );
	 * 
	 * System.exit( 0 ); } catch ( Throwable th ) { logger.error( "", th );
	 * throw th; } }
	 */
	/**
	 * @return champion of last generation
	 */
	public Chromosome getChamp() {
		return fittest;
	}

	/**
	 * Fitness of current champ, 0 ... 1
	 * 
	 * @return maximum fitness value
	 */
	public float getChampAdjustedFitness() {
		return (fittest == null) ? 0 : (float) fittest.getFitnessValue() / config.getBulkFitnessFunction().getMaxFitnessValue();
	}

	/**
	 * @return target fitness value, 0 ... 1
	 */
	public float getTargetPerformance() {
		return targetPerformance;
	}

	///**
	// * @return threshold fitness value, 0 ... 1
	// */
	//public float getThresholdFitness() {
	//	return thresholdFitness;
	//}
	
	public void dispose() {
		bulkFitnessFunc.dispose();
	}

}
