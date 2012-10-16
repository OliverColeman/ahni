package ojc.ahni.hyperneat;

import ojc.bain.NeuralNetwork;
import ojc.bain.base.ComponentCollection;
import ojc.bain.base.NeuronCollection;
import ojc.bain.neuron.rate.NeuronCollectionWithBias;
import ojc.bain.base.SynapseCollection;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.amd.aparapi.Kernel;
import com.anji.integration.AnjiActivator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.nn.*;
import com.anji.util.*;

/**
 * TODO Implement support for recurrent networks.
 * 
 * Constructs a <a href="https://github.com/OliverColeman/bain">Bain</a> neural network from a chromosome using the hypercube (from HyperNEAT) encoding scheme.
 * An {@link com.anji.integration.ActivatorTranscriber} should be used to construct an instance of this class. {@link com.anji.integration.ActivatorTranscriber#newActivator(Chromosome)} is then used to get the resulting
 * network.
 * 
 * To transcribe the neural network from a chromosome a connective pattern producing network (CPPN) is created from the chromosome, and then this is "queried"
 * to determine the weights of the neural network. The CPPN is an {@link com.anji.nn.AnjiNet}.
 * 
 * @author Oliver Coleman
 */
public class HyperNEATTranscriberBain extends HyperNEATTranscriber<BainNN> {
	public static final String SUBSTRATE_SIMULATION_RESOLUTION = "ann.hyperneat.bain.resolution";
	public static final String SUBSTRATE_EXECUTION_MODE = "ann.hyperneat.bain.executionmode";
	public static final String SUBSTRATE_NEURON_MODEL = "ann.hyperneat.bain.neuron.model";
	public static final String SUBSTRATE_SYNAPSE_MODEL = "ann.hyperneat.bain.synapse.model";
	
	private final static Logger logger = Logger.getLogger(HyperNEATTranscriberBain.class);

	private Properties properties;
	private AnjiNetTranscriber cppnTranscriber; // Creates AnjiNets, for use as a CPPN, from chromosomes.
	private int[] neuronLayerSize, bainIndexForNeuronLayer, ffSynapseLayerSize, bainIndexForFFSynapseLayer; //ff=feed forward
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
			ffSynapseLayerSize = new int[depth-1];
			bainIndexForFFSynapseLayer = new int[depth-1];
		}
		resize(width, height, -1);  //Initialise above arrays.
		
		cppnTranscriber = (AnjiNetTranscriber) props.singletonObjectProperty(AnjiNetTranscriber.class);
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
		AnjiActivator cppnActivator = cppnTranscriber.transcribe(genotype);
		AnjiNet cppn = cppnActivator.getAnjiNet();

		// determine cppn input mapping
		// target and source coordinates
		int cppnIdxTX = -1, cppnIdxTY = -1, cppnIdxTZ = -1, cppnIdxSX = -1, cppnIdxSY = -1, cppnIdxSZ = -1;
		// deltas
		int cppnIdxDX = -1, cppnIdxDY = -1, cppnIdxDZ = -1, cppnIdxAn = -1;

		int cppnInputIdx = 1; // 0 is always bias
		cppnIdxTX = cppnInputIdx++;
		cppnIdxTY = cppnInputIdx++;
		cppnIdxSX = cppnInputIdx++;
		cppnIdxSY = cppnInputIdx++;
		if (feedForward && layerEncodingIsInput && depth > 2 || !feedForward && depth > 1) {
			cppnIdxTZ = cppnInputIdx++;
			if (!feedForward) {
				cppnIdxSZ = cppnInputIdx++;
				if (includeDelta) {
					cppnIdxDZ = cppnInputIdx++; // z delta
				}
			}
		}
		if (includeDelta) {
			cppnIdxDY = cppnInputIdx++; // y delta
			cppnIdxDX = cppnInputIdx++; // x delta
		}
		if (includeAngle) {
			cppnIdxAn = cppnInputIdx++; // angle
		}

		// determine cppn output mapping
		int[] cppnIdxW; // weights (either a single output for all layers or one output per layer)
		int[] cppnIdxB = new int[0]; // bias (either a single output for all layers or one output per layer)

		int cppnOutputIdx = 0;
		if (layerEncodingIsInput) { //same output for all layers
			cppnIdxW = new int[1];
			cppnIdxW[0] = cppnOutputIdx++; // weight value

			if (enableBias) {
				cppnIdxB = new int[1];
				cppnIdxB[0] = cppnOutputIdx++; // bias value
			}
		} else { // one output per layer
			cppnIdxW = new int[depth - 1];
			for (int w = 0; w < depth - 1; w++)
				cppnIdxW[w] = cppnOutputIdx++; // weight value

			if (enableBias) {
				cppnIdxB = new int[depth - 1];
				for (int w = 0; w < depth - 1; w++)
					cppnIdxB[w] = cppnOutputIdx++; // bias value
			}
		}

		// System.out.println("ii: " + cppnInputIdx + "   oi: " + cppnOutputIdx);
		
		boolean createNewPhenotype = (phenotype == null);
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		if (createNewPhenotype) {
			String neuronModelClass = properties.getProperty(SUBSTRATE_NEURON_MODEL, "ojc.bain.neuron.rate.SigmoidNeuronCollection");
			String synapseModelClass = properties.getProperty(SUBSTRATE_SYNAPSE_MODEL, "ojc.bain.synapse.rate.FixedSynapseCollection");
			try {
				if (enableBias) {
					neurons = (NeuronCollectionWithBias) ComponentCollection.createCollection(neuronModelClass, neuronCount);
				}
				else {
					neurons = (NeuronCollection) ComponentCollection.createCollection(neuronModelClass, neuronCount);
				}
				// If the neuron collection is configurable and the configuration has a default preset.
				if (neurons.getConfigSingleton() != null && neurons.getConfigSingleton().getPreset(0) != null) {
					neurons.addConfiguration(neurons.getConfigSingleton().getPreset(0));
				}
			} catch (Exception e) {
				System.err.println("Error creating neurons for Bain neural network. Have you specified the name of the neuron collection class correctly, including the containing packages?");
				e.printStackTrace();
			}
			try {
				synapses = (SynapseCollection) ComponentCollection.createCollection(synapseModelClass, synapseCount);
				// If the synapse collection is configurable and the configuration has a default preset.
				if (synapses.getConfigSingleton() != null && synapses.getConfigSingleton().getPreset(0) != null) {
					synapses.addConfiguration(neurons.getConfigSingleton().getPreset(0));
				}
			} catch (Exception e) {
				System.err.println("Error creating synapses for Bain neural network. Have you specified the name of the synapse collection class correctly, including the containing packages?");
				e.printStackTrace();
			}
		}
		else {
			neurons = phenotype.getNeuralNetwork().getNeurons();
			synapses = phenotype.getNeuralNetwork().getSynapses();
		}
		double[] synapseWeights = synapses.getEfficacies();
		
		double[] cppnInput = new double[cppn.getInputDimension()];
		cppnInput[0] = 1; // Bias for the CPPN. 
		
		// Current values for inputs to CPPN. T=target, S=source.
		double cppnTZ=0, cppnTY, cppnTX, cppnSZ=0, cppnSY, cppnSX;
		int synapseIndex = 0;
	
		// query CPPN for substrate connection weights
		for (int tz = feedForward ? 1 : 0; tz < depth; tz++) {
			if (cppnIdxTZ != -1) {
				cppnTZ = ((double) tz) / (depth - 1);
				cppnInput[cppnIdxTZ] = cppnTZ;
			}

			for (int ty = 0; ty < height[tz]; ty++) {
				if (height[tz] > 1)
					cppnTY = ((double) ty) / (height[tz] - 1);
				else
					cppnTY = 0.5f;
				cppnInput[cppnIdxTY] = cppnTY;

				for (int tx = 0; tx < width[tz]; tx++) {
					if (width[tz] > 1)
						cppnTX = ((double) tx) / (width[tz] - 1);
					else
						cppnTX = 0.5f;
					cppnInput[cppnIdxTX] = cppnTX;
					
					int bainNeuronIndexTarget = getBainNeuronIndex(tx, ty, tz);
					
					// Iteration over layers for the source neuron is only used for recurrent networks. 
					for (int sz = (feedForward ? tz-1 : 0); sz < (feedForward ? tz : depth); sz++) {
						if (cppnIdxSZ != -1) {
							cppnSZ = ((double) sz) / (depth - 1);
							cppnInput[cppnIdxSZ] = cppnSZ;
						}
	
						for (int sy = 0; sy < height[sz]; sy++) {
							if (height[sz] > 1)
								cppnSY = ((double) sy) / (height[sz] - 1);
							else
								cppnSY = 0.5f;
							cppnInput[cppnIdxSY] = cppnSY;
	
							for (int sx = 0; sx < width[sz]; sx++) {
								if (width[sz] > 1)
									cppnSX = ((double) sx) / (width[sz] - 1);
								else
									cppnSX = 0.5f;
								cppnInput[cppnIdxSX] = cppnSX;
	
								if (includeDelta) {
									cppnInput[cppnIdxDY] = cppnSY - cppnTY;
									cppnInput[cppnIdxDX] = cppnSX - cppnTX;
									if (cppnIdxDZ != -1) {
										cppnInput[cppnIdxDZ] = cppnSZ - cppnTZ;
									}
								}
								if (includeAngle) {
									double angle = (double) Math.atan2(cppnSY - cppnTY, cppnSX - cppnTX);
									angle /= 2 * (double) Math.PI;
									if (angle < 0)
										angle += 1;
									cppnInput[cppnIdxAn] = angle;
								}
								
								cppnActivator.reset();
								double[] cppnOutput = cppnActivator.next(cppnInput);
								
								int bainNeuronIndexSource = feedForward ? getBainNeuronIndex(sx, sy, sz) : getBainNeuronIndex(sx, sy, sz);
								
								synapses.setPreAndPostNeurons(synapseIndex, bainNeuronIndexSource, bainNeuronIndexTarget); 
	
								// Determine weight for synapse from source to target.
								double weightVal;
								if (layerEncodingIsInput)
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxW[0]]));
								else
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxW[sz]]));
								if (Math.abs(weightVal) > connectionExprThresh) {
									if (weightVal > 0)
										weightVal = (weightVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
									else
										weightVal = (weightVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));
	
									synapseWeights[synapseIndex] = weightVal;
								} else {
									synapseWeights[synapseIndex] = 0;
								}
								
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
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxB[0]]));
									else
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxB[sz]]));
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
							} //sx
						} //sy
					} //sz
				} //tx
			} //ty
		} //tz
		
		if (createNewPhenotype) {
			int simRes = properties.getIntProperty(SUBSTRATE_SIMULATION_RESOLUTION, 1000);
			String execModeName = properties.getProperty(SUBSTRATE_EXECUTION_MODE, null);
			Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
			NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
			int[] outputDims = new int[]{width[depth-1], height[depth-1]};
			phenotype = new BainNN(nn, outputDims, cyclesPerStep, true, "network " + genotype.getId());
			logger.info("Substrate has " + neuronCount  + " neurons and " + synapseCount + " synapses.");
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
				ffSynapseLayerSize[l-1] = neuronLayerSize[l-1] * neuronLayerSize[l];
				bainIndexForFFSynapseLayer[l-1] = synapseCount;
				synapseCount += ffSynapseLayerSize[l-1];
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
	 * @param x The location of the neuron on the x axis.
	 * @param y The location of the neuron on the y axis.
	 * @param z The location of the neuron on the z axis, or layer it is in.
	 */ 
	public int getBainNeuronIndex(int x, int y, int z) {
		return bainIndexForNeuronLayer[z] + y * width[z] + x;
	}
	
	/**
	 * For feed forward networks, get the index of the synapse in the Bain networks SynapseCollection connecting the neurons at the given location.
	 * The layer the source neuron is in is given by tz-1 and so need not be specified.
	 * @param tx The location of the target neuron on the x axis.
	 * @param ty The location of the target neuron on the y axis.
	 * @param tz The location of the target neuron on the z axis, or layer it is in.
	 * @param sx The location of the source neuron on the x axis.
	 * @param sy The location of the source neuron on the y axis.
	 */
	public int getBainSynapseIndex(int tx, int ty, int tz, int sx, int sy) {
		return bainIndexForFFSynapseLayer[tz-1] + width[tz-1] * (height[tz-1] * (width[tz] * ty + tx) + sy) + sx;
	}
	
	/**
	 * For fully recurrent networks, get the index of the synapse in the Bain networks SynapseCollection connecting the neurons at the given location.
	 * The layer the source neuron is in is given by tz-1 and so need not be specified.
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
