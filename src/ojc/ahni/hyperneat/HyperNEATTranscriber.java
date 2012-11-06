package ojc.ahni.hyperneat;

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

	public static final String HYPERNEAT_FEED_FORWARD = "ann.hyperneat.feedforward";
	public static final String HYPERNEAT_ENABLE_BIAS = "ann.hyperneat.enablebias";
	public static final String HYPERNEAT_INCLUDE_DELTA = "ann.hyperneat.includedelta";
	public static final String HYPERNEAT_INCLUDE_ANGLE = "ann.hyperneat.includeangle";
	public static final String HYPERNEAT_LAYER_ENCODING = "ann.hyperneat.useinputlayerencoding";
	public static final String HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD = "ann.hyperneat.connection.expression.threshold";
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MIN = "ann.hyperneat.connection.weight.min";
	public static final String HYPERNEAT_CONNECTION_WEIGHT_MAX = "ann.hyperneat.connection.weight.max";
	public static final String HYPERNEAT_CONNECTION_RANGE = "ann.hyperneat.connection.range";

	public static final String SUBSTRATE_DEPTH = "ann.hyperneat.depth";
	public static final String SUBSTRATE_HEIGHT = "ann.hyperneat.height";
	public static final String SUBSTRATE_WIDTH = "ann.hyperneat.width";
	public static final String SUBSTRATE_CYCLES_PER_STEP = "ann.hyperneat.cyclesperstep";

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
	 * Transcriber for network for use as a CPPN. Not usually accessed directly, see {@link #CPPN}.
	 */
	protected Transcriber cppnTranscriber;

	// Index of target and source coordinate inputs in CPPN input vector.
	int cppnIdxTX = -1, cppnIdxTY = -1, cppnIdxTZ = -1, cppnIdxSX = -1, cppnIdxSY = -1, cppnIdxSZ = -1;
	// Index of delta and angle inputs in CPPN input vector.
	int cppnIdxDX = -1, cppnIdxDY = -1, cppnIdxDZ = -1, cppnIdxAn = -1;
	// Index of output signals in CPPN output vector.
	int[] cppnIdxW; // weights (either a single output for all layers or one output per layer)
	int[] cppnIdxB = new int[0]; // bias (either a single output for all layers or one output per layer)

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

		String[] heightStr = props.getProperty(SUBSTRATE_HEIGHT).split(",");
		String[] widthStr = props.getProperty(SUBSTRATE_WIDTH).split(",");
		height = new int[depth];
		width = new int[depth];
		if (heightStr.length != depth || widthStr.length != depth) {
			throw new IllegalArgumentException("Number of comma-separated layer dimensions in " + SUBSTRATE_HEIGHT + " or " + SUBSTRATE_WIDTH + " does not match " + SUBSTRATE_DEPTH + ".");
		}
		for (int l = 0; l < depth; l++) {
			height[l] = Integer.parseInt(heightStr[l]);
			width[l] = Integer.parseInt(widthStr[l]);
		}

		// Determine CPPN input size and mapping.
		cppnInputCount = 1; // Bias always has index 0.
		cppnIdxTX = cppnInputCount++;
		cppnIdxTY = cppnInputCount++;
		cppnIdxSX = cppnInputCount++;
		cppnIdxSY = cppnInputCount++;
		if (feedForward && layerEncodingIsInput && depth > 2 || !feedForward && depth > 1) {
			cppnIdxTZ = cppnInputCount++;

			if (!feedForward) {
				cppnIdxSZ = cppnInputCount++;
				if (includeDelta) {
					cppnIdxDZ = cppnInputCount++; // z delta
				}
			}
		}
		if (includeDelta) {
			cppnIdxDY = cppnInputCount++; // y delta
			cppnIdxDX = cppnInputCount++; // x delta
		}
		if (includeAngle) {
			cppnIdxAn = cppnInputCount++; // angle
		}

		// Determine CPPN output size and mapping.
		cppnOutputCount = 0;
		if (layerEncodingIsInput) { // same output for all layers
			cppnIdxW = new int[1];
			cppnIdxW[0] = cppnOutputCount++; // weight value

			if (enableBias) {
				cppnIdxB = new int[1];
				cppnIdxB[0] = cppnOutputCount++; // bias value
			}
		} else { // one output per layer
			int layerOutputCount = Math.max(1, depth - 1); // Allow for depth of 1 (2D horizontal substrate, eg for
															// ES-HypernEAT).
			cppnIdxW = new int[layerOutputCount];
			for (int w = 0; w < layerOutputCount; w++)
				cppnIdxW[w] = cppnOutputCount++; // weight value

			if (enableBias) {
				cppnIdxB = new int[layerOutputCount];
				for (int w = 0; w < layerOutputCount; w++)
					cppnIdxB[w] = cppnOutputCount++; // bias value
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
		 * Set the coordinates of the source neuron in a two-dimensional substrate.
		 * 
		 * @param x The x coordinate.
		 * @param y The y coordinate.
		 */
		public void setSourceCoordinates(double x, double y) {
			cppnInput[cppnIdxSX] = x;
			cppnInput[cppnIdxSY] = y;
		}

		/**
		 * Set the coordinates of the source neuron in a three-dimensional substrate. If the z coordinate is not
		 * required it will be ignored.
		 * 
		 * @param x The x coordinate.
		 * @param y The y coordinate.
		 * @param z The z coordinate.
		 */
		public void setSourceCoordinates(double x, double y, double z) {
			cppnInput[cppnIdxSX] = x;
			cppnInput[cppnIdxSY] = y;
			if (cppnIdxSZ != -1) {
				cppnInput[cppnIdxSZ] = z;
			}
		}

		/**
		 * Set the coordinates of the source neuron in a two-dimensional substrate.
		 * 
		 * @param x The x coordinate.
		 * @param y The y coordinate.
		 */
		public void setTargetCoordinates(double x, double y) {
			cppnInput[cppnIdxTX] = x;
			cppnInput[cppnIdxTY] = y;
		}

		/**
		 * Set the coordinates of the source neuron in a three-dimensional substrate. If the z coordinate is not
		 * required it will be ignored.
		 * 
		 * @param x The x coordinate.
		 * @param y The y coordinate.
		 * @param z The z coordinate.
		 */
		public void setTargetCoordinates(double x, double y, double z) {
			cppnInput[cppnIdxTX] = x;
			cppnInput[cppnIdxTY] = y;
			if (cppnIdxTZ != -1) {
				cppnInput[cppnIdxTZ] = z;
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
	}
}
