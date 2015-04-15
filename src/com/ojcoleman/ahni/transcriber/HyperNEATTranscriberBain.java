package com.ojcoleman.ahni.transcriber;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ojcoleman.bain.NeuralNetwork;
import com.ojcoleman.bain.base.ComponentConfiguration;
import com.ojcoleman.bain.base.NeuronCollection;
import com.ojcoleman.bain.base.SynapseCollection;
import com.ojcoleman.bain.base.SynapseConfiguration;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.amd.aparapi.Kernel;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
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
public class HyperNEATTranscriberBain extends HyperNEATTranscriberBainBase {
	/**
	 * When determining if the substrate network is recurrent, the maximum cycle length to search for before the network
	 * is considered recurrent. Note that whichever is the smallest of this value and the number of neurons in the
	 * network is used for any given network.
	 */
	public static final String SUBSTRATE_MAX_RECURRENT_CYCLE = "ann.transcriber.bain.maxrecurrentcyclesearchlength";

	private final static Logger logger = Logger.getLogger(HyperNEATTranscriberBain.class);
	private final static DecimalFormat nf = new DecimalFormat(" 0.00;-0.00");
	

	private Properties properties;
	// ff = feed-forward
	private int[] neuronLayerSize, bainIndexForNeuronLayer, ffSynapseLayerSize, bainIndexForFFSynapseLayer;
	private int neuronCount, synapseCount;
	

	public HyperNEATTranscriberBain() {
	}

	public HyperNEATTranscriberBain(Properties props) {
		init(props);
	}

	@Override
	public void init(Properties props) {
		super.init(props);
		this.properties = props;
		
		neuronLayerSize = new int[depth];
		bainIndexForNeuronLayer = new int[depth];
		if (feedForward) {
			ffSynapseLayerSize = new int[depth - 1];
			bainIndexForFFSynapseLayer = new int[depth - 1];
		}
		resize(width, height, -1); // Initialise above arrays.
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
		boolean recordCoords = true; // options == null ? false : options.get("recordCoordinates").equals(Boolean.TRUE);
		
		CPPN cppn = new CPPN(genotype);
				
		boolean createNewSubstrate = (substrate == null);
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		if (createNewSubstrate) {
			String neuronModelClass = properties.getProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL, "com.ojcoleman.bain.neuron.rate.SigmoidNeuronCollection");
			String synapseModelClass = properties.getProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL, "com.ojcoleman.bain.synapse.rate.FixedSynapseCollection");
			try {
				neurons = BainNN.createNeuronCollection(neuronModelClass, neuronCount, enableBias, neuronTypesEnabled, neuronParamsEnabled);
			} catch (Exception e) {
				e.printStackTrace();
				throw new TranscriberException("Error creating neurons for Bain neural network. Have you specified the name of the neuron collection class correctly, including the containing packages?", e);
			}
			try {
				synapses = BainNN.createSynapseCollection(synapseModelClass, synapseCount, synapseTypesEnabled, synapseParamsEnabled, connectionWeightMin, connectionWeightMax);
			} catch (Exception e) {
				e.printStackTrace();
				throw new TranscriberException("Error creating synapses for Bain neural network. Have you specified the name of the synapse collection class correctly, including the containing packages?", e);
			}
			
			if (neuronParamsEnabled && neuronModelParamClassCount > 0) {
				// Create configurations for neuron parameter classes.
				for (int c = 0; c < neuronModelParamClassCount; c++) {
					ComponentConfiguration config = neurons.getConfigSingleton().createConfiguration();
					neurons.addConfiguration(config);
				}
			}
			if (synapseParamsEnabled && synapseModelParamClassCount > 0) {
				// Create configurations for neuron parameter classes.
				for (int c = 0; c < synapseModelParamClassCount; c++) {
					ComponentConfiguration config = synapses.getConfigSingleton().createConfiguration();
					synapses.addConfiguration(config);
				}
				// Add an additional config for disabled synapses if necessary.
				if (synapseDisableParamName != null) {
					ComponentConfiguration config = synapses.getConfigSingleton().createConfiguration();
					config.setParameterValue(synapseDisableParamName, 0, true);
					synapses.addConfiguration(config);
					synapseDisableParamClassIndex = synapseModelParamClassCount;
				}
			}
		} else {
			neurons = substrate.getNeuralNetwork().getNeurons();
			synapses = substrate.getNeuralNetwork().getSynapses();
			
			// If synapse parameters are enabled and a separate config object is used for each synapse
			// Reset the indices for these in case SynapseCollection.compress() changed the ordering last time around.
			// Not strictly necessary but makes it easier to debug things.
			if (synapseParamsEnabled && synapseModelParamClassCount == 0) {
				for (int si = 0; si < synapses.getSize(); si++) {
					synapses.setComponentConfiguration(si, si);
				}
			}
		}
		
		if (neuronParamsEnabled && neuronModelParamClassCount > 0) {
			double[][] values = this.getNeuronParameterValues(genotype);
			for (int c = 0; c < neuronModelParamClassCount; c++) {
				// Set parameters for the config.
				ComponentConfiguration config = neurons.getConfiguration(c);
				for (int p = 0; p < neuronParamNames.length; p++) {
					// If this is the parameter for the neuron type...
					if (neuronTypesEnabled && neuronParamNames[p].equals(neuronModelTypeParam)) {
						// The value will be in the range [0, neuronModelTypeCount), so apply the floor function to make it valid.
						config.setParameterValue(neuronParamNames[p], (int) values[c][p], true);
					} else {
						// Otherwise apply value as normal.
						config.setParameterValue(neuronParamNames[p], values[c][p], true);
					}
				}
			}
		}
		
		if (synapseParamsEnabled && synapseModelParamClassCount > 0) {
			double[][] values = this.getSynapseParameterValues(genotype);
			for (int c = 0; c < synapseModelParamClassCount; c++) {
				// Set parameters for the config.
				SynapseConfiguration config = (SynapseConfiguration) synapses.getConfiguration(c);
				for (int p = 0; p < synapseParamNames.length; p++) {
					//System.err.println("synapseParamNames: " + Arrays.toString(synapseParamNames));
					//System.err.println("synapseModelTypeParam: " + synapseModelTypeParam);
					// If this is the parameter for the synapse type...
					if (synapseTypesEnabled && synapseParamNames[p].equals(synapseModelTypeParam)) {
						// The value will be in the range [0, synapseModelTypeCount), so apply the floor function to make it valid.
						config.setParameterValue(synapseParamNames[p], (int) values[c][p], true);
					} else {
						// Otherwise apply value as normal.
						config.setParameterValue(synapseParamNames[p], values[c][p], true);
					}
				}
				config.minimumEfficacy = connectionWeightMin;
				config.maximumEfficacy = connectionWeightMax;
			}
		}
		
		double[] synapseWeights = synapses.getEfficacies();
		double sumOfSquaredConnectionLengths = 0;
		
		int synapseIndex = 0;
		
		// query CPPN for substrate neuron parameters.
		boolean[] neuronDisabled = new boolean[neuronCount];
		for (int z = 0; z < depth; z++) {
			for (int y = 0; y < height[z]; y++) {
				for (int x = 0; x < width[z]; x++) {
					cppn.resetSourceCoordinates();
					cppn.setTargetCoordinatesFromGridIndices(x, y, z);
					cppn.query();
					
					int bainNeuronIndex = getBainNeuronIndex(x, y, z);
					int neuronType = 0;
					if (neuronTypesEnabled) {
						if (neuronModelParamClassCount > 0) {
							int classIndex = cppn.getNeuronParamClassIndex();
							ComponentConfiguration c = neurons.getConfiguration(classIndex);
							neuronType = (int) c.getParameterValue(neuronModelTypeParam);
						}
						else {
							neuronType = cppn.getNeuronTypeIndex();
						}
					}

					int outputIndex = layerEncodingIsInput ? neuronType : z;
					
					setNeuronParameters(neurons, bainNeuronIndex, cppn, createNewSubstrate && neuronModelParamClassCount == 0);
					
					// Only allow disabling hidden neurons.
					neuronDisabled[bainNeuronIndex] = z > 0 && z < depth-1 && !cppn.getNEO(outputIndex);
					
					// If substrate is null then this is set below after we create the initial substrate.
					if (substrate != null) {
						substrate.setNeuronDisabled(bainNeuronIndex, neuronDisabled[bainNeuronIndex]);
					}
				}
			}
		}
		
		// Query CPPN for substrate synapse parameters.
		// Start at tz=1: don't allow connections to inputs.
		for (int tz = 1; tz < depth; tz++) {
			for (int ty = 0; ty < height[tz]; ty++) {
				for (int tx = 0; tx < width[tz]; tx++) {
					int bainNeuronIndexTarget = getBainNeuronIndex(tx, ty, tz);
					
					cppn.setTargetCoordinatesFromGridIndices(tx, ty, tz);
					
					// Iteration over layers for the source neuron is only used for recurrent networks.
					for (int sz = (feedForward ? tz - 1 : 0); sz < (feedForward ? tz : depth); sz++) {
						for (int sy = 0; sy < height[sz]; sy++) {
							for (int sx = 0; sx < width[sz]; sx++) {
								cppn.setSourceCoordinatesFromGridIndices(sx, sy, sz);
								cppn.query();
								int bainNeuronIndexSource = getBainNeuronIndex(sx, sy, sz);
								int synapseType = 0;
								if (synapseTypesEnabled) {
									if (synapseModelParamClassCount > 0) {
										int classIndex = cppn.getSynapseParamClassIndex();

										ComponentConfiguration c = synapses.getConfiguration(classIndex);
										synapseType = (int) c.getParameterValue(synapseModelTypeParam);
									}
									else {
										synapseType = cppn.getSynapseTypeIndex();
									}
								}
																
								int outputIndex = layerEncodingIsInput ? synapseType : sz;
								
								synapses.setPreAndPostNeurons(synapseIndex, bainNeuronIndexSource, bainNeuronIndexTarget);
								
								// Synapse is disabled if the source and target are the same neuron, 
								// or source or target neurons are disabled, or if the LEO specifies it.
								boolean disabled = tz==sz && ty==sy && tx==sx || neuronDisabled[bainNeuronIndexTarget] || neuronDisabled[bainNeuronIndexSource] || !cppn.getLEO(outputIndex);
								
								// Determine weight for synapse from source to target.
								synapseWeights[synapseIndex] = disabled ? 0 : cppn.getRangedWeight(outputIndex);
								
								// If we're not using LEO to explicitly enable synapses, then consider a synapse disabled if the weight is zero.
								if (!enableLEO && synapseWeights[synapseIndex] == 0) {
									disabled = true;
								}
								
								setSynapseParameters(synapses, synapseIndex, cppn, disabled, createNewSubstrate && synapseModelParamClassCount == 0);
								
								if (!disabled) {
									sumOfSquaredConnectionLengths += cppn.getSynapseLength() * cppn.getSynapseLength();
								}
								synapseIndex++;
							} // sx
						} // sy
					} // sz
				} // tx
			} // ty
		} // tz

		synapses.setEfficaciesModified();
		
		// Remove unused synapses from simulation calculations.
		//synapses.compress();
		
		if (createNewSubstrate) {
			int simRes = properties.getIntProperty(BainNN.SUBSTRATE_SIMULATION_RESOLUTION, 1000);
			String execModeName = properties.getProperty(BainNN.SUBSTRATE_EXECUTION_MODE, null);
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
			}
			Point p = new Point();
			for (int tz = 0; tz < depth; tz++) {
				for (int ty = 0; ty < height[tz]; ty++) {
					for (int tx = 0; tx < width[tz]; tx++) {
						int bainNeuronIndex = getBainNeuronIndex(tx, ty, tz);
						if (recordCoords) {
							cppn.getCoordinatesForGridIndices(tx, ty, tz, p);
							substrate.setCoords(bainNeuronIndex, p.x, p.y, p.z);
						}
						substrate.setNeuronDisabled(bainNeuronIndex, neuronDisabled[bainNeuronIndex]);
					}
				}
			}
			//logger.info("New substrate has " + neuronCount + " neurons and " + synapseCount + " synapses.");
			
		} else {
			substrate.setName("network " + genotype.getId());
			substrate.setStepsPerStepForNonLayeredFF();
		}
		
		substrate.setSumOfSquaredConnectionLengths(sumOfSquaredConnectionLengths);
				
		// This will cause the kernels to update configuration variables and push all relevant data to the OpenCL device if necessary.
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
