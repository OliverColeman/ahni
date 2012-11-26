package ojc.ahni.transcriber;

import ojc.ahni.nn.BainNN;
import ojc.bain.NeuralNetwork;
import ojc.bain.base.ComponentCollection;
import ojc.bain.base.NeuronCollection;
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
import com.anji.util.*;

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
	public static final String SUBSTRATE_SYNAPSE_MODEL = "ann.hyperneat.bain.synapse.model";
	
	/**
	 * When determining if the substrate network is recurrent, the maximum cycle length to search for before the 
	 * network is considered recurrent. Note that whichever is the smallest of this value and the number of 
	 * neurons in the network is used for any given network.
	 */
	public static final String SUBSTRATE_MAX_RECURRENT_CYCLE = "ann.hyperneat.bain.maxrecurrentcyclesearchlength";
	

	private final static Logger logger = Logger.getLogger(HyperNEATTranscriberBain.class);

	private Properties properties;
	private int[] neuronLayerSize, bainIndexForNeuronLayer, ffSynapseLayerSize, bainIndexForFFSynapseLayer; // ff=feed
																											// forward
	private int neuronCount, synapseCount;

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
	public BainNN newBainNN(Chromosome genotype, BainNN phenotype) throws TranscriberException {
		CPPN cppn = new CPPN(genotype);

		boolean createNewPhenotype = (phenotype == null);
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
				// If the neuron collection is configurable and the configuration has a default preset.
				if (neurons.getConfigSingleton() != null && neurons.getConfigSingleton().getPreset(0) != null) {
					neurons.addConfiguration(neurons.getConfigSingleton().getPreset(0));
				}
			} catch (Exception e) {
				throw new TranscriberException("Error creating neurons for Bain neural network. Have you specified the name of the neuron collection class correctly, including the containing packages?", e);
			}
			try {
				synapses = (SynapseCollection) ComponentCollection.createCollection(synapseModelClass, synapseCount);
				// If the synapse collection is configurable and the configuration has a default preset.
				if (synapses.getConfigSingleton() != null && synapses.getConfigSingleton().getPreset(0) != null) {
					synapses.addConfiguration(neurons.getConfigSingleton().getPreset(0));
				}
			} catch (Exception e) {
				throw new TranscriberException("Error creating synapses for Bain neural network. Have you specified the name of the synapse collection class correctly, including the containing packages?", e);
			}
		} else {
			neurons = phenotype.getNeuralNetwork().getNeurons();
			synapses = phenotype.getNeuralNetwork().getSynapses();
		}
		double[] synapseWeights = synapses.getEfficacies();

		int synapseIndex = 0;

		// query CPPN for substrate connection weights
		for (int tz = feedForward ? 1 : 0; tz < depth; tz++) {
			for (int ty = 0; ty < height[tz]; ty++) {
				for (int tx = 0; tx < width[tz]; tx++) {
					cppn.setTargetCoordinatesFromGridIndices(tx, ty, tz);

					int bainNeuronIndexTarget = getBainNeuronIndex(tx, ty, tz);

					// Iteration over layers for the source neuron is only used for recurrent networks.
					for (int sz = (feedForward ? tz - 1 : 0); sz < (feedForward ? tz : depth); sz++) {
						for (int sy = 0; sy < height[sz]; sy++) {
							for (int sx = 0; sx < width[sz]; sx++) {
								cppn.setSourceCoordinatesFromGridIndices(sx, sy, sz);

								cppn.query();

								int bainNeuronIndexSource = feedForward ? getBainNeuronIndex(sx, sy, sz) : getBainNeuronIndex(sx, sy, sz);

								synapses.setPreAndPostNeurons(synapseIndex, bainNeuronIndexSource, bainNeuronIndexTarget);

								// Determine weight for synapse from source to target.
								double weightVal = 0;
								if (layerEncodingIsInput) {
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getWeight()));
								}
								else {
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getWeight(sz)));
								}
								if (enableLEO) {
									double leo = layerEncodingIsInput ? cppn.getLEO() : cppn.getLEO(sz);
									weightVal = leo > 0 ? weightVal : 0; 
								}
								// Conventional thresholding.
								else if (Math.abs(weightVal) > connectionExprThresh) {
									if (weightVal > 0)
										weightVal = (weightVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
									else
										weightVal = (weightVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));
								}
								synapseWeights[synapseIndex] = weightVal;

								if (feedForward) {
									assert synapseIndex == getBainSynapseIndex(tx, ty, tz, sx, sy);
								} else {
									assert synapseIndex == getBainSynapseIndex(tx, ty, tz, sx, sy, sz);
								}

								synapseIndex++;

								// Bias for each neuron.
								if (enableBias && sz == 0 && sy == 0 && sx == 0) {
									double biasVal;
									if (layerEncodingIsInput)
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getBiasWeight()));
									else
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getBiasWeight(sz)));
									if (Math.abs(biasVal) > connectionExprThresh) {
										if (biasVal > 0)
											biasVal = (biasVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
										else
											biasVal = (biasVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));

										((NeuronCollectionWithBias) neurons).setBias(bainNeuronIndexTarget, biasVal);
									} else {
										((NeuronCollectionWithBias) neurons).setBias(bainNeuronIndexTarget, 0);
									}
								}
							} // sx
						} // sy
					} // sz
				} // tx
			} // ty
		} // tz

		if (createNewPhenotype) {
			int simRes = properties.getIntProperty(SUBSTRATE_SIMULATION_RESOLUTION, 1000);
			String execModeName = properties.getProperty(SUBSTRATE_EXECUTION_MODE, null);
			Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
			NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
			int[] outputDims = new int[] { width[depth - 1], height[depth - 1] };
			int[] inputDims = new int[] { width[0], height[0] };
			int maxRecurrentCycles = properties.getIntProperty(SUBSTRATE_MAX_RECURRENT_CYCLE, 1000);
			try {
				phenotype = new BainNN(nn, inputDims, outputDims, cyclesPerStep, feedForward ? BainNN.Topology.FEED_FORWARD_LAYERED : BainNN.Topology.RECURRENT, "network " + genotype.getId(), maxRecurrentCycles);
			} catch (Exception e) {
				throw new TranscriberException(e);
			}
			logger.info("Substrate has " + neuronCount + " neurons and " + synapseCount + " synapses.");
		} else {
			phenotype.setName("network " + genotype.getId());
		}

		synapses.setEfficaciesModified();

		return phenotype;
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
			synapseCount = neuronCount * neuronCount;
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
	public int getBainSynapseIndex(int tx, int ty, int tz, int sx, int sy) {
		return bainIndexForFFSynapseLayer[tz - 1] + width[tz - 1] * (height[tz - 1] * (width[tz] * ty + tx) + sy) + sx;
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
	 */
	public int getBainSynapseIndex(int tx, int ty, int tz, int sx, int sy, int sz) {
		return getBainNeuronIndex(tx, ty, tz) * neuronCount + getBainNeuronIndex(sx, sy, sz);
	}
}
