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
	
	/**
	 * Create a new BainNN with the given Bain neural network.
	 * @param nn The Bain neural network to use.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to next or nextSequence methods.
	 */
	public BainNN(NeuralNetwork nn, int stepsPerStep) {
		this.nn = nn;
		this.stepsPerStep = stepsPerStep;
		inputs = nn.getNeurons().getInputs();
	}
	
	/**
	 * Create a new BainNN with the given Bain neural network.
	 * @param nn The Bain neural network to use.
	 * @param stepsPerStep The number of simulation steps to perform in the Bain neural network for each call to next or nextSequence methods.
	 * @param name A name for this BainNN.
	 */
	public BainNN(NeuralNetwork nn, int stepsPerStep, String name) {
		this.nn = nn;
		this.stepsPerStep = stepsPerStep;
		this.name = name;
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
		nn.run(stepsPerStep);
		return nn.getNeurons().getOutputs();
	}

	@Override
	public double[] next(double[] stimuli) {
		System.arraycopy(stimuli, 0, inputs, 0, Math.min(inputs.length, stimuli.length));
		nn.run(stepsPerStep);
		return nn.getNeurons().getOutputs();
	}

	@Override
	public double[][] nextSequence(double[][] stimuli) {
		int steps = stimuli.length;
		double[][] result = new double[steps][];
		for (int s = 0; s < steps; s++) {
			double[] output = next(stimuli[s]);
			result[s] = new double[output.length];
			System.arraycopy(output, 0, result[s], 0, output.length);
		}
		return result;
	}

	@Override
	public double[][] next(double[][] stimuli) {
		return arrayUnpack(next(arrayPack(stimuli)), stimuli.length);
	}

	@Override
	public double[][][] nextSequence(double[][][] stimuli) {
		int steps = stimuli.length;
		double[][][] result = new double[steps][][];
		for (int s = 0; s < steps; s++) {
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
		int width = unpacked.length;
		int height = unpacked[0].length;
		double[] packed = new double[width*height];
		int i = 0;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				packed[i++] = unpacked[x][y];
			}
		}
		return packed;
	}
	
	private double[][] arrayUnpack(double[] packed, int width) {
		int height = packed.length / width;
		double[][] unpacked = new double[width][height];
		int i = 0;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				unpacked[x][y] = packed[i++];
			}
		}
		return unpacked;
	}
}
