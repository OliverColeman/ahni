package ojc.ahni.hyperneat;

import ojc.bain.NeuralNetwork;
import ojc.bain.base.ComponentCollection;
import ojc.bain.base.NeuronCollection;
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
 * TODO this is currently not much more than a placeholder, it's copied from HyperNEATTranscriberGridNet.
 * 
 * Constructs a <a href="https://github.com/OliverColeman/bain">Bain</a> neural network from a chromosome using the hypercube (from HyperNEAT) encoding scheme.
 * An {@link com.anji.integration.ActivatorTranscriber} should be used to construct an instance of this class. {@link
 * com.anji.integration.ActivatorTranscriber.getNet()} or {@link com.anji.integration.ActivatorTranscriber.getPhenotype()} is then used to get the resulting
 * network.
 * 
 * To transcribe the neural network from a chromosome a connective pattern producing network (CPPN) is created from the chromosome, and then this is "queried"
 * to determine the weights of the neural network. The CPPN is an {@link com.anji.nn.AnjiNet}.
 * 
 * @author Oliver Coleman
 */
public class HyperNEATTranscriberBain implements Transcriber<BainNN>, Configurable {
	public static final String HYPERNEAT_FEED_FORWARD = "ann.hyperneat.feedforward";
	public static final String HYPERNEAT_ENABLE_BIAS = "ann.hyperneat.enablebias";
	public static final String HYPERNEAT_INCLUDE_DELTA = "ann.hyperneat.includedelta";
	public static final String HYPERNEAT_INCLUDE_ANGLE = "ann.hyperneat.includeangle";
	public static final String HYPERNEAT_LAYER_ENCODING = "ann.hyperneat.useinputlayerencoding";
	public static final String HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD = "ann.hyperneat.connection.expression.threshold";
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MIN = "ann.hyperneat.connection.weight.min";
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MAX = "ann.hyperneat.connection.weight.max";
	
	public static final String SUBSTRATE_SIMULATION_RESOLUTION = "ann.hyperneat.bain.resolution";
	public static final String SUBSTRATE_EXECUTION_MODE = "ann.hyperneat.bain.executionmode";
	public static final String SUBSTRATE_NEURON_MODEL = "ann.hyperneat.bain.neuron.model";
	public static final String SUBSTRATE_SYNAPSE_MODEL = "ann.hyperneat.bain.synapse.model";
	public static final String SUBSTRATE_STEPS_PER_STEP = "ann.hyperneat.stepsperstep";
	public static final String SUBSTRATE_DEPTH = "ann.hyperneat.depth";
	public static final String SUBSTRATE_HEIGHT = "ann.hyperneat.height";
	public static final String SUBSTRATE_WIDTH = "ann.hyperneat.width";
	

	private final static Logger logger = Logger.getLogger(HyperNEATTranscriberBain.class);

	private Properties properties;
	private AnjiNetTranscriber cppnTranscriber; // Creates AnjiNets, for use as a CPPN, from chromosomes.
	private boolean feedForward;
	private boolean enableBias;
	private boolean includeDelta;
	private boolean includeAngle;
	private double connectionExprThresh;
	private double connectionWeightMin;
	private double connectionWeightMax;
	private int depth;
	private boolean layerEncodingIsInput = false;
	private int[] height, width, neuronLayerSize, bainIndexForNeuronLayer, ffSynapseLayerSize, bainIndexForFFSynapseLayer; //ff=feed forward
	private int neuronCount, synapseCount;

	public HyperNEATTranscriberBain() {
	}

	public HyperNEATTranscriberBain(Properties props) {
		init(props);
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		this.properties = props;
		
		feedForward = props.getBooleanProperty(HYPERNEAT_FEED_FORWARD);
		enableBias = props.getBooleanProperty(HYPERNEAT_ENABLE_BIAS);
		includeDelta = props.getBooleanProperty(HYPERNEAT_INCLUDE_DELTA);
		includeAngle = props.getBooleanProperty(HYPERNEAT_INCLUDE_ANGLE);
		layerEncodingIsInput = props.getBooleanProperty(HYPERNEAT_LAYER_ENCODING, layerEncodingIsInput);
		connectionExprThresh = props.getFloatProperty(HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD);
		connectionWeightMin = props.getFloatProperty(HYPERNEAT_CONNECTION_WEIGHT_MIN);
		connectionWeightMax = props.getFloatProperty(HYPERNEAT_CONNECTION_WEIGHT_MAX);

		depth = props.getIntProperty(SUBSTRATE_DEPTH);
		String[] heightStr = props.getProperty(SUBSTRATE_HEIGHT).split(",");
		String[] widthStr = props.getProperty(SUBSTRATE_WIDTH).split(",");
		height = new int[depth];
		width = new int[depth];
		neuronLayerSize = new int[depth];
		bainIndexForNeuronLayer = new int[depth];
		ffSynapseLayerSize = new int[depth-1];
		bainIndexForFFSynapseLayer = new int[depth-1];
		neuronCount = 0;
		synapseCount = 0;
		for (int l = 0; l < depth; l++) {
			height[l] = Integer.parseInt(heightStr[l]);
			width[l] = Integer.parseInt(widthStr[l]);
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

		cppnTranscriber = (AnjiNetTranscriber) props.singletonObjectProperty(AnjiNetTranscriber.class);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	public BainNN transcribe(Chromosome genotype) throws TranscriberException {
		return newBainNN(genotype, null);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome, T substrate)
	 */
	public BainNN transcribe(Chromosome genotype, BainNN substrate) throws TranscriberException {
		return newBainNN(genotype, substrate);
	}

	/**
	 * create a new neural network from the a genotype.
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
		int cppnIdxTX = -1, cppnIdxTY = -1, cppnIdxTZ = -1, cppnIdxSX = -1, cppnIdxSY = -1;
		// deltas
		int cppnIdxDX = -1, cppnIdxDY = -1, cppnIdxAn = -1;

		int cppnInputIdx = 1; // 0 is always bias
		cppnIdxTX = cppnInputIdx++;
		cppnIdxTY = cppnInputIdx++;
		cppnIdxSX = cppnInputIdx++;
		cppnIdxSY = cppnInputIdx++;
		if (depth > 2 && layerEncodingIsInput) { // if depth == 2 network necessarily feed forward, and only one layer of connections can exist
			cppnIdxTZ = cppnInputIdx++;
			if (!feedForward) {
				if (includeDelta) {
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
		if (layerEncodingIsInput) {
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
					cppnIdxB[w] = cppnOutputIdx++; // weight value
			}
		}

		// System.out.println("ii: " + cppnInputIdx + "   oi: " + cppnOutputIdx);
		
		boolean createNewPhenotype = (phenotype == null);
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		if (createNewPhenotype) {
			String neuronModelClass = properties.getProperty(SUBSTRATE_NEURON_MODEL, "ojc.bain.neuron.rate.SigmoidNeuronCollection");
			String synapseModelClass = properties.getProperty(SUBSTRATE_SYNAPSE_MODEL, "ojc.bain.synapse.FixedSynapseCollection");
			try {
				neurons = (NeuronCollection) ComponentCollection.createCollection(neuronModelClass, neuronCount);
			} catch (Exception e) {
				System.err.println("Error creating neurons for Bain neural network. Have you specified the name of the neuron collection class correctly, including the containing packages?");
				e.printStackTrace();
			}
			try {
				synapses = (SynapseCollection) ComponentCollection.createCollection(synapseModelClass, synapseCount);
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
		double cppnTZ, cppnTY, cppnTX, cppnSY, cppnSX;
		int synapseIndex = 0;

		if (feedForward) {
			// query CPPN for substrate connection weights
			for (int tz = 1; tz < depth; tz++) {
				if (depth > 2 && layerEncodingIsInput) {
					// double cppnTZ =((double) tz*2) / (depth-1) - 1;
					cppnTZ = ((double) tz) / (depth - 1);
					cppnInput[cppnIdxTZ] = cppnTZ;
				}

				for (int ty = 0; ty < height[tz]; ty++) {
					// double cppnTY =((double) ty*2) / (height-1) - 1;
					if (height[tz] > 1)
						cppnTY = ((double) ty) / (height[tz] - 1);
					else
						cppnTY = 0.5f;
					cppnInput[cppnIdxTY] = cppnTY;

					for (int tx = 0; tx < width[tz]; tx++) {
						// double cppnTX = ((double) tx*2) / (width-1) - 1;
						if (width[tz] > 1)
							cppnTX = ((double) tx) / (width[tz] - 1);
						else
							cppnTX = 0.5f;
						cppnInput[cppnIdxTX] = cppnTX;

						// if (createNewPhenotype)
						// System.out.println(tz + "," + ty + "," + tx + "  dy = " + dy + "  dx = " + dx);

						// for each connection to target neuron at ZYX from source neurons in the preceding layer.
						for (int sy = 0; sy < height[tz-1]; sy++) {
							// double cppnTY =((double) ty*2) / (height-1) - 1;
							if (height[tz-1] > 1)
								cppnSY = ((double) sy) / (height[tz-1] - 1);
							else
								cppnSY = 0.5f;
							cppnInput[cppnIdxSY] = cppnSY;

							for (int sx = 0; sx < width[tz-1]; sx++) {
								// double cppnTX = ((double) tx*2) / (width-1) - 1;
								if (width[tz-1] > 1)
									cppnSX = ((double) sx) / (width[tz-1] - 1);
								else
									cppnSX = 0.5f;
								cppnInput[cppnIdxSX] = cppnSX;

								// System.out.println(tx + "," + ty + " - " + sx + "," + sy + "  (" + cppnTX + "," + cppnTY + " - " + cppnSX + "," + cppnSY + ")");

								// delta
								if (includeDelta) {
									cppnInput[cppnIdxDY] = cppnSY - cppnTY;
									cppnInput[cppnIdxDX] = cppnSX - cppnTX;
								}
								if (includeAngle) {
									double angle = (double) Math.atan2(cppnSY - cppnTY, cppnSX - cppnTX);
									angle /= 2 * (double) Math.PI;
									if (angle < 0)
										angle += 1;
									cppnInput[cppnIdxAn] = angle;
									// System.out.println(tx + "," + ty + " - " + sx + "," + sy + " : " + Math.toDegrees(angle*Math.PI*2));
								}

								cppnActivator.reset();
								double[] cppnOutput = cppnActivator.next(cppnInput);
								
								synapses.setPreAndPostNeurons(synapseIndex, getBainNeuronIndex(sx, sy, tz-1), getBainNeuronIndex(tx, ty, tz)); 

								// Determine weight for synapse from source at (sx, sy, tz-1) to target at (tx, ty, tz)
								double weightVal;
								if (layerEncodingIsInput)
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxW[0]]));
								else
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxW[tz - 1]]));
								if (Math.abs(weightVal) > connectionExprThresh) {
									if (weightVal > 0)
										weightVal = (weightVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
									else
										weightVal = (weightVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));

									synapseWeights[synapseIndex] = weightVal;
								} else {
									synapseWeights[synapseIndex] = 0;
								}
								
								assert synapseIndex == getBainSynapseIndex(tx, ty, tz, sx, sy);
								
								synapseIndex++;
/*
								// bias
								if (enableBias && sy == ty && sx == tx) {
									double biasVal;
									if (layerEncodingIsInput)
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxB[0]]));
									else
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxB[tz - 1]]));
									if (Math.abs(biasVal) > connectionExprThresh) {
										if (biasVal > 0)
											biasVal = (biasVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
										else
											biasVal = (biasVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));

										bias[tz - 1][ty][tx] = biasVal;
									} else {
										bias[tz - 1][ty][tx] = 0;
									}
								}
*/
								// w[wy][wx] = (ty==sy && tx==sx ? 1 : 0);

								// System.out.print("\t" + w[wy][wx]);
							}
							// System.out.println();
						}
						// System.out.println();
					}
				}

				// System.out.println();
			}
			// System.out.println();
			// System.out.println();


			if (createNewPhenotype) {
				int simRes = properties.getIntProperty(SUBSTRATE_SIMULATION_RESOLUTION, 1000);
				int stepsPerStep = properties.getIntProperty(SUBSTRATE_STEPS_PER_STEP, 1);
				String execModeName = properties.getProperty(SUBSTRATE_EXECUTION_MODE, null);
				Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
				NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
				phenotype = new BainNN(nn, stepsPerStep, "network " + genotype.getId());
				logger.info("Substrate has " + neuronCount  + " neurons and " + synapseCount + " synapses.");
			} else {
				phenotype.setName("network " + genotype.getId());
			}
		} else { // RECURRENT
			/*
			if (createNewPhenotype) {
				weights = new double[depth - 1][][][][][];
				for (int l = 1; l < depth; l++)
					weights[l - 1] = new double[height[l]][width[l]][][][];
			} else {
				weights = phenotype.getWeights();
			}

			// query CPPN for substrate connection weights
			for (int tz = 1; tz < depth; tz++) {
				cppnTZ = ((double) tz) / (depth - 1);
				cppnInput[1] = cppnTZ;

				for (int ty = 0; ty < height[tz]; ty++) {
					if (height[tz] > 1)
						cppnTY = ((double) ty) / (height[tz] - 1);
					else
						cppnTY = 0.5f;
					cppnInput[2] = cppnTY;

					for (int tx = 0; tx < width[tz]; tx++) {
						if (width[tz] > 1)
							cppnTX = ((double) tx) / (width[tz] - 1);
						else
							cppnTX = 0.5f;
						cppnInput[3] = cppnTX;

						// calculate dimensions of this weight matrix (bounded by grid edges)
						int dz = Math.min(depth - 1, tz + connectionRange) - Math.max(1, tz - connectionRange) + 1; // no connections to input layer
						int dy = Math.min(height[tz] - 1, ty + connectionRange) - Math.max(0, ty - connectionRange) + 1;
						int dx = Math.min(width[tz] - 1, tx + connectionRange) - Math.max(0, tx - connectionRange) + 1;

						// System.out.println(z + "," + y + "," + x + "  dz = "
						// + dz + "  dy = " + dy + "  dx = " + dx);

						weights[tz - 1][ty][tx] = new double[dz][dy][dx];
						double[][][] w = weights[tz - 1][ty][tx];

						// for each connection to t{zyx}
						// w{z,y,x} is index into weight matrix
						// s{z,y,x} is index of source neuron
						for (int wz = 0, sz = Math.max(1, tz - connectionRange); wz < dz; wz++, sz++) {
							cppnSZ = ((double) sz) / (depth - 1);
							cppnInput[4] = cppnSZ;

							for (int wy = 0, sy = Math.max(0, ty - connectionRange); wy < dy; wy++, sy++) {
								if (height[tz] > 1)
									cppnSY = ((double) sy) / (height[tz] - 1);
								else
									cppnSY = 0.5f;
								cppnInput[5] = cppnSY;

								for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
									if (width[tz] > 1)
										cppnSX = ((double) sx) / (width[tz] - 1);
									else
										cppnSX = 0.5f;
									cppnInput[6] = cppnSX;

									// delta
									if (includeDelta) {
										cppnInput[7] = cppnSZ - cppnTZ;
										cppnInput[8] = cppnSY - cppnTY;
										cppnInput[9] = cppnSX - cppnTX;
									}

									cppnActivator.reset();
									double[] cppnOutput = cppnActivator.next(cppnInput);

									// weight
									double weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[0]));
									if (Math.abs(weightVal) > connectionExprThresh) {
										if (weightVal > 0)
											weightVal = (weightVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
										else
											weightVal = (weightVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));

										w[wz][wy][wx] = weightVal;
									} else {
										w[wz][wy][wx] = 0;
									}

									// bias
									if (enableBias && wz == 0 && wy == 0 && wx == 0) {
										double biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[1]));
										if (Math.abs(biasVal) > connectionExprThresh) {
											if (biasVal > 0)
												biasVal = (biasVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
											else
												biasVal = (biasVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));

											bias[tz - 1][ty][tx] = biasVal;
										} else {
											bias[tz - 1][ty][tx] = 0;
										}
									}

								}
								// System.out.println();
							}
						}
						// System.out.println();
					}
				}
			}

			if (createNewPhenotype) {
				int[][] layerDimensions = new int[2][depth];
				for (int l = 0; l < depth; l++) {
					layerDimensions[0][l] = width[l];
					layerDimensions[1][l] = height[l];
				}

				phenotype = new GridNet(connectionRange, layerDimensions, weights, bias, activationFunction, stepsPerStep, "network " + genotype.getId());
				logger.info("Substrate has " + phenotype.getConnectionCount(true) + " connections.");
			} else {
				phenotype.setName("network " + genotype.getId());
			}
			*/
		}
		
		synapses.setEfficaciesModified();

		return phenotype;
	}

	public void resize(int[] width, int[] height) {
		this.width = width;
		this.height = height;
	}

	
	/**
	 * @see com.anji.integration.Transcriber#getPhenotypeClass()
	 */
	public Class getPhenotypeClass() {
		return GridNet.class;
	}

	public int getDepth() {
		return depth;
	}

	public int[] getWidth() {
		return width;
	}

	public int[] getHeight() {
		return height;
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
}
