package com.ojcoleman.ahni.transcriber;

import com.ojcoleman.bain.neuron.rate.NeuronCollectionWithBias;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.Activator;
import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.AnjiActivator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.nn.*;
import com.anji.nn.activationfunction.ActivationFunction;
import com.anji.nn.activationfunction.ActivationFunctionFactory;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.GridNet;

/**
 * Constructs a {@link com.ojcoleman.ahni.nn.GridNet} neural network from a chromosome using the hypercube (from HyperNEAT)
 * encoding scheme. An {@link com.anji.integration.ActivatorTranscriber} should be used to construct an instance of this
 * class.
 * 
 * To transcribe the neural network from a {@link Chromosome} a connective pattern producing network (CPPN) is created
 * from the Chromosome, and then this is "queried" to determine the weight of each connection in the neural network. The
 * CPPN is an {@link com.anji.nn.AnjiNet}.
 * 
 * @author Oliver Coleman
 */
public class HyperNEATTranscriberGridNet extends HyperNEATTranscriber {
	public static final String HYPERNEAT_ACTIVATION_FUNCTION_KEY = "ann.hyperneat.activation.function";

	private final static Logger logger = Logger.getLogger(HyperNEATTranscriberGridNet.class);

	private ActivationFunction activationFunction;
	private boolean layerEncodingIsInput = false;

	public HyperNEATTranscriberGridNet() {
	}

	public HyperNEATTranscriberGridNet(Properties props) {
		init(props);
	}

	public void init(com.ojcoleman.ahni.hyperneat.Properties props) {
		super.init(props);
		activationFunction = ActivationFunctionFactory.getInstance().get(props.getProperty(HYPERNEAT_ACTIVATION_FUNCTION_KEY));
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	public GridNet transcribe(Chromosome genotype) throws TranscriberException {
		return newGridNet(genotype, null);
	}

	public GridNet transcribe(Chromosome genotype, Activator substrate) throws TranscriberException {
		return newGridNet(genotype, (GridNet) substrate);
	}

	/**
	 * Create a new neural network from the a genotype.
	 * 
	 * @param genotype chromosome to transcribe
	 * @return phenotype If given this will be updated and returned, if NULL then a new network will be created.
	 * @throws TranscriberException
	 */
	public GridNet newGridNet(Chromosome genotype, GridNet phenotype) throws TranscriberException {
		CPPN cppn = new CPPN(genotype);

		int connectionRange = this.connectionRange == -1 ? Integer.MAX_VALUE / 4 : this.connectionRange;

		double[][][][][][] weights;
		double[][][] bias;
		boolean createNewPhenotype = (phenotype == null);

		if (createNewPhenotype) {
			bias = new double[depth - 1][][];
			for (int l = 1; l < depth; l++)
				bias[l - 1] = new double[height[l]][width[l]];
			// logger.info("Creating new substrate.");
		} else {
			bias = phenotype.getBias();
		}

		if (feedForward) {
			if (createNewPhenotype) {
				weights = new double[depth - 1][][][][][];
				for (int l = 1; l < depth; l++)
					weights[l - 1] = new double[height[l]][width[l]][1][][];
			} else {
				weights = phenotype.getWeights();
			}

			// query CPPN for substrate connection weights
			for (int tz = 1; tz < depth; tz++) {
				for (int ty = 0; ty < height[tz]; ty++) {
					for (int tx = 0; tx < width[tz]; tx++) {
						cppn.setTargetCoordinatesFromGridIndices(tx, ty, tz);

						// bias
						if (enableBias) {
							cppn.setSourceCoordinatesFromGridIndices(tx, ty, tz);
							cppn.query();
							int cppnOutputIndex = layerEncodingIsInput ? 0 : tz-1; 						
							double biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getBiasWeight(cppnOutputIndex)));
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

						// calculate dimensions of this weight target matrix
						// (bounded by grid edges)
						int dy = Math.min(height[tz - 1] - 1, ty + connectionRange) - Math.max(0, ty - connectionRange) + 1;
						int dx = Math.min(width[tz - 1] - 1, tx + connectionRange) - Math.max(0, tx - connectionRange) + 1;

						// if (createNewPhenotype)
						// System.out.println(tz + "," + ty + "," + tx + "  dy = " + dy + "  dx = " + dx);

						if (createNewPhenotype)
							weights[tz - 1][ty][tx][0] = new double[dy][dx];
						double[][] w = weights[tz - 1][ty][tx][0];

						// System.out.println("\tsy0 = " + Math.max(0,
						// ty-connectionRange) + ", sx0 = " + Math.max(0,
						// tx-connectionRange));

						// for each connection to zyx
						// w{y,x} is index into weight matrix
						// s{y,x} is index of source neuron
						for (int wy = 0, sy = Math.max(0, ty - connectionRange); wy < dy; wy++, sy++) {
							for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
								cppn.setSourceCoordinatesFromGridIndices(sx, sy, tz-1);

								cppn.query();

								// Determine weight for synapse from source to target.
								int cppnOutputIndex = layerEncodingIsInput ? 0 : tz-1; 
								double weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getWeight(cppnOutputIndex)));
								
								if (enableLEO) {
									weightVal = cppn.getLEO(cppnOutputIndex) > 0 ? weightVal : 0; 
								}
								// Otherwise use conventional thresholding.
								else if (Math.abs(weightVal) > connectionExprThresh) {
									if (weightVal > 0)
										weightVal = (weightVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
									else
										weightVal = (weightVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));
								}
								else {
									weightVal = 0;
								}
								w[wy][wx] = weightVal;
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

			int[][][] connectionMaxRanges = new int[depth - 1][3][2];
			for (int l = 0; l < depth - 1; l++) {
				connectionMaxRanges[l][0][0] = -1; // no connections to previous or own layer
				connectionMaxRanges[l][0][1] = 1;
				connectionMaxRanges[l][1][0] = connectionRange;
				connectionMaxRanges[l][1][1] = connectionRange;
				connectionMaxRanges[l][2][0] = connectionRange;
				connectionMaxRanges[l][2][1] = connectionRange;
			}
			int[][] layerDimensions = new int[2][depth];
			for (int l = 0; l < depth; l++) {
				layerDimensions[0][l] = width[l];
				layerDimensions[1][l] = height[l];
			}

			if (createNewPhenotype) {
				phenotype = new GridNet(connectionMaxRanges, layerDimensions, weights, bias, activationFunction, 1, "network " + genotype.getId());
				logger.info("New substrate has input size " + width[0] + "x" + height[0] + " and " + phenotype.getConnectionCount(true) + " connections.");
			} else {
				phenotype.setName("network " + genotype.getId());
			}
		} else { // RECURRENT
			if (createNewPhenotype) {
				weights = new double[depth - 1][][][][][];
				for (int l = 1; l < depth; l++)
					weights[l - 1] = new double[height[l]][width[l]][][][];
			} else {
				weights = phenotype.getWeights();
			}

			// query CPPN for substrate connection weights
			for (int tz = 1; tz < depth; tz++) {
				for (int ty = 0; ty < height[tz]; ty++) {
					for (int tx = 0; tx < width[tz]; tx++) {
						cppn.setTargetCoordinatesFromGridIndices(tx, ty, tz);

						// calculate dimensions of this weight matrix (bounded by grid edges)
						int dz = Math.min(depth - 1, tz + connectionRange) - Math.max(1, tz - connectionRange) + 1; // no
																													// connections
																													// to
																													// input
																													// layer
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
							for (int wy = 0, sy = Math.max(0, ty - connectionRange); wy < dy; wy++, sy++) {
								for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
									cppn.setSourceCoordinatesFromGridIndices(sx, sy, sz);

									cppn.query();

									// weight
									double weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getWeight()));
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
										double biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppn.getBiasWeight()));
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

				phenotype = new GridNet(connectionRange, layerDimensions, weights, bias, activationFunction, cyclesPerStep, "network " + genotype.getId());
				logger.info("New substrate has " + phenotype.getConnectionCount(true) + " connections.");
			} else {
				phenotype.setName("network " + genotype.getId());
			}
		}

		return phenotype;
	}

	/**
	 * @see com.anji.integration.Transcriber#getPhenotypeClass()
	 */
	public Class getPhenotypeClass() {
		return GridNet.class;
	}
}
