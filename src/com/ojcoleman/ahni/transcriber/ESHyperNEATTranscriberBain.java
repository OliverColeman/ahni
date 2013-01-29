package com.ojcoleman.ahni.transcriber;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.ojcoleman.bain.NeuralNetwork;
import com.ojcoleman.bain.base.ComponentCollection;
import com.ojcoleman.bain.base.NeuronCollection;
import com.ojcoleman.bain.base.SynapseCollection;
import com.ojcoleman.bain.neuron.rate.NeuronCollectionWithBias;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.amd.aparapi.Kernel;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.ojcoleman.ahni.event.*;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.ahni.util.Point;

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
public class ESHyperNEATTranscriberBain extends HyperNEATTranscriber<BainNN> implements AHNIEventListener {
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
	
	/**
	 * If true then the substrate is considered as occupying a 3D space, with the 
	 * input and outputs located on the XY plane at z=-1 and z=1 respectively, and
	 * hidden neurons located on the ZX plane at y=0.
	 * If false then all neurons are located in a 2D plane with X-Y axes.
	 */
	public static final String ES_HYPERNEAT_3D_PSEUDO = "ann.eshyperneat.3D.pseudo";
	
	private Properties properties;

	List<Neuron> inputNeurons; // Coordinates are in unit ranges.
	List<Neuron> outputNeurons; // Coordinates are in unit ranges.
	int esIterations = 1;
	int initialDepth = 3;
	int maxDepth = 3;
	double divisionThreshold = 0.03;
	double varianceThreshold = 0.03;
	double bandThrehold = 0.3;
	boolean pseudo3D = false;
	
	double runningAvgHiddenNeuronCount = 16;
	double runningAvgSynapseCount = 160;
	int maxQuadTreeSize = 1;
	
	int maxNeuronCount = 0;
	int maxSynapseCount = 0;
	int avgNeuronCount = 0;
	int avgSynapseCount = 0;
	int noPathFromInputToOutputCount = 0;
	int popSize = 0;

	public ESHyperNEATTranscriberBain() {
	}

	public ESHyperNEATTranscriberBain(Properties props) {
		init(props);
	}

	@Override
	public void init(Properties props) {
		this.properties = props;

		pseudo3D = props.getBooleanProperty(ES_HYPERNEAT_3D_PSEUDO, pseudo3D);
		zCoordsForCPPN = pseudo3D ? "force" : "prevent";

		// Set substrate dimensions to prevent HyperNEATTranscriber.init() throwing an error.
		if (!props.containsKey(SUBSTRATE_DEPTH)) {
			// If depth not specified then use a default depth of 2 (input and output layer), hidden neurons do not exist in a conventional layer.
			props.put(SUBSTRATE_DEPTH, "2");
		}

		depth = props.getIntProperty(SUBSTRATE_DEPTH);
		
		if (props.containsKey(ES_HYPERNEAT_INPUT_POSITIONS) || props.containsKey(ES_HYPERNEAT_OUTPUT_POSITIONS)) {
			throw new IllegalArgumentException("The properties " + ES_HYPERNEAT_INPUT_POSITIONS + " and " + ES_HYPERNEAT_OUTPUT_POSITIONS + " are no longer supported. Please use " + NEURON_POSITIONS_FOR_LAYER + ".");
		}
		
		if (props.containsKey(NEURON_POSITIONS_FOR_LAYER + ".0")) {
			inputNeurons = extractCoords(Neuron.INPUT);
		}
		if (props.containsKey(NEURON_POSITIONS_FOR_LAYER + "." + (depth-1))) {
			outputNeurons = extractCoords(Neuron.OUTPUT);
		}
		
		// Allow specifying substrate input and/or output layer dimensions in case 2D layers are important. 
		if (!props.containsKey(SUBSTRATE_HEIGHT) || !props.containsKey(SUBSTRATE_WIDTH)) {
			// Otherwise just configure as 1D vectors.
			if (inputNeurons != null && outputNeurons != null) {
				String width = ""+inputNeurons.size(), height = "1";
				// There are no real hidden layers, just set minimal dimensions for them.
				for (int d = 1; d < depth-1; d++) {
					width += ", 1";
					height += ", 1";
				}
				width += ", " + outputNeurons.size();
				height += ", 1";
				props.put(SUBSTRATE_WIDTH, width);
				props.put(SUBSTRATE_HEIGHT, height);
			}
			else {
				throw new IllegalArgumentException("Neither input and output layer dimensions or input and output neuron coordinates have been specified.");
			}
		}
		
		// There are no real layers.
		boolean warn = props.getBooleanProperty(HYPERNEAT_LAYER_ENCODING, false);
		props.put(HYPERNEAT_LAYER_ENCODING, "true");
		if (warn) {
			logger.info("Forcing " + HYPERNEAT_LAYER_ENCODING + " to true (there are no real layers in ES-HyperNEAT substrate).");
		}
		
		super.init(props);
		
		// Automatically generate input neuron coordinates if they were not specified.
		if (inputNeurons == null) {
			inputNeurons = generateCoords(width[0], height[0], Neuron.INPUT);
			logger.info("Input coordinates are: " + inputNeurons);
		}
		else if (inputNeurons.size() != width[0] * height[0]) {
			throw new IllegalArgumentException("Input layer size does not match number of input neurons specified by " + ES_HYPERNEAT_INPUT_POSITIONS + ".");
		}

		// Automatically generate output neuron coordinates if they were not specified.
		if (outputNeurons == null) {
			outputNeurons = generateCoords(width[depth-1], height[depth-1], Neuron.OUTPUT);
			logger.info("Output coordinates are: " + outputNeurons);
		}

		esIterations = props.getIntProperty(ES_HYPERNEAT_ITERATIONS, esIterations);
		initialDepth = props.getIntProperty(ES_HYPERNEAT_INITIAL_DEPTH, initialDepth);
		maxDepth = props.getIntProperty(ES_HYPERNEAT_MAX_DEPTH, maxDepth);
		divisionThreshold = props.getDoubleProperty(ES_HYPERNEAT_DIVISION_THRESHOLD, divisionThreshold);
		varianceThreshold = props.getDoubleProperty(ES_HYPERNEAT_VARIANCE_THRESHOLD, varianceThreshold);
		bandThrehold = props.getDoubleProperty(ES_HYPERNEAT_BAND_THRESHOLD, bandThrehold);
		
		// Override setting of cycles per step based on depth for feed-forward networks.
		cyclesPerStep = props.getIntProperty(SUBSTRATE_CYCLES_PER_STEP, 1);
		
		maxQuadTreeSize = (int) Math.pow(4, maxDepth+0.25);
		
		((Properties) props).getEvolver().addEventListener(this);
	}

	private ArrayList<Neuron> extractCoords(int type) {
		double z = pseudo3D ? (type == Neuron.INPUT ? 0 : 1) : 0;
		Point defaultCoords = new Point(0, 0, z);
		defaultCoords.translateFromUnit(rangeX, rangeY, rangeZ);
		double[] defaultArgs = new double[] { defaultCoords.x, defaultCoords.y, defaultCoords.z };
		int layer = type == Neuron.INPUT ? 0 : depth-1;
		Point[] points = properties.getObjectArrayProperty(NEURON_POSITIONS_FOR_LAYER + "." + layer, Point.class, defaultArgs);
		ArrayList<Neuron> neurons = new ArrayList<Neuron>(points.length);
		for (int i = 0; i < points.length; i++) {
			points[i].translateToUnit(rangeX, rangeY, rangeZ);
			neurons.add(new Neuron(points[i].x, points[i].y, points[i].z, type));
		}
		return neurons;
	}
	
	private ArrayList<Neuron> generateCoords(int width, int height, int type) {
		ArrayList<Neuron> points = new ArrayList<Neuron>(width * height);
		if (pseudo3D) {
			double z = type == Neuron.INPUT ? 0 : 1;
			for (int yi = 0; yi < height; yi++) {
				double y = height > 1 ? ((double) yi / (height - 1)) : 0.5;
				for (int xi = 0; xi < width; xi++) {
					double x = width > 1 ? ((double) xi / (width - 1)) : 0.5;
					Neuron p = new Neuron(x, y, z, type);
					points.add(p);
				}
			}
		}
		else {
			if (height > 1) {
				throw new IllegalArgumentException("Either the input or output layer height is greater 1 for a 2D substrate.");
			}
			double y = type == Neuron.INPUT ? 0 : 1;
			for (int xi = 0; xi < width; xi++) {
				double x = width > 1 ? ((double) xi / (width - 1)) : 0.5;
				Neuron p = new Neuron(x, y, 0, type);
				points.add(p);
			}
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

	/**
	 * Generate a substrate from a Chromosome according to the ES-HyperNEAT algorithm.
	 * @param genotype The genotype from which to generate a substrate.
	 */
	public BainNN generateSubstrate(Chromosome genotype) throws TranscriberException {
		long startTime = System.currentTimeMillis();
		CPPN cppn = new CPPN(genotype);
		
		
		/*DecimalFormat nf1 = new DecimalFormat("###0.0#");
		for (double zs = 0; zs <=1 ; zs+=0.1) {
			for (double zt = 0; zt <=1 ; zt+=0.1) {
				logger.info(nf1.format(zs) + " -> " + nf1.format(zt) + " : " + nf1.format(cppn.query(0, 0, zs, 0, 0, zt)));
				//logger.info(nf1.format(zs) + " -> " + nf1.format(zt) + " : " + nf1.format(cppn.query(Math.random(), Math.random(), zs, Math.random(), Math.random(), zt)));
			}
		}*/

		int inputCount = inputNeurons.size();
		int outputCount = outputNeurons.size();
		List<Neuron> inputNeuronPositionsCopy = new ArrayList<Neuron>(inputCount);
		for (Neuron input : inputNeurons) {
			inputNeuronPositionsCopy.add(new Neuron(input.x, input.y, input.z, input.type));
		}
		List<Neuron> outputNeuronPositionsCopy = new ArrayList<Neuron>(outputCount);
		for (Neuron output : outputNeurons) {
			outputNeuronPositionsCopy.add(new Neuron(output.x, output.y, output.z, output.type));
		}
		
		// Use a hash map to be able to quickly find if a node already exists at a given location.
		Map<Neuron, Neuron> hiddenNeurons = new HashMap<Neuron, Neuron>((int) runningAvgHiddenNeuronCount);
		List<Connection> connections = new ArrayList<Connection>((int) runningAvgSynapseCount);
		List<TempConnection> tempConnections = new ArrayList<TempConnection>((int) runningAvgSynapseCount);
		
		// This list is passed to the quadTreeInitialisation and pruneAndExpress methods to be reused for performance reasons.
		double[] tempStorageForCPPNValues = new double[maxQuadTreeSize];
		
		// Generate connections from input nodes.
		for (Neuron input : inputNeuronPositionsCopy) {
			// Analyse outgoing connectivity pattern from this input.		
			QuadPoint root = quadTreeInitialisation(cppn, input, true, tempStorageForCPPNValues);
			
			// Traverse quad tree and retrieve connections.
			tempConnections.clear();
			pruneAndExpress(cppn, input, tempConnections, root, true, tempStorageForCPPNValues);
			
			for (TempConnection tempCon : tempConnections) {
				Neuron newHidden = new Neuron(tempCon.targetPoint.x, tempCon.targetPoint.y, tempCon.targetPoint.z, Neuron.HIDDEN);
				if (hiddenNeurons.containsKey(newHidden)) {
					newHidden = hiddenNeurons.get(newHidden);
				} else {
					hiddenNeurons.put(newHidden, newHidden);
				}
				double weight = tempCon.weight < 0 ? tempCon.weight * connectionWeightMin : tempCon.weight * connectionWeightMax;
				connections.add(new Connection(input, newHidden, weight));
			}
		}
		tempConnections.clear();
		
		// Iteratively search for hidden nodes from those already found.
		Map<Neuron, Neuron> unexploredHiddenNodes = new HashMap<Neuron, Neuron>(hiddenNeurons); // Use a hash map to quickly be able to find and remove a node.
		for (int step = 0; step < esIterations; step++) {
			for (Neuron hiddenNeuron : unexploredHiddenNodes.values()) {
				// Analyse outgoing connectivity pattern from hidden neuron.
				QuadPoint root = quadTreeInitialisation(cppn, hiddenNeuron, true, tempStorageForCPPNValues);
				
				// Traverse quad tree and retrieve connections.
				tempConnections.clear();
				pruneAndExpress(cppn, hiddenNeuron, tempConnections, root, true, tempStorageForCPPNValues);
				
				for (TempConnection tempCon : tempConnections) {
					Neuron newHidden = new Neuron(tempCon.targetPoint.x, tempCon.targetPoint.y, tempCon.targetPoint.z, Neuron.HIDDEN);
					if (hiddenNeurons.containsKey(newHidden)) {
						newHidden = hiddenNeurons.get(newHidden);
					} else {
						hiddenNeurons.put(newHidden, newHidden);
					}
					double weight = tempCon.weight < 0 ? tempCon.weight * connectionWeightMin : tempCon.weight * connectionWeightMax;
					connections.add(new Connection(hiddenNeuron, newHidden, weight));
				}
			}
			// Remove the just explored nodes.
			Map<Neuron, Neuron> temp = new HashMap<Neuron, Neuron>(hiddenNeurons);
			for (Neuron f : unexploredHiddenNodes.values())
				temp.remove(f);

			unexploredHiddenNodes = temp;
		}
		tempConnections.clear();
		
		// Connect discovered hidden neurons to output neurons.
		for (Neuron outputPos : outputNeuronPositionsCopy) {
			// Analyse incoming connectivity pattern to this output
			QuadPoint root = quadTreeInitialisation(cppn, outputPos, false, tempStorageForCPPNValues);
			tempConnections.clear();
			pruneAndExpress(cppn, outputPos, tempConnections, root, false, tempStorageForCPPNValues);

			for (TempConnection tempCon : tempConnections) {
				Neuron source = new Neuron(tempCon.sourcePoint.x, tempCon.sourcePoint.y, tempCon.sourcePoint.z, Neuron.HIDDEN);
				// New nodes not created here because all the hidden nodes that are connected to an input/hidden node
				// are already expressed.
				if (hiddenNeurons.containsKey(source)) { // only connect if hidden neuron already exists
					double weight = tempCon.weight < 0 ? tempCon.weight * connectionWeightMin : tempCon.weight * connectionWeightMax;
					connections.add(new Connection(hiddenNeurons.get(source), outputPos, weight));
				}
			}
		}
		runningAvgHiddenNeuronCount = runningAvgHiddenNeuronCount * 0.9 + hiddenNeurons.size() * 0.1;
		runningAvgSynapseCount = runningAvgSynapseCount * 0.9 + connections.size() * 0.1;
		
		// Find hidden neurons with only incoming connections. We leave hidden neurons with only outgoing connections as
		// they can still have an influence (in the original ES-HyperNEAT all hidden nodes without a path to an input
		// and output neuron are removed).
		boolean removedAllDeadEnds = false;
		while (!removedAllDeadEnds) {
			// Reset marker for each hidden neuron.
			for (Neuron hidden : hiddenNeurons.values()) {
				hidden.hasOutgoingConnection = false;
			}
			// Mark the source neuron for each connection as having an outgoing connection.
			for (Connection c : connections) {
				c.source.hasOutgoingConnection = true;
			}
			removedAllDeadEnds = true;
			// Remove hidden neurons with no outgoing connections.
			Iterator<Neuron> hiddenNeuronsIterator = hiddenNeurons.values().iterator();
			while (hiddenNeuronsIterator.hasNext()) {
				if (!hiddenNeuronsIterator.next().hasOutgoingConnection) {
					hiddenNeuronsIterator.remove();
					removedAllDeadEnds = false; // We might need to do another iteration to remove dead-end chains.
				}
			}
		}
		
		int hiddenCount = hiddenNeurons.size();
		int neuronCount = inputCount + hiddenCount + outputCount;
		int synapseCount = connections.size();
		synchronized (this) {
			maxNeuronCount = Math.max(maxNeuronCount, neuronCount);
			maxSynapseCount = Math.max(maxSynapseCount, synapseCount);
			avgNeuronCount += neuronCount;
			avgSynapseCount += synapseCount;
			popSize++;
		}
		
		// Make sure there's a path from at least one input to one output.
		for (Connection c : connections) {
			c.source.targets.add(c.target);
		}
		Set<Neuron> covered = new HashSet<Neuron>(inputNeuronPositionsCopy);
		Set<Neuron> current = new HashSet<Neuron>(inputNeuronPositionsCopy);
		Set<Neuron> next = new HashSet<Neuron>();
		boolean foundOutput = false, foundNew;
		do {
			foundNew = false;
			for (Neuron n : current) {
				for (Neuron t : n.targets) {
					if (t.type == Neuron.OUTPUT) {
						foundOutput = true;
						break;
					}
					else if (!covered.contains(t)) {
						foundNew = true;
						next.add(t);
					}
				}
				if (foundOutput) {
					break;
				}
			}
			covered.addAll(next);
			Set<Neuron> temp = current;
			current = next;
			next = temp;
			next.clear();
		} while (!foundOutput && foundNew);
		if (!foundOutput) {
			logger.debug("Inputs not connected to outputs!");
			noPathFromInputToOutputCount++;
			return null; // Indicate that this substrate should have zero fitness.
		}
		

		// Create Bain NeuralNetwork.
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		String neuronModelClass = properties.getProperty(HyperNEATTranscriberBain.SUBSTRATE_NEURON_MODEL, "com.ojcoleman.bain.neuron.rate.SigmoidNeuronCollection");
		String synapseModelClass = properties.getProperty(HyperNEATTranscriberBain.SUBSTRATE_SYNAPSE_MODEL, "com.ojcoleman.bain.synapse.rate.FixedSynapseCollection");
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

		// Determine index in Bain NN for all neurons (Bain NNs connectivity is specified by indices rather than object references).
		int indexInBainNN = 0;
		for (Neuron point : inputNeuronPositionsCopy) {
			point.indexInBainNN = indexInBainNN++;
		}
		for (Neuron point : hiddenNeurons.values()) {
			point.indexInBainNN = indexInBainNN;
			if (enableBias) {
				cppn.query(0, 0, point.x, point.y);
				((NeuronCollectionWithBias) neurons).setBias(indexInBainNN, cppn.getBiasWeight());
			}
			indexInBainNN++;
		}
		for (Neuron point : outputNeuronPositionsCopy) {
			point.indexInBainNN = indexInBainNN;
			if (enableBias) {
				cppn.query(0, 0, point.x, point.y);
				((NeuronCollectionWithBias) neurons).setBias(indexInBainNN, cppn.getBiasWeight());
			}
			indexInBainNN++;
		}
		assert (indexInBainNN == neuronCount);

		// Set pre and post neuron indexes and weight value for each connection.
		double[] synapseWeights = synapses.getEfficacies();
		int ci = 0;
		for (Connection c : connections) {
			assert (c.source.indexInBainNN < neuronCount);
			assert (c.target.indexInBainNN < neuronCount);
			synapses.setPreAndPostNeurons(ci, c.source.indexInBainNN, c.target.indexInBainNN);
			synapseWeights[ci] = c.weight;
			ci++;
		}
		synapses.setEfficaciesModified();

		int simRes = properties.getIntProperty(HyperNEATTranscriberBain.SUBSTRATE_SIMULATION_RESOLUTION, 1000);
		String execModeName = properties.getProperty(HyperNEATTranscriberBain.SUBSTRATE_EXECUTION_MODE, null);
		Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
		NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
		int[] inputDims = new int[] { inputCount, 1 };
		int[] outputDims = new int[] { outputCount, 1 };
		int maxRecurrentCycles = properties.getIntProperty(HyperNEATTranscriberBain.SUBSTRATE_MAX_RECURRENT_CYCLE, 1000000);
		try {
			BainNN network = new BainNN(nn, inputDims, outputDims, cyclesPerStep, feedForward ? BainNN.Topology.FEED_FORWARD_NONLAYERED : BainNN.Topology.RECURRENT, "network " + genotype.getId(), maxRecurrentCycles);
			if (properties.getBooleanProperty(ES_HYPERNEAT_RECORD_COORDINATES, false)) {
				network.enableCoords();
				int neuronIndex = 0;
				if (pseudo3D) {
					for (Neuron point : inputNeuronPositionsCopy) {
						network.setCoords(neuronIndex, point.x, point.z, point.y);
						neuronIndex++;
					}
					for (Neuron point : hiddenNeurons.values()) {
						network.setCoords(neuronIndex, point.x, point.z, point.y);
						neuronIndex++;
					}
					for (Neuron point : outputNeuronPositionsCopy) {
						network.setCoords(neuronIndex, point.x, point.z, point.y);
						neuronIndex++;
					}
				}
				else {
					for (Neuron point : inputNeuronPositionsCopy) {
						network.setCoords(neuronIndex, point.x, point.y);
						neuronIndex++;
					}
					for (Neuron point : hiddenNeurons.values()) {
						network.setCoords(neuronIndex, point.x, point.y);
						neuronIndex++;
					}
					for (Neuron point : outputNeuronPositionsCopy) {
						network.setCoords(neuronIndex, point.x, point.y);
						neuronIndex++;
					}
				}
			}

			if (logger.isDebugEnabled()) {
				long endTime = System.currentTimeMillis();
				logger.debug("Substrate input/hidden/output/total neuron count: " + inputCount + "/" + hiddenNeurons.size() + "/" + outputCount + "/" + neuronCount + ", synapse count: " + synapseCount + ". Took + " + ((endTime - startTime) / 1000f) + "s.");
				logger.debug(network);
				if (properties.getBooleanProperty(ES_HYPERNEAT_RECORD_COORDINATES, false)) {
					BufferedImage image = new BufferedImage(800, 800, BufferedImage.TYPE_3BYTE_BGR);
					network.render(image.createGraphics(), image.getWidth(), image.getHeight(), 30);
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

	private class Neuron extends Point {
		public static final int INPUT = 1, HIDDEN = 2, OUTPUT = 3;
		public int type;
		public int indexInBainNN;
		public boolean hasOutgoingConnection;
		public List<Neuron> targets = new ArrayList<Neuron>();

		public Neuron(double x, double y, double z, int type) {
			super(x, y, z);
			this.type = type;
		}
	}

	public class QuadPoint extends Point {
		public double cppnValue; // stores the CPPN value
		public double leo; // stores the CPPN LEO output value (if LEO enabled). 
		public double width; // width of this quad tree square
		public QuadPoint[] children;
		public int level; // the level in the quad tree

		public QuadPoint(double x, double y, double z, double width, int level) {
			super (x, y, z);
			this.width = width;
			this.level = level;
			children = new QuadPoint[4];
		}
		
		public String toString() {
			return super.toString() + ": " + (float) cppnValue;
		}

		public String toStringDeep() {
			String s = "";
			for (int i = 0; i < level; i++) s += "\t";
			s += toString() + "\n";
			if (children[0] != null) {
				for (int i = 0; i < 4; i++) 
					s += children[i].toStringDeep();
			}
			return s;
		}
	}
	
	public class Connection {
		public Neuron source, target;
		double weight;

		public Connection(Neuron source, Neuron target, double weight) {
			this.source = source;
			this.target = target;
			this.weight = weight;
		}
		
		public String toString() {
			return source + " -> " + target;
		}
	}

	public class TempConnection {
		public Point sourcePoint, targetPoint;
		public double weight;

		public TempConnection(Point p1, Point p2, double weight) {
			this.sourcePoint = p1;
			this.targetPoint = p2;
			this.weight = weight;
		}
		
		public String toString() {
			return sourcePoint + " -> " + targetPoint;
		}
	}

	/**
	 * Creates a quadtree by recursively subdividing the initial square, which spans the space from (-1, -1) to (1, 1), 
	 * until a desired initial resolution is reached. For every quadtree square with centre (x, y) the CPPN is queried 
	 * with arguments (a, b, x, y) and the resulting connection weight value w is stored.
	 * 
	 * @param cppn The CPPN to use.
	 * @param n The source or target neuron position.
	 * @param outgoing Specifies whether the connection is for a source (outgoing = true) or target node (outgoing = false).
	 * @param tempStorageForCPPNValues A list store the CPPN value for each node in the quadtree. Allows reuse of same list for performance reasons.
	 * @return The root of the generated quadtree, each QuadPoint stores CPPN activation level for its position.
	 */
	public QuadPoint quadTreeInitialisation(CPPN cppn, Point n, boolean outgoing, double[] tempStorageForCPPNValues) {
		QuadPoint root = new QuadPoint(0.5, 0.5, 0.5, 1, 1); // x, y, z, width, level
		ArrayDeque<QuadPoint> queue = new ArrayDeque<QuadPoint>(maxQuadTreeSize);
		queue.add(root);

		//DecimalFormat nf1 = new DecimalFormat("###0.0##");
		/*BufferedImage image = null;
		Graphics2D g = null;
		int w = 800;
		int h = 800;
		if (firstTime) {
			image = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
			g = image.createGraphics();
		}*/
		
		while (!queue.isEmpty()) {
			QuadPoint parent = queue.removeFirst();

			// Divide into sub-regions and assign children to parent.
			int childLevel = parent.level+1;
			double childWidth = parent.width * 0.5;
			double offset = childWidth * 0.5;
			if (pseudo3D ) {
				// Hidden nodes located on XZ plane at y = 0.5.
				parent.children[0] = new QuadPoint(parent.x - offset, 0.5, parent.z - offset, childWidth, childLevel);
				parent.children[1] = new QuadPoint(parent.x - offset, 0.5, parent.z + offset, childWidth, childLevel);
				parent.children[2] = new QuadPoint(parent.x + offset, 0.5, parent.z - offset, childWidth, childLevel);
				parent.children[3] = new QuadPoint(parent.x + offset, 0.5, parent.z + offset, childWidth, childLevel);
			}
			else {
				// Hidden nodes located on XY plane.
				parent.children[0] = new QuadPoint(parent.x - offset, parent.y - offset, 0, childWidth, childLevel);
				parent.children[1] = new QuadPoint(parent.x - offset, parent.y + offset, 0, childWidth, childLevel);
				parent.children[2] = new QuadPoint(parent.x + offset, parent.y - offset, 0, childWidth, childLevel);
				parent.children[3] = new QuadPoint(parent.x + offset, parent.y + offset, 0, childWidth, childLevel);
			}
			
			// Get CPPN output for each child.
			for (int ci = 0; ci < 4; ci++) {
				QuadPoint child = parent.children[ci];
				if (outgoing) // Querying connection from input or hidden node.
					child.cppnValue = cppn.query(n, child); // Outgoing connectivity pattern.
				else // Querying connection to output node.
					child.cppnValue = cppn.query(child, n); // Incoming connectivity pattern.
				
				if (enableLEO)
					child.leo = cppn.getLEO();
				
				/*if (firstTime) {
					int hwI = (int) Math.round(childWidth * w);
					int x = (int) Math.round(child.x * w) - hwI/2;
					int y = (int) Math.round((pseudo3D ? child.z : child.y) * h) - hwI/2;
					int c = Math.min((int) Math.round(child.cppnValue * 0.5 * 255), 255);
					g.setColor(new Color(c, c, c));
					g.fillRect(x, y, hwI, hwI);*/
					//logger.info(child.level + " : " + child.width + " : " + nf1.format(n.x) + ", " + nf1.format(n.y) + ", " + nf1.format(n.z) +  " -> " + nf1.format(child.x) + ", " + nf1.format(child.y) + ", " + nf1.format(child.z) + " : " + nf1.format(child.cppnValue));
				//}
			}

			// Divide if minimum resolution hasn't been reached or variance is above threshold and maximum resolution hasn't been reached.
			if (parent.level < initialDepth || (parent.level < maxDepth && variance(parent, tempStorageForCPPNValues) > divisionThreshold)) {
				for (int ci = 0; ci < 4; ci++) {
					queue.add(parent.children[ci]);
				}
			}
		}
		
		/*if (firstTime) {
			//firstTime = false;
			File outputfile = new File("/home/data/Dropbox/ai/PhD/cppn + " + n + ".png");
			try {
				ImageIO.write(image, "png", outputfile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}*/
		
		return root;
	}
	//static boolean firstTime = true;

	/**
	 * The given quadtree is traversed depth-first until the current node's variance is smaller than the variance threshold
	 * or until the node has no children (which means that the variance is zero). Subsequently, a connection (a, b, x, y) is 
	 * created for each qualifying node with centre (x, y). Thus adds connections that are in bands of the two-dimensional 
	 * cross-section of the hypercube containing the source or target node to the connections list.
	 * 
	 * @param cppn The CPPN to use.
	 * @param neuron The source or target neuron (position).
	 * @param connections The list to add new connections to.
	 * @param root The root of the quadtree.
	 * @param outgoing Specifies whether the connection is for a source (outgoing = true) or target node (outgoing = false).
	 * @param tempStorageForCPPNValues A list store the CPPN value for each node in the quadtree. Allows reuse of same list for performance reasons.
	 */
	protected void pruneAndExpress(CPPN cppn, Point neuron, List<TempConnection> connections, QuadPoint root, boolean outgoing, double[] tempStorageForCPPNValues) {
		double left = 0, right = 0, top = 0, bottom = 0;

		if (root.children[0] == null)
			return;

		// Traverse quadtree depth-first.
		for (int ci = 0; ci < 4; ci++) {
			QuadPoint child = root.children[ci];
			double childVariance = variance(child, tempStorageForCPPNValues);
			if (childVariance >= varianceThreshold) {
				pruneAndExpress(cppn, neuron, connections, child, outgoing, tempStorageForCPPNValues);
			} else if (!enableLEO || child.leo > 0) { // If LEO disabled this should always happen for at least the leaf nodes because their variance is zero.
				// Determine if point is in a band by checking neighbour CPPN values.
				double width = root.width;
				if (outgoing) {
					left = Math.abs(child.cppnValue - cppn.query(neuron.x, neuron.y, neuron.z, child.x - width, child.y, child.z));
					right = Math.abs(child.cppnValue - cppn.query(neuron.x, neuron.y, neuron.z, child.x + width, child.y, child.z));
					if (pseudo3D) { // Hidden nodes located on XZ plane.
						top = Math.abs(child.cppnValue - cppn.query(neuron.x, neuron.y, neuron.z, child.x, child.y, child.z - width));
						bottom = Math.abs(child.cppnValue - cppn.query(neuron.x, neuron.y, neuron.z, child.x, child.y, child.z + width));
					}
					else { // Hidden nodes located on XY plane.
						top = Math.abs(child.cppnValue - cppn.query(neuron.x, neuron.y, neuron.z, child.x, child.y - width, child.z));
						bottom = Math.abs(child.cppnValue - cppn.query(neuron.x, neuron.y, neuron.z, child.x, child.y + width, child.z));
					}
				} else {
					left = Math.abs(child.cppnValue - cppn.query(child.x - width, child.y, child.z, neuron.x, neuron.y, neuron.z));
					right = Math.abs(child.cppnValue - cppn.query(child.x + width, child.y, child.z, neuron.x, neuron.y, neuron.z));
					if (pseudo3D) { // Hidden nodes located on XZ plane.
						top = Math.abs(child.cppnValue - cppn.query(child.x, child.y, child.z - width, neuron.x, neuron.y, neuron.z));
						bottom = Math.abs(child.cppnValue - cppn.query(child.x, child.y, child.z + width, neuron.x, neuron.y, neuron.z));
					}
					else { // Hidden nodes located on XY plane.
						top = Math.abs(child.cppnValue - cppn.query(child.x, child.y - width, child.z, neuron.x, neuron.y, neuron.z));
						bottom = Math.abs(child.cppnValue - cppn.query(child.x, child.y + width, child.z, neuron.x, neuron.y, neuron.z));
					}
				}
				
				if (Math.max(Math.min(top, bottom), Math.min(left, right)) > bandThrehold) {
					TempConnection tc;
					if (outgoing) {
						tc = new TempConnection(neuron, child, child.cppnValue);
					} else {
						tc = new TempConnection(child, neuron, child.cppnValue);
					}
					connections.add(tc);
				}

			}
		}
	}

	/**
	 * Determine the variance of a given region.
	 * @param p The root of the quadtree.  
	 * @param tempStorageForCPPNValues A list store the CPPN value for each node in the quadtree. Allows reuse of same list for performance reasons. This list will be cleared.
	 */
	protected double variance(QuadPoint p, double[] tempStorageForCPPNValues) {
		if (p.children[0] == null) {
			return 0;
		}
		
		int size = getCPPNValues(p, tempStorageForCPPNValues, 0);
		double avg = 0, variance = 0;
		for (int i = 0 ; i < size; i++) {
			avg += tempStorageForCPPNValues[i];
		}
		avg /= size;
		for (int i = 0 ; i < size; i++) {
			double d = tempStorageForCPPNValues[i] - avg;
			variance += d*d;
		}
		variance /= size;
		return variance;
	}

	/**
	 *  Collect the CPPN values for each leaf node in a quadtree.
	 *  Used to estimate the variance in a certain region in space.
	 *  @param p The root of the quadtree.
	 *  @param tempStorageForCPPNValues The list to store the CPPN values in.
	 *  @param index The current index into tempStorageForCPPNValues.
	 */
	private int getCPPNValues(QuadPoint p, double[] tempStorageForCPPNValues, int index) {
		if (p.children[0] != null) {
			for (int ci = 0; ci < 4; ci++) {
				index = getCPPNValues(p.children[ci], tempStorageForCPPNValues, index);
			}
		} else {
			tempStorageForCPPNValues[index] = p.cppnValue;
			index++;
		}
		return index;
	}
	
	@Override
	public Class getPhenotypeClass() {
		return BainNN.class;
	}

	/**
	 * Listen for the start and end of the population evaluation so we can keep statistics on the the transcribed networks for each generation.
	 */
	@Override
	public void ahniEventOccurred(AHNIEvent event) {
		if (event.getType() == AHNIEvent.Type.EVALUATION_START) {
			maxNeuronCount = 0;
			maxSynapseCount = 0;
			avgNeuronCount = 0;
			avgSynapseCount = 0;
			noPathFromInputToOutputCount = 0;
			popSize = 0;
		}
		else if (event.getType() == AHNIEvent.Type.EVALUATION_END) {
			avgNeuronCount /= popSize;
			avgSynapseCount /= popSize;
			logger.info("Network size (average / maximum) (neurons, synapses): " + avgNeuronCount + ", " + avgSynapseCount + " / " + maxNeuronCount + ", " + maxSynapseCount + ".   " + (noPathFromInputToOutputCount > 0 ? (noPathFromInputToOutputCount + " networks have no path from the input layer to the output layer.") : ""));
		}
	}
	
	private static String repeat(String s, int n) {
	    if(s == null) {
	        return null;
	    }
	    final StringBuilder sb = new StringBuilder();
	    for(int i = 0; i < n; i++) {
	        sb.append(s);
	    }
	    return sb.toString();
	}
	
	private static String colString(Collection c) {
		StringBuilder s = new StringBuilder();
		for (Object o : c) {
			s.append("\n\t\t" + o);
		}
		return s.toString();
	}
}
