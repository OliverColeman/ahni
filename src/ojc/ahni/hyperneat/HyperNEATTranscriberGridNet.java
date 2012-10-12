package ojc.ahni.hyperneat;

import ojc.bain.neuron.rate.NeuronCollectionWithBias;

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
import com.anji.util.*;

/**
 * Constructs a {@link ojc.ahni.hyperneat.GridNet} neural network from a chromosome using the hypercube (from HyperNEAT) encoding scheme. An
 * {@link com.anji.integration.ActivatorTranscriber} should be used to construct an instance of this class. {@link
 * com.anji.integration.ActivatorTranscriber.getNet()} or {@link com.anji.integration.ActivatorTranscriber.getPhenotype()} is then used to get the resulting
 * network.
 * 
 * To transcribe the neural network from a chromosome a connective pattern producing network (CPPN) is created from the chromosome, and then this is "queried"
 * to determine the weights of the neural network. The CPPN is an {@link com.anji.nn.AnjiNet}.
 * 
 * @author Oliver Coleman
 */
public class HyperNEATTranscriberGridNet extends HyperNEATTranscriber {
	public static final String HYPERNEAT_ACTIVATION_FUNCTION_KEY = "ann.hyperneat.activation.function";
	
	private final static Logger logger = Logger.getLogger(HyperNEATTranscriberGridNet.class);

	private AnjiNetTranscriber cppnTranscriber; // creates AnjiNets from
												// chromosomes

	private ActivationFunction activationFunction;
	private boolean layerEncodingIsInput = false;
	
	public HyperNEATTranscriberGridNet() {
	}

	public HyperNEATTranscriberGridNet(Properties props) {
		init(props);
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		super.init(props);
		
		activationFunction = ActivationFunctionFactory.getInstance().get(props.getProperty(HYPERNEAT_ACTIVATION_FUNCTION_KEY));

		if (!feedForward)
			cyclesPerStep = props.getIntProperty(SUBSTRATE_CYCLES_PER_STEP);

		cppnTranscriber = (AnjiNetTranscriber) props.singletonObjectProperty(AnjiNetTranscriber.class);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	public GridNet transcribe(Chromosome genotype) throws TranscriberException {
		return newGridNet(genotype, null);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome, T substrate)
	 */
	public GridNet transcribe(Chromosome genotype, Activator substrate) throws TranscriberException {
		return newGridNet(genotype, (GridNet) substrate);
	}

	/**
	 * create a new neural network from the a genotype.
	 * 
	 * @param genotype chromosome to transcribe
	 * @return phenotype If given this will be updated and returned, if NULL then a new network will be created.
	 * @throws TranscriberException
	 */
	public GridNet newGridNet(Chromosome genotype, GridNet phenotype) throws TranscriberException {
		AnjiActivator cppnActivator = cppnTranscriber.transcribe(genotype);
		AnjiNet cppn = cppnActivator.getAnjiNet();
		
		int connectionRange = this.connectionRange == -1 ? Integer.MAX_VALUE/4 : this.connectionRange;
		
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

		double[] cppnInput = new double[cppn.getInputDimension()];
		cppnInput[0] = 1; // bias

		double cppnTZ, cppnTY, cppnTX, cppnSZ, cppnSY, cppnSX;

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
							// double cppnSY = ((double) sy * 2) / (height-1) - 1;
							if (height[tz - 1] > 1)
								cppnSY = ((double) sy) / (height[tz - 1] - 1);
							else
								cppnSY = 0.5f;

							cppnInput[cppnIdxSY] = cppnSY;

							for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
								// double cppnSX = ((double) sx * 2) / (width-1) - 1;
								if (width[tz - 1] > 1)
									cppnSX = ((double) sx) / (width[tz - 1] - 1);
								else
									cppnSX = 0.5f;

								cppnInput[cppnIdxSX] = cppnSX;

								// System.out.println(tx + "," + ty + " - " + sx + "," + sy + "  (" + cppnTX + "," + cppnTY + " - " + cppnSX + "," + cppnSY +
								// ")");

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

								// weights
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

									w[wy][wx] = weightVal;
								} else {
									w[wy][wx] = 0;
								}

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
				logger.info("Substrate has input size " + width[0] + "x" + height[0] + " and " + phenotype.getConnectionCount(true) + " connections.");
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

				phenotype = new GridNet(connectionRange, layerDimensions, weights, bias, activationFunction, cyclesPerStep, "network " + genotype.getId());
				logger.info("Substrate has " + phenotype.getConnectionCount(true) + " connections.");
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

	/*
	 * public static void main(String[] args) { Point2D.Double p1 = new Point2D.Double(1, 1); Point2D.Double p2 = new Point2D.Double(1, 2); for (double a = 0; a
	 * < Math.PI*2; a += Math.PI * 0.1) { p2.x = p1.x + Math.cos(a); p2.y = p1.y + Math.sin(a);
	 * 
	 * double angle = Math.atan2(p2.y-p1.y, p2.x-p1.x); if (angle < 0) angle += 2*Math.PI;
	 * 
	 * System.out.println(p1.x + "," + p1.y + " - " + (double)p2.x + "," + (double)p2.y + " - " + (double) Math.toDegrees(a) + " - " + (double)
	 * Math.toDegrees(angle) + " - " + (double) (angle / (2*Math.PI))); } }
	 */
}
