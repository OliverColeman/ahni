package com.ojcoleman.ahni.nn;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.log4j.Logger;

import com.anji.integration.Activator;
import com.anji.integration.TranscriberException;
import com.ojcoleman.ahni.util.ArrayUtil;

import com.ojcoleman.bain.NeuralNetwork;
import com.ojcoleman.bain.base.ComponentCollection;
import com.ojcoleman.bain.base.ComponentConfiguration;
import com.ojcoleman.bain.base.NeuronCollection;
import com.ojcoleman.bain.base.SynapseCollection;
import com.ojcoleman.bain.base.SynapseConfiguration;
import com.ojcoleman.bain.neuron.rate.NeuronCollectionWithBias;

/**
 * <p>
 * Provides an interface to <a href="https://github.com/OliverColeman/bain">Bain</a> neural networks to allow
 * integration with the ANJI/AHNI framework.
 * </p>
 * <p>
 * Bain neural networks are not well suited to feed-forward networks as every neuron and synapse must be
 * activated every simulation step. Bain is intended for recurrent networks, however the implementations of 
 * {@link com.anji.integration.Activator#nextSequence(double[][])} and {@link com.anji.integration.Activator#nextSequence(double[][])}
 * are optimised to provide amortised performance over the number of input sequences for layered feed-forward networks.
 * </p>
 */
public class BainNN extends NNAdaptor {
	private final static Logger logger = Logger.getLogger(BainNN.class);
	
	public static final String SUBSTRATE_EXECUTION_MODE = "ann.transcriber.bain.executionmode";
	public static final String SUBSTRATE_SIMULATION_RESOLUTION = "ann.transcriber.bain.resolution";

	
	/**
	 * Describes the basic topology of a network.
	 */
	public enum Topology {
		/**
		 * The network contains recurrent connections or cycles.
		 */
		RECURRENT,
		/**
		 * The network is strictly feed-forward (no recurrent connections or cycles), and is arranged in layers.
		 */
		FEED_FORWARD_LAYERED,
		/**
		 * The network is strictly feed-forward (no recurrent connections or cycles), but the longest and shortest paths
		 * between input and output neurons may not be equal.
		 */
		FEED_FORWARD_NONLAYERED
	}
	
	private NeuralNetwork nn;
	private double[] nnOutputs; // We keep a local reference to this so the Bain neural network doesn't go unnecessarily
								// fetching input values from a GPU.
	private int stepsPerStep;
	private Topology topology;
	private String name;
	private int[] inputDimensions;
	private int[] outputDimensions;
	private int inputSize, outputIndex, outputSize;
	int neuronCount;

	private int synapseCount;
	private int maxCycleLength;

	private static boolean reportedExecutionModeProblem = false;

	/**
	 * Create a new BainNN with the given Bain neural network.
	 * 
	 * @param nn The Bain neural network to use.
	 * @param inputDimensions The size of each dimension in the input, see {@link Activator#getInputDimension()}. At the
	 *            moment only one or two dimensional input vectors are supported, so this array should only be of length
	 *            one or two accordingly. Dimensions should be in the order x, y.
	 * @param outputDimensions The size of each dimension in the output, see {@link Activator#getOutputDimension()}. At
	 *            the moment only one or two dimensional output vectors are supported, so this array should only be of
	 *            length one or two accordingly. Dimensions should be in the order x, y.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to
	 *            next(..) or nextSequence(..) methods. For feed-forward networks this should be equal to the number of
	 *            layers. <strong>For non-layered feed forward networks this value will be calculated automatically, the
	 *            supplied value will be ignored.</strong>
	 * @param topology Specifies the network topology, see {@link #topology}.
	 * @throws Exception
	 */
	public BainNN(NeuralNetwork nn, int[] inputDimensions, int[] outputDimensions, int stepsPerStep, Topology topology) throws Exception {
		init(nn, inputDimensions, outputDimensions, stepsPerStep, topology, null, 1000);
	}

	/**
	 * Create a new BainNN with the given Bain neural network.
	 * 
	 * @param nn The Bain neural network to use.
	 * @param inputDimensions The size of each dimension in the input, see {@link Activator#getInputDimension()}. At the
	 *            moment only one or two dimensional input vectors are supported, so this array should only be of length
	 *            one or two accordingly. Dimensions should be in the order x, y.
	 * @param outputDimensions The size of each dimension in the output, see {@link Activator#getOutputDimension()}. At
	 *            the moment only one or two dimensional output vectors are supported, so this array should only be of
	 *            length one or two accordingly. Dimensions should be in the order x, y.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to
	 *            next(..) or nextSequence(..) methods. For feed-forward networks this should be equal to the number of
	 *            layers. <strong>For non-layered feed forward networks this value will be calculated automatically, the
	 *            supplied value will be ignored.</strong>
	 * @param topology Specifies the network topology, see {@link #topology}.
	 * @param name A name for this BainNN.
	 * @param maxCycleLength When determining if this network is recurrent, the maximum cycle length to search for before the network is considered recurrent. Note that this is set to the smaller of the given value and number of neurons in the network.
	 * @throws Exception
	 */
	public BainNN(NeuralNetwork nn, int[] inputDimensions, int[] outputDimensions, int stepsPerStep, Topology topology, String name, int maxCycleLength) throws Exception {
		init(nn, inputDimensions, outputDimensions, stepsPerStep, topology, name, maxCycleLength);
	}

	private void init(NeuralNetwork nn, int[] inputDimensions, int[] outputDimensions, int stepsPerStep, Topology topology, String name, int maxCycleLength) throws Exception {
		this.nn = nn;
		this.stepsPerStep = stepsPerStep;
		this.topology = topology;
		this.inputDimensions = inputDimensions;
		this.outputDimensions = outputDimensions;
		this.name = name;
		this.neuronCount = nn.getNeurons().getSize();
		this.synapseCount = nn.getSynapses().getSize();;
		this.maxCycleLength = Math.min(neuronCount, maxCycleLength);
		inputSize = 1;
		for (int i = 0; i < inputDimensions.length; i++) {
			inputSize *= inputDimensions[i];
		}
		outputSize = 1;
		for (int i = 0; i < outputDimensions.length; i++) {
			outputSize *= outputDimensions[i];
		}
		outputIndex = neuronCount - outputSize;
		nnOutputs = nn.getNeurons().getOutputs();
		if (topology == Topology.FEED_FORWARD_NONLAYERED) {
			setStepsPerStepForNonLayeredFF();
		}
	}

	/**
	 * Returns the underlying Bain neural network.
	 */
	public NeuralNetwork getNeuralNetwork() {
		return nn;
	}
	

	public BainNN.Topology getTopology() {
		return topology;
	}


	@Override
	public Object next() {
		return next((double[]) null);
	}

	@Override
	public double[] next(double[] stimuli) {
		if (topology == Topology.FEED_FORWARD_NONLAYERED) {
			// For non-layered FF networks we have to run the network stepsPerStep times to propagate the
			// signals all the way through, while making sure the input neurons have the stimuli values
			// maintained each step.
			for (int s = 0; s < stepsPerStep; s++) {
				if (stimuli != null) {
					System.arraycopy(stimuli, 0, nnOutputs, 0, stimuli.length);
					nn.getNeurons().setOutputsModified();
				}
				nn.step();
			}
			System.arraycopy(stimuli, 0, nnOutputs, 0, stimuli.length);
		} else {
			if (stimuli != null) {
				System.arraycopy(stimuli, 0, nnOutputs, 0, stimuli.length);
				nn.getNeurons().setOutputsModified();
			}
			nn.run(stepsPerStep);
		}
		double[] outputs = new double[outputSize];
		System.arraycopy(nn.getNeurons().getOutputs(), outputIndex, outputs, 0, outputSize);
		checkExecMode();
		return outputs;
	}

	@Override
	public double[][] nextSequence(double[][] stimuli) {
		int stimuliCount = stimuli.length;
		double[][] result = new double[stimuliCount][outputSize];
		// Optmisation for layered FF networks.

		if (topology == Topology.FEED_FORWARD_LAYERED) {
			for (int stimuliIndex = 0, responseIndex = 1 - stepsPerStep; stimuliIndex < stimuliCount + stepsPerStep - 1; stimuliIndex++, responseIndex++) {
				if (stimuliIndex < stimuliCount) {
					System.arraycopy(stimuli[stimuliIndex], 0, nnOutputs, 0, stimuli[stimuliIndex].length);
					nn.getNeurons().setOutputsModified();
				}
				nn.step();
				if (responseIndex >= 0) {
					System.arraycopy(nnOutputs, outputIndex, result[responseIndex], 0, outputSize);
				}
			}
		} else {
			for (int s = 0; s < stimuliCount; s++) {
				result[s] = next(stimuli[s]);
			}
		}
		checkExecMode();
		return result;
	}

	@Override
	public double[][] next(double[][] stimuli) {
		return ArrayUtil.unpack(next(ArrayUtil.pack(stimuli)), outputDimensions[0], outputDimensions[1], 0);
	}

	@Override
	public double[][][] nextSequence(double[][][] stimuli) {
		int stimuliCount = stimuli.length;
		double[][][] result = new double[stimuliCount][][];

		// Optmisation for layered FF networks.
		if (topology == Topology.FEED_FORWARD_LAYERED) {
			for (int stimuliIndex = 0, responseIndex = 1 - stepsPerStep; stimuliIndex < stimuliCount + stepsPerStep - 1; stimuliIndex++, responseIndex++) {
				if (stimuliIndex < stimuliCount) {
					double[] input = ArrayUtil.pack(stimuli[stimuliIndex]);
					System.arraycopy(input, 0, nnOutputs, 0, input.length);
				}
				nn.step();
				if (responseIndex >= 0) {
					result[responseIndex] = ArrayUtil.unpack(nnOutputs, outputDimensions[0], outputDimensions[1], outputIndex);
				}
			}
		} else {
			for (int s = 0; s < stimuliCount; s++) {
				result[s] = next(stimuli[s]);
			}
		}
		checkExecMode();
		return result;
	}
	
	private void checkExecMode() {
		if (!reportedExecutionModeProblem && nn.getPreferredExecutionMode() != null && nn.getPreferredExecutionMode() != nn.getSynapses().getExecutionMode()) {
			logger.warn("Preferred execution mode for Bain network unable to be used.");
		}
	}

	@Override
	public void reset() {
		nn.reset();
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String newName) {
		name = newName;
	}

	@Override
	public double getMinResponse() {
		return nn.getNeurons().getMinimumPossibleOutputValue();
	}

	@Override
	public double getMaxResponse() {
		return nn.getNeurons().getMaximumPossibleOutputValue();
	}

	@Override
	public int[] getInputDimension() {
		return inputDimensions;
	}

	@Override
	public int[] getOutputDimension() {
		return outputDimensions;
	}

	@Override
	public String getXmlRootTag() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getXmld() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String toXml() {
		// TODO Auto-generated method stub
		return null;
	}

	private void setStepsPerStepForNonLayeredFF() {
		// For each neuron store the list of neurons which have connections to it.
		ArrayList<ArrayList<Integer>> neuronSourceIDs = new ArrayList<ArrayList<Integer>>();
		for (int id = 0; id < neuronCount; id++) {
			neuronSourceIDs.add(new ArrayList<Integer>());
		}
		SynapseCollection<? extends ComponentConfiguration> synapses = nn.getSynapses();
		for (int c = 0; c < synapses.getSize(); c++) {
			if (neuronSourceIDs.get(synapses.getPostNeuron(c)) == null) {
				logger.error("neuronSourceIDs.get(synapses.getPostNeuron(c)) == null");
				logger.error("synapses.getPostNeuron(c) = " + synapses.getPostNeuron(c));
				logger.error("neuronCount = " + neuronCount);
			}
			neuronSourceIDs.get(synapses.getPostNeuron(c)).add(synapses.getPreNeuron(c));
		}

		// Start at output neurons, iterate through network finding source neurons until we reach dead ends or find a cycle.
		int maxDepth = 0;
		boolean[] visited = new boolean[neuronCount];
		boolean cyclic = false;
		ArrayList<Integer> current = new ArrayList<Integer>();
		ArrayList<Integer> next = new ArrayList<Integer>();
		for (int id = outputIndex; id < neuronCount && !cyclic; id++) {
			int depth = 0;
			Arrays.fill(visited, false);
			current.clear();
			
			current.add(id);
			visited[id] = true;
			while (!current.isEmpty() && depth < maxCycleLength && !cyclic) {
				next.clear();
				// Get the neurons in the next "layer".
				for (Integer c : current) {
					for (Integer source : neuronSourceIDs.get(c)) {
						if (visited[source]) {
							cyclic = true;
							break;
						}
						else {
							next.add(source);
							visited[source] = true;
						}
					}
					if (cyclic) break;
				}
				ArrayList<Integer> temp = current; 
				current = next;
				next = temp;
				depth++;
			}
			if (depth > maxDepth) maxDepth = depth;
		}
		
		if (!cyclic && maxDepth < maxCycleLength) {
			stepsPerStep = maxDepth - 1;
		}
		else {
			logger.debug("Error determining depth of non-layered feed forward Bain network, stopping at apparent depth of " + maxCycleLength + ", perhaps the network contains cycles? Switching to recurrent topology mode with " + stepsPerStep + " activation cycles per step.");
			this.topology = Topology.RECURRENT;
		}
	}

	/**
	 * Returns a string describing this network and its connectivity.
	 */
	@Override
	public String toString() {
		DecimalFormat nf = new DecimalFormat(" 0.00;-0.00");
		StringBuilder out = new StringBuilder(125 + synapseCount * 30);
		out.append("Neuron class: " + nn.getNeurons().getClass());
		out.append("\nSynapse class: " + nn.getSynapses().getClass());
		out.append("\nNeuron count: " + neuronCount + "  Populated size: " + nn.getNeurons().getSizePopulated());
		out.append("\nSynapse count: " + synapseCount + "  Populated size: " + nn.getSynapses().getSizePopulated());
		out.append("\nTopology type: " + topology);
		out.append("\nCycles per step: " + stepsPerStep);
		
		out.append("\nNeurons:\n\t");
		NeuronCollectionWithBias biasNeurons = (nn.getNeurons() instanceof NeuronCollectionWithBias) ? (NeuronCollectionWithBias) nn.getNeurons() : null;
		if (coordsEnabled()) {
			out.append("Coordinates\t\t");
		}
		if (biasNeurons != null) {
		  out.append("bias\t");
		}
		String[] paramNames = nn.getNeurons().getConfigSingleton() != null ? nn.getNeurons().getConfigSingleton().getParameterNames() : null;
		if (paramNames != null) {
			for (int p = 0; p < paramNames.length; p++) {
				out.append(paramNames[p].substring(0, Math.min(6, paramNames[p].length())) + "\t");
			}
		}
		for (int i = 0; i < neuronCount; i++) {
			out.append("\n");
			if (coordsEnabled()) {
				out.append("\t(" + nf.format(getXCoord(i)) + ", " + nf.format(getYCoord(i)) + ", " + nf.format(getZCoord(i)) + ")");
			}
			if (biasNeurons != null) {
				out.append("\t"+nf.format(biasNeurons.getBias(i)));
			}
			if (paramNames != null && nn.getNeurons().getComponentConfiguration(i) != null) {
				out.append("\t" + ArrayUtil.toString(nn.getNeurons().getComponentConfiguration(i).getParameterValues(), "\t", nf));
			}
		}
		
		out.append("\nSynapses:");
		out.append("\n\tpre > post\tweight");
		paramNames = nn.getSynapses().getConfigSingleton() != null ? nn.getSynapses().getConfigSingleton().getParameterNames() : null;
		if (paramNames != null) {
			out.append("\t");
			for (int p = 0; p < paramNames.length; p++) {
				out.append(paramNames[p].substring(0, Math.min(6, paramNames[p].length())) + "\t");
			}
		}
		for (int i = 0; i < synapseCount; i++) {
			int pre = nn.getSynapses().getPreNeuron(i);
			int post = nn.getSynapses().getPostNeuron(i);
			String preType = isInput(pre) ? "i" : isOutput(pre) ? "o" : "h";
			String postType = isInput(post) ? "i" : isOutput(post) ? "o" : "h";
			out.append("\n\t" + preType + ":" + pre + " > " + postType + ":" + post + "\t" + nf.format(nn.getSynapses().getEfficacy(i)));
			if (paramNames != null && nn.getSynapses().getComponentConfiguration(i) != null) {
				out.append("\t" + ArrayUtil.toString(nn.getSynapses().getComponentConfiguration(i).getParameterValues(), "\t", nf));
			}
		}
		
		return out.toString();
	}
	
	public boolean isInput(int neuronIndex) {
		return neuronIndex < inputSize;
	}
	
	public boolean isOutput(int neuronIndex) {
		return neuronIndex >= outputIndex;
	}

	/**
	 * Renders this network as an image.
	 */
	@Override
	public boolean render(Graphics2D g, int width, int height, int nodeSize) {
		if (!coordsEnabled()) return false;
		
		width -= nodeSize*2;
		height -= nodeSize*2;
		float[] dashes = new float[]{10, 10}; // For dashed lines.
		
		// Create Point2D for each neuron, scaled to pixel location in image.
		Point2D.Double[] nodes = new Point2D.Double[neuronCount];
		g.setPaint(Color.GREEN);
		for (int i = 0; i < neuronCount; i++) {
			nodes[i] = new Point2D.Double(scaleXCoord(coords[i].x, width)+nodeSize, scaleYCoord(coords[i].y, height)+nodeSize);
		}
		
		// Create Line2D for each synapse, scaled to pixel location in image.
		SynapseCollection<? extends ComponentConfiguration> synapses = nn.getSynapses();
		Line2D.Double[] lines = new Line2D.Double[synapseCount];
		g.setPaint(Color.GREEN);
		for (int i = 0; i < synapseCount; i++) {
			lines[i] = new Line2D.Double(
					scaleXCoord(coords[synapses.getPreNeuron(i)].x, width)+nodeSize,
					scaleYCoord(coords[synapses.getPreNeuron(i)].y, height)+nodeSize,
					scaleXCoord(coords[synapses.getPostNeuron(i)].x, width)+nodeSize,
					scaleYCoord(coords[synapses.getPostNeuron(i)].y, height)+nodeSize);
		}
		
		// Determine min and max weights, and lines that pass through other nodes.
		double maxWeight = -Double.MAX_VALUE, minWeight = Double.MAX_VALUE;
		boolean[] overlaps = new boolean[synapseCount];
		for (int i = 0; i < synapseCount; i++) {
			maxWeight = Math.max(maxWeight, synapses.getEfficacy(i));
			minWeight = Math.min(minWeight, synapses.getEfficacy(i));
			for (int j = 0; j < neuronCount; j++) {
				if (j != synapses.getPreNeuron(i) && j != synapses.getPostNeuron(i) && lines[i].ptSegDist(nodes[j]) <= nodeSize/2d) {
					overlaps[i] = true;
					break;
				}
			}
		}
		double maxWeightAbs = Math.max(maxWeight, Math.abs(minWeight));
		Arc2D.Double arc = new Arc2D.Double(Arc2D.OPEN);
		Line2D.Double l = new Line2D.Double();
		for (int i = 0; i < synapseCount; i++) {
			float w = (float) (synapses.getEfficacy(i) / maxWeightAbs);
			if (w >= 0)
				g.setStroke(new BasicStroke(w * nodeSize * 0.25f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10));
			else
				g.setStroke(new BasicStroke(-w * nodeSize * 0.25f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashes, 0));
			
			Line2D.Double line = lines[i];
			Point2D p1 = line.getP1();
			Point2D p2 = line.getP2();
			int x1 = (int) p1.getX();
			int y1 = (int) p1.getY();
			int x2 = (int) p2.getX();
			int y2 = (int) p2.getY();
			
			// If the connection isn't recurrent.
			if (synapses.getPreNeuron(i) != synapses.getPostNeuron(i)) {
				double dist = p1.distance(p2);
				double dx = (x2-x1) / dist;
				double dy = (y2-y1) / dist;
				
				// Draw an arc instead of a straight line if this line passes through other nodes.
				if (overlaps[i]) {
					double dev = nodeSize; // How much the arc deviates from the straight line between the neuron centres, in pixels.
					double radius = (((dist*dist)/4) + (dev*dev)) / (2*dev); // Arc radius.
					// Arc centre.
					double cx = ((x1+x2) / 2d) + (radius-dev) * dy;
					double cy = ((y1+y2) / 2d) - (radius-dev) * dx;
					arc.setArcByCenter(cx, cy, radius, 0, 180, Arc2D.OPEN);
					arc.setAngles(p1, p2);
					double arcExtent = arc.getAngleExtent();
					double endArcExtent = Math.toDegrees(nodeSize/radius)*1.5;
					arc.setAngleExtent(arcExtent - endArcExtent);
					g.setPaint(Color.WHITE);
					g.draw(arc);
					arc.setAngleStart(arc.getAngleStart() + arcExtent - endArcExtent);
					arc.setAngleExtent(endArcExtent);
					g.setPaint(Color.GRAY);
					g.draw(arc);
				}
				else {
					double startLength = Math.max(dist*0.66667, dist - nodeSize*1.5);
					double xm = x1 + dx*startLength;
					double ym = y1 + dy*startLength;
					l.setLine(x1, y1, xm, ym);
					g.setPaint(Color.WHITE);
					g.draw(l);
					l.setLine(xm, ym, x2, y2);
					g.setPaint(Color.GRAY);
					g.draw(l);
				}
			}
			else {
				// Recurrent connection.
				g.drawOval(x1, y1, nodeSize*2, nodeSize*2);
			}
		}
		
		// Neurons.
		int neuronIndex = 0;
		g.setPaint(Color.GREEN);
		for (int i = 0; i < inputSize; i++, neuronIndex++) {
			g.fillOval((int) nodes[neuronIndex].getX() - nodeSize/2, (int) nodes[neuronIndex].getY() - nodeSize/2, nodeSize, nodeSize);
		}
		g.setPaint(Color.ORANGE);
		for (int i = 0; i < neuronCount - (inputSize + outputSize); i++, neuronIndex++) {
			g.fillOval((int) nodes[neuronIndex].getX() - nodeSize/2, (int) nodes[neuronIndex].getY() - nodeSize/2, nodeSize, nodeSize); 
		}
		g.setPaint(Color.CYAN);
		for (int i = 0; i < outputSize; i++, neuronIndex++) {
			g.fillOval((int) nodes[neuronIndex].getX() - nodeSize/2, (int) nodes[neuronIndex].getY() - nodeSize/2, nodeSize, nodeSize); 
		}
		if (nn.getNeurons() instanceof NeuronCollectionWithBias) {
			NeuronCollectionWithBias neurons = (NeuronCollectionWithBias) nn.getNeurons();
			g.setPaint(Color.WHITE);
			for (neuronIndex = 0; neuronIndex < neuronCount; neuronIndex++) {
				float w = (float) neurons.getBias(neuronIndex);
				if (w >= 0)
					g.setStroke(new BasicStroke(w * nodeSize * 0.25f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10));
				else
					g.setStroke(new BasicStroke(-w * nodeSize * 0.25f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashes, 0));
				int size = (int) Math.round((w / maxWeightAbs) * nodeSize * 0.9);
				g.drawOval((int) nodes[neuronIndex].getX() - size/2, (int) nodes[neuronIndex].getY() - size/2, size, size);
			}
		}
		return true;
	}
	// Scale the coordinate to the range [0, 1].
	private int scaleXCoord(double c, int scale) {
		return (int) Math.round(((c - coordsMin.x) / coordsRange.x) * scale);
	}
	// Scale the coordinate to the range [0, 1].
	private int scaleYCoord(double c, int scale) {
		return (int) Math.round(((c - coordsMin.y) / coordsRange.y) * scale);
	}

	@Override
	public int getInputCount() {
		return inputSize;
	}

	@Override
	public int getOutputCount() {
		return outputSize;
	}

	@Override
	public int getNeuronCount() {
		return neuronCount;
	}

	@Override
	public void dispose() {
		nn.dispose();
	}
	
	
	public static NeuronCollection createNeuronCollection(String modelClass, int size, boolean enableBias, boolean typesEnabled, boolean paramsEnabled) throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException {
		NeuronCollection neurons = (NeuronCollection) ComponentCollection.createCollection(modelClass, size);
		if (enableBias && !(neurons instanceof NeuronCollectionWithBias)) {
			throw new IllegalArgumentException("Error creating Bain neural network: bias for neurons is enabled but the specified neuron class does not support bias (it does not extend NeuronCollectionWithBias).");
		}
		// If we're not setting neuron model parameters and the neuron collection is configurable and the configuration has a default preset.
		if (!(paramsEnabled || typesEnabled) && neurons.getConfigSingleton() != null && neurons.getConfigSingleton().getPreset(0) != null) {
			neurons.addConfiguration(neurons.getConfigSingleton().getPreset(0));
		}
		return neurons;
	}

	public static SynapseCollection createSynapseCollection(String modelClass, int size, boolean typesEnabled, boolean paramsEnabled, double minWeight, double maxWeight) throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException {
		SynapseCollection synapses = (SynapseCollection) ComponentCollection.createCollection(modelClass, size);
		// If we're not setting synapse model parameters and the synapse collection is configurable and the configuration has a default preset.
		if (!(paramsEnabled || typesEnabled) && synapses.getConfigSingleton() != null && synapses.getConfigSingleton().getPreset(0) != null) {
			synapses.addConfiguration(synapses.getConfigSingleton().getPreset(0));
			((SynapseConfiguration) synapses.getConfiguration(0)).minimumEfficacy = minWeight;
			((SynapseConfiguration) synapses.getConfiguration(0)).maximumEfficacy = maxWeight;
		}
		return synapses;
	}
}
