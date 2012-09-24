/**
 * Implements a layer or grid-type neural network.
 *
 * The width (tx-axis), height (ty-axis) and depth (tz-axis) are defined and fixed
 * upon construction. Inputs can be applied to the "top" layer (tz=0) and the
 * bottom layer (tz=depth-1) is the output.
 *
 * The distance connections extend in any direction (axis) is configurable.
 * Anything from "Standard" feed-forward networks to fully recurrent networks
 * can be created.
 *
 * This implementation is designed to be fast and minimise memory consumption.
 *
 * @author oliver
 */

package com.anji.hyperneat;

import com.anji.integration.Activator;
import com.anji.nn.activationfunction.ActivationFunction;
import com.anji.nn.activationfunction.ActivationFunctionFactory;

public class GridNet implements Activator {

	/**
	 * base XML tag
	 */
	public final static String XML_TAG = "grid network";

	private ActivationFunction activationFunction;

	private boolean isFeedForward;

	// The maximum length/range of connections in either direction in the tx, ty and tz axes.
	private int[][][] connectionMaxRanges; // [tz=0|ty=1|tx=2][negative=0|positive=1] = max range
	private int[][] connectionTotalRanges; // [tz=0|ty=1|tx=2] = negative + positive max range

	// connections weights, [z1][y1][x1][zR][yR][xR], where xR, yR, and zR are relative to x1, y1 and z1,
	// and x1,y1,z1 is the target and xR, yR, and zR is the source, with the corresponding negative
	// extents from connectionMaxRanges added to avoid negative array indices.
	private double[][][][][][] weights; // z1,y1,x1,z2,y2,x2

	private double[][][] bias; // z,y,x

	private int depth;
	private int[] width; // dimensions for each layer
	private int[] height;

	private double[][][] activation; // tz,ty,tx
	private double[][][] activationNew; // tz,ty,tx

	private String name;

	private int cyclesPerStep; // Number of activation cycles to perform per step()

	/**
	 * Creates a GridNet with the given specifications.
	 * 
	 * @param connectionMaxRanges The maximum length/range of connections in either direction in the x, y and z axes. The array has format
	 *            [z=0|y=1|x=2],[negative=0|positive=1] = max range. For example a standard feed-forward network with width W (x-axis), height H (y-axis) and
	 *            depth D (z-axis) would be described: {{-1, 1}, {H, H}, {W, W}}, where -1 for the negative direction for the z-axis indicates that connections
	 *            may not go from layer n to layer &lt; n.
	 * @param weights The connection weights of the network between each neuron, where neurons are specfied by their coordinates, in the format
	 *            [z1][y1][x1][zR][yR][xR], where xR, yR, and zR are relative to x1, y1 and z1, and with the corresponding negative direction ranges from
	 *            connectionMaxRanges added to avoid negative array indices. x1, y1, z1 is the target and xR, yR, zR is the source. Note that the range of
	 *            connections for neurons at or close to the edge of the grid must be circumscribed by the edge of the grid, so the last three dimensions of the
	 *            array must have differing sizes to account for this (except in the unlikely scenario that connections extend only straight down); also note
	 *            that no connections may extend into the input layer so the connection matrices must also be circumscribed accordingly for this (thus the
	 *            number of layers in the network including the input layer is weights.length+1).
	 * @param bias the bias for each neuron not in the input layer, in the format [z][y][x] where z, y, and x are the coordinates of the neuron (as input
	 *            neurons don't receive a bias bias.length should match weights.length).
	 * @param function the ActivationFunction to use; only one type of activation function is used throughout the network.
	 * @param cyclesPerStep Number of activation cycles to perform per step(), must be >= 1.
	 * @param aName Name of the network.
	 */
	public GridNet(int[][][] connectionMaxRanges, int[][] layerDimensions, double[][][][][][] weights, double[][][] bias, ActivationFunction function, int cyclesPerStep, String aName) {
		depth = weights.length + 1;
		width = layerDimensions[0];
		height = layerDimensions[1];

		this.weights = weights;
		this.bias = bias;
		this.activationFunction = function;
		this.connectionMaxRanges = connectionMaxRanges;
		connectionTotalRanges = new int[depth - 1][connectionMaxRanges[0].length];
		for (int l = 0; l < depth - 1; l++) {
			for (int i = 0; i < connectionMaxRanges[l].length; i++) {
				connectionTotalRanges[l][i] = connectionMaxRanges[l][i][0] + connectionMaxRanges[l][i][1] + 1;
			}
		}
		activation = new double[depth][][];
		activationNew = new double[depth][][];
		for (int l = 0; l < depth; l++) {
			activation[l] = new double[height[l]][width[l]];
			activationNew[l] = new double[height[l]][width[l]];
		}

		this.cyclesPerStep = cyclesPerStep;
		name = aName;
		isFeedForward = (this.connectionMaxRanges[0][0][0] == -1 && this.connectionMaxRanges[0][0][1] == 1);
	}

	/**
	 * Creates a GridNet with the given specifications.
	 * 
	 * @param connectionMaxRange The maximum length/range of connections in either direction in the x, y and z axes.
	 * @param weights The connection weights of the network between each neuron, where neurons are specfied by their coordinates, in the format
	 *            [z1][y1][x1][zR][yR][xR], where xR, yR, and zR are relative to x1, y1 and z1, and with the corresponding negative direction ranges from
	 *            connectionMaxRanges added to avoid negative array indices. x1, y1, z1 is the target and xR, yR, zR is the source. Note that the range of
	 *            connections for neurons at or close to the edge of the grid must be circumscribed by the edge of the grid, so the last three dimensions of the
	 *            array must have differing sizes to account for this (except in the unlikely scenario that connections extend only straight down); also note
	 *            that no connections may extend into the input layer so the connection matrices must also be circumscribed accordingly for this (thus the
	 *            number of layers in the network including the input layer is weights.length+1).
	 * @param bias the bias for each neuron not in the input layer, in the format [z][y][x] where z, y, and x are the coordinates of the neuron (as input
	 *            neurons don't receive a bias bias.length should match weights.length).
	 * @param function the ActivationFunction to use; only one type of activation function is used throughout the network.
	 * @param cyclesPerStep Number of activation cycles to perform per step(), must be >= 1.
	 * @param aName Name of the network.
	 */
	public GridNet(int connectionMaxRange, int[][] layerDimensions, double[][][][][][] weights, double[][][] bias, ActivationFunction function, int cyclesPerStep, String aName) {
		depth = weights.length + 1;
		width = layerDimensions[0];
		height = layerDimensions[1];

		// System.out.println("GridNet dimensions (DxHxW): " + depth + " x " + height + " x " + width);

		this.connectionMaxRanges = new int[depth - 1][3][2];
		for (int l = 0; l < depth - 1; l++) {
			this.connectionMaxRanges[l][0][0] = -1; // no connections to previous or own layer
			this.connectionMaxRanges[l][0][1] = 1;
			this.connectionMaxRanges[l][1][0] = connectionMaxRanges[l][0][0];
			this.connectionMaxRanges[l][1][1] = connectionMaxRanges[l][0][1];
			this.connectionMaxRanges[l][2][0] = connectionMaxRanges[l][1][0];
			this.connectionMaxRanges[l][2][1] = connectionMaxRanges[l][1][1];
		}

		this.weights = weights;
		this.bias = bias;
		this.activationFunction = function;
		connectionTotalRanges = new int[depth - 1][connectionMaxRanges[0].length];
		for (int l = 0; l < depth - 1; l++) {
			for (int i = 0; i < connectionMaxRanges[l].length; i++) {
				connectionTotalRanges[l][i] = connectionMaxRanges[l][i][0] + connectionMaxRanges[l][i][1] + 1;
			}
		}
		activation = new double[depth][][];
		activationNew = new double[depth][][];
		for (int l = 0; l < depth; l++) {
			activation[l] = new double[height[l]][width[l]];
			activationNew[l] = new double[height[l]][width[l]];
		}
		this.cyclesPerStep = cyclesPerStep;
		name = aName;
		isFeedForward = false;
	}

	/**
	 * Creates a feed-forward GridNet with the given specifications.
	 * 
	 * @param connectionMaxRange The maximum length/range of connections in either direction in the tx and ty axes.
	 * @param weights The connection weights of the network between layers. Format is [l-1][y1][x1][yR][xR], where x1 and y1 are the coordinates of the target
	 *            neuron in layer l and xR and yR are the relative (to x1 and y1) coordinates of the source neuron in layer l-1, and with the corresponding
	 *            negative direction ranges from connectionMaxRanges added to avoid negative array indices. Note that the range of connections for neurons at or
	 *            close to the edge of the grid must be circumscribed by the edge of the grid, so the last two dimensions of the array must have differing sizes
	 *            to account for this (except in the unlikely scenario that connections extend only straight down). The number of layers in the network
	 *            including the input layer is weights.length+1.
	 * @param bias the bias for each neuron not in the input layer, in the format [z][y][x] where z, y, and x are the coordinates of the neuron (as input
	 *            neurons don't receive a bias bias.length should match weights.length).
	 * @param function the ActivationFunction to use; only one type of activation function is used throughout the network.
	 * @param aName Name of the network.
	 */
	public GridNet(int connectionMaxRange, int[][] layerDimensions, double[][][][][] weights, double[][][] bias, ActivationFunction function, String aName) {
		depth = weights.length + 1;
		width = layerDimensions[0];
		height = layerDimensions[1];

		this.connectionMaxRanges = new int[depth - 1][3][2];
		for (int l = 0; l < depth - 1; l++) {
			this.connectionMaxRanges[l][0][0] = -1; // no connections to previous or own layer
			this.connectionMaxRanges[l][0][1] = 1;
			this.connectionMaxRanges[l][1][0] = connectionMaxRanges[l][0][0];
			this.connectionMaxRanges[l][1][1] = connectionMaxRanges[l][0][1];
			this.connectionMaxRanges[l][2][0] = connectionMaxRanges[l][1][0];
			this.connectionMaxRanges[l][2][1] = connectionMaxRanges[l][1][1];
		}
		this.bias = bias;

		initFeedForward(this.connectionMaxRanges, weights, function, aName);
	}

	/**
	 * Creates a feed-forward GridNet with the given specifications.
	 * 
	 * @param connectionMaxRanges The maximum length/range of connections in either direction in the tx and ty axes. The array has format
	 *            [ty=0|tx=1],[negative=0|positive=1] = max range.
	 * @param weights The connection weights of the network between layers. Format is [l-1][y1][x1][yR][xR], where x1 and y1 are the coordinates of the target
	 *            neuron in layer l and xR and yR are the relative (to x1 and y1) coordinates of the source neuron in layer l-1, and with the corresponding
	 *            negative direction ranges from connectionMaxRanges added to avoid negative array indices. Note that the range of connections for neurons at or
	 *            close to the edge of the grid must be circumscribed by the edge of the grid, so the last two dimensions of the array must have differing sizes
	 *            to account for this (except in the unlikely scenario that connections extend only straight down). The number of layers in the network
	 *            including the input layer is weights.length+1.
	 * @param bias the bias for each neuron not in the input layer, in the format [z][y][x] where z, y, and x are the coordinates of the neuron (as input
	 *            neurons don't receive a bias bias.length should match weights.
	 * @param function the ActivationFunction to use; only one type of activation function is used throughout the network.
	 * @param aName Name of the network.
	 */
	public GridNet(int[][][] connectionMaxRanges, int[][] layerDimensions, double[][][][][] weights, double[][][] bias, ActivationFunction function, String aName) {
		depth = weights.length + 1;
		width = layerDimensions[0];
		height = layerDimensions[1];

		this.connectionMaxRanges = new int[depth - 1][3][2];
		for (int l = 0; l < depth - 1; l++) {
			this.connectionMaxRanges[l][0][0] = -1; // no connections to previous or own layer
			this.connectionMaxRanges[l][0][1] = 1;
			this.connectionMaxRanges[l][1][0] = connectionMaxRanges[l][0][0];
			this.connectionMaxRanges[l][1][1] = connectionMaxRanges[l][0][1];
			this.connectionMaxRanges[l][2][0] = connectionMaxRanges[l][1][0];
			this.connectionMaxRanges[l][2][1] = connectionMaxRanges[l][1][1];
		}
		this.bias = bias;

		initFeedForward(this.connectionMaxRanges, weights, function, aName);
	}

	/**
	 * Creates a fully-connected feed-forward GridNet with the given specifications.
	 * 
	 * @param weights The connection weights of the network between layers. Format is [l-1][y1][x1][y2][x2], where x1 and y1 are coordinates of target neuron in
	 *            layer l and x2 and y2 are coordinates of source neuron in layer l-1. The number of layers in the network including the input layer is
	 *            weights.length+1.
	 * @param bias the bias for each neuron not in the input layer, in the format [z][y][x] where z, y, and x are the coordinates of the neuron (as input
	 *            neurons don't receive a bias bias.length should match weights.
	 * @param function the ActivationFunction to use; only one type of activation function is used throughout the network.
	 * @param aName Name of the network.
	 */
	public GridNet(double[][][][][] weights, int[][] layerDimensions, double[][][] bias, ActivationFunction function, String aName) {
		depth = weights.length + 1;
		width = layerDimensions[0];
		height = layerDimensions[1];

		// System.out.println("GridNet dimensions (DxHxW): " + depth + " x " + height + " x " + width);

		// create standard feed-forward network fully connected from one layer to the nextSequence
		this.connectionMaxRanges = new int[depth - 1][3][2];
		for (int l = 0; l < depth - 1; l++) {
			this.connectionMaxRanges[l][0][0] = -1; // no connections to previous or own layer
			this.connectionMaxRanges[l][0][1] = 1;
			this.connectionMaxRanges[l][1][0] = height[l] - 1;
			this.connectionMaxRanges[l][1][1] = height[l] - 1;
			this.connectionMaxRanges[l][2][0] = width[l] - 1;
			this.connectionMaxRanges[l][2][1] = width[l] - 1;
		}
		this.bias = bias;

		initFeedForward(connectionMaxRanges, weights, function, aName);
	}

	private void initFeedForward(int[][][] connectionMaxRanges, double[][][][][] weights, ActivationFunction function, String aName) {
		// System.out.println("GridNet dimensions (DxHxW): " + depth + " x " + height + " x " + width);

		connectionTotalRanges = new int[depth - 1][connectionMaxRanges[0].length];
		for (int l = 0; l < depth - 1; l++) {
			for (int i = 0; i < connectionMaxRanges[l].length; i++) {
				connectionTotalRanges[l][i] = connectionMaxRanges[l][i][0] + connectionMaxRanges[l][i][1] + 1;
			}
		}

		double[][][][][][] w = new double[depth - 1][][][][][];
		// for each neuron zyx
		for (int z = 0; z < depth - 1; z++) {
			w = new double[z][height[z + 1]][width[z + 1]][1][][];
			for (int y = 0; y < height[z + 1]; y++) {
				for (int x = 0; x < width[z + 1]; x++) {
					w[z][y][x][0] = weights[z][y][x];
				}
			}
		}
		this.weights = w;
		activation = new double[depth][][];
		for (int l = 0; l < depth; l++) {
			activation[l] = new double[height[l]][width[l]];
		}
		this.activationFunction = function;
		this.cyclesPerStep = 1;
		name = aName;
		isFeedForward = true;
	}

	/**
	 * for testing only
	 */
	protected GridNet() {
		// no-op
	}

	/**
	 * @return Number corresponding to cost of network activation in resources. This function over-estimates the cost, dependent on the ratio of the
	 *         connectionMaxRanges to the dimensions of the network.
	 */
	public long cost() {
		return getConnectionCount(true) * activationFunction.cost();
	}

	/**
	 * Set array to use as the input layer.
	 * 
	 * @param inputs The new input pattern. The format is input[ty][tx]. The array should be the same dimensions as the network, otherwise things will break.
	 *            The array is copied by reference, replacing the original first layer activation array. Changes to newInput after this call outside of this
	 *            network will be reflected in the network. The network never changes the input layer.
	 */
	public void setInputs(double[][] inputs) {
		activation[0] = inputs;
	}

	/**
	 * Get the input layer.
	 * 
	 * @return A reference to the input layer array. The format is input[ty][tx].
	 */
	public double[][] getInputs() {
		return activation[0];
	}

	/**
	 * Get output pattern.
	 * 
	 * @return A reference to the output pattern. The format is output[ty][tx]. For recurrent networks it's probably not a good idea to modify the returned
	 *         array.
	 */
	public double[][] getOutputs() {
		return activation[depth - 1];
	}

	/**
	 * @return True iff this GridNet was created as a feed-forward type, false otherwise.
	 */
	public boolean isFeedForward() {
		return isFeedForward;
	}

	/**
	 * Provides a reference to the internal weights array. Format is [z1][y1][x1][zR][yR][xR], where xR, yR, and zR are relative to x1, y1 and z1, and with the
	 * corresponding negative direction ranges from connectionMaxRanges added to avoid negative array indices. x1, y1, z1 is the target and xR, yR, zR is the
	 * source coordinates. Note that the range of connections for neurons at or close to the edge of the grid are circumscribed by the edge of the grid, so the
	 * last three dimensions of the array will have differing sizes to account for this (except in the unlikely scenario that connections extend only straight
	 * down). Note that modifications to the returned array are reflected in the operation of the GridNet.
	 * 
	 * @return A reference to the weights array.
	 */
	public double[][][][][][] getWeights() {
		return weights;
	}

	public double[][][] getBias() {
		return bias;
	}

	/**
	 * Perform one activation step, consisting of cyclesPerStep steps, calculating new activation for all neurons. For feed-forward networks use stepFF.
	 */
	public void step() {
		// the activationNew and activation arrays are swapped every step, so set
		// the first/input layer (which doesn't changes during stepping) of
		// activationNew to the same input layer array
		activationNew[0] = activation[0];

		for (int cycle = 0; cycle < cyclesPerStep; cycle++) {
			// for each target neuron
			for (int tz = 1; tz < depth; tz++) { // first layer is input layer, don't update it
				for (int ty = 0; ty < height[tz]; ty++) {
					for (int tx = 0; tx < width[tz]; tx++) {

						// System.out.println(tz + "," + ty + "," + tx);

						double[][][] w = weights[tz - 1][ty][tx];
						double sum = bias[tz - 1][ty][tx];

						// for each source neuron for connections to zyx
						// s{z,y,x} is an index into the activation matrix to the source neuron
						// w{z,y,x} is an index into the weight matrix w for the connection from s{z,y,x} to zyx
						for (int wz = 0, sz = Math.max(0, tz + wz - connectionMaxRanges[tz - 1][0][0]); wz < w.length; wz++, sz++) {

							for (int wy = 0, sy = Math.max(0, ty + wy - connectionMaxRanges[tz - 1][1][0]); wy < w[wz].length; wy++, sy++) {

								for (int wx = 0, sx = Math.max(0, tx + wx - connectionMaxRanges[tz - 1][2][0]); wx < w[wz][wy].length; wx++, sx++) {

									// System.out.println("\tw: " + wz + "," + wy + "," + wx + "\tt: " + tz + "," + ty + "," + tx);

									sum += activation[sz][sy][sx] * w[wz][wy][wx];
								}
							}
						}

						activationNew[tz][ty][tx] = activationFunction.apply(sum);

						// System.out.println();

						System.out.print("\t" + activationNew[tz][ty][tx]);
					}
					System.out.println();
				}
				System.out.println();
			}
			System.out.println();
			System.out.println();
			System.out.println();

			double[][][] temp = activation;
			activation = activationNew;
			activationNew = temp;
		}
	}

	/**
	 * Perform one complete cycle for a feed forward network, propogating signal from input layer to output layer, only activating each layer once in sequence
	 * (connections can only exist from layer n to layer n+1).
	 */
	public void stepFF() {
		// for each target layer
		for (int tz = 1, sz = 0; tz < depth; tz++, sz++) { // first layer is input layer, don't update it
			for (int ty = 0; ty < height[tz]; ty++) {
				for (int tx = 0; tx < width[tz]; tx++) {
					// System.out.println(tz + "," + ty + "," + tx);

					double[][] w = weights[tz - 1][ty][tx][0]; // weight matrix for connections from s{z,y,x} to t{z,y,x}
					double sum = bias[tz - 1][ty][tx];

					// for each source neuron for connections from zyx
					// s{z,y,x} is an index into the activation matrix to the source neuron
					// w{y,x} is an index into the weight matrix w for the connection from s{z,y,x} to t{z,y,x}

					for (int wy = 0, sy = Math.max(0, ty + wy - connectionMaxRanges[tz - 1][1][0]); wy < w.length; wy++, sy++) {

						for (int wx = 0, sx = Math.max(0, tx + wx - connectionMaxRanges[tz - 1][2][0]); wx < w[wy].length; wx++, sx++) {

							// System.out.println("\tw: " + wz + "," + wy + "," + wx + "\tt: " + tz + "," + ty + "," + tx);
							sum += activation[sz][sy][sx] * w[wy][wx];
						}
					}
					activation[tz][ty][tx] = activationFunction.apply(sum);

					// System.out.println();

					// System.out.print("\t" + activation[tz][ty][tx]);
				}
				// System.out.println();
			}
			// System.out.println();
		}
	}

	// +++++++++++ Activator interface ++++++++++++++

	/**
	 * @return double[][] output layer given last provided input activation via <code>next(double[])</code> or <code>next(double[][])</code>, copied by reference.
	 *         For recurrent networks it's probably not a good idea to modify the returned array.
	 * @see Activator#next(double[])
	 * @see Activator#next(double[][])
	 */
	public Object next() {
		if (isFeedForward)
			stepFF();
		else
			step();
		return getOutputs();
	}

	/**
	 * 
	 * @param stimuli first row of input layer. The stimuli array is copied by reference, replacing the original first layer row array. Changes to stimuli after
	 *            this call outside of this network will be reflected in the network. The network never changes the input layer.
	 * @return double[] first row of output array given input stimuli, copied by reference. For recurrent networks it's probably not a good idea to modify the
	 *         returned array.
	 */
	public double[] next(double[] stimuli) {
		activation[0][0] = stimuli;
		if (isFeedForward)
			stepFF();
		else
			step();
		return activation[depth - 1][0];
	}

	/**
	 * @param stimuli sequence of first row of input layer. The stimuli arrays are copied by reference, replacing the original first layer array. Changes to the
	 *            last stimuli array after this call outside of this network will be reflected in the network. The GridNet never changes the input layer.
	 * @return double[][] sequence of first row of output values array given input stimuli.
	 */
	public double[][] nextSequence(double[][] stimuli) {
		double[][] response = new double[stimuli.length][width[depth - 1]];
		for (int seq = 0; seq < stimuli.length; seq++) {
			activation[0][0] = stimuli[seq];
			if (isFeedForward)
				stepFF();
			else
				step();
			System.arraycopy(activation[depth - 1][0], 0, response[seq], 0, width[depth - 1]);
		}
		return response;
	}

	/**
	 * @param stimuli input layer. The stimuli array is copied by reference, replacing the original first layer array. Changes to stimuli after this call
	 *            outside of this network will be reflected in the network. The network never changes the input layer.
	 * @return double[][] output layer given input stimuli, copied by reference.
	 */
	public double[][] next(double[][] stimuli) {
		activation[0] = stimuli;
		if (isFeedForward)
			stepFF();
		else
			step();
		return getOutputs();
	}

	/**
	 * @param stimuli sequence of input layer values. The stimuli arrays are copied by reference, replacing the original first layer array. Changes to the last
	 *            stimuli array after this call outside of this network will be reflected in the network. The network never changes the values in the input
	 *            layer.
	 * 
	 * @return double[][][] sequence of output value arrays given input stimuli.
	 */
	public double[][][] nextSequence(double[][][] stimuli) {
		double[][][] response = new double[stimuli.length][height[depth - 1]][width[depth - 1]];
		for (int seq = 0; seq < stimuli.length; seq++) {
			activation[0] = stimuli[seq];
			if (isFeedForward)
				stepFF();
			else
				step();
			for (int y = 0; y < height[depth - 1]; y++) {
				for (int x = 0; x < width[depth - 1]; x++) {
					response[seq][y][x] = activation[depth - 1][y][x];
				}
			}
		}
		return response;
	}

	/**
	 * reset object to initial state, clear all activation.
	 */
	public void reset() {
		for (int z = 0; z < depth; z++) {
			for (int y = 0; y < height[z]; y++) {
				for (int x = 0; x < width[z]; x++) {
					activation[z][y][x] = 0;
				}
			}
		}
	}

	/**
	 * @return String identifier, preferably unique, of object.
	 */
	public String getName() {
		return name;
	}

	public void setName(String newName) {
		name = newName;
	}

	/**
	 * @return min response value
	 */
	public double getMinResponse() {
		return activationFunction.getMinValue();
	}

	/**
	 * @return max response value
	 */
	public double getMaxResponse() {
		return activationFunction.getMaxValue();
	}

	/**
	 * @return dimension of input array
	 */
	public int[] getInputDimension() {
		return new int[] { height[0], width[0] };
	}

	/**
	 * @return dimension of output array
	 */
	public int[] getOutputDimension() {
		return new int[] { height[depth - 1], width[depth - 1] };
	}

	public double[][][] getActivation() {
		return activation;
	}

	/**
	 * @return <code>String</code> XML representation
	 */
	public String toXml() {
		StringBuffer result = new StringBuffer();
		result.append("<").append(XML_TAG).append(">\n");
		result.append("<title>").append(getName()).append("</title>\n");
		// Neuron.appendToXml( allNeurons, outNeurons, result );
		// NeuronConnection.appendToXml( allNeurons, result );
		result.append("</").append(XML_TAG).append(">\n");

		return result.toString();
	}

	/**
	 * @see com.anji.util.XmlPersistable#getXmld()
	 */
	public String getXmld() {
		return name;
	}

	public String getXmlRootTag() {
		return "network";
	}

	/*
	 * @Override public String toString() { return getName(); }
	 */

	@Override
	public String toString() {
		String output = "";
		for (int tz = 1, sz = 0; tz < depth; tz++, sz++) {
			for (int ty = 0; ty < height[tz]; ty++) {
				for (int tx = 0; tx < width[tz]; tx++) {
					output += "t " + tz + "," + ty + "," + tx + "\n";

					double[][] w = weights[tz - 1][ty][tx][0]; // weight matrix for connections from s{z,y,x} to t{z,y,x}

					// for each source neuron for connections from zyx
					// s{z,y,x} is an index into the activation matrix to the source neuron
					// w{y,x} is an index into the weight matrix w for the connection from s{z,y,x} to t{z,y,x}

					for (int wy = 0, sy = Math.max(0, ty + wy - connectionMaxRanges[tz - 1][1][0]); wy < w.length; wy++, sy++) {

						for (int wx = 0, sx = Math.max(0, tx + wx - connectionMaxRanges[tz - 1][2][0]); wx < w[wy].length; wx++, sx++) {

							output += "\t" + (int) (100 * w[wy][wx]) + (sy == ty && sx == tx ? "*" : "");
						}
						output += "\n";
					}
					output += "\n";
				}
				output += "\n";
			}
			output += "\n";
		}
		return output;
	}

	/**
	 * Return a count of the total number of connections in this network.
	 * 
	 * $param includeBias Iff true then include bias connections (if they exist for this network).
	 */
	public int getConnectionCount(boolean includeBias) {
		int connectionCount = 0;
		for (int tz = 1; tz < depth; tz++) {
			for (int ty = 0; ty < height[tz]; ty++)
				for (int tx = 0; tx < width[tz]; tx++)
					for (int sz = 0; sz < weights[tz - 1][ty][tx].length; sz++)
						for (int sy = 0; sy < weights[tz - 1][ty][tx][sz].length; sy++)
							connectionCount += weights[tz - 1][ty][tx][sz][sy].length;
			if (includeBias)
				connectionCount += height[tz] * width[tz];
		}
		return connectionCount;
	}

	public static void main(String[] args) {
		/*
		 * double[][][] array = new double[5][500][500]; //double[][][] arrayEmpty = new double[5*500*500];
		 * 
		 * long time = 0; for (int i = 0; i < 2500; i++) { long start = System.currentTimeMillis();
		 * 
		 * for (int tx = 0; tx < 5; tx++) { for (int ty = 0; ty < 500; ty++) { for (int tz = 0; tz < 500; tz++) { array[tx][ty][tz] = 0; }
		 * //Arrays.fill(array[tx][ty], 0); //System.arraycopy(arrayEmpty, 0, array[tx][ty], 0, arrayEmpty.length); } } time += System.currentTimeMillis() -
		 * start;
		 * 
		 * for (int tx = 0; tx < 5; tx++) { for (int ty = 0; ty < 500; ty++) { for (int tz = 0; tz < 500; tz++) { if (array[tx][ty][tz] != 0) {
		 * System.out.println("non zero"); System.exit(0); } } } }
		 * 
		 * for (int tx = 0; tx < 5; tx++) { for (int ty = 0; ty < 500; ty++) { for (int tz = 0; tz < 500; tz++) { array[tx][ty][tz] = tx*ty*tz; } } } }
		 * 
		 * System.out.println(time);
		 */

		int depth = 4;
		int height = 4;
		int width = 4;
		/*
		 * int[][] cmr = new int[3][2]; cmr[0][0] = 1; //tz neg, no connections to previous or own layer cmr[0][1] = 1; //tz pos cmr[1][0] = 1; //ty neg
		 * cmr[1][1] = 1; //ty pos cmr[2][0] = 1; //tx neg cmr[2][1] = 1; //tx pos
		 */

		int cmr = 2;
		/*
		 * double[][][][][] weights = new double[depth-1][height][width][][]; //for each target neuron for (int z = 0; z < depth-1; z++) { for (int y = 0; y <
		 * height; y++) { for (int x = 0; x < width; x++) { //calculate dimensions of this weight matrix (bounded by grid edges) //int dy = Math.min(height-1,
		 * ty+cmr[1][1]) - Math.max(0, ty-cmr[1][0]) + 1; //int dx = Math.min(height-1, tx+cmr[2][1]) - Math.max(0, tx-cmr[2][0]) + 1; int dy =
		 * Math.min(height-1, y+cmr) - Math.max(0, y-cmr) + 1; int dx = Math.min(height-1, x+cmr) - Math.max(0, x-cmr) + 1;
		 * 
		 * //System.out.println(tz + "," + ty + "," + tx + "  dy = " + dy + "  dx = " + dx);
		 * 
		 * weights[z][y][x] = new double[dy][dx]; double[][] w = weights[z][y][x];
		 * 
		 * //for each connection to zyx //w{y,x} is index into weight matrix //s{y,x} is index of source neuron //for (int wy = 0, ty = Math.max(0,
		 * ty-cmr[1][0]); for (int wy = 0, sy = Math.max(0, y-cmr); wy < dy; wy++, sy++) { //for (int wx = 0, tx = Math.max(0, tx-cmr[2][0]); for (int wx = 0,
		 * sx = Math.max(0, x-cmr); wx < dx; wx++, sx++) { //System.out.print("\t" + ty + "," + tx);
		 * 
		 * if (y-sy >= 0 && x-sx >= 0) //if (y == sy+1 && x == sx+1) //weights[tz][ty][tx][wy][wx] = 0.5/(0.5+Math.sqrt((ty-wy)*(ty-wy) + (tx-wx)*(tx-wx)));
		 * w[wy][wx] = 1; else w[wy][wx] = 0;
		 * 
		 * //System.out.print("\t" + w[wy][wx]); } //System.out.println(); } //System.out.println(); } } }
		 */

		double[][][][][][] weights = new double[depth - 1][height][width][][][];
		double[][][] bias = new double[depth - 1][height][width];

		for (int tz = 1; tz < depth; tz++) {
			for (int ty = 0; ty < height; ty++) {
				for (int tx = 0; tx < width; tx++) {
					// calculate dimensions of this weight matrix (bounded by grid edges)
					int dz = Math.min(depth - 1, tz + cmr) - Math.max(0, tz - cmr) + 1; // no connections to input layer
					int dy = Math.min(height - 1, ty + cmr) - Math.max(0, ty - cmr) + 1;
					int dx = Math.min(width - 1, tx + cmr) - Math.max(0, tx - cmr) + 1;

					// System.out.println(tz + ", " + ty + ", " + tx + "  dz = " + dz + "  dy = " + dy + "  dx = " + dx);

					weights[tz - 1][ty][tx] = new double[dz][dy][dx];
					double[][][] w = weights[tz - 1][ty][tx];

					bias[tz - 1][ty][tx] = 0;

					// for each connection from zyx
					// w{z,y,x} is index into weight matrix
					// s{z,y,x} is index of target neuron
					for (int wz = 0, sz = Math.max(0, tz - cmr); wz < dz; wz++, sz++) {
						for (int wy = 0, sy = Math.max(0, ty - cmr); wy < dy; wy++, sy++) {
							for (int wx = 0, sx = Math.max(0, tx - cmr); wx < dx; wx++, sx++) {
								// if (Math.abs(tz-sz) == 1 && Math.abs(ty-sy) == 1 && Math.abs(tx-sx) == 1)
								if (ty >= sy && tx >= sx && tz == sz + 1 && sy == 0 && sx == 0)
									// weights[tz][ty][tx][wy][wx] = 0.5/(0.5+Math.sqrt((ty-wy)*(ty-wy) + (tx-wx)*(tx-wx)));
									w[wz][wy][wx] = 1;
								// w[wz][wy][wx] = tx*height + ty;
								else
									w[wz][wy][wx] = 0;

								// System.out.println("\t" + sz + ", " + sy + ", " + sx + " : " + w[wz][wy][wx]);
							}
						}
					}
				}
			}
		}

		/*
		 * double[][][][][] weights = new double[depth-1][height][width][height][width];
		 * 
		 * for (int tz = 0; tz < depth-1; tz++) { for (int ty = 0; ty < height; ty++) { for (int tx = 0; tx < width; tx++) { //for each connection from zyx for
		 * (int wy = 0; wy < height; wy++) { for (int wx = 0; wx < width; wx++) { if (Math.abs(ty-wy) <= 1 && Math.abs(tx-wx) <= 1) weights[tz][ty][tx][wy][wx]
		 * = 0.5/(0.5+Math.sqrt((ty-wy)*(ty-wy) + (tx-wx)*(tx-wx))); else weights[tz][ty][tx][wy][wx] = 0; } } } } }
		 */

		// GridNet net = new GridNet(weights, ActivationFunctionFactory.getInstance().getLinear(), "test");
		// GridNet net = new GridNet(cmr, weights, ActivationFunctionFactory.getInstance().getLinear(), "test");
		/*
		 * GridNet net = new GridNet(cmr, weights, bias, ActivationFunctionFactory.getInstance().getLinear(), depth-1, "test");
		 * 
		 * double[][] input = net.getInputs();
		 * 
		 * for (int ty = 0, v = 1; ty < input.length; ty++) { for (int tx = 0; tx < input[0].length; tx++, v++) { input[ty][tx] = v; //input[ty][tx] = 1; } }
		 * //input[1][1] = 1;
		 * 
		 * //for (int s = 0; s < depth; s++) { // System.out.println("\nStep " + s + "\n"); net.step(); //} //net.stepFF();
		 */
	}
}
