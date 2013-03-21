package com.ojcoleman.ahni.transcriber;

import java.util.Map;

import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.ojcoleman.ahni.evaluation.AHNIFitnessFunction;
import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.Point;
import com.ojcoleman.ahni.util.Range;

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
	 * Property to specify CPPN outputs that set the parameters for each neuron. Allowable values are implementation
	 * dependent.
	 */
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS = "ann.transcriber.neuron.model.params";
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
	 * Property to specify the minimum CPPN output required to produce a non-zero parameter value. Actual parameter values
	 * will scale from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min, 0].
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
	 * Property to specify CPPN outputs that set the parameters for each synapse. Allowable values are implementation
	 * dependent.
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS = "ann.transcriber.synapse.model.params";
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
	 * Property to specify the minimum CPPN output required to produce a non-zero parameter value. Actual parameter values
	 * will scale from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min, 0].
	 */
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS_THRESHOLD = "ann.transcriber.synapse.model.params.expression.threshold";
	/**
	 * Property to specify which parameter in the synapse model will be set to 0 if the connection should not be
	 * plastic. This is typically applied to a "learning rate" parameter. Usage and allowable values are
	 * implementation dependent.
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
	 * the minimum (possibly CPPN) output required to produce a non-zero parameter value. Actual parameter values
	 * are typically scaled from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min, 0].
	 * Not always implemented.
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
	 * the minimum (possibly CPPN) output required to produce a non-zero parameter value. Actual parameter values
	 * are typically scaled from [0, ann.transcriber.neuron.model.params.max] or [ann.transcriber.neuron.model.params.min, 0].
	 * Not always implemented.
	 */
	protected double[] synapseModelParamsThreshold;

	
	/**
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM
	 */
	protected String synapseDisableParamName = null;

	/**
	 * The name of the parameter in the neuron model that specifies the type of a neuron.
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_TYPES
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_PARAMS
	 */
	protected String neuronModelTypeParam;
	/**
	 * The name of the parameter in the synapse model that specifies the type of a synapse.
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_TYPES
	 * @see TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_PARAMS
	 */
	protected String synapseModelTypeParam;
	
	/**
	 * The allowable values for the {@link #neuronModelTypeParam} model parameter that specify the type of a neuron.
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_TYPES
	 * @see TranscriberAdaptor#SUBSTRATE_NEURON_MODEL_PARAMS
	 */
	protected double[] neuronModelTypes = new double[0];
	/**
	 * The allowable values for the {@link #synapseModelTypeParam} model parameter that specify the type of a synapse.
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
	
	

	/**
	 * This method should be called from overriding methods.
	 * 
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		connectionWeightMax = props.getDoubleProperty(HYPERNEAT_CONNECTION_WEIGHT_MAX);
		connectionWeightMin = props.getDoubleProperty(HYPERNEAT_CONNECTION_WEIGHT_MIN, -connectionWeightMax);


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
	
			}
		} else {
			synapseModelTypeCount = 1;
		}
	
	
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
				neuronModelParamsThreshold = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL_PARAMS_THRESHOLD, ArrayUtil.newArray(paramCount, 0));
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
				synapseModelParamsThreshold = props.getDoubleArrayProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_PARAMS_THRESHOLD, ArrayUtil.newArray(paramCount, 0));
			}
	
			synapseDisableParamName = props.getProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM, null);
		}
	}
	

	/**
	 * Sub-classes can override this method to convert the genotype to a phenotype, using an existing substrate object
	 * to allow performance gains by avoiding destroying and creating large objects or arrays, and optionally providing
	 * additional options.
	 * 
	 * @param c chromosome to transcribe
	 * @param substrate An existing phenotype substrate to reuse
	 * @param A set of transcriver specific options. May be null.
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
	 * Get the number of available neuron model parameters.
	 */
	public int getNeuronParameterCount() {
		return neuronParamNames.length;
	}
	/**
	 * Get the number of available synapse model parameters.
	 */
	public int getSynapseParameterCount() {
		return synapseParamNames.length;
	}

}
