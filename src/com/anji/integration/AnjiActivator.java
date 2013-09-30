/*
 * Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of ANJI (Another NEAT Java Implementation).
 * 
 * ANJI is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Created on Feb 26, 2004 by Philip Tucker
 */
package com.anji.integration;

import java.awt.Graphics2D;
import java.util.Arrays;

import com.anji.nn.AnjiNet;
import com.anji.nn.Neuron;
import com.anji.nn.Pattern;
import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * Simple neural network implementation of Activator interface.
 * 
 * @author Philip Tucker
 */
public class AnjiActivator implements Activator {

	private AnjiNet net;

	private Pattern inputPattern = null;

	private int numCycles = 1;

	private int outputDimension;

	private double minResponseValue;

	private double maxResponseValue;

	/**
	 * @param aNet ANN
	 * @param aNumCycles number of times input pattern is "shown" to network before a result is returned; this allows
	 *            for recurrent connections to take effect
	 * @throws IllegalArgumentException
	 */
	public AnjiActivator(AnjiNet aNet, int aNumCycles) throws IllegalArgumentException {
		super();
		setNumCycles(aNumCycles);
		net = aNet;
		outputDimension = aNet.getOutputDimension();

		// input neurons
		inputPattern = new Pattern(aNet.getInputDimension());
		for (int i = 0; i < aNet.getInputDimension(); ++i) {
			Neuron n = aNet.getInputNeuron(i);
			n.addIncomingConnection(inputPattern.getConnection(i));
		}

		// verify consistent response ranges
		minResponseValue = net.getOutputNeuron(0).getFunc().getMinValue();
		maxResponseValue = net.getOutputNeuron(0).getFunc().getMaxValue();
		for (int i = 1; i < net.getOutputDimension(); ++i)
			if (minResponseValue != net.getOutputNeuron(i).getFunc().getMinValue() || maxResponseValue != net.getOutputNeuron(i).getFunc().getMaxValue())
				throw new IllegalArgumentException("min and max values for response nodes differ");
	}

	public double[] next() {
		return next((double[]) null);
	}

	public double[] next(double[] newInputValues) {
		assert !Double.isNaN(ArrayUtil.sum(newInputValues)) : "input array contains NaN: " + Arrays.toString(newInputValues);
		
		if (newInputValues != null)
			inputPattern.setValues(newInputValues);

		// step through network activations for recurrent network
		for (int cycle = 0; cycle < numCycles - 1; ++cycle) {
			net.step();

			// sanity check - this may not be necessary if we construct the nets properly such that
			// recurrency is determined by building the network backward from the output layer; in that
			// way, a neuron could not be created having only recurrent outputs
			net.fullyActivate();
		}

		// last step, get results
		net.step();
		double[] result = new double[outputDimension];
		for (int idx = 0; idx < outputDimension; ++idx) {
			Neuron n = net.getOutputNeuron(idx);
			result[idx] = n.getValue();
		}
		if (net.isRecurrent()) {
			net.fullyActivate();
		}
		
		assert !Double.isNaN(ArrayUtil.sum(result)) : "result array contains NaN: " + Arrays.toString(result);
		
		return result;
	}

	public double[][] nextSequence(double[][] newInputValues) {
		double[][] result = new double[newInputValues.length][];
		for (int i = 0; i < newInputValues.length; ++i) {
			result[i] = next(newInputValues[i]);
		}
		return result;
	}

	public double[][] next(double[][] stimuli) {
		throw new IllegalArgumentException("AnjiActivator can only accept one dimensional input patterns");
	}

	public double[][][] nextSequence(double[][][] stimuli) {
		throw new IllegalArgumentException("AnjiActivator can only accept one dimensional input patterns");
	}

	/**
	 * @param array glue between double arrays and neuron connections.
	 */
	public void setInputPattern(Pattern array) {
		inputPattern = array;
	}

	/**
	 * clear all memory in network, including neurons and recurrent connections
	 */
	public void reset() {
		net.reset();
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return net.toString();
	}

	/**
	 * @see com.anji.integration.Activator#toXml()
	 */
	public String toXml() {
		return net.toXml();
	}

	/**
	 * @see com.anji.integration.Activator#getName()
	 */
	public String getName() {
		return net.getName();
	}

	/**
	 * @param aNumCycles number of times input pattern is "shown" to network before a result is returned; this allows
	 *            for recurrent connections to take effect
	 * @throws IllegalArgumentException
	 */
	public void setNumCycles(int aNumCycles) throws IllegalArgumentException {
		if (aNumCycles < 1)
			throw new IllegalArgumentException("numCycles must be >= 1");
		numCycles = aNumCycles;
	}

	/**
	 * @return dimension of input pattern
	 */
	public int[] getInputDimension() {
		return new int[] { inputPattern.getDimension() };
	}

	/**
	 * @return dimension of output pattern
	 */
	public int[] getOutputDimension() {
		return new int[] { outputDimension };
	}

	/**
	 * @return true if network contains any recurrent connections, false otherwise
	 */
	public boolean isRecurrent() {
		return net.isRecurrent();
	}

	/**
	 * @return min response
	 */
	public double getMinResponse() {
		return net.getOutputNeuron(0).getFunc().getMinValue();
	}

	/**
	 * @return max responses
	 */
	public double getMaxResponse() {
		return net.getOutputNeuron(0).getFunc().getMaxValue();
	}

	/**
	 * @see com.anji.util.XmlPersistable#getXmlRootTag()
	 */
	public String getXmlRootTag() {
		return "network";
	}

	/**
	 * @see com.anji.util.XmlPersistable#getXmld()
	 */
	public String getXmld() {
		return net.getName();
	}

	/**
	 * Return the underlying AnjiNet.
	 */
	public AnjiNet getAnjiNet() {
		return net;
	}

	@Override
	public int getInputCount() {
		return inputPattern.getDimension();
	}

	@Override
	public int getOutputCount() {
		return outputDimension;
	}

	public boolean render(Graphics2D g, int width, int height, int neuronSize) {
		return false;
	}

	@Override
	public void dispose() {
	}

	public void setName(String string) {
		net.setName(string);
	}
}
