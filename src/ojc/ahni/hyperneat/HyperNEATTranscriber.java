package ojc.ahni.hyperneat;

import ojc.ahni.util.Range;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.AnjiActivator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.nn.AnjiNet;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * To "transcribe" is to construct a phenotype from a genotype.
 * 
 * @author Oliver Coleman
 */
public abstract class HyperNEATTranscriber<T extends Activator> implements Transcriber<T>, Configurable {
	private final static Logger logger = Logger.getLogger(HyperNEATTranscriber.class);

	/**
	 * Set to true to restrict the substrate network to a strictly feed-forward topology.
	 */
	public static final String HYPERNEAT_FEED_FORWARD = "ann.hyperneat.feedforward";
	/**
	 * Enable bias connections in the substrate network.
	 */
	public static final String HYPERNEAT_ENABLE_BIAS = "ann.hyperneat.enablebias";
	/**
	 * If true indicates that the CPPN should receive the delta value for each axis between the source and target neuron
	 * coordinates.
	 */
	public static final String HYPERNEAT_INCLUDE_DELTA = "ann.hyperneat.includedelta";
	/**
	 * If true indicates that the CPPN should receive the angle in the XY plane between the source and target neuron
	 * coordinates (relative to the line X axis).
	 */
	public static final String HYPERNEAT_INCLUDE_ANGLE = "ann.hyperneat.includeangle";
	/**
	 * If true indicates that instead of using a separate output from the CPPN to specify weight values for each weight
	 * layer in a feed-forward network, the layer coordinate is input to the CPPN and only a single output from CPPN is
	 * used to specify weight values for all weight layers.
	 */
	public static final String HYPERNEAT_LAYER_ENCODING = "ann.hyperneat.useinputlayerencoding";
	/**
	 * The minimum CPPN output required to produce a non-zero weight in the substrate network.
	 */
	public static final String HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD = "ann.hyperneat.connection.expression.threshold";
	/**
	 * The minimum weight values in the substrate network.
	 */
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MIN = "ann.hyperneat.connection.weight.min";
	/**
	 * The maximum weight values in the substrate network.
	 */
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MAX = "ann.hyperneat.connection.weight.max";
	/**
	 * Limits the incoming connections to a target neuron to include those from source neurons within the specified
	 * range of the target neuron. Set this to -1 to disable it.
	 */
	public static final String HYPERNEAT_CONNECTION_RANGE = "ann.hyperneat.connection.range";
	/**
	 * The number of layers in the substrate network.
	 */
	public static final String SUBSTRATE_DEPTH = "ann.hyperneat.depth";
	/**
	 * Comma separated list of the height of each layer in the network, including input and output layers and starting
	 * with the input layer.
	 */
	public static final String SUBSTRATE_HEIGHT = "ann.hyperneat.height";
	/**
	 * Comma separated list of the width of each layer in the network, including input and output layers and starting
	 * with the input layer.
	 */
	public static final String SUBSTRATE_WIDTH = "ann.hyperneat.width";
	/**
	 * The coordinate range of neurons in the substrate in the X dimension, corresponding to the width (this is used to
	 * determine the input to the CPPN for a given neuron location). Defaults to [0, 1].
	 */
	public static final String RANGE_X = "ann.hyperneat.range.x";
	/**
	 * The coordinate range of neurons in the substrate in the Y dimension, corresponding to the height (this is used to
	 * determine the input to the CPPN for a given neuron location). Defaults to [0, 1].
	 */
	public static final String RANGE_Y = "ann.hyperneat.range.y";
	/**
	 * The coordinate range of neurons in the substrate in the Z dimension, corresponding to the depth (this is used to
	 * determine the input to the CPPN for a given neuron location). Defaults to [0, 1].
	 */
	public static final String RANGE_Z = "ann.hyperneat.range.z";

	/**
	 * For recurrent networks, the number of activation cycles to perform each time the substrate network is presented
	 * with new input and queried for its output.
	 */
	public static final String SUBSTRATE_CYCLES_PER_STEP = "ann.hyperneat.cyclesperstep";
	/**
	 * Enable or disable Link Expression Output (LEO). See P. Verbancsics and K. O. Stanley (2011) Constraining
	 * Connectivity to Encourage Modularity in HyperNEAT. In Proceedings of the Genetic and Evolutionary Computation
	 * Conference (GECCO 2011). Default is "false".
	 * 
	 * @see #HYPERNEAT_LEO_LOCALITY
	 */
	public static final String HYPERNEAT_LEO = "ann.hyperneat.leo";
	/**
	 * Enable or disable seeding of the initial population of {@link org.jgapcustomised.Chromosome}s to incorporate a
	 * bias towards local connections via the Link Expression Output (LEO), see {@link #HYPERNEAT_LEO} and the article
	 * reference within. Default is "false".
	 */
	public static final String HYPERNEAT_LEO_LOCALITY = "ann.hyperneat.leo.localityseeding";

	/**
	 * The width of each layer in the substrate.
	 */
	protected int width[];
	/**
	 * The height of each layer in the substrate.
	 */
	protected int height[];
	/**
	 * The number of layers in the substrate, including input and output layers.
	 */
	protected int depth;
	/**
	 * Used by {@link HyperNEATTranscriber.CPPN} to translate from the default range [0, 1] to the range specified by {@link #RANGE_X}.
	 */
	protected Range rangeX = new Range();
	/**
	 * Used by {@link HyperNEATTranscriber.CPPN} to translate from the default range [0, 1] to the range specified by {@link #RANGE_Y}.
	 */
	protected Range rangeY = new Range();
	/**
	 * Used by {@link HyperNEATTranscriber.CPPN} to translate from the default range [0, 1] to the range specified by {@link #RANGE_Z}.
	 */
	protected Range rangeZ = new Range();
	/**
	 * If true indicates whether the substrate should have a feed-forward topology (no recurrent connections).
	 */
	protected boolean feedForward;
	/**
	 * If true indicates that each neuron in the substrate should receive a bias signal.
	 */
	protected boolean enableBias;
	/**
	 * If true indicates that the CPPN should receive the delta value for each axis between the source and target neuron
	 * coordinates.
	 */
	protected boolean includeDelta;
	/**
	 * If true indicates that the CPPN should receive the angle in the XY plane between the source and target neuron
	 * coordinates.
	 */
	protected boolean includeAngle;
	/**
	 * The minimum output from the CPPN required to create a non-zero weight value in the substrate.
	 */
	protected double connectionExprThresh = 0.2;
	/**
	 * The minimum connection weight in the substrate.
	 */
	protected double connectionWeightMin;
	/**
	 * The maximum connection weight in the substrate.
	 */
	protected double connectionWeightMax;
	/**
	 * Limits the incoming connections to a target neuron to include those from source neurons within the specified
	 * range of the target neuron. A value of -1 indicates that this is disabled.
	 */
	protected int connectionRange;
	/**
	 * If true indicates that instead of using a separate output from the CPPN to specify weight values for each weight
	 * layer in a feed-forward network, the layer coordinate is input to the CPPN and only a single output from CPPN is
	 * used to specify weight values for all weight layers.
	 */
	protected boolean layerEncodingIsInput = false;
	/**
	 * For substrate networks with recurrent connections, the number of activation cycles to perform each time the
	 * substrate network is presented with new input and queried for its output.
	 */
	protected int cyclesPerStep;
	/**
	 * If true indicates that Link Expression Output is enabled.
	 */
	protected boolean enableLEO = false;
	/**
	 * The number of inputs to the CPPN.
	 * 
	 * @see #getCPPNInputCount()
	 */
	protected short cppnInputCount;
	/**
	 * The number of outputs from the CPPN.
	 * 
	 * @see #getCPPNOutputCount()
	 */
	protected short cppnOutputCount;

	/**
	 * Transcriber for network for use as a CPPN. Not usually accessed directly, see {@link HyperNEATTranscriber.CPPN}.
	 */
	protected Transcriber cppnTranscriber;

	// Index of target and source coordinate inputs in CPPN input vector.
	int cppnIdxTX = -1, cppnIdxTY = -1, cppnIdxTZ = -1, cppnIdxSX = -1, cppnIdxSY = -1, cppnIdxSZ = -1;
	// Index of delta and angle inputs in CPPN input vector.
	int cppnIdxDX = -1, cppnIdxDY = -1, cppnIdxDZ = -1, cppnIdxAn = -1;
	// Index of output signals in CPPN output vector.
	int[] cppnIdxW = new int[] { -1 }; // weights (either a single output for all layers or one output per layer)
	int[] cppnIdxB = new int[] { -1 }; // bias (either a single output for all layers or one output per layer)
	int[] cppnIdxL = new int[] { -1 }; // link expression (either a single output for all layers or one output per
										// layer)

	/**
	 * Subclasses may set this to "force" or "prevent" before calling super.init(Properties) to either force or prevent
	 * the use of Z coordinate inputs for the CPPN (both source and target neuron Z coordinates will be affected).
	 */
	protected String zCoordsForCPPN = "";

	/**
	 * This method should be called from overriding methods.
	 * 
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		feedForward = props.getBooleanProperty(HYPERNEAT_FEED_FORWARD);
		enableBias = props.getBooleanProperty(HYPERNEAT_ENABLE_BIAS);
		includeDelta = props.getBooleanProperty(HYPERNEAT_INCLUDE_DELTA);
		includeAngle = props.getBooleanProperty(HYPERNEAT_INCLUDE_ANGLE);
		layerEncodingIsInput = props.getBooleanProperty(HYPERNEAT_LAYER_ENCODING, layerEncodingIsInput);
		// layerEncodingIsInput must be used for recurrent networks.
		layerEncodingIsInput = !feedForward ? true : layerEncodingIsInput;
		connectionExprThresh = props.getDoubleProperty(HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD, connectionExprThresh);
		connectionWeightMin = props.getDoubleProperty(HYPERNEAT_CONNECTION_WEIGHT_MIN);
		connectionWeightMax = props.getDoubleProperty(HYPERNEAT_CONNECTION_WEIGHT_MAX);
		connectionRange = props.getIntProperty(HYPERNEAT_CONNECTION_RANGE, -1);
		depth = props.getIntProperty(SUBSTRATE_DEPTH);
		cyclesPerStep = feedForward ? depth - 1 : props.getIntProperty(SUBSTRATE_CYCLES_PER_STEP, 1);
		enableLEO = props.getBooleanProperty(HYPERNEAT_LEO, enableLEO);

		height = props.getIntArrayProperty(SUBSTRATE_HEIGHT);
		width = props.getIntArrayProperty(SUBSTRATE_WIDTH);

		rangeX = (Range) props.getObjectFromArgsProperty(RANGE_X, Range.class, rangeX);
		rangeY = (Range) props.getObjectFromArgsProperty(RANGE_Y, Range.class, rangeY);
		rangeZ = (Range) props.getObjectFromArgsProperty(RANGE_Z, Range.class, rangeZ);

		// Determine CPPN input size and mapping.
		cppnInputCount = 1; // Bias always has index 0.
		cppnIdxSX = cppnInputCount++;
		cppnIdxSY = cppnInputCount++;
		cppnIdxTX = cppnInputCount++;
		cppnIdxTY = cppnInputCount++;
		logger.info("CPPN: Added bias, sx, sy, tx, ty inputs.");
		if (zCoordsForCPPN.equals("force") || (!zCoordsForCPPN.equals("prevent") && (feedForward && layerEncodingIsInput && depth > 2 || !feedForward && depth > 1))) {
			cppnIdxTZ = cppnInputCount++;
			logger.info("CPPN: Added tz input.");

			if (zCoordsForCPPN.equals("force") || !feedForward) {
				cppnIdxSZ = cppnInputCount++;
				logger.info("CPPN: Added sz input.");
				if (includeDelta) {
					cppnIdxDZ = cppnInputCount++; // z delta
					logger.info("CPPN: Added delta z input.");
				}
			}
		}
		if (includeDelta) {
			cppnIdxDY = cppnInputCount++; // y delta
			cppnIdxDX = cppnInputCount++; // x delta
			logger.info("CPPN: Added delta x and y inputs.");
		}
		if (includeAngle) {
			cppnIdxAn = cppnInputCount++; // angle
			logger.info("CPPN: Added angle input.");
		}

		// Determine CPPN output size and mapping.
		cppnOutputCount = 0;
		if (layerEncodingIsInput) { // same output for all layers
			cppnIdxW = new int[1];
			cppnIdxW[0] = cppnOutputCount++; // weight value
			logger.info("CPPN: Added single weight output.");

			if (enableBias) {
				cppnIdxB = new int[1];
				cppnIdxB[0] = cppnOutputCount++; // bias value
				logger.info("CPPN: Added single bias output.");
			}

			if (enableLEO) {
				cppnIdxL = new int[1];
				cppnIdxL[0] = cppnOutputCount++; // bias value
				logger.info("CPPN: Added single link expression output.");
			}
		} else { // one output per layer
			int layerOutputCount = Math.max(1, depth - 1); // Allow for depth of 1 (2D horizontal substrate)
			cppnIdxW = new int[layerOutputCount];
			for (int w = 0; w < layerOutputCount; w++)
				cppnIdxW[w] = cppnOutputCount++; // weight value
			logger.info("CPPN: Added " + layerOutputCount + " weight outputs.");

			if (enableBias) {
				cppnIdxB = new int[layerOutputCount];
				for (int w = 0; w < layerOutputCount; w++)
					cppnIdxB[w] = cppnOutputCount++; // bias value
				logger.info("CPPN: Added " + layerOutputCount + " bias outputs.");
			}

			if (enableLEO) {
				cppnIdxL = new int[layerOutputCount];
				for (int w = 0; w < layerOutputCount; w++)
					cppnIdxL[w] = cppnOutputCount++; // leo value
				logger.info("CPPN: Added " + layerOutputCount + " link expression outputs.");
			}
		}

		logger.info("CPPN input/output size: " + cppnInputCount + "/" + cppnOutputCount);

		cppnTranscriber = (Transcriber) props.singletonObjectProperty(AnjiNetTranscriber.class);
	}

	/**
	 * Get the number of layers in the substrate, including input and output layers.
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * Get the width of each layer in the substrate.
	 */
	public int[] getWidth() {
		return width;
	}

	/**
	 * Get the height of each layer in the substrate.
	 */
	public int[] getHeight() {
		return height;
	}

	/**
	 * Get the limit of the range of connections, this limits the incoming connections to a target neuron to include
	 * those from source neurons within the specified range of the target neuron. This is not implemented by all
	 * transcribers. A value of -1 indicates that this is disabled.
	 */
	public int getConnectionRange() {
		return connectionRange;
	}

	/**
	 * Specify new dimensions for each layer. If connection range is not used it can be set to -1.
	 */
	public void resize(int[] width, int[] height, int connectionRange) {
		this.width = width;
		this.height = height;
		this.connectionRange = connectionRange;
	}

	/**
	 * The number of inputs to the CPPN.
	 */
	public short getCPPNInputCount() {
		return cppnInputCount;
	}

	public short getCPPNOutputCount() {
		return cppnOutputCount;
	}

	/**
	 * Provides a wrapper for an {@link com.anji.integration.Activator} that represents a CPPN.
	 */
	public class CPPN {
		Activator cppnActivator;
		double[] cppnInput = new double[cppnInputCount];
		double[] cppnOutput;

		public CPPN(Chromosome genotype) throws TranscriberException {
			cppnActivator = cppnTranscriber.transcribe(genotype);
			cppnInput[0] = 1; // Bias.
		}

		/**
		 * Set the coordinates of the source neuron in a two-dimensional substrate with dimensions with range [0, 1]
		 * (coordinates are translated to a user-specified range if necessary, e.g. see {@link #RANGE_X}).
		 * 
		 * @param x The x coordinate, range should be [0, 1].
		 * @param y The y coordinate, range should be [0, 1].
		 */
		public void setSourceCoordinates(double x, double y) {
			cppnInput[cppnIdxSX] = rangeX.translate(x);
			cppnInput[cppnIdxSY] = rangeY.translate(y);
		}

		/**
		 * Set the coordinates of the source neuron in a three-dimensional substrate with dimensions with range [0, 1]
		 * (coordinates are translated to a user-specified range if necessary, e.g. see {@link #RANGE_X}). If the z coordinate is not required it
		 * will be ignored.
		 * 
		 * @param x The x coordinate, range should be [0, 1].
		 * @param y The y coordinate, range should be [0, 1].
		 * @param z The z coordinate, range should be [0, 1].
		 */
		public void setSourceCoordinates(double x, double y, double z) {
			cppnInput[cppnIdxSX] = rangeX.translate(x);
			cppnInput[cppnIdxSY] = rangeY.translate(y);
			if (cppnIdxSZ != -1) {
				cppnInput[cppnIdxSZ] = rangeZ.translate(z);
			}
		}

		/**
		 * Set the coordinates of the source neuron in a two-dimensional substrate with dimensions with range [0, 1]
		 * (coordinates are translated to a user-specified range if necessary, e.g. see {@link #RANGE_X}).
		 * 
		 * @param x The x coordinate, range should be [0, 1].
		 * @param y The y coordinate, range should be [0, 1].
		 */
		public void setTargetCoordinates(double x, double y) {
			cppnInput[cppnIdxTX] = rangeX.translate(x);
			cppnInput[cppnIdxTY] = rangeY.translate(y);
		}

		/**
		 * Set the coordinates of the source neuron in a three-dimensional substrate with dimensions with range [0, 1]
		 * (coordinates are translated to a user-specified range if necessary, e.g. see {@link #RANGE_X}). If the z coordinate is not required it
		 * will be ignored.
		 * 
		 * @param x The x coordinate, range should be [0, 1].
		 * @param y The y coordinate, range should be [0, 1].
		 * @param z The z coordinate, range should be [0, 1].
		 */
		public void setTargetCoordinates(double x, double y, double z) {
			cppnInput[cppnIdxTX] = rangeX.translate(x);
			cppnInput[cppnIdxTY] = rangeY.translate(y);
			if (cppnIdxTZ != -1) {
				cppnInput[cppnIdxTZ] = rangeZ.translate(z);
			}
		}

		/**
		 * Query this CPPN.
		 * 
		 * @return The value of the (first) weight output. Other outputs can be retrieved with the various get methods.
		 */
		public double query() {
			if (includeDelta) {
				cppnInput[cppnIdxDX] = cppnInput[cppnIdxSX] - cppnInput[cppnIdxTX];
				cppnInput[cppnIdxDY] = cppnInput[cppnIdxSY] - cppnInput[cppnIdxTY];
				if (cppnIdxDZ != -1) {
					cppnInput[cppnIdxDZ] = cppnInput[cppnIdxSZ] - cppnInput[cppnIdxTZ];
				}
			}
			if (includeAngle) {
				double angle = (double) Math.atan2(cppnInput[cppnIdxSY] - cppnInput[cppnIdxTY], cppnInput[cppnIdxSX] - cppnInput[cppnIdxTX]);
				angle /= 2 * (double) Math.PI;
				if (angle < 0)
					angle += 1;
				cppnInput[cppnIdxAn] = angle;
			}

			cppnActivator.reset();
			cppnOutput = cppnActivator.next(cppnInput);
			return getWeight();
		}

		/**
		 * Query this CPPN with the specified coordinates.
		 * 
		 * @return The value of the (first) weight output. Other outputs can be retrieved with the various get methods.
		 */
		public double query(double sx, double sy, double tx, double ty) {
			setSourceCoordinates(sx, sy);
			setTargetCoordinates(tx, ty);
			return query();
		}

		/**
		 * Query this CPPN with the specified coordinates.
		 * 
		 * @return The value of the (first) weight output. Other outputs can be retrieved with the various get methods.
		 */
		public double query(double sx, double sy, double sz, double tx, double ty, double tz) {
			setSourceCoordinates(sx, sy, sz);
			setTargetCoordinates(tx, ty, tz);
			return query();
		}

		/**
		 * Get the value of the weight. Should be called after calling {@link #query()}.
		 */
		public double getWeight() {
			return cppnOutput[cppnIdxW[0]];
		}

		/**
		 * Get the value of the weight for the specified layer in a layered feed-forward network encoded with
		 * {@link #HYPERNEAT_LAYER_ENCODING} set to false. Should be called after calling {@link #query()}.
		 */
		public double getWeight(int sourceLayerIndex) {
			return cppnOutput[cppnIdxW[sourceLayerIndex]];
		}

		/**
		 * Get the value of the weight for a bias connection. Should be called after calling {@link #query()}. The bias
		 * is usually queried for a target neuron after setting the source coordinate values to 0.
		 */
		public double getBiasWeight() {
			return cppnOutput[cppnIdxB[0]];
		}

		/**
		 * Get the value of the weight for a bias connection for the specified layer in a layered feed-forward network
		 * encoded with {@link #HYPERNEAT_LAYER_ENCODING} set to false. Should be called after calling {@link #query()}.
		 * The bias is usually queried for a target neuron after setting the source coordinate values to 0.
		 */
		public double getBiasWeight(int sourceLayerIndex) {
			return cppnOutput[cppnIdxB[sourceLayerIndex]];
		}

		/**
		 * Get the value of the link expression output (see {@link #HYPERNEAT_LEO}). Should be called after calling
		 * {@link #query()}.
		 */
		public double getLEO() {
			return cppnOutput[cppnIdxL[0]];
		}

		/**
		 * Get the value of the link expression output (see {@link #HYPERNEAT_LEO}) for the specified layer in a layered
		 * feed-forward network encoded with {@link #HYPERNEAT_LAYER_ENCODING} set to false. Should be called after
		 * calling {@link #query()}.
		 */
		public double getLEO(int sourceLayerIndex) {
			return cppnOutput[cppnIdxL[sourceLayerIndex]];
		}
	}

	/**
	 * Returns true iff the Link Expression Output (LEO) is enabled.
	 * 
	 * @see #HYPERNEAT_LEO
	 */
	public boolean leoEnabled() {
		return enableLEO;
	}

	/**
	 * @return the index in the CPPN inputs for the bias.
	 */
	public int getCPPNIndexBiasInput() {
		return 0;
	}

	/**
	 * @return the index in the CPPN inputs for the x coordinate for the target neuron.
	 */
	public int getCPPNIndexTargetX() {
		return cppnIdxTX;
	}

	/**
	 * @return the index in the CPPN inputs for the y coordinate for the target neuron.
	 */
	public int getCPPNIndexTargetY() {
		return cppnIdxTY;
	}

	/**
	 * @return the index in the CPPN inputs for the z coordinate for the target neuron. Returns -1 if this input is not
	 *         enabled.
	 */
	public int getCPPNIndexTargetZ() {
		return cppnIdxTZ;
	}

	/**
	 * @return the index in the CPPN inputs for the x coordinate for the source neuron.
	 */
	public int getCPPNIndexSourceX() {
		return cppnIdxSX;
	}

	/**
	 * @return the index in the CPPN inputs for the y coordinate for the source neuron.
	 */
	public int getCPPNIndexSourceY() {
		return cppnIdxSY;
	}

	/**
	 * @return the index in the CPPN inputs for the z coordinate for the source neuron. Returns -1 if this input is not
	 *         enabled.
	 */
	public int getCPPNIndexSourceZ() {
		return cppnIdxSZ;
	}

	/**
	 * @return the index in the CPPN inputs for the delta (difference) for the x coordinates (the difference between the
	 *         x coordinates of the source and target neurons). Returns -1 if this input is not enabled.
	 */
	public int getCPPNIndexDeltaX() {
		return cppnIdxDX;
	}

	/**
	 * @return the index in the CPPN inputs for the delta (difference) for the y coordinates (the difference between the
	 *         y coordinates of the source and target neurons). Returns -1 if this input is not enabled.
	 */
	public int getCPPNIndexDeltaY() {
		return cppnIdxDY;
	}

	/**
	 * @return the index in the CPPN inputs for the delta (difference) for the z coordinates (the difference between the
	 *         z coordinates of the source and target neurons). Returns -1 if this input is not enabled.
	 */
	public int getCPPNIndexDeltaZ() {
		return cppnIdxDZ;
	}

	/**
	 * @return the index in the CPPN inputs for the angle in the XY plane between the source and target neuron
	 *         coordinates (relative to the line X axis). Returns -1 if this input is not enabled.
	 */
	public int getCPPNIndexAngle() {
		return cppnIdxAn;
	}

	/**
	 * @return an array containing the indexes in the CPPN outputs for the weight value(s).
	 */
	public int[] getCPPNIndexWeight() {
		return cppnIdxW;
	}

	/**
	 * @return an array containing the indexes in the CPPN outputs for the bias value(s). Returns [-1] if this output is
	 *         not enabled.
	 */
	public int[] getCPPNIndexBiasOutput() {
		return cppnIdxB;
	}

	/**
	 * @return an array containing the indexes in the CPPN outputs for the Link Expression Output (LEO) value(s).
	 *         Returns [-1] if this output is not enabled.
	 */
	public int[] getCPPNIndexLEO() {
		return cppnIdxL;
	}
}
