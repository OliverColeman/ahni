package com.anji.hyperneat;

import java.awt.Point;
import java.awt.geom.Point2D;

import org.apache.log4j.Logger;
import org.jgap.*;

import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.AnjiActivator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.nn.*;
import com.anji.nn.activationfunction.ActivationFunction;
import com.anji.nn.activationfunction.ActivationFunctionFactory;
import com.anji.util.*;

import com.javamex.classmexer.MemoryUtil;

/**
 * The purpose of this class is to construct a neural net object (
 * <code>GridNet</code>) from a chromosome. An <code>ActivatorTranscriber</code>
 * should be used to construct a <code>HyperNEATTranscriber</code>.
 * <code>getNet()</code> or <code>getPhenotype()</code> returns the resulting
 * network.
 * 
 * To transcribe a GridNet from a chromosome a connective pattern producing
 * network (CPPN) is created from the chromosome, and then this is "queried" to
 * determine the weights of the GridNet substrate. The CPPN is an AnjiNet, which
 * is an arbitrary topology network.
 * 
 * @see com.anji.hyperneat.GridNet
 * @author Oliver Coleman
 */
public class HyperNEATTranscriber implements Transcriber<GridNet>, Configurable {
	public static final String HYPERNEAT_ACTIVATION_FUNCTION_KEY = "ann.hyperneat.activation.function";
	public static final String HYPERNEAT_FEED_FORWARD_KEY = "ann.hyperneat.feedforward";
	public static final String HYPERNEAT_ENABLE_BIAS = "ann.hyperneat.enablebias";
	public static final String HYPERNEAT_INCLUDE_DELTA = "ann.hyperneat.includedelta";
	public static final String HYPERNEAT_INCLUDE_ANGLE = "ann.hyperneat.includeangle";
	public static final String HYPERNEAT_LAYER_ENCODING = "ann.hyperneat.useinputlayerencoding";
	public static final String HYPERNEAT_CYCLES_PER_STEP = "ann.hyperneat.cyclesperstep";
	public static final String HYPERNEAT_CONNECTION_RANGE = "ann.hyperneat.connection.range";
	public static final String HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD = "ann.hyperneat.connection.expression.threshold";
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MIN = "ann.hyperneat.connection.weight.min";
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MAX = "ann.hyperneat.connection.weight.max";
	public static final String HYPERNEAT_DEPTH = "ann.hyperneat.depth";
	public static final String HYPERNEAT_HEIGHT = "ann.hyperneat.height";
	public static final String HYPERNEAT_WIDTH = "ann.hyperneat.width";
	
	
	private final static Logger logger = Logger.getLogger(HyperNEATTranscriber.class);

	private AnjiNetTranscriber cppnTranscriber; // creates AnjiNets from
												// chromosomes
	private int genotypeRecurrentCycles;

	private ActivationFunction activationFunction;
	private boolean feedForward;
	private int cyclesPerStep;
	private boolean enableBias;
	private boolean includeDelta;
	private boolean includeAngle;
	private int connectionRange;
	private float connectionWeightMin;
	private float connectionWeightMax;
	private float connectionExprThresh;
	private int depth;
	private boolean layerEncodingIsInput = false;
	private int[] height, width;
	
	public HyperNEATTranscriber() {
	}

	public HyperNEATTranscriber(Properties props) {
		init(props);
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		activationFunction = ActivationFunctionFactory.getInstance().get(props.getProperty(HYPERNEAT_ACTIVATION_FUNCTION_KEY));

		feedForward = props.getBooleanProperty(HYPERNEAT_FEED_FORWARD_KEY);
		if (!feedForward)
			cyclesPerStep = props.getIntProperty(HYPERNEAT_CYCLES_PER_STEP);

		enableBias = props.getBooleanProperty(HYPERNEAT_ENABLE_BIAS);

		includeDelta = props.getBooleanProperty(HYPERNEAT_INCLUDE_DELTA);
		includeAngle = props.getBooleanProperty(HYPERNEAT_INCLUDE_ANGLE);
		
		layerEncodingIsInput = props.getBooleanProperty(HYPERNEAT_LAYER_ENCODING, layerEncodingIsInput);

		connectionRange = props.getIntProperty(HYPERNEAT_CONNECTION_RANGE);
		connectionExprThresh = props.getFloatProperty(HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD);
		connectionWeightMin = props.getFloatProperty(HYPERNEAT_CONNECTION_WEIGHT_MIN);
		connectionWeightMax = props.getFloatProperty(HYPERNEAT_CONNECTION_WEIGHT_MAX);

		depth = props.getIntProperty(HYPERNEAT_DEPTH);
		String[] heightStr = props.getProperty(HYPERNEAT_HEIGHT).split(",");
		String[] widthStr = props.getProperty(HYPERNEAT_WIDTH).split(",");
		height = new int[depth];
		width = new int[depth];
		for (int l = 0; l < depth; l++) {
			height[l] = Integer.parseInt(heightStr[l]);
			width[l] = Integer.parseInt(widthStr[l]);
		}

		cppnTranscriber = (AnjiNetTranscriber) props.singletonObjectProperty(AnjiNetTranscriber.class);
		genotypeRecurrentCycles = props.getIntProperty(ActivatorTranscriber.RECURRENT_CYCLES_KEY, 1);

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
	public GridNet transcribe(Chromosome genotype, GridNet substrate) throws TranscriberException {
		return newGridNet(genotype, substrate);
	}

	/**
	 * create new <code>GridNet</code> from <code>genotype</code>
	 * 
	 * @param genotype
	 *            chromosome to transcribe
	 * @return phenotype
	 * @throws TranscriberException
	 */
	public GridNet newGridNet(Chromosome genotype, GridNet phenotype) throws TranscriberException {
		AnjiNet cppn = cppnTranscriber.transcribe(genotype);
		AnjiActivator cppnActivator = new AnjiActivator(cppn, genotypeRecurrentCycles);

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
		if (depth > 2 && layerEncodingIsInput) { // if depth == 2 network necessarily feed forward, and only one layer of connections can exist
			cppnIdxTZ = cppnInputIdx++;
			if (!feedForward) {
				cppnIdxSZ = cppnInputIdx++; // source layer (could be any layer, not just previous layer)
				if (includeDelta) // delta only when not feed forward (z delta always 1 for FF network)
					cppnIdxDZ = cppnInputIdx++; // z delta
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
		int[] cppnIdxW; //weights (either a single output for all layers or one output per layer)
		int[] cppnIdxB = new int[0]; //bias (either a single output for all layers or one output per layer)
		
		int cppnOutputIdx = 0;
		if (layerEncodingIsInput) {
			cppnIdxW = new int[1];
			cppnIdxW[0] = cppnOutputIdx++; // weight value 
			
			if (enableBias) {
				cppnIdxB = new int[1];
				cppnIdxB[0] = cppnOutputIdx++; // bias value
			}
		} else { //one output per layer
			cppnIdxW = new int[depth-1];
			for (int w = 0; w < depth-1; w++)
				cppnIdxW[w] = cppnOutputIdx++; // weight value 
			
			if (enableBias) {
				cppnIdxB = new int[depth-1];
				for (int w = 0; w < depth-1; w++)
					cppnIdxB[w] = cppnOutputIdx++; // weight value 
			}
		}
		
		//System.out.println("ii: " + cppnInputIdx + "   oi: " + cppnOutputIdx);

		float[][][][][][] weights;
		float[][][] bias;
		boolean createNewPhenotype = (phenotype == null);
		
		if (createNewPhenotype) {
			bias = new float[depth - 1][][];
			for (int l = 1; l < depth; l++)
				bias[l-1] = new float[height[l]][width[l]];
			//logger.info("Creating new substrate.");
		} else {
			bias = phenotype.getBias();
		}

		float[] cppnInput = new float[cppn.getInputDimension()];
		cppnInput[0] = 1; // bias
		
		float cppnTZ, cppnTY, cppnTX, cppnSZ, cppnSY, cppnSX;	
		
		if (feedForward) {
			if (createNewPhenotype) {
				weights = new float[depth - 1][][][][][];
				for (int l = 1; l < depth; l++)
					weights[l-1] = new float[height[l]][width[l]][1][][];
			}
			else {
				weights = phenotype.getWeights();
			}
			
			// query CPPN for substrate connection weights
			for (int tz = 1; tz < depth; tz++) {
				if (depth > 2 && layerEncodingIsInput) {
					// float cppnTZ =((float) tz*2) / (depth-1) - 1;
					cppnTZ = ((float) tz) / (depth - 1);
					cppnInput[cppnIdxTZ] = cppnTZ;
				}

				for (int ty = 0; ty < height[tz]; ty++) {
					// float cppnTY =((float) ty*2) / (height-1) - 1;
					if (height[tz] > 1)
						cppnTY = ((float) ty) / (height[tz] - 1);
					else
						cppnTY = 0.5f;
					cppnInput[cppnIdxTY] = cppnTY;

					for (int tx = 0; tx < width[tz]; tx++) {
						// float cppnTX = ((float) tx*2) / (width-1) - 1;
						if (width[tz] > 1)
							cppnTX = ((float) tx) / (width[tz] - 1);
						else
							cppnTX = 0.5f;
						cppnInput[cppnIdxTX] = cppnTX;

						// calculate dimensions of this weight target matrix
						// (bounded by grid edges)
						int dy = Math.min(height[tz-1] - 1, ty + connectionRange) - Math.max(0, ty - connectionRange) + 1;
						int dx = Math.min(width[tz-1] - 1, tx + connectionRange) - Math.max(0, tx - connectionRange) + 1;
						
						//if (createNewPhenotype)
						//	System.out.println(tz + "," + ty + "," + tx + "  dy = " + dy + "  dx = " + dx);

						if (createNewPhenotype)
							weights[tz - 1][ty][tx][0] = new float[dy][dx];
						float[][] w = weights[tz - 1][ty][tx][0];

						// System.out.println("\tsy0 = " + Math.max(0,
						// ty-connectionRange) + ", sx0 = " + Math.max(0,
						// tx-connectionRange));

						// for each connection to zyx
						// w{y,x} is index into weight matrix
						// s{y,x} is index of source neuron
						for (int wy = 0, sy = Math.max(0, ty - connectionRange); wy < dy; wy++, sy++) {
							// float cppnSY = ((float) sy * 2) / (height-1) - 1;
							if (height[tz-1] > 1)
								cppnSY = ((float) sy) / (height[tz-1] - 1);
							else
								cppnSY = 0.5f;
								
							cppnInput[cppnIdxSY] = cppnSY;

							for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
								// float cppnSX = ((float) sx * 2) / (width-1) - 1;
								if (width[tz-1] > 1)
									cppnSX = ((float) sx) / (width[tz-1] - 1);
								else
									cppnSX = 0.5f;
								
								cppnInput[cppnIdxSX] = cppnSX;
								

								//System.out.println(tx + "," + ty + " - " + sx + "," + sy + "  (" + cppnTX + "," + cppnTY + " - " + cppnSX + "," + cppnSY + ")");
								
								// delta
								if (includeDelta) {
									cppnInput[cppnIdxDY] = cppnSY - cppnTY;
									cppnInput[cppnIdxDX] = cppnSX - cppnTX;
								}
								if (includeAngle) {
									float angle = (float) Math.atan2(cppnSY - cppnTY, cppnSX - cppnTX);
									angle /= 2 * (float) Math.PI;
									if (angle < 0)
										angle += 1;
									cppnInput[cppnIdxAn] = angle;
									//System.out.println(tx + "," + ty + " - " + sx + "," + sy + " : " + Math.toDegrees(angle*Math.PI*2));
								}
								
								cppnActivator.reset();
								float[] cppnOutput = cppnActivator.next(cppnInput);

								//weights
								float weightVal;
								if (layerEncodingIsInput)
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxW[0]]));
								else 
									weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxW[tz-1]]));
								if (Math.abs(weightVal) > connectionExprThresh) {
									if (weightVal > 0)
										weightVal = (weightVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
									else
										weightVal = (weightVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));

									w[wy][wx] = weightVal;
								} else {
									w[wy][wx] = 0;
								}
								
								//bias
								if (enableBias && sy == ty && sx == tx) {
									float biasVal;
									if (layerEncodingIsInput)
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxB[0]]));
									else
										biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[cppnIdxB[tz-1]]));
									if (Math.abs(biasVal) > connectionExprThresh) {
										if (biasVal > 0)
											biasVal = (biasVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
										else
											biasVal = (biasVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));
	
										bias[tz-1][ty][tx] = biasVal;
									} else {
										bias[tz-1][ty][tx] = 0;
									}
								}

								// w[wy][wx] = (ty==sy && tx==sx ? 1 : 0);

								// System.out.print("\t" + w[wy][wx]);
							}
							// System.out.println();
						}
						// System.out.println();
					}
				}
				
				//System.out.println();
			}
			//System.out.println();
			//System.out.println();
			
			int[][][] connectionMaxRanges = new int[depth-1][3][2];
			for (int l = 0; l < depth-1; l++) {
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
				logger.info("Substrate has " + phenotype.getConnectionCount(true) + " connections.");
				
				try {
					logger.info("Substrate memory size: " + (int) Math.round(MemoryUtil.deepMemoryUsageOf(phenotype) / 1024.0) + "Kb.");
				}
				catch (IllegalStateException e) {};
			}
			else {
				phenotype.setName("network " + genotype.getId());
			}
		}
		else { // RECURRENT
			if (createNewPhenotype) {
				weights = new float[depth - 1][][][][][];
				for (int l = 1; l < depth; l++)
					weights[l-1] = new float[height[l]][width[l]][][][];
			}
			else {
				weights = phenotype.getWeights();
			}

			// query CPPN for substrate connection weights
			for (int tz = 1; tz < depth; tz++) {
				cppnTZ = ((float) tz) / (depth - 1);
				cppnInput[1] = cppnTZ;

				for (int ty = 0; ty < height[tz]; ty++) {
					if (height[tz] > 1)
						cppnTY = ((float) ty) / (height[tz] - 1);
					else
						cppnTY = 0.5f;
					cppnInput[2] = cppnTY;

					for (int tx = 0; tx < width[tz]; tx++) {
						if (width[tz] > 1)
							cppnTX = ((float) tx) / (width[tz] - 1);
						else
							cppnTX = 0.5f;
						cppnInput[3] = cppnTX;

						// calculate dimensions of this weight matrix (bounded by grid edges)
						int dz = Math.min(depth - 1, tz + connectionRange) - Math.max(1, tz - connectionRange) + 1; // no connections to input layer
						int dy = Math.min(height[tz] - 1, ty + connectionRange) - Math.max(0, ty - connectionRange) + 1;
						int dx = Math.min(width[tz] - 1, tx + connectionRange) - Math.max(0, tx - connectionRange) + 1;

						// System.out.println(z + "," + y + "," + x + "  dz = "
						// + dz + "  dy = " + dy + "  dx = " + dx);

						weights[tz - 1][ty][tx] = new float[dz][dy][dx];
						float[][][] w = weights[tz - 1][ty][tx];

						// for each connection to t{zyx}
						// w{z,y,x} is index into weight matrix
						// s{z,y,x} is index of source neuron
						for (int wz = 0, sz = Math.max(1, tz - connectionRange); wz < dz; wz++, sz++) {
							cppnSZ = ((float) sz) / (depth - 1);
							cppnInput[4] = cppnSZ;

							for (int wy = 0, sy = Math.max(0, ty - connectionRange); wy < dy; wy++, sy++) {
								if (height[tz] > 1)
									cppnSY = ((float) sy) / (height[tz] - 1);
								else
									cppnSY = 0.5f;
								cppnInput[5] = cppnSY;

								for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
									if (width[tz] > 1)
										cppnSX = ((float) sx) / (width[tz] - 1);
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
									float[] cppnOutput = cppnActivator.next(cppnInput);

									//weight
									float weightVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[0]));
									if (Math.abs(weightVal) > connectionExprThresh) {
										if (weightVal > 0)
											weightVal = (weightVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
										else
											weightVal = (weightVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));

										w[wz][wy][wx] = weightVal;
									} else {
										w[wz][wy][wx] = 0;
									}
									
									//bias
									if (enableBias && wz==0 && wy==0 && wx == 0) {
										float biasVal = Math.min(connectionWeightMax, Math.max(connectionWeightMin, cppnOutput[1]));
										if (Math.abs(biasVal) > connectionExprThresh) {
											if (biasVal > 0)
												biasVal = (biasVal - connectionExprThresh) * (connectionWeightMax / (connectionWeightMax - connectionExprThresh));
											else
												biasVal = (biasVal + connectionExprThresh) * (connectionWeightMin / (connectionWeightMin + connectionExprThresh));
		
											bias[tz-1][ty][tx] = biasVal;
										} else {
											bias[tz-1][ty][tx] = 0;
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
			}
			else {
				phenotype.setName("network " + genotype.getId());
			}
		}

		return phenotype;
	}
	
	public void resize(int[] width, int[] height, int connectionRange) {
		this.width = width;
		this.height = height;
		this.connectionRange = connectionRange;
	}
	
	public int getConnectionRange() {
    	return connectionRange;
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
	
	/*
	public static void main(String[] args) {
		Point2D.Double p1 = new Point2D.Double(1, 1);
		Point2D.Double p2 = new Point2D.Double(1, 2);
		for (double a = 0; a < Math.PI*2; a += Math.PI * 0.1) {
			p2.x = p1.x + Math.cos(a);
			p2.y = p1.y + Math.sin(a);
			
			double angle = Math.atan2(p2.y-p1.y, p2.x-p1.x);
			if (angle < 0)
				angle += 2*Math.PI;
						
			System.out.println(p1.x + "," + p1.y + " - " + (float)p2.x + "," + (float)p2.y + " - " + (float) Math.toDegrees(a) + " - " + (float) Math.toDegrees(angle) + " - " + (float) (angle / (2*Math.PI)));
		}
	}
	*/
}
