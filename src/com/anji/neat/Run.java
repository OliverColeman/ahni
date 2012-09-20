package com.anji.neat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.*;
import java.util.Random;

import ojc.util.Misc;

import org.apache.log4j.Logger;

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
			String resultFileNameBase = "";
			if (args.length > 1)
				resultFileNameBase = args[1];
			else 
				resultFileNameBase = "results-" + System.currentTimeMillis() + ".txt";
			
			Properties props = new Properties(args[0]);
			
			logger.info("Performance results will be written to " + resultFileNameBase + "-[performance|Fitness]");
			
			int numRuns = props.getIntProperty(NUM_RUNS_KEY);
            int numGens = props.getIntProperty(NUM_GENS_KEY);
            
			// varying pan and pac not being used at the moment, use values in config file
	        float panMin = props.getFloatProperty("add.neuron.mutation.rate");
	        float panMax = props.getFloatProperty("add.neuron.mutation.rate") + 0.000001f;
	        float pacMin = props.getFloatProperty("add.connection.mutation.rate");
	    	float pacMax = props.getFloatProperty("add.connection.mutation.rate") + 0.000001f;
	    	
	        float[] probAddNeuron = new float[100];
	        int i = 0;
	        //for (float p = 0.02f; p < 0.081f; p *= Math.pow(2, 0.25)) {
	        for (float p = panMin; p < panMax; p *= Math.pow(2, 0.5)) {
	            probAddNeuron[i++] = p;
	            //System.out.print(p + ",  ");
	        }
	        int probAddNeuronCount = i;
	        //System.out.println();
	
	        float[] probAddConn = new float[100];
	        i = 0;
	        //for (float p = 0.08f; p < 0.321f; p *= Math.pow(2, 0.25)) {
	        for (float p = pacMin; p < pacMax; p *= Math.pow(2, 0.5)) {
	            probAddConn[i++] = p;
	            //System.out.print(p + ",  ");
	        }
	        int probAddConnCount = i;
	        //System.out.println();

        
	        float[][][][] performance = new float[probAddNeuronCount][probAddConnCount][][];
	        float[][][][] fitness = new float[probAddNeuronCount][probAddConnCount][][];
	        float[][][][] percentCorrect = new float[probAddNeuronCount][probAddConnCount][][];
			//float[][] bestFitnessMin = new float[probAddNeuronCount][probAddConnCount];
            //float[][] bestFitnessAvg = new float[probAddNeuronCount][probAddConnCount];
            //float[][] bestFitnessMax = new float[probAddNeuronCount][probAddConnCount];

            //int totalCombos = probAddNeuronCount * probAddConnCount;
            int combosDone = 0;
            for (int pan = 0; pan < probAddNeuronCount; pan++) {
                for (int pac = 0; pac < probAddConnCount; pac++) {
                    //System.out.print("pan=" + nf.format(probAddNeuron[pan]) + "  pac=" + nf.format(probAddConn[pac]) + "  ");
                    //System.out.println();
                    		
                    performance[pan][pac] = new float[numRuns][];
                    fitness[pan][pac] = new float[numRuns][];
                    percentCorrect[pan][pac] = new float[numRuns][];
                    
                    //float[][] champFitnesses = new float[numRuns][];
                    
                    //float bfmin = Double.MAX_VALUE;
                    //float bfavg = 0;
                    //float bfmax = Double.MIN_VALUE;
                    float cumulativeFinalChampPerf = 0;
                    long start = System.currentTimeMillis();
                    double avgRunTime = 0;
                    for (int run = 0; run < numRuns; run++) {
                    	long startRun = System.currentTimeMillis();
                        
                    	props = new Properties(args[0]);
                        props.setProperty("add.neuron.mutation.rate", ""+probAddNeuron[pan]);
                        props.setProperty("add.connection.mutation.rate", ""+probAddConn[pac]);
                        
                        String runID = props.getProperty("run.name") + "-" + System.currentTimeMillis();
                        String outputDir = props.getProperty("fitness_function.class") + File.separatorChar + runID + File.separatorChar;
                        props.setProperty("run.id", ""+runID);
                        props.setProperty("output.dir", outputDir);

                    	//System.out.print("run: " + run + "\t");
                    	logger.info("\n\n--- START RUN: " + (run + 1) + " of " + numRuns + " (" + ((run*100)/(numRuns)) + "%) ---------------------------------------\n\n");
                        Evolver evolver = new Evolver();
                        evolver.init( props );
                        //champFitnesses[run] = evolver.run();
                        evolver.run();
                        
                        performance[pan][pac][run] = evolver.getBestPerformance();
                        fitness[pan][pac][run] = evolver.getBestFitness();
                        percentCorrect[pan][pac][run] = evolver.getBestPC();
                        
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
                    //bestFitnessMin[pan][pac] = bfmin;
                    //bestFitnessMax[pan][pac] = bfmax;
                    //bestFitnessAvg[pan][pac] = bfavg;

                    combosDone++;
                    //System.out.println("\n" + ((combosDone*100) / totalCombos) + "% combos complete. min=" + bfmin + "  avg=" + bfavg + "  max=" + bfmax);

                    /*

                    for (int run = 0; run < numRuns; run++) {
                        System.out.print("Run " + run);
                        for ( int generation = 0; generation < champFitnesses[run].length; generation++) {
                            System.out.print("," + Math.round(champFitnesses[run][generation] * 100));
                        }
                        System.out.println();
                    }
                     */
                }
            }
            
            
            //print results
            BufferedWriter resultFilePerf = new BufferedWriter(new FileWriter(resultFileNameBase + "-performance"));
            BufferedWriter resultFileFit = new BufferedWriter(new FileWriter(resultFileNameBase + "-fitness"));
            BufferedWriter resultFilePC = new BufferedWriter(new FileWriter(resultFileNameBase + "-pc"));
            for (int pan = 0; pan < probAddNeuronCount; pan++) {
                for (int pac = 0; pac < probAddConnCount; pac++) {
                	//System.out.println("pan=" + nf.format(probAddNeuron[pan]) + "  pac=" + nf.format(probAddConn[pac]) + "  ");
                	
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
		            		p = performance[pan][pac][run][gen];
		            		f = fitness[pan][pac][run][gen];
		            		pc = percentCorrect[pan][pac][run][gen];
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
                }
            }
	        
	        
//	        HyperNEATTranscriber transcriber = new HyperNEATTranscriber(props);
//	        GridNet net = transcriber.newGridNet(evolver.getChamp());
//	        System.out.println("Champ weights:");
//	        System.out.println(net.toString());


//	        NeatActivator na = new NeatActivator();
//			na.init( props );
//			logger.info( "\n" + na.displayActivation( "" + evolver.getChamp().getId() ) );

            /*
            System.out.println("\n\nMinimums");
            System.out.print("\t");
            for (int pac = 0; pac < probAddConnCount; pac++)
                System.out.print("\t" + nf.format(probAddConn[pac]) + ",");
            System.out.println();
            for (int pan = 0; pan < probAddNeuronCount; pan++) {
                System.out.print(nf.format(probAddNeuron[pan]) + ",\t");
                for (int pac = 0; pac < probAddConnCount; pac++) {
                    System.out.print(nf.format(bestFitnessMin[pan][pac]) + ",\t");
                }
                System.out.println();
            }

            System.out.println("\n\nAverages");
            System.out.print("\t");
            for (int pac = 0; pac < probAddConnCount; pac++)
                System.out.print("\t" + nf.format(probAddConn[pac]) + ",");
            System.out.println();
            for (int pan = 0; pan < probAddNeuronCount; pan++) {
                System.out.print(nf.format(probAddNeuron[pan]) + ",\t");
                for (int pac = 0; pac < probAddConnCount; pac++) {
                    System.out.print(nf.format(bestFitnessAvg[pan][pac]) + ",\t");
                }
                System.out.println();
            }

            System.out.println("\n\nMaximums");
            System.out.print("\t");
            for (int pac = 0; pac < probAddConnCount; pac++)
                System.out.print("\t" + nf.format(probAddConn[pac]) + ",");
            System.out.println();
            for (int pan = 0; pan < probAddNeuronCount; pan++) {
                System.out.print(nf.format(probAddNeuron[pan]) + ",\t");
                for (int pac = 0; pac < probAddConnCount; pac++) {
                    System.out.print(nf.format(bestFitnessMax[pan][pac]) + ",\t");
                }
                System.out.println();
            }
*/

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
