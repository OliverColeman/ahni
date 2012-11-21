package ojc.ahni.hyperneat;

import java.util.*;

import ojc.ahni.integration.BulkFitnessFunctionMT;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.*;
import com.anji.util.*;
import com.anji.util.Properties;
import com.anji.neat.Evolver;

/**
 * <p>Provides a base for fitness functions for use with HyperNEAT. Provides a multi-threaded framework for performing
 * evaluations on multiple genomes. The methods {@link #getMaxFitnessValue()} and
 * {@link #evaluate(Chromosome, Activator, int)} must be implemented in subclasses. Subclasses may also need to override
 * the methods {@link #init(Properties)}, {@link #initialiseEvaluation()},
 * {@link #postEvaluate(Chromosome, Activator, int)}, {@link #scale(int, int)} and {@link #dispose()}.</p>
 * 
 * <p>Subclasses may wish to override {@link #evolutionFinished(ojc.ahni.hyperneat.HyperNEATEvolver)} to perform testing or other analysis
 * on the fittest and/or best performing Chromosomes evolved during the run; the method
 * {@link #generateSubstrate(Chromosome, Activator)} may be used to create substrates for a Chromosome.</p>
 * 
 * <p>See
 * {@link ojc.ahni.experiments.TestTargetFitnessFunction} and
 * {@link ojc.ahni.experiments.objectrecognition.ObjectRecognitionFitnessFunction3} for examples.</p>
 * 
 * @author Oliver Coleman
 */
public abstract class HyperNEATFitnessFunction extends BulkFitnessFunctionMT {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(HyperNEATFitnessFunction.class);

	/**
	 * Property key for multiplicative amount to scale substrate by when the performance of the best individual in the
	 * population has reached the specified level.
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
	 * Property key for whether the performance values should be recorded before the final scaling has been performed.
	 * If false them the performance of individuals (Chromosomes) is set to 0 after each evaluation.
	 */
	public static final String SCALE_RIP_KEY = "fitness.hyperneat.scale.recordintermediateperformance";

	/**
	 * The Transcriber used to transcribe from a CPPN to a substrate network.
	 */
	protected HyperNEATTranscriber transcriberHN;
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
	 * Limits the incoming connections to a target neuron to include those from source neurons within the specified
	 * range of the target neuron. This is optional.
	 */
	protected int connectionRange;

	/**
	 * The performance level required before a scaling is performed.
	 * 
	 * @see #SCALE_PERFORMANCE_KEY
	 */
	protected double scalePerformance = 0.98f;
	/**
	 * The maximum number of rescalings to perform.
	 * 
	 * @see #SCALE_COUNT_KEY
	 */
	protected int scaleCount = 0;

	private int scaleFactor = 2;
	private int scaleTimes = 2;
	private boolean scaleRecordIntermediatePerf = true;

	/**
	 * Subclasses may override this method to perform initialise tasks. <strong>Make sure to call this method from the
	 * overriding method.</strong>
	 * 
	 * @param props Configuration parameters, typically read from the/a properties file.
	 */
	public void init(Properties props) {
		super.init(props);

		transcriberHN = (HyperNEATTranscriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
		depth = transcriberHN.getDepth();
		height = transcriberHN.getHeight();
		width = transcriberHN.getWidth();
		connectionRange = transcriberHN.getConnectionRange();

		scalePerformance = props.getDoubleProperty(SCALE_PERFORMANCE_KEY, scalePerformance);
		scaleTimes = Math.max(0, props.getIntProperty(SCALE_COUNT_KEY, scaleTimes));
		scaleRecordIntermediatePerf = props.getBooleanProperty(SCALE_RIP_KEY, scaleRecordIntermediatePerf);
	}

	/**
	 * {@inheritDoc}
	 */
	public void evaluate(List<Chromosome> genotypes) {
		super.evaluate(genotypes);

		endRun = false;
		// If we've completed all scalings and reached the target performance, end the run.
		if (scaleCount >= 0 && scaleCount == scaleTimes && scaleFactor > 1 && ((targetPerformanceType == 1 && bestPerformance >= targetPerformance) || (targetPerformanceType == 0 && bestPerformance <= targetPerformance))) {
			endRun = true;
		}

		// if we should scale the substrate
		if (scaleCount < scaleTimes && scaleFactor > 1 && ((targetPerformanceType == 1 && bestPerformance >= scalePerformance) || (targetPerformanceType == 0 && bestPerformance <= scalePerformance))) {
			// allow sub-class to make necessary changes
			scale(scaleCount, scaleFactor);
			for (Evaluator ev : evaluators)
				ev.resetSubstrate(); // don't reuse old size substrate
			transcriberHN.resize(width, height, connectionRange);

			scaleCount++;
		}
	}

	/**
	 * Allow sub-class to make the necessary changes when a substrate scale occurs. If implemented, then at a minimum
	 * this method will usually need to set new values for the {@link #width} and {#link height} array fields, and the
	 * {@link #connectionRange} field if applicable.
	 * 
	 * @param scaleCount A count of how many times a scale has previously occurred. In the first call this has value 0.
	 * @param scaleFactor The amount the substrate is being scaled by.
	 */
	protected void scale(int scaleCount, int scaleFactor) {
	}

	public int getConnectionRange() {
		return transcriberHN.getConnectionRange();
	}

	/**
	 * Sub-classes may override this method to perform operations after a Chromosome has been evaluated. This method
	 * should be called from the overriding method.
	 * 
	 * @param genotype the Chromosome that has been evaluated. It's fitness and performance will have been set.
	 * @param substrate the network (activator), or phenotype, of the evaluated Chromosome.
	 * @param evalThreadIndex The index of the evaluator thread. This is not usually required but may be useful in some
	 *            cases.
	 */
	protected void postEvaluate(Chromosome genotype, Activator substrate, int evalThreadIndex) {
		// only record performance when all scales have been performed.
		if (!scaleRecordIntermediatePerf && scaleCount < scaleTimes)
			genotype.setPerformanceValue(0);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>This default implementation does nothing.</p>
	 */
	public void evolutionFinished(HyperNEATEvolver evolver) {
	}
}
