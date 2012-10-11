package ojc.ahni.hyperneat;

import com.anji.integration.Activator;

import ojc.bain.NeuralNetwork;

/**
 * Provides an interface to <a href="https://github.com/OliverColeman/bain">Bain</a> neural networks to allow integration with the ANJI/AHNI framework.
 */
public class BainNN implements Activator {
	private NeuralNetwork nn;
	private double[] inputs; // We keep a local reference to this so the Bain neural network doesn't go unnecessarily fetching input values from a GPU.
	private int stepsPerStep;
	private String name;
	private int[] outputDimensions;
	private int outputIndex, outputSize;
	
	/**
	 * Create a new BainNN with the given Bain neural network.
	 * @param nn The Bain neural network to use.
	 * @param outputDimensions The size of each dimension in the output. At the moment only one or two dimensional output vectors are supported, so this array should only be of length one or two accordingly. Dimensions should be in the order x, y.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to next or nextSequence methods.
	 */
	public BainNN(NeuralNetwork nn, int[] outputDimensions, int stepsPerStep) {
		this.nn = nn;
		this.stepsPerStep = stepsPerStep;
		this.outputDimensions = outputDimensions;
		outputSize = 1;
		for (int i = 0; i < outputDimensions.length; i++) {
			outputSize *= outputDimensions[i];
		}
		outputIndex = nn.getNeurons().getSize() - outputSize;
		inputs = nn.getNeurons().getInputs();
	}
	
	/**
	 * Create a new BainNN with the given Bain neural network.
	 * @param nn The Bain neural network to use.
	 * @param outputDimensions The size of each dimension in the output. At the moment only one or two dimensional output vectors are supported, so this array should only be of length one or two accordingly. Dimensions should be in the order x, y.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to next or nextSequence methods.
	 * @param name A name for this BainNN.
	 */
	public BainNN(NeuralNetwork nn, int[] outputDimensions, int stepsPerStep, String name) {
		this.nn = nn;
		this.stepsPerStep = stepsPerStep;
		this.outputDimensions = outputDimensions;
		this.name = name;
		outputSize = 1;
		for (int i = 0; i < outputDimensions.length; i++) {
			outputSize *= outputDimensions[i];
		}
		outputIndex = nn.getNeurons().getSize() - outputSize;

		inputs = nn.getNeurons().getInputs();
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
		if (stimuli != null) {
			System.arraycopy(stimuli, 0, inputs, 0, Math.min(inputs.length, stimuli.length));
		}
		nn.run(stepsPerStep);
		double[] outputs = new double[outputSize];
		System.arraycopy(nn.getNeurons().getOutputs(), outputIndex, outputs, 0, outputSize);
		return outputs;
	}

	@Override
	public double[][] nextSequence(double[][] stimuli) {
		int stimCount = stimuli.length;
		double[][] result = new double[stimCount][];
		for (int s = 0; s < stimCount; s++) {
			result[s] = next(stimuli[s]);
		}
		return result;
	}

	@Override
	public double[][] next(double[][] stimuli) {
		return arrayUnpack(next(arrayPack(stimuli)), outputDimensions[0]);
	}

	@Override
	public double[][][] nextSequence(double[][][] stimuli) {
		int stimCount = stimuli.length;
		double[][][] result = new double[stimCount][][];
		for (int s = 0; s < stimCount; s++) {
			result[s] = next(stimuli[s]);
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] getOutputDimension() {
		// TODO Auto-generated method stub
		return null;
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
	
	private double[][] arrayUnpack(double[] packed, int width) {
		int height = packed.length / width;
		double[][] unpacked = new double[height][width];
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				unpacked[y][x] = packed[i++];
			}
		}
		return unpacked;
	}
}
