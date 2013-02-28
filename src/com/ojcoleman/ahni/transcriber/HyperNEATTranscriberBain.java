package com.ojcoleman.ahni.transcriber;

import java.util.Map;

import com.ojcoleman.bain.NeuralNetwork;
import com.ojcoleman.bain.base.ComponentCollection;
import com.ojcoleman.bain.base.ComponentConfiguration;
import com.ojcoleman.bain.base.NeuronCollection;
import com.ojcoleman.bain.base.SynapseConfiguration;
import com.ojcoleman.bain.neuron.rate.NeuronCollectionWithBias;
import com.ojcoleman.bain.base.SynapseCollection;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.amd.aparapi.Kernel;
import com.anji.integration.Activator;
import com.anji.integration.AnjiActivator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.nn.*;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.ahni.nn.NNAdaptor;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.Point;

/**
 * Constructs a <a href="https://github.com/OliverColeman/bain">Bain</a> neural network from a chromosome using the
 * hypercube (from HyperNEAT) encoding scheme. An {@link com.anji.integration.ActivatorTranscriber} should be used to
 * construct an instance of this class. {@link com.anji.integration.ActivatorTranscriber#newActivator(Chromosome)} is
 * then used to get the resulting network.
 * 
 * To transcribe the neural network from a chromosome a connective pattern producing network (CPPN) is created from the
 * chromosome, and then this is "queried" to determine the weights of the neural network. The CPPN is an
 * {@link com.anji.nn.AnjiNet}.
 * 
 * @author Oliver Coleman
 */
public class HyperNEATTranscriberBain extends HyperNEATTranscriber<BainNN> {
	public static final String SUBSTRATE_SIMULATION_RESOLUTION = "ann.hyperneat.bain.resolution";
	public static final String SUBSTRATE_EXECUTION_MODE = "ann.hyperneat.bain.executionmode";
	
	public static final String SUBSTRATE_NEURON_MODEL = "ann.hyperneat.bain.neuron.model";
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS = "ann.hyperneat.bain.neuron.model.params";
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS_MIN = "ann.hyperneat.bain.neuron.model.params.min";
	public static final String SUBSTRATE_NEURON_MODEL_PARAMS_MAX = "ann.hyperneat.bain.neuron.model.params.max";
	public static final String SUBSTRATE_NEURON_MODEL_TYPES = "ann.hyperneat.bain.neuron.model.types";

	public static final String SUBSTRATE_SYNAPSE_MODEL = "ann.hyperneat.bain.synapse.model";
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS = "ann.hyperneat.bain.synapse.model.params";
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS_MIN = "ann.hyperneat.bain.synapse.model.params.min";
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS_MAX = "ann.hyperneat.bain.synapse.model.params.max";
	public static final String SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM = "ann.hyperneat.bain.synapse.model.plasticitydisableparam";
	public static final String SUBSTRATE_SYNAPSE_MODEL_TYPES = "ann.hyperneat.bain.synapse.model.types";
	
	

	/**
	 * When determining if the substrate network is recurrent, the maximum cycle length to search for before the network
	 * is considered recurrent. Note that whichever is the smallest of this value and the number of neurons in the
	 * network is used for any given network.
	 */
	public static final String SUBSTRATE_MAX_RECURRENT_CYCLE = "ann.hyperneat.bain.maxrecurrentcyclesearchlength";

	private final static Logger logger = Logger.getLogger(HyperNEATTranscriberBain.class);

	private Properties properties;
	private int[] neuronLayerSize, bainIndexForNeuronLayer, ffSynapseLayerSize, bainIndexForFFSynapseLayer; // ff=feed
																											// forward
	private int neuronCount, synapseCount;
	private double connectionWeightRange;

	// The names of the parameters that specify the type of a neuron or synapse.
	String  neuronModelTypeParam, synapseModelTypeParam;
	// The values that specify the (available) types in the model.
	double[] neuronModelTypes = new double[0], synapseModelTypes = new double[0];
	int neuronModelTypeCount = 1, synapseModelTypeCount = 1; // Set to 1 to allow things like getBainSynapseIndex() to work.
	
	// The set of CPPN outputs that determine which neuron or synapse type should be used.
	int[] cppnIDXNeuronTypeSelector = new int[1]; // Indices into CPPN output array
	int[] cppnIDXSynapseTypeSelector = new int[1]; // Indices into CPPN output array
	
	int[] cppnIDXNeuronTypeBiases = new int[1]; // Index into CPPN output array
	int[] cppnIDXSynapseTypeWeights = new int[1]; // Index into CPPN output array
	
	boolean neuronTypesEnabled, synapseTypesEnabled;
	double[] neuronModelParamsMin, neuronModelParamsMax, neuronModelParamsRange;
	double[] synapseModelParamsMin, synapseModelParamsMax, synapseModelParamsRange;

	// The names of neuron and synapse model parameters set via CPPN outputs.
	String[] cppnNeuronParamNames = new String[0];
	String[] cppnSynapseParamNames = new String[0];
	
	// The index of CPPN outputs for neuron and synapse model parameters, format is [type][param].
	int[][] cppnIDXNeuronParams = new int[0][0]; 
	int[][] cppnIDXSynapseParams = new int[0][0];
	
	boolean neuronParamsEnabled = false, synapseParamsEnabled = false;
	String synapseDisableParamName = null;
	

	public HyperNEATTranscriberBain() {
	}

	public HyperNEATTranscriberBain(Properties props) {
		init(props);
	}

	@Override
	public void init(Properties props) {
		// If the properties specify a separate weight output per layer.
		if (!props.getBooleanProperty(HYPERNEAT_LAYER_ENCODING, layerEncodingIsInput)) {
			if (props.containsKey(SUBSTRATE_NEURON_MODEL_PARAMS) || props.containsKey(SUBSTRATE_SYNAPSE_MODEL_PARAMS)) {
				logger.warn("Separate neuron and synapse model parameter outputs per layer are not currently supported, forcing " + HYPERNEAT_LAYER_ENCODING + " to true."); 
				props.setProperty(HYPERNEAT_LAYER_ENCODING, "true");
			}
			if (props.containsKey(SUBSTRATE_NEURON_MODEL_TYPES) || props.containsKey(SUBSTRATE_SYNAPSE_MODEL_TYPES)) {
				logger.warn("Separate bias and weight outputs for each neuron and synapse type per layer are not currently supported, forcing " + HYPERNEAT_LAYER_ENCODING + " to true."); 
				props.setProperty(HYPERNEAT_LAYER_ENCODING, "true");
			}
		}
		
		super.init(props);

		this.properties = props;
		
		connectionWeightRange = connectionWeightMax - connectionWeightMin;
		
		// Bias for first neuron type comes from the standard CPPN bias output.
		// This gets set even if multiple neuron types aren't enabled.
		cppnIDXNeuronTypeBiases[0] = getCPPNIndexBiasOutput()[0];
		// If multiple neuron types are enabled.
		if (props.containsKey(SUBSTRATE_NEURON_MODEL_TYPES)) {
			String[] typeData = props.getProperty(SUBSTRATE_NEURON_MODEL_TYPES).replaceAll("\\s|\\)", "").split(",");
			if (typeData.length > 1) {
				neuronModelTypeCount = typeData.length-1;
				
				// The CPPN only needs to specify the type to use if there is more than one type.
				if (neuronModelTypeCount > 1) {
					// If there are only two neuron types then we can use a single CPPN output to specify which to use,
					// otherwise use a "whichever has the highest output value" encoding to specify the type. 
					cppnIDXNeuronTypeSelector = new int[neuronModelTypeCount > 2 ? neuronModelTypeCount : 1];
				}
				
				neuronTypesEnabled = true;
				neuronModelTypeParam = typeData[0];
				neuronModelTypes = new double[neuronModelTypeCount];
				for (int i = 0; i < neuronModelTypeCount; i++) {
					neuronModelTypes[i] = Double.parseDouble(typeData[i+1]);
				}
				for (int i = 1; i < neuronModelTypeCount; i++) {
					cppnIDXNeuronTypeBiases[i] = cppnOutputCount++;
				}
				logger.info("CPPN output size with neuron model types: " + cppnOutputCount);
			}
		}
		
		// Weight for first synapse type comes from the standard CPPN weight output.
		// This gets set even if multiple synapse types aren't enabled.
		cppnIDXSynapseTypeWeights[0] = getCPPNIndexWeight()[0];
		// If multiple synapse types are enabled.
		if (props.containsKey(SUBSTRATE_SYNAPSE_MODEL_TYPES)) {
			String[] typeData = props.getProperty(SUBSTRATE_SYNAPSE_MODEL_TYPES).replaceAll("\\s|\\)", "").split(",");
			if (typeData.length > 1) {
				synapseModelTypeCount = typeData.length-1;
				
				// The CPPN only needs to specify the type to use if there is more than one type.
				if (synapseModelTypeCount > 1) {
					// If there are only two synapse types then we can use a single CPPN output to specify which to use,
					// otherwise use a "whichever has the highest output value" encoding to specify the type. 
					cppnIDXSynapseTypeSelector = new int[synapseModelTypeCount > 2 ? synapseModelTypeCount : 1];
				}

				cppnIDXSynapseTypeWeights = new int[synapseModelTypeCount];
				cppnIDXSynapseTypeWeights[0] = getCPPNIndexWeight()[0];
				synapseTypesEnabled = true;
				synapseModelTypeParam = typeData[0];
				synapseModelTypes = new double[synapseModelTypeCount];
				for (int i = 0; i < synapseModelTypeCount; i++) {
					synapseModelTypes[i] = Double.parseDouble(typeData[i+1]);
				}
				for (int i = 1; i < synapseModelTypeCount; i++) {
					cppnIDXSynapseTypeWeights[i] = cppnOutputCount++;
				}
				logger.info("CPPN output size with synapse model types: " + cppnOutputCount);
			}
		}

		neuronLayerSize = new int[depth];
		bainIndexForNeuronLayer = new int[depth];
		if (feedForward) {
			ffSynapseLayerSize = new int[depth - 1];
			bainIndexForFFSynapseLayer = new int[depth - 1];
		}
		resize(width, height, -1); // Initialise above arrays.

		if (props.containsKey(SUBSTRATE_NEURON_MODEL_PARAMS)) {
			cppnNeuronParamNames = props.getProperty(SUBSTRATE_NEURON_MODEL_PARAMS).replaceAll("\\s", "").split(",");
			int paramCount = cppnNeuronParamNames.length;
			if (paramCount > 0) {
				cppnIDXNeuronParams = new int[neuronModelTypeCount][paramCount];
				for (int t = 0; t < neuronModelTypeCount; t++) {
					for (int p = 0; p < paramCount; p++) {
						cppnIDXNeuronParams[t][p] = cppnOutputCount++;
					}
				}
				neuronParamsEnabled = true;

				neuronModelParamsMax = props.getDoubleArrayProperty(SUBSTRATE_NEURON_MODEL_PARAMS_MAX, ArrayUtil.newArray(paramCount, connectionWeightMax));
				double[] defaultMin = props.containsKey(SUBSTRATE_NEURON_MODEL_PARAMS_MAX) ? ArrayUtil.negate(neuronModelParamsMax) : ArrayUtil.newArray(paramCount, connectionWeightMin);
				neuronModelParamsMin = props.getDoubleArrayProperty(SUBSTRATE_NEURON_MODEL_PARAMS_MIN, defaultMin);
				neuronModelParamsRange = new double[paramCount];
				for (int p = 0; p < paramCount; p++) {
					neuronModelParamsRange[p] = neuronModelParamsMax[p] - neuronModelParamsMin[p];
				}
			}
		}

		if (props.containsKey(SUBSTRATE_SYNAPSE_MODEL_PARAMS)) {
			cppnSynapseParamNames = props.getProperty(SUBSTRATE_SYNAPSE_MODEL_PARAMS).replaceAll("\\s", "").split(",");
			int paramCount = cppnSynapseParamNames.length;
			if (paramCount > 0) {
				cppnIDXSynapseParams = new int[synapseModelTypeCount][paramCount];
				for (int t = 0; t < synapseModelTypeCount; t++) {
					for (int p = 0; p < paramCount; p++) {
						cppnIDXSynapseParams[t][p] = cppnOutputCount++;
					}
				}
				synapseParamsEnabled = true;
				
				synapseModelParamsMax = props.getDoubleArrayProperty(SUBSTRATE_SYNAPSE_MODEL_PARAMS_MAX, ArrayUtil.newArray(paramCount, connectionWeightMax));
				double[] defaultMin = props.containsKey(SUBSTRATE_SYNAPSE_MODEL_PARAMS_MAX) ? ArrayUtil.negate(synapseModelParamsMax) : ArrayUtil.newArray(paramCount, connectionWeightMin);
				synapseModelParamsMin = props.getDoubleArrayProperty(SUBSTRATE_SYNAPSE_MODEL_PARAMS_MIN, defaultMin);
				synapseModelParamsRange = new double[paramCount];
				for (int p = 0; p < paramCount; p++) {
					synapseModelParamsRange[p] = synapseModelParamsMax[p] - synapseModelParamsMin[p];
				}
			}
		}
		if (neuronParamsEnabled || synapseParamsEnabled) {
			logger.info("CPPN output size with synapse and/or neuron model parameters: " + cppnOutputCount);
		}
		
		synapseDisableParamName = props.getProperty(SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM, null);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	@Override
	public BainNN transcribe(Chromosome genotype) throws TranscriberException {
		return newBainNN(genotype, null, null);
	}

	@Override
	public BainNN transcribe(Chromosome genotype, BainNN substrate) throws TranscriberException {
		return newBainNN(genotype, substrate, null);
	}
	
	@Override
	public BainNN transcribe(Chromosome genotype, BainNN substrate, Map<String, Object> options) throws TranscriberException {
		return newBainNN(genotype, substrate, options);
	}

	/**
	 * Create a new neural network from a genotype.
	 * 
	 * @param genotype chromosome to transcribe
	 * @return phenotype If given this will be updated and returned, if NULL then a new network will be created.
	 * @throws TranscriberException
	 */
	public BainNN newBainNN(Chromosome genotype, BainNN substrate, Map<String, Object> options) throws TranscriberException {
		boolean recordCoords = options == null ? false : options.get("recordCoordinates").equals(Boolean.TRUE);
		
		CPPN cppn = new CPPN(genotype);
				
		//substrate = null;
		boolean createNewPhenotype = (substrate == null);
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		if (createNewPhenotype) {
			String neuronModelClass = properties.getProperty(SUBSTRATE_NEURON_MODEL, "com.ojcoleman.bain.neuron.rate.SigmoidNeuronCollection");
			String synapseModelClass = properties.getProperty(SUBSTRATE_SYNAPSE_MODEL, "com.ojcoleman.bain.synapse.rate.FixedSynapseCollection");
			try {
				if (enableBias) {
					neurons = (NeuronCollectionWithBias) ComponentCollection.createCollection(neuronModelClass, neuronCount);
				} else {
					neurons = (NeuronCollection) ComponentCollection.createCollection(neuronModelClass, neuronCount);
				}
			} catch (Exception e) {
				e.printStackTrace();
				throw new TranscriberException("Error creating neurons for Bain neural network. Have you specified the name of the neuron collection class correctly, including the containing packages?", e);
			}
			// If the CPPN will not be setting neuron model parameters and the neuron collection is configurable and the configuration has a default preset.
			if (!(neuronParamsEnabled || neuronTypesEnabled) && neurons.getConfigSingleton() != null && neurons.getConfigSingleton().getPreset(0) != null) {
				neurons.addConfiguration(neurons.getConfigSingleton().getPreset(0));
			}

			try {
				synapses = (SynapseCollection) ComponentCollection.createCollection(synapseModelClass, synapseCount);
			} catch (Exception e) {
				e.printStackTrace();
				throw new TranscriberException("Error creating synapses for Bain neural network. Have you specified the name of the synapse collection class correctly, including the containing packages?", e);
			}
			// If the CPPN will not be setting synapse model parameters and the synapse collection is configurable and the configuration has a default preset.
			if (!(synapseParamsEnabled || synapseTypesEnabled) && synapses.getConfigSingleton() != null && synapses.getConfigSingleton().getPreset(0) != null) {
				synapses.addConfiguration(synapses.getConfigSingleton().getPreset(0));
				((SynapseConfiguration) synapses.getConfiguration(0)).minimumEfficacy = connectionWeightMin;
				((SynapseConfiguration) synapses.getConfiguration(0)).maximumEfficacy = connectionWeightMax;
			}
		} else {
			neurons = substrate.getNeuralNetwork().getNeurons();
			synapses = substrate.getNeuralNetwork().getSynapses();
		}
		double[] synapseWeights = synapses.getEfficacies();
		
		int synapseIndex = 0;
		
		// query CPPN for substrate connection weights
		for (int tz = 0; tz < depth; tz++) {
			for (int ty = 0; ty < height[tz]; ty++) {
				for (int tx = 0; tx < width[tz]; tx++) {
					int bainNeuronIndexTarget = getBainNeuronIndex(tx, ty, tz);
					cppn.setTargetCoordinatesFromGridIndices(tx, ty, tz);
					
					// Neuron configuration.
					if (enableBias || neuronTypesEnabled || neuronParamsEnabled) {
						int neuronType = neuronModelTypeCount > 1 ? cppn.getSelectorValue(cppnIDXNeuronTypeSelector) : 0;
						
						cppn.setSourceCoordinatesFromGridIndices(tx, ty, tz);
						cppn.query();
						
						// Bias for neuron (non-inputs for feed-forward)
						if (enableBias) {
							double biasVal = 0;
							int cppnOutputIndex = layerEncodingIsInput ? cppnIDXNeuronTypeBiases[neuronType] : getCPPNIndexBiasOutput()[tz - 1];
							// TODO Separate LEO for each neuron type bias?
							if (!enableLEO || enableLEO && cppn.getLEO(cppnOutputIndex) > 0) {
								biasVal = cppn.getRangedOutput(cppnOutputIndex, connectionWeightMin, connectionWeightMax, connectionWeightRange, connectionExprThresh);
							}
							((NeuronCollectionWithBias) neurons).setBias(bainNeuronIndexTarget, biasVal);
						}
						
						// Type and/or parameters for neuron.
						if (neuronTypesEnabled || neuronParamsEnabled) {
							// Each neuron has its own configuration object.
							ComponentConfiguration c = createNewPhenotype ? neurons.getConfigSingleton().createConfiguration() : neurons.getComponentConfiguration(bainNeuronIndexTarget);
							
							if (neuronTypesEnabled) {
								c.setParameterValue(neuronModelTypeParam, neuronModelTypes[neuronType], true);
							}
							
							if (neuronParamsEnabled) {
								// Set parameters for the config.
								for (int p = 0; p < cppnNeuronParamNames.length; p++) {
									// TODO provide setting for threshold for params? Currently just set it to 10% of whatever the current param range is.
									double v = cppn.getRangedOutput(cppnIDXNeuronParams[neuronType][p], neuronModelParamsMin[p], neuronModelParamsMax[p], neuronModelParamsRange[p], neuronModelParamsRange[p]*0.1);
									c.setParameterValue(cppnNeuronParamNames[p], v, true);
								}
							}
							if (createNewPhenotype) {
								// Add the configuration to the neuron collection.
								neurons.addConfiguration(c);
								// Set the current neuron to use the new configuration.
								neurons.setComponentConfiguration(bainNeuronIndexTarget, bainNeuronIndexTarget);
							}
						}
					}
					
					// We don't allow connections to the first layer, since this is purely an input layer.
					// (However we still want to allow setting parameters for neurons in the first layer in the above code).
					if (tz == 0)
						continue;
					
					// Iteration over layers for the source neuron is only used for recurrent networks.
					for (int sz = (feedForward ? tz - 1 : 0); sz < (feedForward ? tz : depth); sz++) {
						for (int sy = 0; sy < height[sz]; sy++) {
							for (int sx = 0; sx < width[sz]; sx++) {
								cppn.setSourceCoordinatesFromGridIndices(sx, sy, sz);
								cppn.query();

								int bainNeuronIndexSource = feedForward ? getBainNeuronIndex(sx, sy, sz) : getBainNeuronIndex(sx, sy, sz);
								int synapseType = synapseModelTypeCount > 1 ? cppn.getSelectorValue(cppnIDXSynapseTypeSelector) : 0;
								
								synapses.setPreAndPostNeurons(synapseIndex, bainNeuronIndexSource, bainNeuronIndexTarget);
								
								// Determine weight for synapse from source to target.
								int cppnOutputIndex = layerEncodingIsInput ? cppnIDXSynapseTypeWeights[synapseType] : getCPPNIndexWeight()[sz];
								double weightVal = 0;
								// TODO Separate LEO for each synapse type?
								if (!enableLEO || enableLEO && cppn.getLEO(layerEncodingIsInput ? 0 : sz) > 0) {
									weightVal = cppn.getRangedOutput(cppnOutputIndex, connectionWeightMin, connectionWeightMax, connectionWeightRange, connectionExprThresh);
								}
								synapseWeights[synapseIndex] = weightVal;
								
								if (synapseParamsEnabled || synapseTypesEnabled) {
									// Each synapse has its own configuration object.
									SynapseConfiguration c = (SynapseConfiguration) (createNewPhenotype ? synapses.getConfigSingleton().createConfiguration() : synapses.getComponentConfiguration(synapseIndex));
									c.minimumEfficacy = connectionWeightMin;
									c.maximumEfficacy = connectionWeightMax;
									
									if (synapseTypesEnabled) {
										c.setParameterValue(synapseModelTypeParam, synapseModelTypes[synapseType], true);
									}
							
									// Set parameters for the config.
									for (int p = 0; p < cppnSynapseParamNames.length; p++) {
										// TODO provide setting for threshold for params? Currently just set it to 10% of whatever the current param range is.
										double v = cppn.getRangedOutput(cppnIDXSynapseParams[synapseType][p], synapseModelParamsMin[p], synapseModelParamsMax[p], synapseModelParamsRange[p], synapseModelParamsRange[p]*0.1);
										c.setParameterValue(cppnSynapseParamNames[p], v, true);
									}
									if (synapseDisableParamName != null && weightVal == 0) {
										c.setParameterValue(synapseDisableParamName, 0, true);
									}
									
									if (createNewPhenotype) {
										// Add the configuration to the synapse collection.
										synapses.addConfiguration(c);
										// Set the current synapse to use the new configuration.
										synapses.setComponentConfiguration(synapseIndex, synapseIndex);
									}
								}

								/* The getBainSynapseIndex methods aren't used, and currently aren't correct.
								if (feedForward) {
									assert synapseIndex == getBainSynapseIndex(tx, ty, tz, sx, sy);
								} else {
									assert synapseIndex == getBainSynapseIndex(tx, ty, tz, sx, sy, sz);
								}*/

								synapseIndex++;
							} // sx
						} // sy
					} // sz
				} // tx
			} // ty
		} // tz
		synapses.setEfficaciesModified();
		
		if (createNewPhenotype) {
			int simRes = properties.getIntProperty(SUBSTRATE_SIMULATION_RESOLUTION, 1000);
			String execModeName = properties.getProperty(SUBSTRATE_EXECUTION_MODE, null);
			Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
			NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
			int[] outputDims = new int[] { width[depth - 1], height[depth - 1] };
			int[] inputDims = new int[] { width[0], height[0] };
			int maxRecurrentCycles = properties.getIntProperty(SUBSTRATE_MAX_RECURRENT_CYCLE, 1000);
			try {
				substrate = new BainNN(nn, inputDims, outputDims, cyclesPerStep, feedForward ? BainNN.Topology.FEED_FORWARD_LAYERED : BainNN.Topology.RECURRENT, "network " + genotype.getId(), maxRecurrentCycles);
			} catch (Exception e) {
				throw new TranscriberException(e);
			}
			if (recordCoords) {
				substrate.enableCoords();
				Point p = new Point();
				for (int tz = 0; tz < depth; tz++) {
					for (int ty = 0; ty < height[tz]; ty++) {
						for (int tx = 0; tx < width[tz]; tx++) {
							int bainNeuronIndex = getBainNeuronIndex(tx, ty, tz);
							cppn.getCoordinatesForGridIndices(tx, ty, tz, p);
							substrate.setCoords(bainNeuronIndex, p.x, p.y, p.z);
						}
					}
				}
			}
			//logger.info("New substrate has " + neuronCount + " neurons and " + synapseCount + " synapses.");
			
		} else {
			substrate.setName("network " + genotype.getId());
		}
		
		// This will cause the kernels to push all relevant data to the OpenCL device if necessary.
		neurons.init();
		synapses.init();
		substrate.reset();
		return substrate;
	}

	@Override
	public void resize(int[] width, int[] height, int connectionRange) {
		this.width = width;
		this.height = height;
		neuronCount = 0;
		synapseCount = 0;
		for (int l = 0; l < depth; l++) {
			neuronLayerSize[l] = height[l] * width[l];
			bainIndexForNeuronLayer[l] = neuronCount;
			neuronCount += neuronLayerSize[l];
			if (l > 0 && feedForward) {
				ffSynapseLayerSize[l - 1] = neuronLayerSize[l - 1] * neuronLayerSize[l];
				bainIndexForFFSynapseLayer[l - 1] = synapseCount;
				synapseCount += ffSynapseLayerSize[l - 1];
			}
		}
		if (!feedForward) {
			// All possible connections between all neurons except connections going to the input layer 
			// (including connections amongst the input layer).
			synapseCount = neuronCount * (neuronCount - neuronLayerSize[0]);
		}
	}

	/**
	 * @see com.anji.integration.Transcriber#getPhenotypeClass()
	 */
	@Override
	public Class getPhenotypeClass() {
		return NeuralNetwork.class;
	}

	/**
	 * Get the index of the neuron in the Bain networks NeuronCollection for the neuron at the given location.
	 * 
	 * @param x The location of the neuron on the x axis.
	 * @param y The location of the neuron on the y axis.
	 * @param z The location of the neuron on the z axis, or layer it is in.
	 */
	public int getBainNeuronIndex(int x, int y, int z) {
		return bainIndexForNeuronLayer[z] + y * width[z] + x;
	}

	/**
	 * For feed forward networks, get the index of the synapse in the Bain networks SynapseCollection connecting the
	 * neurons at the given location. The layer the source neuron is in is given by tz-1 and so need not be specified.
	 * 
	 * @param tx The location of the target neuron on the x axis.
	 * @param ty The location of the target neuron on the y axis.
	 * @param tz The location of the target neuron on the z axis, or layer it is in.
	 * @param sx The location of the source neuron on the x axis.
	 * @param sy The location of the source neuron on the y axis.
	 */
	//public int getBainSynapseIndex(int tx, int ty, int tz, int sx, int sy) {
	//	return bainIndexForFFSynapseLayer[tz - 1] + width[tz - 1] * (height[tz - 1] * (width[tz] * ty + tx) + sy) + sx;
	//}

	/**
	 * For fully recurrent networks, get the index of the synapse in the Bain networks SynapseCollection connecting the
	 * neurons at the given location. The layer the source neuron is in is given by tz-1 and so need not be specified.
	 * 
	 * @param tx The location of the target neuron on the x axis.
	 * @param ty The location of the target neuron on the y axis.
	 * @param tz The location of the target neuron on the z axis, or layer it is in.
	 * @param sx The location of the source neuron on the x axis.
	 * @param sy The location of the source neuron on the y axis.
	 * @param sz The location of the source neuron on the z axis, or layer it is in.
	 */
	// TODO This is currently incorrect as it doesn't take into account no connections to neurons in input layer.
	//public int getBainSynapseIndex(int tx, int ty, int tz, int sx, int sy, int sz) {
	//	return getBainNeuronIndex(tx, ty, tz) * neuronCount + getBainNeuronIndex(sx, sy, sz);
	//}
}
