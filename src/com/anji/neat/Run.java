package com.anji.neat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.*;
import java.util.Random;


import org.apache.log4j.Logger;

import com.anji.util.Misc;
import com.anji.util.Properties;


public class Run {
	private static Logger logger = Logger.getLogger(Run.class.getName());
	
	private static final String NUM_RUNS_KEY = "num.runs";
	private static final String NUM_GENS_KEY = "num.generations";
	private static final DecimalFormat nf = new DecimalFormat("0.0000");

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if ( args.length == 0 ) {
				usage();
				System.exit(-1);
			}
			
			Properties props = new Properties(args[0]);
			
			long experimentID = System.currentTimeMillis();
			String outputDir = props.getProperty("output.dir") + File.separatorChar + experimentID + File.separatorChar;
			String resultFileNameBase = outputDir;
			if (args.length > 1)
				resultFileNameBase += args[1];
			else 
				resultFileNameBase += "results";
			
			logger.info("Output directory is " + outputDir + ".");
			logger.info("Performance results will be written to " + resultFileNameBase + "-[performance|fitness].");
			
			int numRuns = props.getIntProperty(NUM_RUNS_KEY);
            int numGens = props.getIntProperty(NUM_GENS_KEY);
            
	        float[][] performance;
	        float[][] fitness;
	        float[][] percentCorrect;
	        
	        performance = new float[numRuns][];
            fitness = new float[numRuns][];
            percentCorrect = new float[numRuns][];
            
            //float[][] champFitnesses = new float[numRuns][];
            
            //float cumulativeFinalChampPerf = 0;
            long start = System.currentTimeMillis();
            double avgRunTime = 0;
            for (int run = 0; run < numRuns; run++) {
            	long startRun = System.currentTimeMillis();
                
            	props = new Properties(args[0]);
                props.setProperty("run.id", props.getProperty("run.name") + "-" + experimentID);
                props.setProperty("output.dir", outputDir);
                
            	//System.out.print("run: " + run + "\t");
            	logger.info("\n\n--- START RUN: " + (run + 1) + " of " + numRuns + " (" + ((run*100)/(numRuns)) + "%) ---------------------------------------\n\n");
                Evolver evolver = new Evolver();
                evolver.init( props );
                //champFitnesses[run] = evolver.run();
                evolver.run();
                
                performance[run] = evolver.getBestPerformance();
                fitness[run] = evolver.getBestFitness();
                percentCorrect[run] = evolver.getBestPC();
                
                evolver.dispose();
                
                //cumulativeFinalChampPerf += performance[pan][pac][run][numGens-1];
                //System.out.println(", avg so far: " + (cumulativeFinalChampPerf / (run+1)));
                             
                //float finalChampFitness = champFitnesses[run][champFitnesses[run].length-1];

                //bfmin = Math.min(bfmin, finalChampFitness);
                //bfmax = Math.max(bfmax, finalChampFitness);
                //bfavg += finalChampFitness;
                
                long duration = (System.currentTimeMillis() - startRun) / 1000;
				if (avgRunTime == 0)
					avgRunTime = duration;
				else
					avgRunTime = avgRunTime * 0.9 + duration * 0.1;
				int eta = (int) Math.round(avgRunTime * (numRuns - (run+1)));
				logger.info("\n--- Run finished in " + Misc.formatTimeInterval(duration) +".  ETA to complete all runs:" + Misc.formatTimeInterval(eta) + ". ------------------\n");
            }
            long end = System.currentTimeMillis();
            //System.out.println(numRuns + " runs completed in " + nf.format((end - start) / 1000.0) + "s");
            logger.info(numRuns + " runs completed in " + Misc.formatTimeInterval((end - start) / 1000));
            //bfavg /= numRuns;

            //for (int run = 0; run < numRuns; run++) {
            //    System.out.print("Run " + run);
            //    for ( int generation = 0; generation < champFitnesses[run].length; generation++) {
            //        System.out.print("," + Math.round(champFitnesses[run][generation] * 100));
            //    }
            //    System.out.println();
            //}
            
            //print results
            BufferedWriter resultFilePerf = new BufferedWriter(new FileWriter(resultFileNameBase + "-avg_performance_in_each_gen_over_all_runs.txt"));
            BufferedWriter resultFileFit = new BufferedWriter(new FileWriter(resultFileNameBase + "-avg_fitness_in_each_gen_over_all_runs.txt"));
            BufferedWriter resultFilePC = new BufferedWriter(new FileWriter(resultFileNameBase + "-avg_percent_corrent_in_each_gen_over_all_runs.txt"));
            //float[] minFitness = new float[numGens];
            float[] avgPerf = new float[numGens];
            float[] avgFit = new float[numGens];
            float[] avgPC = new float[numGens];
            //float[] maxFitness = new float[numGens];
            float p, f, pc;
            for (int gen = 0; gen < numGens; gen++) {
            	//minFitness[gen] = Double.MAX_VALUE;
            	//maxFitness[gen] = Double.MIN_VALUE;
            	avgPerf[gen] = 0;
            	avgFit[gen] = 0;
            	avgPC[gen] = 0;
	            for (int run = 0; run < numRuns; run++) {
            		p = performance[run][gen];
            		f = fitness[run][gen];
            		pc = percentCorrect[run][gen];
            		//minFitness[gen] = Math.min(minFitness[gen], f);
	            	//maxFitness[gen] = Math.max(maxFitness[gen], f);
	            	avgPerf[gen] += p;
	            	avgFit[gen] += f;
	            	avgPC[gen] += pc;
	            }
	            avgPerf[gen] /= numRuns;
	            avgFit[gen] /= numRuns;
	            avgPC[gen] /= numRuns;
            }
            
            //for (int gen = 0; gen < numGens; gen++)
	        //    System.out.print(minFitness[gen] + "\t");
            //System.out.println();
            
            logger.info("Average performance for each gen over " + numRuns + " runs:");
            String results = "";
            for (int gen = 0; gen < numGens; gen++)
	            results += nf.format(avgPerf[gen]) + ", ";
            logger.info(results);
            resultFilePerf.write(results + "\n");
            resultFilePerf.close();
            
            logger.info("Average fitness for each gen over " + numRuns + " runs:");
            results = "";
            for (int gen = 0; gen < numGens; gen++)
	            results += nf.format(avgFit[gen]) + ", ";
            logger.info(results);
            resultFileFit.write(results + "\n");
            resultFileFit.close();
            
            logger.info("Average percent correct for each gen over " + numRuns + " runs:");
            results = "";
            for (int gen = 0; gen < numGens; gen++)
	            results += nf.format(avgPC[gen]) + ", ";
            logger.info(results);
            resultFilePC.write(results + "\n");
            resultFilePC.close();
            
            
            //for (int gen = 0; gen < numGens; gen++)
	        //    System.out.print(maxFitness[gen] + "\t");
            //System.out.println();
	        
	        
//	        HyperNEATTranscriber transcriber = new HyperNEATTranscriber(props);
//	        GridNet net = transcriber.newGridNet(evolver.getChamp());
//	        System.out.println("Champ weights:");
//	        System.out.println(net.toString());


//	        NeatActivator na = new NeatActivator();
//			na.init( props );
//			logger.info( "\n" + na.displayActivation( "" + evolver.getChamp().getId() ) );

			System.exit( 0 );
		}
		catch (Throwable th ) {
	 	  logger.error(th);
          th.printStackTrace();
		}
	}
	
	
	/**
	 * command line usage
	 */
	private static void usage() {
		System.err.println( "usage: <cmd> <properties-file> [result file name]" );
	}
}
