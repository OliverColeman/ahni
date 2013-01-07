package ojc.ahni.transcriber;

import ojc.ahni.hyperneat.Properties;
import ojc.ahni.nn.BainNN;
import ojc.bain.NeuralNetwork;
import ojc.bain.base.ComponentCollection;
import ojc.bain.base.ComponentConfiguration;
import ojc.bain.base.NeuronCollection;
import ojc.bain.base.SynapseConfiguration;
import ojc.bain.neuron.rate.NeuronCollectionWithBias;
import ojc.bain.base.SynapseCollection;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.amd.aparapi.Kernel;
import com.anji.integration.Activator;
import com.anji.integration.AnjiActivator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.nn.*;

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
	public static final String SUBSTRATE_SYNAPSE_MODEL = "ann.hyperneat.bain.synapse.model";
	public static final String SUBSTRATE_SYNAPSE_MODEL_PARAMS = "ann.hyperneat.bain.synapse.model.params";
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

	// The names of neuron and synapse model parameters set via CPPN outputs.
	String[] cppnNeuronParamNames = new String[0];
	String[] cppnSynapseParamNames = new String[0];
	// The index of CPPN outputs for neuron and synapse model parameters.
	int[] cppnIDXNeuronParams = new int[0];
	int[] cppnIDXSynapseParams = new int[0];
	boolean neuronParamsEnabled = false, synapseParamsEnabled = false;
	String synapseDisableParamName = null;
	
	String  synapseModelTypeParam;
	double[] synapseModelTypes = new double[0];
	int synapseModelTypeCount = 1; // Set to 1 to allow things like getBainSynapseIndex() to work.
	int[] cppnIDXSynapseTypeWeights = new int[1];
	boolean synapseTypesEnabled;

	public HyperNEATTranscriberBain() {
	}

	public HyperNEATTranscriberBain(Properties props) {
		init(props);
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	@Override
	public void init(Properties props) {
		// If the properties specify a separate weight output per layer.
		if (!props.getBooleanProperty(HYPERNEAT_LAYER_ENCODING, layerEncodingIsInput)) {
			if (props.containsKey(SUBSTRATE_NEURON_MODEL_PARAMS)|| props.containsKey(SUBSTRATE_SYNAPSE_MODEL_PARAMS)) {
				logger.warn("Separate neuron and synapse model parameter outputs per layer are not currently supported, forcing " + HYPERNEAT_LAYER_ENCODING + " to true."); 
				props.setProperty(HYPERNEAT_LAYER_ENCODING, "true");
			}
			if (props.containsKey(SUBSTRATE_SYNAPSE_MODEL_TYPES)) {
				logger.warn("Separate weight outputs for each synapse type per layer are not currently supported, forcing " + HYPERNEAT_LAYER_ENCODING + " to true."); 
				props.setProperty(HYPERNEAT_LAYER_ENCODING, "true");
			}
		}
		
		super.init(props);

		this.properties = props;
		
		connectionWeightRange = connectionWeightMax - connectionWeightMin;
		
		// Weight for first synapse type comes from the standard CPPN weight output.
		// This gets set even if multiple synapse types aren't enabled.
		cppnIDXSynapseTypeWeights[0] = getCPPNIndexWeight()[0];
		// If multiple synapse types are enabled.
		if (props.containsKey(SUBSTRATE_SYNAPSE_MODEL_TYPES)) {
			String[] typeData = props.getProperty(SUBSTRATE_SYNAPSE_MODEL_TYPES).replaceAll("\\s|\\)", "").split(",");
			if (typeData.length > 1) {
				synapseModelTypeCount = typeData.length-1;
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
			if (cppnNeuronParamNames.length > 0) {
				cppnIDXNeuronParams = new int[cppnNeuronParamNames.length];
				for (int i = 0; i < cppnIDXNeuronParams.length; i++) {
					cppnIDXNeuronParams[i] = cppnOutputCount++;
				}
				neuronParamsEnabled = true;
			}
		}
		if (props.containsKey(SUBSTRATE_SYNAPSE_MODEL_PARAMS)) {
			cppnSynapseParamNames = props.getProperty(SUBSTRATE_SYNAPSE_MODEL_PARAMS).replaceAll("\\s", "").split(",");
			if (cppnSynapseParamNames.length > 0) {
				cppnIDXSynapseParams = new int[cppnSynapseParamNames.length];
				for (int i = 0; i < cppnIDXSynapseParams.length; i++) {
					cppnIDXSynapseParams[i] = cppnOutputCount++;
				}
				synapseParamsEnabled = true;
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
		return newBainNN(genotype, null);
	}

	@Override
	public BainNN transcribe(Chromosome genotype, BainNN substrate) throws TranscriberException {
		return newBainNN(genotype, substrate);
	}

	/**
	 * Create a new neural network from a genotype.
	 * 
	 * @param genotype chromosome to transcribe
	 * @return phenotype If given this will be updated and returned, if NULL then a new network will be created.
	 * @throws TranscriberException
	 */
	public BainNN newBainNN(Chromosome genotype, BainNN substrate) throws TranscriberException {
		CPPN cppn = new CPPN(genotype);
				
		//substrate = null;
		boolean createNewPhenotype = (substrate == null);
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		if (createNewPhenotype) {
			String neuronModelClass = properties.getProperty(SUBSTRATE_NEURON_MODEL, "ojc.bain.neuron.rate.SigmoidNeuronCollection");
			String synapseModelClass = properties.getProperty(SUBSTRATE_SYNAPSE_MODEL, "ojc.bain.synapse.rate.FixedSynapseCollection");
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
			if (!neuronParamsEnabled && neurons.getConfigSingleton() != null && neurons.getConfigSingleton().getPreset(0) != null) {
				neurons.addConfiguration(neurons.getConfigSingleton().getPreset(0));
			}

			try {
				synapses = (SynapseCollection) ComponentCollection.createCollection(synapseModelClass, synapseCount);
			} catch (Exception e) {
				e.printStackTrace();
				throw new TranscriberException("Error creating synapses for Bain neural network. Have you specified the name of the synapse collection class correctly, including the containing packages?", e);
			}
			// If the CPPN will not be setting synapse model parameters and the synapse collection is configurable and the configuration has a default preset.
			if (!synapseParamsEnabled && synapses.getConfigSingleton() != null && synapses.getConfigSingleton().getPreset(0) != null) {
				synapses.addConfiguration(neurons.getConfigSingleton().getPreset(0));
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
		for (int tz = feedForward ? 1 : 0; tz < depth; tz++) {
			for (int ty = 0; ty < height[tz]; ty++) {
				for (int tx = 0; tx < width[tz]; tx++) {
					cppn.setTargetCoordinatesFromGridIndices(tx, ty, tz);
					int bainNeuronIndexTarget = getBainNeuronIndex(tx, ty, tz);

					// Bias for each neuron (non-inputs for feed-forward) and/or neuron model parameters.
					if (enableBias || neuronParamsEnabled) {
						cppn.setSourceCoordinatesFromGridIndices(tx, ty, tz);
						cppn.query();
						
						if (enableBias) {
							double biasVal = 0;
							int cppnOutputIndex = layerEncodingIsInput ? 0 : tz - 1;
							if (!enableLEO || enableLEO && cppn.getLEO(cppnOutputIndex) > 0) {
								biasVal = cppn.getRangedBiasWeight(cppnOutputIndex, connectionWeightMin, connectionWeightMax, connectionWeightRange, connectionExprThresh);
							}
							((NeuronCollectionWithBias) neurons).setBias(bainNeuronIndexTarget, biasVal);
						}
						
						if (neuronParamsEnabled) {
							// Each neuron has its own configuration object.
							ComponentConfiguration c = createNewPhenotype ? neurons.getConfigSingleton().createConfiguration() : neurons.getComponentConfiguration(bainNeuronIndexTarget);
							// Set parameters for the config.
							for (int p = 0; p < cppnNeuronParamNames.length; p++) {
								// We suppress the change event call as we're setting many parameter values, so we call init() on the neuron and synapse collections when we're done.
								c.setParameterValue(cppnNeuronParamNames[p], cppn.getOutput(cppnIDXNeuronParams[p]), true);
							}
							if (createNewPhenotype) {
								// Add the configuration to the neuron collection.
								neurons.addConfiguration(c);
								// Set the current neuron to use the new configuration.
								neurons.setComponentConfiguration(bainNeuronIndexTarget, bainNeuronIndexTarget);
							}
						}
					}
					
					// Iteration over layers for the source neuron is only used for recurrent networks.
					for (int sz = (feedForward ? tz - 1 : 0); sz < (feedForward ? tz : depth); sz++) {
						for (int sy = 0; sy < height[sz]; sy++) {
							for (int sx = 0; sx < width[sz]; sx++) {
								cppn.setSourceCoordinatesFromGridIndices(sx, sy, sz);
								cppn.query();

								int bainNeuronIndexSource = feedForward ? getBainNeuronIndex(sx, sy, sz) : getBainNeuronIndex(sx, sy, sz);
								
								for (int synapseType = 0; synapseType < synapseModelTypeCount; synapseType++) {
									synapses.setPreAndPostNeurons(synapseIndex, bainNeuronIndexSource, bainNeuronIndexTarget);
									
									// Determine weight for synapse from source to target.
									int cppnOutputIndex = !synapseTypesEnabled ? this.getCPPNIndexWeight()[layerEncodingIsInput ? 0 : sz] : cppnIDXSynapseTypeWeights[synapseType];
									double weightVal = 0;
									if (!enableLEO || enableLEO && cppn.getLEO(layerEncodingIsInput ? 0 : sz) > 0) {
										weightVal = cppn.getRangedOutput(cppnOutputIndex, connectionWeightMin, connectionWeightMax, connectionWeightRange, connectionExprThresh);
									}
									synapseWeights[synapseIndex] = weightVal;
									
									if (synapseParamsEnabled || synapseTypesEnabled) {
										// Each synapse has its own configuration object.
										SynapseConfiguration c = (SynapseConfiguration) (createNewPhenotype ? synapses.getConfigSingleton().createConfiguration() : synapses.getComponentConfiguration(synapseIndex));
										c.minimumEfficacy = connectionWeightMin;
										c.maximumEfficacy = connectionWeightMax;
										// Set parameters for the config.
										for (int p = 0; p < cppnSynapseParamNames.length; p++) {
											double v = cppn.getRangedOutput(cppnIDXSynapseParams[p], connectionWeightMin, connectionWeightMax, connectionWeightRange, connectionExprThresh);
											c.setParameterValue(cppnSynapseParamNames[p], v, true);
										}
										if (synapseTypesEnabled) {
											c.setParameterValue(synapseModelTypeParam, synapseModelTypes[synapseType], true);
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
	
									if (feedForward) {
										assert synapseIndex == getBainSynapseIndex(tx, ty, tz, sx, sy, synapseType);
									} else {
										assert synapseIndex == getBainSynapseIndex(tx, ty, tz, sx, sy, sz, synapseType);
									}
	
									synapseIndex++;
								} // synapseType
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
				ffSynapseLayerSize[l - 1] = neuronLayerSize[l - 1] * neuronLayerSize[l] * synapseModelTypeCount;
				bainIndexForFFSynapseLayer[l - 1] = synapseCount;
				synapseCount += ffSynapseLayerSize[l - 1];
			}
		}
		if (!feedForward) {
			synapseCount = neuronCount * neuronCount * synapseModelTypeCount;
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
	 * @param type The index of the type of synapse. Set to 0 if multiple synapse types not enabled.
	 */
	public int getBainSynapseIndex(int tx, int ty, int tz, int sx, int sy, int type) {
		return (bainIndexForFFSynapseLayer[tz - 1] + width[tz - 1] * (height[tz - 1] * (width[tz] * ty + tx) + sy) + sx) * synapseModelTypeCount + type;
	}

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
	 * @param type The index of the type of synapse. Set to 0 if multiple synapse types not enabled.
	 */
	public int getBainSynapseIndex(int tx, int ty, int tz, int sx, int sy, int sz, int type) {
		return (getBainNeuronIndex(tx, ty, tz) * neuronCount + getBainNeuronIndex(sx, sy, sz)) * synapseModelTypeCount + type;
	}
}
