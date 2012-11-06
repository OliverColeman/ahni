package ojc.ahni.hyperneat;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import ojc.ahni.integration.BainNN;
import ojc.bain.NeuralNetwork;
import ojc.bain.base.ComponentCollection;
import ojc.bain.base.NeuronCollection;
import ojc.bain.base.SynapseCollection;
import ojc.bain.neuron.rate.NeuronCollectionWithBias;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.amd.aparapi.Kernel;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Constructs a <a href="https://github.com/OliverColeman/bain">Bain</a> neural network from a chromosome using the
 * ES-HyperNEAT encoding scheme (see Risi S. and Stanley K.O. (2012) An Enhanced Hypercube-Based Encoding for Evolving
 * the Placement, Density and Connectivity of Neurons. Artificial Life, MIT Press). An
 * {@link com.anji.integration.ActivatorTranscriber} should be used to construct an instance of this class.
 * {@link com.anji.integration.ActivatorTranscriber#newActivator(Chromosome)} is then used to get the resulting network.
 * 
 * To transcribe the neural network from a chromosome a connective pattern producing network (CPPN) is created from the
 * chromosome, and then this is "queried" to determine the weights of the neural network according to the ES-HyperNEAT
 * algorithm. The CPPN is an {@link com.anji.nn.AnjiNet}.
 * 
 * The ES-HyperNEAT algorithm code is adapted from code by Sebastian Risi as part of the "ES-HyperNEAT C#" package.
 * 
 * @author Oliver Coleman
 */
public class ESHyperNEATTranscriberBain extends HyperNEATTranscriber<BainNN> {
	private final static Logger logger = Logger.getLogger(ESHyperNEATTranscriberBain.class);

	public static final String ES_HYPERNEAT_ITERATIONS = "ann.eshyperneat.iterations";
	public static final String ES_HYPERNEAT_INITIAL_DEPTH = "ann.eshyperneat.depth.initial";
	public static final String ES_HYPERNEAT_MAX_DEPTH = "ann.eshyperneat.depth.max";
	public static final String ES_HYPERNEAT_DIVISION_THRESHOLD = "ann.eshyperneat.division.threshold";
	public static final String ES_HYPERNEAT_VARIANCE_THRESHOLD = "ann.eshyperneat.variance.threshold";
	public static final String ES_HYPERNEAT_BAND_THRESHOLD = "ann.eshyperneat.band.threshold";
	public static final String ES_HYPERNEAT_INPUT_POSITIONS = "ann.eshyperneat.input.positions";
	public static final String ES_HYPERNEAT_OUTPUT_POSITIONS = "ann.eshyperneat.output.positions";
	public static final String ES_HYPERNEAT_RECORD_COORDINATES = "ann.eshyperneat.record.coordinates";

	private Properties properties;

	List<TempNeuron> inputNeuronPositions;
	List<TempNeuron> outputNeuronPositions;
	int esIterations = 1;
	int initialDepth = 3;
	int maxDepth = 3;
	double divisionThreshold = 0.03;
	double varianceThreshold = 0.03;
	double bandThrehold = 0.3;

	public ESHyperNEATTranscriberBain() {
	}

	public ESHyperNEATTranscriberBain(Properties props) {
		init(props);
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	@Override
	public void init(Properties props) {
		inputNeuronPositions = extractCoords(props.getProperty(ES_HYPERNEAT_INPUT_POSITIONS).trim(), TempNeuron.INPUT);
		outputNeuronPositions = extractCoords(props.getProperty(ES_HYPERNEAT_OUTPUT_POSITIONS).trim(), TempNeuron.OUTPUT);

		// Set substrate dimensions to prevent HyperNEATTranscriber.init() throwing an error.
		props.put(SUBSTRATE_DEPTH, "1");
		props.put(SUBSTRATE_HEIGHT, "1");
		props.put(SUBSTRATE_WIDTH, ""+inputNeuronPositions.size()); // Not necessarily accurate but allows some classes to function properly (eg TestTargetFitnessFunction).

		super.init(props);
		this.properties = props;
		esIterations = props.getIntProperty(ES_HYPERNEAT_ITERATIONS, esIterations);
		initialDepth = props.getIntProperty(ES_HYPERNEAT_INITIAL_DEPTH, initialDepth);
		maxDepth = props.getIntProperty(ES_HYPERNEAT_MAX_DEPTH, maxDepth);
		divisionThreshold = props.getDoubleProperty(ES_HYPERNEAT_DIVISION_THRESHOLD, divisionThreshold);
		varianceThreshold = props.getDoubleProperty(ES_HYPERNEAT_VARIANCE_THRESHOLD, varianceThreshold);
		bandThrehold = props.getDoubleProperty(ES_HYPERNEAT_BAND_THRESHOLD, bandThrehold);

		// Override setting of cycles per step based on depth for feed-forward networks.
		cyclesPerStep = props.getIntProperty(SUBSTRATE_CYCLES_PER_STEP, 1);
	}

	private ArrayList<TempNeuron> extractCoords(String positions, int type) {
		Pattern coordPattern = Pattern.compile("(\\(\\s*-?\\d+\\.?\\d*\\s*,\\s*-?\\d+\\.?\\d*\\s*\\)\\s*)+");
		Matcher positionsMatcher = coordPattern.matcher(positions);
		ArrayList<TempNeuron> points = new ArrayList<TempNeuron>(positionsMatcher.groupCount());
		while (positionsMatcher.find()) {
			String[] coords = positionsMatcher.group(1).replaceAll("[\\(\\)]", "").split(",");
			TempNeuron p = new TempNeuron(Double.parseDouble(coords[0].trim()), Double.parseDouble(coords[1].trim()), type);
			points.add(p);
		}
		return points;
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	@Override
	public BainNN transcribe(Chromosome genotype) throws TranscriberException {
		return generateSubstrate(genotype);
	}

	@Override
	public BainNN transcribe(Chromosome genotype, BainNN substrate) throws TranscriberException {
		return generateSubstrate(genotype);
	}

	/*
	 * The main method that generations a list of ANN connections based on the information in the underlying hypercube.
	 * Input : CPPN, InputPositions, OutputPositions, ES-HyperNEAT parameters Output: Connections, HiddenNodes
	 */
	public BainNN generateSubstrate(Chromosome genotype) throws TranscriberException {
		long startTime = System.currentTimeMillis();
		CPPN cppn = new CPPN(genotype);

		int inputCount = inputNeuronPositions.size();
		int outputCount = outputNeuronPositions.size();
		List<TempNeuron> inputNeuronPositionsCopy = new ArrayList<TempNeuron>(inputCount);
		for (TempNeuron input : inputNeuronPositions) {
			inputNeuronPositionsCopy.add(new TempNeuron(input.x, input.y, input.type));
		}
		List<TempNeuron> outputNeuronPositionsCopy = new ArrayList<TempNeuron>(outputCount);
		for (TempNeuron output : outputNeuronPositions) {
			outputNeuronPositionsCopy.add(new TempNeuron(output.x, output.y, output.type));
		}
		
		List<TempNeuron> hiddenNeurons = new LinkedList<TempNeuron>();
		List<Connection> connections = new ArrayList<Connection>();
		List<TempConnection> tempConnections = new LinkedList<TempConnection>();

		// Generate connections from input nodes.
		for (TempNeuron input : inputNeuronPositionsCopy) {
			// Analyse outgoing connectivity pattern from this input
			QuadPoint root = quadTreeInitialisation(cppn, input.x, input.y, true, initialDepth, maxDepth);
			tempConnections.clear();
			// Traverse quad tree and add connections to list
			pruneAndExpress(cppn, input.x, input.y, tempConnections, root, true, maxDepth);

			for (TempConnection tempCon : tempConnections) {
				TempNeuron newHidden = new TempNeuron(tempCon.x2, tempCon.y2, TempNeuron.HIDDEN);
				int targetIndex = hiddenNeurons.indexOf(newHidden);
				if (targetIndex == -1) {
					hiddenNeurons.add(newHidden);
				} else {
					newHidden = hiddenNeurons.get(targetIndex);
				}
				double weight = tempCon.weight < 0 ? tempCon.weight * connectionWeightMin : tempCon.weight * connectionWeightMax;
				connections.add(new Connection(input, newHidden, weight));
			}
		}

		tempConnections.clear();

		List<TempNeuron> unexploredHiddenNodes = new LinkedList<TempNeuron>();
		unexploredHiddenNodes.addAll(hiddenNeurons);

		for (int step = 0; step < esIterations; step++) {
			for (TempNeuron hiddenPoint : unexploredHiddenNodes) {
				tempConnections.clear();
				QuadPoint root = quadTreeInitialisation(cppn, hiddenPoint.x, hiddenPoint.y, true, initialDepth, maxDepth);
				pruneAndExpress(cppn, hiddenPoint.x, hiddenPoint.y, tempConnections, root, true, maxDepth);

				for (TempConnection tempCon : tempConnections) {
					TempNeuron newHidden = new TempNeuron(tempCon.x2, tempCon.y2, TempNeuron.HIDDEN);
					int targetIndex = hiddenNeurons.indexOf(newHidden);
					if (targetIndex == -1) {
						hiddenNeurons.add(newHidden);
					} else {
						newHidden = hiddenNeurons.get(targetIndex);
					}
					double weight = tempCon.weight < 0 ? tempCon.weight * connectionWeightMin : tempCon.weight * connectionWeightMax;
					connections.add(new Connection(hiddenPoint, newHidden, weight));
				}
			}
			// Remove the just explored nodes.
			List<TempNeuron> temp = new LinkedList<TempNeuron>();
			temp.addAll(hiddenNeurons);
			for (TempNeuron f : unexploredHiddenNodes)
				temp.remove(f);

			unexploredHiddenNodes = temp;

		}

		tempConnections.clear();

		// Connect discovered hidden neurons to output neurons.
		for (TempNeuron outputPos : outputNeuronPositionsCopy) {
			// Analyse incoming connectivity pattern to this output
			QuadPoint root = quadTreeInitialisation(cppn, outputPos.x, outputPos.y, false, initialDepth, maxDepth);
			tempConnections.clear();
			pruneAndExpress(cppn, outputPos.x, outputPos.y, tempConnections, root, false, maxDepth);

			for (TempConnection t : tempConnections) {
				TempNeuron source = new TempNeuron(t.x1, t.y1, TempNeuron.HIDDEN);
				int sourceIndex = hiddenNeurons.indexOf(source);
				//New nodes not created here because all the hidden nodes that are connected to an input/hidden node
				// are already expressed.
				if (sourceIndex != -1) { // only connect if hidden neuron already exists
					double weight = t.weight < 0 ? t.weight * connectionWeightMin : t.weight * connectionWeightMax;
					connections.add(new Connection(hiddenNeurons.get(sourceIndex), outputPos, weight));
				}
			}
		}

		// Find hidden neurons with only incoming connections. We leave hidden neurons with only outgoing connections as
		// they can still have an influence (in the original ES-HyperNEAT all hidden nodes without a path to an input
		// and output neuron are removed).
		boolean removedAllDeadEnds = false;
		while (!removedAllDeadEnds) {
			for (TempNeuron hidden : hiddenNeurons) {
				hidden.hasOutgoingConnection = false;
			}
			for (Connection c : connections) {
				// Mark the hidden neuron as having an outgoing connection.
				c.source.hasOutgoingConnection = true;
			}
			removedAllDeadEnds = true;
			// Remove hidden neurons with only incoming connections.
			Iterator<TempNeuron> hiddenNeuronsIterator = hiddenNeurons.iterator();
			while (hiddenNeuronsIterator.hasNext()) {
				if (!hiddenNeuronsIterator.next().hasOutgoingConnection) {
					hiddenNeuronsIterator.remove();
					removedAllDeadEnds = false; // We might need to do another iteration to remove dead-end chains.
				}
			}
		}

		// Create Bain NeuralNetwork.
		int hiddenCount = hiddenNeurons.size();
		int neuronCount = inputCount + hiddenCount + outputCount;
		int synapseCount = connections.size();
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		String neuronModelClass = properties.getProperty(HyperNEATTranscriberBain.SUBSTRATE_NEURON_MODEL, "ojc.bain.neuron.rate.SigmoidNeuronCollection");
		String synapseModelClass = properties.getProperty(HyperNEATTranscriberBain.SUBSTRATE_SYNAPSE_MODEL, "ojc.bain.synapse.rate.FixedSynapseCollection");
		try {
			neurons = (NeuronCollection) ComponentCollection.createCollection(neuronModelClass, neuronCount);
			if (enableBias && !(neurons instanceof NeuronCollectionWithBias)) {
				throw new TranscriberException("Error creating Bain neural network: bias for neurons is enabled but the specified neuron class does not support bias (it does not extend NeuronCollectionWithBias).");
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

		// Set index in Bain NN for input neurons.
		int indexInBainNN = 0;
		for (int i = 0; i < inputNeuronPositionsCopy.size(); i++, indexInBainNN++) {
			inputNeuronPositionsCopy.get(i).indexInBainNN = indexInBainNN;
		}
		for (int i = 0; i < hiddenNeurons.size(); i++, indexInBainNN++) {
			TempNeuron point = hiddenNeurons.get(i);
			point.indexInBainNN = indexInBainNN;
			if (enableBias) {
				cppn.query(0, 0, point.x, point.y);
				((NeuronCollectionWithBias) neurons).setBias(indexInBainNN, cppn.getBiasWeight());
			}
		}
		for (int i = 0; i < outputCount; i++, indexInBainNN++) {
			TempNeuron point = outputNeuronPositionsCopy.get(i);
			point.indexInBainNN = indexInBainNN;
			if (enableBias) {
				cppn.query(0, 0, point.x, point.y);
				((NeuronCollectionWithBias) neurons).setBias(indexInBainNN, cppn.getBiasWeight());
			}
		}
		assert (indexInBainNN == neuronCount);

		// Set pre and post neuron indexes and weight value for each connection.
		double[] synapseWeights = synapses.getEfficacies();
		for (int i = 0; i < synapseCount; i++) {
			Connection c = connections.get(i);
			assert (c.source.indexInBainNN < neuronCount);
			assert (c.target.indexInBainNN < neuronCount);
			synapses.setPreAndPostNeurons(i, c.source.indexInBainNN, c.target.indexInBainNN);
			synapseWeights[i] = c.weight;
		}
		synapses.setEfficaciesModified();

		int simRes = properties.getIntProperty(HyperNEATTranscriberBain.SUBSTRATE_SIMULATION_RESOLUTION, 1000);
		String execModeName = properties.getProperty(HyperNEATTranscriberBain.SUBSTRATE_EXECUTION_MODE, null);
		Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
		NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
		int[] inputDims = new int[] { inputCount, 1 };
		int[] outputDims = new int[] { outputCount, 1 };
		try {
			BainNN network = new BainNN(nn, inputDims, outputDims, cyclesPerStep, feedForward ? BainNN.Topology.FEED_FORWARD_NONLAYERED : BainNN.Topology.RECURRENT, "network " + genotype.getId());
			if (properties.getBooleanProperty(ES_HYPERNEAT_RECORD_COORDINATES, false)) {
				network.enableCoords();
				int neuronIndex = 0;
				for (int i = 0; i < inputCount; i++, neuronIndex++) {
					network.setCoords(neuronIndex, inputNeuronPositionsCopy.get(i).x, inputNeuronPositionsCopy.get(i).y);
				}
				for (int i = 0; i < hiddenNeurons.size(); i++, neuronIndex++)
					network.setCoords(neuronIndex, hiddenNeurons.get(i).x, hiddenNeurons.get(i).y);
				for (int i = 0; i < outputCount; i++, neuronIndex++)
					network.setCoords(neuronIndex, outputNeuronPositionsCopy.get(i).x, outputNeuronPositionsCopy.get(i).y);
			}

			if (logger.isDebugEnabled()) {
				long endTime = System.currentTimeMillis();
				logger.debug("Substrate input/hidden/output/total neuron count: " + inputCount + "/" + hiddenNeurons.size() + "/" + outputCount + "/" + neuronCount + ", synapse count: " + synapseCount + ". Took + " + ((endTime - startTime) / 1000f) + "s.");
				logger.debug(network);
				if (properties.getBooleanProperty(ES_HYPERNEAT_RECORD_COORDINATES, false)) {
					BufferedImage image = new BufferedImage(800, 800, BufferedImage.TYPE_3BYTE_BGR);
					network.draw(image.createGraphics(), image.getWidth(), image.getHeight(), 30);
					File outputfile = new File(properties.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + "ESHyperNEATTranscriberBain-" + genotype.getId() + ".png");
					ImageIO.write(image, "png", outputfile);
				}
			}

			return network;
		} catch (Exception e) {
			e.printStackTrace();
			throw new TranscriberException(e.getMessage(), e.getCause());
		}
	}

	private static int imageCount = 0;

	public class TempNeuron {
		public static final int INPUT = 1, HIDDEN = 2, OUTPUT = 3;
		public double x, y;
		public int type;
		public int indexInBainNN;
		public boolean hasOutgoingConnection;

		public TempNeuron(double x, double y, int type) {
			this.x = x;
			this.y = y;
			this.type = type;
		}

		@Override
		public boolean equals(Object o) {
			return ((o instanceof TempNeuron) && ((TempNeuron) o).x == this.x && ((TempNeuron) o).y == this.y);
		}

		@Override
		public String toString() {
			return "(" + x + "," + y + ")";
		}
	}

	public class QuadPoint {
		public double x, y;
		public double w; // stores the CPPN value
		public double width; // width of this quad tree square
		public List<QuadPoint> children;
		public int level; // the level in the quad tree

		public QuadPoint(double _x, double _y, double _w, int _level) {
			level = _level;
			w = 0;
			x = _x;
			y = _y;
			width = _w;
			children = new ArrayList<QuadPoint>();
		}
	}

	public class Connection {
		public TempNeuron source, target;
		double weight;

		public Connection(TempNeuron source, TempNeuron target, double weight) {
			this.source = source;
			this.target = target;
			this.weight = weight;
		}
	}

	public class TempConnection {
		public double x1, y1, x2, y2;
		// public PointF start, end;
		public double weight;

		public TempConnection(double x1, double y1, double x2, double y2, double weight) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.weight = weight;
		}
	}

	/*
	 * Input: Coordinates of source (outgoing = true) or target node (outgoing = false) at (a,b) Output: Quadtree, in
	 * which each quadnode at (x,y) stores CPPN activation level for its position. The initialized quadtree is used in
	 * the PruningAndExtraction phase to generate the actual ANN connections.
	 */
	public QuadPoint quadTreeInitialisation(CPPN cppn, double a, double b, boolean outgoing, int initialDepth, int maxDepth) {
		QuadPoint root = new QuadPoint(0, 0, 1.0f, 1); // x, y, width, level
		LinkedList<QuadPoint> queue = new LinkedList<QuadPoint>();
		queue.add(root);

		while (!queue.isEmpty()) {
			QuadPoint p = queue.removeFirst();// dequeue

			// Divide into sub-regions and assign children to parent
			p.children.add(new QuadPoint(p.x - p.width / 2, p.y - p.width / 2, p.width / 2, p.level + 1));
			p.children.add(new QuadPoint(p.x - p.width / 2, p.y + p.width / 2, p.width / 2, p.level + 1));
			p.children.add(new QuadPoint(p.x + p.width / 2, p.y - p.width / 2, p.width / 2, p.level + 1));
			p.children.add(new QuadPoint(p.x + p.width / 2, p.y + p.width / 2, p.width / 2, p.level + 1));

			for (QuadPoint c : p.children) {
				if (outgoing) // Querying connection from input or hidden node
				{
					c.w = cppn.query(a, b, c.x, c.y); // Outgoing connectivity pattern
				} else // Querying connection to output node
				{
					c.w = cppn.query(c.x, c.y, a, b); // Incoming connectivity pattern
				}
			}

			// Divide until initial resolution or if variance is still high
			if (p.level < initialDepth || (p.level < maxDepth && variance(p) > divisionThreshold)) {
				for (QuadPoint c : p.children) {
					queue.add(c);
				}
			}
		}
		return root;
	}

	/*
	 * Input : Coordinates of source (outgoing = true) or target node (outgoing = false) at (a,b) and initialized
	 * quadtree p. Output: Adds the connections that are in bands of the two-dimensional cross-section of the hypercube
	 * containing the source or target node to the connections list.
	 */

	public void pruneAndExpress(CPPN cppn, double a, double b, List<TempConnection> connections, QuadPoint node, boolean outgoing, double maxDepth) {
		double left = 0, right = 0, top = 0, bottom = 0;

		if (node.children.get(0) == null)
			return;

		// Traverse quadtree depth-first
		for (QuadPoint c : node.children) {
			double childVariance = variance(c);

			if (childVariance >= varianceThreshold) {
				pruneAndExpress(cppn, a, b, connections, c, outgoing, maxDepth);
			} else // this should always happen for at least the leaf nodes because their variance is zero
			{
				// Determine if point is in a band by checking neighbor CPPN values
				if (outgoing) {
					left = Math.abs(c.w - cppn.query(a, b, c.x - node.width, c.y));
					right = Math.abs(c.w - cppn.query(a, b, c.x + node.width, c.y));
					top = Math.abs(c.w - cppn.query(a, b, c.x, c.y - node.width));
					bottom = Math.abs(c.w - cppn.query(a, b, c.x, c.y + node.width));
				} else {
					left = Math.abs(c.w - cppn.query(c.x - node.width, c.y, a, b));
					right = Math.abs(c.w - cppn.query(c.x + node.width, c.y, a, b));
					top = Math.abs(c.w - cppn.query(c.x, c.y - node.width, a, b));
					bottom = Math.abs(c.w - cppn.query(c.x, c.y + node.width, a, b));
				}

				if (Math.max(Math.min(top, bottom), Math.min(left, right)) > bandThrehold) {
					TempConnection tc;
					if (outgoing) {
						tc = new TempConnection(a, b, c.x, c.y, c.w);
					} else {
						tc = new TempConnection(c.x, c.y, a, b, c.w);
					}
					connections.add(tc);
				}

			}
		}
	}

	// Collect the CPPN values stored in a given quadtree p
	// Used to estimate the variance in a certain region in space
	private void getCPPNValues(List<Double> l, QuadPoint p) {
		if (p != null && !p.children.isEmpty()) {
			for (int i = 0; i < 4; i++) {
				getCPPNValues(l, p.children.get(i));
			}
		} else {
			l.add(p.w);
		}
	}

	// determine the variance of a certain region
	public double variance(QuadPoint p) {
		if (p.children.isEmpty()) {
			return 0.0;
		}

		List<Double> l = new LinkedList<Double>();
		getCPPNValues(l, p);

		double m = 0, v = 0.0;
		for (double f : l) {
			m += f;
		}
		m /= l.size();
		for (double f : l) {
			v += Math.pow(f - m, 2);
		}
		v /= l.size();
		return v;
	}

	@Override
	public Class getPhenotypeClass() {
		return BainNN.class;
	}
}
