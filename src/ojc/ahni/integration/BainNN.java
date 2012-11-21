package ojc.ahni.integration;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.anji.integration.Activator;
import com.anji.integration.TranscriberException;

import ojc.ahni.hyperneat.ESHyperNEATTranscriberBain;
import ojc.bain.NeuralNetwork;
import ojc.bain.base.ComponentConfiguration;
import ojc.bain.base.NeuronCollection;
import ojc.bain.base.SynapseCollection;
import ojc.bain.neuron.rate.NeuronCollectionWithBias;

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
public class BainNN implements Activator {
	private final static Logger logger = Logger.getLogger(BainNN.class);

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
	
	private static final int X = 0, Y = 1, Z = 2;

	private NeuralNetwork nn;
	private double[] nnOutputs; // We keep a local reference to this so the Bain neural network doesn't go unnecessarily
								// fetching input values from a GPU.
	private int stepsPerStep;
	private Topology topology;
	private String name;
	private int[] inputDimensions;
	private int[] outputDimensions;
	private int inputSize, outputIndex, outputSize;
	private int neuronCount, synapseCount;
	private int maxCycleLength;

	private double[][] coords;
	private double[] coordsMin, coordsMax, coordsRange; 

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
		return result;
	}

	@Override
	public double[][] next(double[][] stimuli) {
		return arrayUnpack(next(arrayPack(stimuli)), outputDimensions[0], outputDimensions[1], 0);
	}

	@Override
	public double[][][] nextSequence(double[][][] stimuli) {
		int stimuliCount = stimuli.length;
		double[][][] result = new double[stimuliCount][][];

		// Optmisation for layered FF networks.
		if (topology == Topology.FEED_FORWARD_LAYERED) {
			for (int stimuliIndex = 0, responseIndex = 1 - stepsPerStep; stimuliIndex < stimuliCount + stepsPerStep - 1; stimuliIndex++, responseIndex++) {
				if (stimuliIndex < stimuliCount) {
					double[] input = arrayPack(stimuli[stimuliIndex]);
					System.arraycopy(input, 0, nnOutputs, 0, input.length);
				}
				nn.step();
				if (responseIndex >= 0) {
					result[responseIndex] = arrayUnpack(nnOutputs, outputDimensions[0], outputDimensions[1], outputIndex);
				}
			}
		} else {
			for (int s = 0; s < stimuliCount; s++) {
				result[s] = next(stimuli[s]);
			}
		}
		return result;
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

	private double[] arrayPack(double[][] unpacked) {
		int width = unpacked[0].length;
		int height = unpacked.length;
		double[] packed = new double[width * height];
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				packed[i++] = unpacked[y][x];
			}
		}
		return packed;
	}

	private double[][] arrayUnpack(double[] packed, int width, int height, int outputIndex) {
		double[][] unpacked = new double[height][width];
		int i = outputIndex;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				unpacked[y][x] = packed[i++];
			}
		}
		return unpacked;
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
	 * Enable storing coordinates for neurons.
	 */
	public void enableCoords() {
		if (coords == null) {
			coords = new double[neuronCount][3];
			coordsMin = new double[3];
			coordsMax = new double[3];
			coordsRange = new double[3];
		}
	}
	
	/**
	 * Returns true iff storing coordinates for neurons has been enabled.
	 */
	public boolean coordsEnabled() {
		return (coords != null);
	}

	/**
	 * Returns the x coordinate of the specified neuron.
	 */
	public double getXCoord(int neuronIndex) {
		return coords[neuronIndex][X];
	}

	/**
	 * Returns the y coordinate of the specified neuron.
	 */
	public double getYCoord(int neuronIndex) {
		return coords[neuronIndex][Y];
	}

	/**
	 * Returns the z coordinate of the specified neuron.
	 */
	public double getZCoord(int neuronIndex) {
		return coords[neuronIndex][Z];
	}

	/**
	 * Sets the coordinate of the specified neuron.
	 */
	public void setCoords(int neuronIndex, double x, double y) {
		coords[neuronIndex][X] = x;
		coords[neuronIndex][Y] = y;
		updateMinMax(coords[neuronIndex]);
	}

	/**
	 * Sets the coordinate of the specified neuron.
	 */
	public void setCoords(int neuronIndex, double x, double y, double z) {
		coords[neuronIndex][X] = x;
		coords[neuronIndex][Y] = y;
		coords[neuronIndex][Z] = z;
		updateMinMax(coords[neuronIndex]);
	}
	
	private void updateMinMax(double[] c) {
		for (int d = 0; d < c.length; d++) {
			if (c[d] < coordsMin[d]) coordsMin[d] = c[d]; else if (c[d] > coordsMax[d]) coordsMax[d] = c[d];
			coordsRange[d] = coordsMax[d] - coordsMin[d];
		}		
	}
	
	/**
	 * Returns a string describing this network and its connectivity.
	 */
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder(125 + synapseCount * 30);
		out.append("Neuron class: " + nn.getNeurons().getClass());
		out.append("\nSynapse class: " + nn.getSynapses().getClass());
		out.append("\nNeuron count: " + neuronCount);
		out.append("\nSynapse count: " + synapseCount);
		out.append("\nTopology type: " + topology);
		out.append("\nCycles per step: " + stepsPerStep);
		out.append("\nConnectivity:");
		for (int i = 0; i < synapseCount; i++) {
			int pre = nn.getSynapses().getPreNeuron(i);
			int post = nn.getSynapses().getPostNeuron(i);
			String preType = isInput(pre) ? "i" : isOutput(pre) ? "o" : "h";
			String postType = isInput(post) ? "i" : isOutput(post) ? "o" : "h";
			out.append("\n\t" + preType + ":" + pre + " > " + postType + ":" + post + "  w: " + (float) nn.getSynapses().getEfficacy(i));
		}
		if (nn.getNeurons() instanceof NeuronCollectionWithBias) {
			out.append("\nNeuron bias values:");
			NeuronCollectionWithBias<? extends ComponentConfiguration> neurons = (NeuronCollectionWithBias) nn.getNeurons();
			for (int i = 0; i < neuronCount; i++) {
				out.append("\n\t" + (float) neurons.getBias(i));
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
	public void draw(Graphics2D g, int width, int height, int nodeSize) {
		width -= nodeSize*2;
		height -= nodeSize*2;
		float[] dashes = new float[]{10, 10}; // For dashed lines.
		
		// Create Point2D for each neuron, scaled to pixel location in image.
		Point2D.Double[] nodes = new Point2D.Double[neuronCount];
		g.setPaint(Color.GREEN);
		for (int i = 0; i < neuronCount; i++) {
			nodes[i] = new Point2D.Double(scaleCoord(coords[i][X], X, width)+nodeSize, scaleCoord(coords[i][Y], Y, height)+nodeSize);
		}
		
		// Create Line2D for each synapse, scaled to pixel location in image.
		SynapseCollection<? extends ComponentConfiguration> synapses = nn.getSynapses();
		Line2D.Double[] lines = new Line2D.Double[synapseCount];
		g.setPaint(Color.GREEN);
		for (int i = 0; i < synapseCount; i++) {
			lines[i] = new Line2D.Double(
					scaleCoord(coords[synapses.getPreNeuron(i)][X], X, width)+nodeSize,
					scaleCoord(coords[synapses.getPreNeuron(i)][Y], Y, height)+nodeSize,
					scaleCoord(coords[synapses.getPostNeuron(i)][X], X, width)+nodeSize,
					scaleCoord(coords[synapses.getPostNeuron(i)][Y], Y, height)+nodeSize);
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
			NeuronCollectionWithBias<? extends ComponentConfiguration> neurons = (NeuronCollectionWithBias) nn.getNeurons();
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
	}
	// Scale the coordinate to the range [0, 1].
	private int scaleCoord(double c, int d, int scale) {
		return (int) Math.round(((c - coordsMin[d]) / coordsRange[d]) * scale);
	}

	@Override
	public int getInputCount() {
		return inputSize;
	}

	@Override
	public int getOutputCount() {
		return outputSize;
	}
}
