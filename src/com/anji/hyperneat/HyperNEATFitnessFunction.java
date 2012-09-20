package com.anji.hyperneat;

import java.util.*;
import org.apache.log4j.Logger;
import org.jgap.*;

import com.anji.hyperneat.GridNet;
import com.anji.hyperneat.HyperNEATTranscriber;
import com.anji.integration.*;
import com.anji.util.*;
import com.anji.util.Properties;
import com.anji.neat.Evolver;


/**
 * Determines fitness based on how close <code>Activator</code> output is to a target.
 *
 * @author Oliver Coleman
 */
public abstract class HyperNEATFitnessFunction implements BulkFitnessFunction, Configurable {
	private static final long serialVersionUID = 1L;
    
	/**
	 * fitness.hyperneat.max_threads
	 * Maximum number of threads to use for fitness evaluation (including 
	 * transcription of genotype/cppn to phenotype/substrate).
	 * If value is <= 0 then the detected number of processor cores will be used.
	 */
	public static final String MIN_THREADS_KEY = "fitness.hyperneat.min_threads";
	public static final String MAX_THREADS_KEY = "fitness.hyperneat.max_threads";
	
	public static final String SCALE_FACTOR_KEY = "fitness.hyperneat.scale.factor";
	public static final String SCALE_TIMES_KEY = "fitness.hyperneat.scale.times";
	public static final String SCALE_PERFORMANCE_KEY = "fitness.hyperneat.scale.performance";
	public static final String SCALE_RIP_KEY = "fitness.hyperneat.scale.recordintermediateperformance";
	
	protected Properties props;
	
    private HyperNEATTranscriber transcriber;
    private int numThreads;
    private int evaluatorsFinishedCount;
    private Evaluator[] evaluators;
    private Iterator<Chromosome> chromosomesIterator;
    protected int generation;
    
    protected static Logger logger = Logger.getLogger( HyperNEATFitnessFunction.class );
	protected Random random;
	/**
     * Dimensions of each layer in the substrate network.
     */
    protected int width[], height[];
    protected int depth;
    protected int connectionRange; 
    
    private int scaleFactor = 2;
    private int scaleTimes = 2;
    protected float scalePerformance = 0.98f;
    protected int scaleCount = 0;
    private boolean endRun = false;
    private boolean scaleRecordIntermediatePerf = true;
    
    private float bestPerformance;
    protected float lastBestPerformance;
    protected Chromosome lastBestChrom;
    protected Chromosome newBestChrom;
    
    protected int targetPerformanceType = 1;
    protected float targetPerformance; //may be used by sub-classes
    
    
	

    /**
     * See <a href=" {@docRoot}/params.htm" target="anji_params">Parameter Details </a> for
     * specific property settings.
     * 
     * IF YOU OVERRIDE THIS METHOD make sure to call super.init(props) in the
     * over-riding method.
     *
     * @param props configuration parameters
     */
    public void init(Properties props) {
        try {
        	this.props = props;
        	
            random = ((Randomizer) props.singletonObjectProperty( Randomizer.class )).getRand();
            transcriber = new HyperNEATTranscriber(props);
            depth = transcriber.getDepth();
            height = transcriber.getHeight();
            width = transcriber.getWidth();
            connectionRange = transcriber.getConnectionRange();
            
            
            targetPerformance = props.getFloatProperty(Evolver.PERFORMANCE_TARGET_KEY, 1);
            targetPerformanceType = props.getProperty(Evolver.PERFORMANCE_TARGET_TYPE_KEY, "higher").equals("higher") ? 1 : 0;
            scalePerformance = props.getFloatProperty(SCALE_PERFORMANCE_KEY, scalePerformance);
            scaleTimes = props.getIntProperty(SCALE_TIMES_KEY, scaleTimes);
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
        catch ( Exception e ) {
            throw new IllegalArgumentException( "invalid properties: " + e.getClass().toString() + ", message: "
                    + e.getMessage() );
        }
    }

    /**
     * @return maximum possible fitness value for this function.
     */
    abstract public int getMaxFitnessValue();
    
    /**
     * If required, initialise data for the current evaluation run.
     * This method is called at the beginning of evaluate(List genotypes).
     * It should be over-ridden if data (eg input and/or output patterns)
     * needs to be set-up before every evaluation run.
     * */
    public void initialiseEvaluation() { }
    
    
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
        
        while(true) {
            try {
                synchronized(this) {
                    if (evaluatorsFinishedCount == evaluators.length)
                        break;
                    wait();
                }
            } catch (InterruptedException ignore) {System.out.println(ignore);}
        }
        
        lastBestChrom = newBestChrom;
        lastBestPerformance = bestPerformance;
        
        endRun = false;
        //if we should scale the substrate
        if (scaleCount < scaleTimes && scaleFactor > 1 && 
        		((targetPerformanceType == 1 && bestPerformance >= scalePerformance) || (targetPerformanceType == 0 && bestPerformance <= scalePerformance))
        	) {
        	//allow sub-class to make necessary changes
        	scale(scaleCount, scaleFactor);
        	for (Evaluator ev : evaluators)
        		ev.resetSubstrate(); //don't reuse old size substrate
        	transcriber.resize(width, height, connectionRange);
        	        	
        	scaleCount++;
        }
        
        if ((targetPerformanceType == 1 && bestPerformance >= targetPerformance) || (targetPerformanceType == 0 && bestPerformance <= targetPerformance)) {
        	System.out.println("End run, solution found. bestPerformance: " + bestPerformance + ", targetPerformance: " + targetPerformance);
        	endRun = true;
        }
        
        generation++;
    }
 
    
    /**
     * Evaluate an individual genotype. This method is called while running 
     * evaluate(List&lt;Chromosome&gt; genotypes), and must be ovver-ridden
     * in order to evaluate the genotypes.
     * @param genotype the genotype being evaluated (not necessarily used)
     * @param substrate the phenotypic substrate of the genotype being evaluated
     */ 
    protected abstract int evaluate(Chromosome genotype, GridNet substrate, int evalThreadIndex);
    
    
    /**
     * Allow sub-class to make necessary changes when a substrate scale occurs.
     * When this method is called the substrate will already have been scaled,
     * so the width and height variables will have the new (scaled) values.
     * @param scaleCount A count of how many times a scale has previously 
     * occurred. In the first call this has value 0.
     * @param scaleFactor The amount the substrate is being scaled by. 
     */ 
    protected abstract void scale(int scaleCount, int scaleFactor);
    
    
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
        //System.out.println("finishedEvaluating: " + evaluatorsFinishedCount);
        notifyAll();
        //System.out.println("finishedEvaluating exit");
    }

    private class Evaluator extends Thread {
        private volatile boolean go = false;
        private int id;
        private GridNet substrate;

        public Evaluator(int id) {
            this.id = id;
            substrate = null;
        }
        
        public void resetSubstrate() {
        	substrate = null;
        }

        public void run() {
            for ( ; ; ) {
                while (go) {
                    Chromosome chrom;
                    while ((chrom = getNextChromosome()) != null) {
                    	try {
                    		substrate = transcriber.transcribe(chrom, substrate);
                    		int fitness = evaluate(chrom, substrate, id);
                        	chrom.setFitnessValue(fitness);
                        	synchronized(this) {
                        		if ((targetPerformanceType == 1 && chrom.getPerformanceValue() > bestPerformance) || (targetPerformanceType == 0 && chrom.getPerformanceValue() < bestPerformance)) {
                        			bestPerformance = chrom.getPerformanceValue();
                        			newBestChrom = chrom;
                        		}
                        	}
                        	//only record performance when all scales have been performed
                        	if (!scaleRecordIntermediatePerf && scaleCount < scaleTimes)
                        		chrom.setPerformanceValue(0);
                    	}
                    	catch ( TranscriberException e ) {
                            logger.warn("transcriber error: " + e.getMessage());
                            chrom.setFitnessValue(1);
                            chrom.setPerformanceValue(0);
                        }
                    }
                    
                    go = false;
                    //System.out.println("ev " + id + " finished");
                    finishedEvaluating();
                }
                try {
                    synchronized(this) {
                        //System.out.println("ev " + id + " wait");
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
            //System.out.println("ev " + id + " go");
            notifyAll();
        }
    }
    
    public void dispose() {}
}

