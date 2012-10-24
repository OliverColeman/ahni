package ojc.ahni.integration;

import com.anji.integration.Activator;

import ojc.bain.NeuralNetwork;

/**
 * <p>Provides an interface to <a href="https://github.com/OliverColeman/bain">Bain</a> neural networks to allow integration with the ANJI/AHNI framework.</p>
 * <p>Bain neural networks are not well suited to non-layered feed-forward networks as every neuron and synapse must be activated every simulation step.</p>
 */
public class BainNN implements Activator {
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
		 * The network is strictly feed-forward (no recurrent connections or cycles), but the longest and shortest paths between input and output neurons may not be equal.
		 */
		FEED_FORWARD_NONLAYERED
	}
	
	private NeuralNetwork nn;
	private double[] nnOutputs; // We keep a local reference to this so the Bain neural network doesn't go unnecessarily fetching input values from a GPU.
	private int stepsPerStep;
	private Topology topology;
	private String name;
	private int[] inputDimensions;
	private int[] outputDimensions;
	private int outputIndex, outputSize;
	
	/**
	 * Create a new BainNN with the given Bain neural network.
	 * @param nn The Bain neural network to use.
	 * @param inputDimensions The size of each dimension in the input, see {@link Activator#getInputDimension()}. At the moment only one or two dimensional input vectors are supported, so this array should only be of length one or two accordingly. Dimensions should be in the order x, y.
	 * @param outputDimensions The size of each dimension in the output, see {@link Activator#getOutputDimension()}. At the moment only one or two dimensional output vectors are supported, so this array should only be of length one or two accordingly. Dimensions should be in the order x, y.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to next(..) or nextSequence(..) methods. For feed-forward networks this should be equal to the number of layers.
	 * @param topology Specifies the network topology, see {@link #topology}. 
	 */
	public BainNN(NeuralNetwork nn, int[] inputDimensions, int[] outputDimensions, int stepsPerStep, Topology topology) {
		this.nn = nn;
		this.stepsPerStep = stepsPerStep;
		this.topology = topology;
		this.inputDimensions = inputDimensions;
		this.outputDimensions = outputDimensions;
		outputSize = 1;
		for (int i = 0; i < outputDimensions.length; i++) {
			outputSize *= outputDimensions[i];
		}
		outputIndex = nn.getNeurons().getSize() - outputSize;
		nnOutputs = nn.getNeurons().getOutputs();
	}
	
	/**
	 * Create a new BainNN with the given Bain neural network.
	 * @param nn The Bain neural network to use.
	 * @param inputDimensions The size of each dimension in the input, see {@link Activator#getInputDimension()}. At the moment only one or two dimensional input vectors are supported, so this array should only be of length one or two accordingly. Dimensions should be in the order x, y.
	 * @param outputDimensions The size of each dimension in the output, see {@link Activator#getOutputDimension()}. At the moment only one or two dimensional output vectors are supported, so this array should only be of length one or two accordingly. Dimensions should be in the order x, y.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to next(..) or nextSequence(..) methods. For feed-forward networks this should be equal to the number of layers.
	 * @param topology Specifies the network topology, see {@link #topology}. 
	 * @param name A name for this BainNN.
	 */
	public BainNN(NeuralNetwork nn, int[] inputDimensions, int[] outputDimensions, int stepsPerStep, Topology topology, String name) {
		this.nn = nn;
		this.stepsPerStep = stepsPerStep;
		this.topology = topology;
		this.inputDimensions = inputDimensions;
		this.outputDimensions = outputDimensions;
		this.name = name;
		outputSize = 1;
		for (int i = 0; i < outputDimensions.length; i++) {
			outputSize *= outputDimensions[i];
		}
		outputIndex = nn.getNeurons().getSize() - outputSize;

		nnOutputs = nn.getNeurons().getOutputs();
	}
	
	
	/**
	 * Returns the underlying Bain neural network.
	 */
	public NeuralNetwork getNeuralNetwork() {
		return nn;
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
			//System.out.println();
			for (int s = 0; s < stepsPerStep; s++) {
				if (stimuli != null) {
					System.arraycopy(stimuli, 0, nnOutputs, 0, stimuli.length);
					nn.getNeurons().setOutputsModified();
				}
				//System.out.println(Arrays.toString(nn.getNeurons().getOutputs()));
				nn.step();
			}
			System.arraycopy(stimuli, 0, nnOutputs, 0, stimuli.length);
			//System.out.println();
		}
		else {
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
		} 
		else {
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
		} 
		else {
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
		double[] packed = new double[width*height];
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
}
