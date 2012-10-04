package ojc.ahni.hyperneat;

import com.anji.integration.Activator;
import com.anji.integration.Transcriber;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * To "transcribe" is to construct a phenotype from a genotype.
 * 
 * @author Oliver Coleman
 */
public abstract class HyperNEATTranscriber<T extends Activator> implements Transcriber<T>, Configurable {
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
	 * If true indicates that the CPPN should receive the delta value for each axis between the source and target neuron coordinates.
	 */ 
	protected boolean includeDelta;
	/**
	 * If true indicates that the CPPN should receive the angle in the XY plane between the source and target neuron coordinates.
	 */ 
	protected boolean includeAngle;
	/**
	 * The minimum output from the CPPN required to create a non-zero weight value in the substrate.
	 */ 
	protected double connectionExprThresh;
	/**
	 * The minimum connection weight in the substrate.
	 */ 
	protected double connectionWeightMin;
	/**
	 * The maximum connection weight in the substrate.
	 */ 
	protected double connectionWeightMax;
	/**
	 * Limits the incoming connections to a target neuron to include those from source neurons within the specified range of the target neuron. This is optional.
	 */ 
	protected int connectionRange;
	/**
	 * If true indicates that instead of using a separate output from CPPN to specify weight values for each weight layer, the layer coordinate is input to the CPPN and only a single output from CPPN is used to specify weight values for all weight layers.
	 */ 
	protected boolean layerEncodingIsInput = false;
	
	/**
	 * This method should be called from overriding methods.
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		feedForward = props.getBooleanProperty(HYPERNEAT_FEED_FORWARD);
		enableBias = props.getBooleanProperty(HYPERNEAT_ENABLE_BIAS);
		includeDelta = props.getBooleanProperty(HYPERNEAT_INCLUDE_DELTA);
		includeAngle = props.getBooleanProperty(HYPERNEAT_INCLUDE_ANGLE);
		layerEncodingIsInput = props.getBooleanProperty(HYPERNEAT_LAYER_ENCODING, layerEncodingIsInput);
		connectionExprThresh = props.getFloatProperty(HYPERNEAT_CONNECTION_EXPRESSION_THRESHOLD);
		connectionWeightMin = props.getFloatProperty(HYPERNEAT_CONNECTION_WEIGHT_MIN);
		connectionWeightMax = props.getFloatProperty(HYPERNEAT_CONNECTION_WEIGHT_MAX);
		connectionRange = props.getIntProperty(HYPERNEAT_CONNECTION_RANGE, -1);
		
		depth = props.getIntProperty(SUBSTRATE_DEPTH);
		String[] heightStr = props.getProperty(SUBSTRATE_HEIGHT).split(",");
		String[] widthStr = props.getProperty(SUBSTRATE_WIDTH).split(",");
		height = new int[depth];
		width = new int[depth];
		for (int l = 0; l < depth; l++) {
			height[l] = Integer.parseInt(heightStr[l]);
			width[l] = Integer.parseInt(widthStr[l]);
		}
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
	 * Get the limit of the range of connections, this limits the incoming connections to a target neuron to include those from source neurons within the specified range of the target neuron. This is not implemented by all transcribers.
	 * A value of -1 indicates that this is disabled.
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
}
