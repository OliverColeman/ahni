package com.ojcoleman.ahni.transcriber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.apache.commons.lang3.ArrayUtils;
import org.jgapcustomised.Allele;
import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.InvalidConfigurationException;
import org.jgapcustomised.MutationOperator;

import com.anji.integration.Activator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.neat.NeatConfiguration;
import com.ojcoleman.ahni.evaluation.AHNIFitnessFunction;
import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.integration.ParamAllele;
import com.ojcoleman.ahni.integration.ParamCollection;
import com.ojcoleman.ahni.integration.ParamGene;
import com.ojcoleman.ahni.integration.ParamMutationOperator;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.Point;
import com.ojcoleman.ahni.util.Range;

/**
 * A base class for neural network transcribers that may define neuron or synapse model parameters. Some settings are
 * specific to HyperNEAT type encoding schemes.
 */
public abstract class TranscriberAdaptor<T extends Activator> implements Transcriber<T> {
	/**
	 * The minimum weight values in the substrate network. If this is not specified then the negated value of
	 * HYPERNEAT_CONNECTION_WEIGHT_MAX will be used.
	 */
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MIN = "ann.transcriber.connection.weight.min";
	/**
	 * The maximum weight values in the substrate network.
	 */
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MAX = "ann.transcriber.connection.weight.max";

	/**
	 * Property to specify which neuron model to use. Allowable values are implementation dependent.
	 */
	public static final String SUBSTRATE_NEURON_MODEL = "ann.transcriber.neuron.model";
	/**
	 * Property to specify the available parameters for neurons. Allowable values are implementation dependent. If
	 * HyperNEAT is being used then the CPPN may have an output for each parameter.
	 */
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS = "ann.transcriber.neuron.model.params";
	/**
	 * Optional property to allow specifying that instead of parameter values being defined individually for each
	 * neuron, there will be a set of parameter value collections which can be thought of as defining a class of neuron
	 * as determined by the specific parameter values (which will determine the behaviour of that class of neuron). Each
	 * neuron should then reference one of these collections/classes and have its parameters set accordingly. If this is
	 * set to 0 or not specified then each neuron should be assigned parameters values directly (e.g. via an output for
	 * each parameter from a CPPN in a HyperNEAT encoding scheme), if this is set to a value greater than 0 then it
	 * should specify the number of classes.
	 */
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS_CLASSES = "ann.transcriber.neuron.model.params.classes";
	/**
	 * Property to specify minimum values for the parameters for each neuron. Allowable values are implementation
	 * dependent.
	 */
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS_MIN = "ann.transcriber.neuron.model.params.min";
	/**
	 * Property to specify maximum values for the parameters for each neuron. Allowable values are implementation
	 * dependent.
	 */
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS_MAX = "ann.transcriber.neuron.model.params.max";
	/**
	 * Property to specify the minimum CPPN output required to produce a non-zero parameter value. Actual parameter
	 * values will scale from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min,
	 * 0].
	 */
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS_THRESHOLD = "ann.transcriber.neuron.model.params.expression.threshold";
	/**
	 * Property to specify allowable neuron model types. Typically: the CPPN only needs to specify the type to use if
	 * there is more than one type; if there are only two neuron types then a single CPPN output will be used to specify
	 * which to use, otherwise a "whichever CPPN output has the highest output value" encoding will be used to specify
	 * the type. Allowable values are implementation dependent, however by default it is assumed that a parameter name
	 * followed by one or more integer values to specify the allowable types is used (this assumes that the model is
	 * set-up with a parameter that can take on integer values to decide what type the model is).
	 */
	public static final String SUBSTRATE_NEURON_MODEL_TYPES = "ann.transcriber.neuron.model.types";

	/**
	 * Property to specify which synapse model to use. Allowable values are implementation dependent.
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL = "ann.transcriber.synapse.model";
	/**
	 * Property to specify the available parameters for synapses. Allowable values are implementation dependent. If
	 * HyperNEAT is being used then the CPPN may have an output for each parameter.
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS = "ann.transcriber.synapse.model.params";
	/**
	 * Optional property to allow specifying that instead of parameter values being defined individually for each
	 * synapse, there will be a set of parameter value collections which can be thought of as defining a class of
	 * synapse as determined by the specific parameter values (which will determine the behaviour of that class of
	 * synapse). Each synapse should then reference one of these collections/classes and have its parameters set
	 * accordingly. If this is set to 0 or not specified then each synapse should be assigned parameters values directly
	 * (e.g. via an output for each parameter from a CPPN in a HyperNEAT encoding scheme), if this is set to a value
	 * greater than 0 then it should specify the number of classes.
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS_CLASSES = "ann.transcriber.synapse.model.params.classes";
	/**
	 * Property to specify minimum values for the parameters for each synapse. Allowable values are implementation
	 * dependent.
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS_MIN = "ann.transcriber.synapse.model.params.min";
	/**
	 * Property to specify maximum values for the parameters for each synapse. Allowable values are implementation
	 * dependent.
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS_MAX = "ann.transcriber.synapse.model.params.max";
	/**
	 * Property to specify the minimum CPPN output required to produce a non-zero parameter value. Actual parameter
	 * values will scale from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min,
	 * 0].
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS_THRESHOLD = "ann.transcriber.synapse.model.params.expression.threshold";
	/**
	 * Property to specify which parameter in the synapse model will be set to 0 if the connection should not be enabled
	 * or plastic. This is typically applied to a "learning rate" parameter. Usage and allowable values are
	 * implementation dependent. The criteria for whether a synapse should be enabled is implementation dependent.
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM = "ann.transcriber.synapse.model.plasticitydisableparam";
	/**
	 * Property to specify allowable synapse model types. Typically: the CPPN only needs to specify the type to use if
	 * there is more than one type; if there are only two synapse types then a single CPPN output will be used to
	 * specify which to use, otherwise a "whichever CPPN output has the highest output value" encoding will be used to
	 * specify the type. Allowable values are implementation dependent, however by default it is assumed that a
	 * parameter name followed by one or more integer values to specify the allowable types is used (this assumes that
	 * the model is set-up with a parameter that can take on integer values to decide what type the model is).
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_TYPES = "ann.transcriber.synapse.model.types";

	/**
	 * The minimum connection weight in the substrate.
	 */
	protected double connectionWeightMin;
	/**
	 * The maximum connection weight in the substrate.
	 */
	protected double connectionWeightMax;

	/**
	 * Subclasses may use this to know whether multiple neuron types are enabled and available.
	 */
	protected boolean neuronTypesEnabled;
	/**
	 * Subclasses may use this to know whether multiple synapse types are enabled and available.
	 */
	protected boolean synapseTypesEnabled;

	/**
	 * Subclasses may use this to know whether neuron model parameters are enabled and available.
	 */
	protected boolean neuronParamsEnabled = false;
	/**
	 * Subclasses may use this to know whether synapse model parameters are enabled and available.
	 */
	protected boolean synapseParamsEnabled = false;

	/**
	 * The names or labels for parameters in the neuron model.
	 */
	protected String[] neuronParamNames = new String[0];
	/**
	 * The names or labels for parameters in the synapse model.
	 */
	protected String[] synapseParamNames = new String[0];

	/**
	 * The number of parameter collections defining neuron model classes/types.
	 * 
	 * @see #SUBSTRATE_NEURON_MODEL_PARAMS_CLASSES
	 */
	protected int neuronModelParamClassCount;
	/**
	 * The number of parameter collections defining synapse model classes/types.
	 * 
	 * @see #SUBSTRATE_SYNAPSE_MODEL_PARAMS_CLASSES
	 */
	protected int synapseModelParamClassCount;

	/**
	 * The minimum values for each neuron model parameter.
	 */
	protected double[] neuronModelParamsMin;
	/**
	 * The maximum values for each neuron model parameter.
	 */
	protected double[] neuronModelParamsMax;
	/**
	 * The value range for each neuron model parameter (neuronModelParamsMax - neuronModelParamsMin).
	 */
	protected double[] neuronModelParamsRange;
	/**
	 * the minimum (possibly CPPN) output required to produce a non-zero parameter value. Actual parameter values are
	 * typically scaled from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min,
	 * 0]. Not always implemented.
	 */
	protected double[] neuronModelParamsThreshold;

	/**
	 * The minimum values for each synapse model parameter.
	 */
	protected double[] synapseModelParamsMin;
	/**
	 * The maximum values for each synapse model parameter.
	 */
	protected double[] synapseModelParamsMax;
	/**
	 * The value range for each synapse model parameter (synapseModelParamsMax[i] - synapseModelParamsMin[i]).
	 */
	protected double[] synapseModelParamsRange;
	/**
	 * the minimum (possibly CPPN) output required to produce a non-zero parameter value. Actual parameter values are
	 * typically scaled from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min,
	 * 0]. Not always implemented.
	 */
	protected double[] synapseModelParamsThreshold;

	/**
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM
	 */
	protected String synapseDisableParamName = null;

	/**
	 * The name of the parameter in the neuron model that specifies the type of a neuron.
	 * 
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_TYPES
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_PARAMS
	 */
	protected String neuronModelTypeParam;
	/**
	 * The name of the parameter in the synapse model that specifies the type of a synapse.
	 * 
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_TYPES
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_PARAMS
	 */
	protected String synapseModelTypeParam;

	/**
	 * The allowable values for the {@link #neuronModelTypeParam} model parameter that specify the type of a neuron.
	 * 
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_TYPES
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_PARAMS
	 */
	protected double[] neuronModelTypes = new double[0];
	/**
	 * The allowable values for the {@link #synapseModelTypeParam} model parameter that specify the type of a synapse.
	 * 
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_TYPES
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_PARAMS
	 */
	protected double[] synapseModelTypes = new double[0];

	/**
	 * The number of available neuron types.
	 */
	protected int neuronModelTypeCount = 1; // Always has to be at least one type.
	/**
	 * The number of available synapse types.
	 */
	protected int synapseModelTypeCount = 1; // Always has to be at least one type.

	private Properties props;
	
	private ParamCollection neuronCollection;
	private ParamCollection synapseCollection;

	/**
	 * This method should be called from overriding methods.
	 * 
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		this.props = props;

		connectionWeightMax = props.getDoubleProperty(HYPERNEAT_CONNECTION_WEIGHT_MAX);
		connectionWeightMin = props.getDoubleProperty(HYPERNEAT_CONNECTION_WEIGHT_MIN, -connectionWeightMax);

		// If neuron model parameter outputs are enabled (to set the parameters for substrate neurons (other than
		// bias)).
		if (props.containsKey(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_PARAMS)) {
			neuronParamNames = props.getProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_PARAMS).replaceAll("\\s", "").split(",");
			int paramCount = neuronParamNames.length;
			if (paramCount > 0) {
				neuronParamsEnabled = true;
				neuronModelParamsMax = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_PARAMS_MAX, ArrayUtil.newArray(paramCount, connectionWeightMax));
				double[] defaultMin = props.containsKey(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_PARAMS_MAX) ? ArrayUtil.negate(neuronModelParamsMax) : ArrayUtil.newArray(paramCount, connectionWeightMin);
				neuronModelParamsMin = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_PARAMS_MIN, defaultMin);
				neuronModelParamsRange = new double[paramCount];
				for (int p = 0; p < paramCount; p++) {
					neuronModelParamsRange[p] = neuronModelParamsMax[p] - neuronModelParamsMin[p];
				}
				neuronModelParamsThreshold = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_PARAMS_THRESHOLD, ArrayUtil.newArray(paramCount, 0d));
				
				// Only enable param classes if there are parameters...
				neuronModelParamClassCount = props.getIntProperty(SUBSTRATE_NEURON_MODEL_PARAMS_CLASSES, 0);
			}
		}

		// If synapse model parameter outputs are enabled (to set the parameters for substrate synapses (other than
		// weight)).
		if (props.containsKey(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_PARAMS)) {
			synapseParamNames = props.getProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_PARAMS).replaceAll("\\s", "").split(",");
			int paramCount = synapseParamNames.length;
			if (paramCount > 0) {
				synapseParamsEnabled = true;
				synapseModelParamsMax = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_PARAMS_MAX, ArrayUtil.newArray(paramCount, connectionWeightMax));
				double[] defaultMin = props.containsKey(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_PARAMS_MAX) ? ArrayUtil.negate(synapseModelParamsMax) : ArrayUtil.newArray(paramCount, connectionWeightMin);
				synapseModelParamsMin = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_PARAMS_MIN, defaultMin);
				synapseModelParamsRange = new double[paramCount];
				for (int p = 0; p < paramCount; p++) {
					synapseModelParamsRange[p] = synapseModelParamsMax[p] - synapseModelParamsMin[p];
				}
				synapseModelParamsThreshold = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_PARAMS_THRESHOLD, ArrayUtil.newArray(paramCount, 0d));
				
				// Only enable param classes if there are parameters...
				synapseModelParamClassCount = props.getIntProperty(SUBSTRATE_SYNAPSE_MODEL_PARAMS_CLASSES, 0);
			}

			synapseDisableParamName = props.getProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM, null);
		}
		
		
		// If multiple neuron types are enabled.
		if (props.containsKey(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_TYPES)) {
			String[] typeData = props.getProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_TYPES).replaceAll("\\s|\\)", "").split(",");
			if (typeData.length > 1) {
				neuronModelTypeCount = typeData.length - 1;
				neuronTypesEnabled = true;
				neuronModelTypeParam = typeData[0];
				neuronModelTypes = new double[neuronModelTypeCount];
				for (int i = 0; i < neuronModelTypeCount; i++) {
					neuronModelTypes[i] = Double.parseDouble(typeData[i + 1]);
				}
				
				// If parameter classes are enabled...
				if (neuronModelParamClassCount > 0) {
					// ... add the parameter specifying the neuron type to the list of parameters so it's included in the classes.
					neuronParamNames = ArrayUtils.add(neuronParamNames, neuronModelTypeParam);
					// The value of the parameter will be converted to an integer with the floor function, so use a max value that's not quite 1 more than the maximum index.
					double maxValue = Math.nextAfter(neuronModelTypeCount, 0);
					neuronModelParamsMin = ArrayUtils.add(neuronModelParamsMin, 0);
					neuronModelParamsMax = ArrayUtils.add(neuronModelParamsMax, maxValue);
					neuronModelParamsRange = ArrayUtils.add(neuronModelParamsRange, maxValue);
					// (ignore neuronModelParamsThreshold as it's not used for parameter classes).
				}
			}
		} else {
			neuronModelTypeCount = 1;
		}

		// If multiple synapse types are enabled.
		if (props.containsKey(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_TYPES)) {
			String[] typeData = props.getProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_TYPES).replaceAll("\\s|\\)", "").split(",");
			if (typeData.length > 1) {
				synapseModelTypeCount = typeData.length - 1;
				synapseTypesEnabled = true;
				synapseModelTypeParam = typeData[0];
				synapseModelTypes = new double[synapseModelTypeCount];
				for (int i = 0; i < synapseModelTypeCount; i++) {
					synapseModelTypes[i] = Double.parseDouble(typeData[i + 1]);
				}

				// If parameter classes are enabled...
				if (synapseModelParamClassCount > 0) {
					// ... add the parameter specifying the synapse type to the list of parameters so it's included in the classes.
					synapseParamNames = ArrayUtils.add(synapseParamNames, synapseModelTypeParam);
					// The value of the parameter will be converted to an integer with the floor function, so use a max value that's not quite 1 more than the maximum index.
					double maxValue = Math.nextAfter(synapseModelTypeCount, 0);
					synapseModelParamsMin = ArrayUtils.add(synapseModelParamsMin, 0);
					synapseModelParamsMax = ArrayUtils.add(synapseModelParamsMax, maxValue);
					synapseModelParamsRange = ArrayUtils.add(synapseModelParamsRange, maxValue);
					// (ignore synapseModelParamsThreshold as it's not used for parameter classes).
				}
			}
		} else {
			synapseModelTypeCount = 1;
		}
	}

	/**
	 * Sub-classes can override this method to convert the genotype to a phenotype, using an existing substrate object
	 * to allow performance gains by avoiding destroying and creating large objects or arrays, and optionally providing
	 * additional options.
	 * 
	 * @param c chromosome to transcribe
	 * @param substrate An existing phenotype substrate to reuse
	 * @param options A set of transcriber specific options. May be null.
	 * @return phenotype
	 * @throws TranscriberException
	 */
	public T transcribe(Chromosome c, T substrate, Map<String, Object> options) throws TranscriberException {
		return transcribe(c, substrate);
	}

	/**
	 * Get the number of available neuron types.
	 */
	public int getNeuronTypeCount() {
		return neuronModelTypeCount;
	}

	/**
	 * Get the number of available synapse types.
	 */
	public int getSynapseTypeCount() {
		return synapseModelTypeCount;
	}

	/**
	 * Get the number of available neuron types.
	 */
	public int getNeuronParamClassCount() {
		return neuronModelParamClassCount;
	}

	/**
	 * Get the number of available synapse types.
	 */
	public int getSynapseParamClassCount() {
		return synapseModelParamClassCount;
	}

	/**
	 * Get the number of available neuron model parameters.
	 */
	public int getNeuronParameterCount() {
		return neuronParamNames.length;
	}

	/**
	 * Get the name of the specified neuron model parameter.
	 */
	public String getNeuronParameterName(int index) {
		return neuronParamNames[index];
	}

	/**
	 * Get the number of available synapse model parameters.
	 */
	public int getSynapseParameterCount() {
		return synapseParamNames.length;
	}

	/**
	 * Get the name of the specified synapse model parameter.
	 */
	public String getSynapseParameterName(int index) {
		return synapseParamNames[index];
	}

	public double[] getNeuronModelParamsMin() {
		return neuronModelParamsMin;
	}

	public double[] getNeuronModelParamsMax() {
		return neuronModelParamsMax;
	}

	public double[] getSynapseModelParamsMin() {
		return synapseModelParamsMin;
	}

	public double[] getSynapseModelParamsMax() {
		return synapseModelParamsMax;
	}

	public double getNeuronModelParamsMin(int index) {
		return neuronModelParamsMin[index];
	}

	public double getNeuronModelParamsMax(int index) {
		return neuronModelParamsMax[index];
	}

	public double getSynapseModelParamsMin(int index) {
		return synapseModelParamsMin[index];
	}

	public double getSynapseModelParamsMax(int index) {
		return synapseModelParamsMax[index];
	}

	/**
	 * May be overridden to specify how many input neurons the Chromosome should encode. This default implementation
	 * returns the value specified by the property {@link com.anji.neat.NeatConfiguration#STIMULUS_SIZE_KEY} if given,
	 * otherwise {@link com.anji.neat.NeatConfiguration#DEFAULT_STIMULUS_SIZE}.
	 */
	public int getChromosomeInputNeuronCount() {
		return props.getShortProperty(NeatConfiguration.STIMULUS_SIZE_KEY, NeatConfiguration.DEFAULT_STIMULUS_SIZE);
	}

	/**
	 * May be overridden to specify how many output neurons the Chromosome should encode. This default implementation
	 * returns the value specified by the property {@link com.anji.neat.NeatConfiguration#RESPONSE_SIZE_KEY} if given,
	 * otherwise {@link com.anji.neat.NeatConfiguration#DEFAULT_RESPONSE_SIZE}.
	 */
	public int getChromosomeOutputNeuronCount() {
		return props.getShortProperty(NeatConfiguration.RESPONSE_SIZE_KEY, NeatConfiguration.DEFAULT_RESPONSE_SIZE);
	}

	/**
	 * Gets the neuron parameter values for each parameter in each parameter class for the specified chromosome, in the
	 * format [class][param] (param order same as {@link #neuronParamNames}).
	 * 
	 * @see #SUBSTRATE_NEURON_MODEL_PARAMS_CLASSES
	 */
	public double[][] getNeuronParameterValues(Chromosome chrom) {
		double[][] vals = new double[getNeuronParamClassCount()][getNeuronParameterCount()];
		int numberFound = getParameterValuesForCollection(chrom, neuronCollection, vals);
		assert numberFound == getNeuronParamClassCount() * getNeuronParameterCount() : "The number of neuron parameter alleles in Chromosome " + chrom.getId() + " does not match the number of parameter classes and number of parameters defined (found " + numberFound + " but expecting " + getNeuronParamClassCount() * getNeuronParameterCount() + ").";
		return vals;
	}

	/**
	 * Gets the synapse parameter values for each parameter in each parameter class the specified chromosome, in the
	 * format [class][param] (param order same as {@link #synapseParamNames}).
	 * 
	 * @see #SUBSTRATE_SYNAPSE_MODEL_PARAMS_CLASSES
	 */
	public double[][] getSynapseParameterValues(Chromosome chrom) {
		double[][] vals = new double[getSynapseParamClassCount()][getSynapseParameterCount()];
		int numberFound = getParameterValuesForCollection(chrom, synapseCollection, vals);
		assert numberFound == getSynapseParamClassCount() * getSynapseParameterCount() : "The number of synapse parameter alleles in Chromosome " + chrom.getId() + " does not match the number of parameter classes and number of parameters defined (found " + numberFound + " but expecting " + getNeuronParamClassCount() * getNeuronParameterCount() + ").";
		return vals;
	}
	
	protected int getParameterValuesForCollection(Chromosome chrom, ParamCollection collection, double[][] values) {
		int foundCount = 0;
		for (Allele a : chrom.getAlleles()) {
			if (a instanceof ParamAllele) {
				ParamAllele allele = (ParamAllele) a;
				ParamCollection type = allele.getGene().getCollection().getSuperCollection();
				if (type == collection) {
					int param = allele.getGene().getIndexWithinCollection();
					int clazz = allele.getGene().getCollection().getIndexWithinSuperCollection();
					values[clazz][param] = allele.getValue();
					foundCount++;
					
					//if (Math.random() < 0.001) System.err.println(clazz + ":" + param + ":" + allele.getValue());
				}
			}
		}
		return foundCount;
	}

	/**
	 * Returns a list of {@link Allele}s, or more importantly {@link org.jgapcustomised.Gene}s referenced by the Alleles, that this
	 * transcriber knows what to do with. This default implementation adds {@link ParamAllele}s for each neuron and
	 * synapse model parameter class (see {@link #SUBSTRATE_NEURON_MODEL_PARAMS_CLASSES} and
	 * {@link #SUBSTRATE_SYNAPSE_MODEL_PARAMS_CLASSES}). It also adds the ParamMutationOperator to the list of mutation
	 * operators for the given configuration if it hasn't already been added. Sub-classes may override this method to
	 * add alternate or additional Alleles/Genes.
	 */
	public List<Allele> getExtraGenesForInitialChromosome(NeatConfiguration config) throws InvalidConfigurationException {
		ArrayList<Allele> extraAlleles = new ArrayList<Allele>();

		neuronCollection = new ParamCollection("neuron", getNeuronModelParamsMin(), getNeuronModelParamsMax());
		for (int c = 0; c < getNeuronParamClassCount(); c++) {
			ParamCollection subCollection = new ParamCollection("neuron-" + c, neuronCollection, c);
			for (int p = 0; p < getNeuronParameterCount(); p++) {
				ParamGene gene = new ParamGene(config.nextInnovationId(), subCollection, p);
				ParamAllele allele = new ParamAllele(gene);
				//allele.setValue((gene.getMaxValue() + gene.getMinValue()) / 2);
				double r = gene.getMaxValue() - gene.getMinValue();
				allele.setValue(Math.random() * r + gene.getMinValue());
				extraAlleles.add(allele);
			}
		}
		synapseCollection = new ParamCollection("synapse", getSynapseModelParamsMin(), getSynapseModelParamsMax());
		for (int c = 0; c < getSynapseParamClassCount(); c++) {
			ParamCollection subCollection = new ParamCollection("synapse-" + c, synapseCollection, c);
			for (int p = 0; p < getSynapseParameterCount(); p++) {
				ParamGene gene = new ParamGene(config.nextInnovationId(), subCollection, p);
				ParamAllele allele = new ParamAllele(gene);
				//allele.setValue((gene.getMaxValue() + gene.getMinValue()) / 2);
				double r = gene.getMaxValue() - gene.getMinValue();
				allele.setValue(Math.random() * r + gene.getMinValue());
				extraAlleles.add(allele);
			}
		}

		if (!extraAlleles.isEmpty()) {
			MutationOperator mutOp = props.newObjectProperty(ParamMutationOperator.class);
			if (!config.getMutationOperators().contains(mutOp)) {
				config.addMutationOperator(props.newObjectProperty(ParamMutationOperator.class));
			}
		}

		return extraAlleles;
	}
}
