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
 * created by Philip Tucker on Jun 9, 2004
 */

package com.anji.nn;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Aggregates all pieces for a full neural network.
 * 
 * @author Philip Tucker
 */
public class AnjiNet {

	/**
	 * base XML tag
	 */
	public final static String XML_TAG = "network";

	private ArrayList<Neuron> allNeurons;

	private List<Neuron> inNeurons;

	private List<Neuron> outNeurons;

	private List<CacheNeuronConnection> recurrentConns;

	private String name;

	/**
	 * @param allNeurons
	 * @param inputNeurons
	 * @param outputNeurons
	 * @param recurrentConns
	 * @param aName
	 */
	public AnjiNet(Collection<Neuron> allNeurons, List<Neuron> inputNeurons, List<Neuron> outputNeurons, List<CacheNeuronConnection> recurrentConns, String aName) {
		init(allNeurons, inputNeurons, outputNeurons, recurrentConns, aName);
	}

	/**
	 * for testing only
	 */
	protected AnjiNet() {
		// no-op
	}

	/**
	 * @return number corresponding to cost of network activation in resources
	 */
	public long cost() {
		long result = 0;

		Iterator it = allNeurons.iterator();
		while (it.hasNext()) {
			Neuron n = (Neuron) it.next();
			result += n.cost();
		}

		return result;
	}

	/**
	 * @param someNeurons all neurons
	 * @param someInNeurons input neurons (also included in someNeurons)
	 * @param someOutNeurons output neurons (also included in someNeurons)
	 * @param someRecurrentConns recurrent connections
	 * @param aName
	 */
	protected void init(Collection<Neuron> someNeurons, List<Neuron> someInNeurons, List<Neuron> someOutNeurons, List<CacheNeuronConnection> someRecurrentConns, String aName) {
		allNeurons = new ArrayList<Neuron>(someNeurons);

		inNeurons = someInNeurons;
		outNeurons = someOutNeurons;
		recurrentConns = someRecurrentConns;
		name = aName;
	}

	/**
	 * @param idx
	 * @return input neuron at position <code>idx</code>
	 */
	public Neuron getInputNeuron(int idx) {
		return inNeurons.get(idx);
	}

	/**
	 * @return number input neurons
	 */
	public int getInputDimension() {
		return inNeurons.size();
	}

	// /**
	// * @return <code>Collection</code> contains all <code>Neuron</code> objects
	// */
	// public Collection getAllNeurons() {
	// return allNeurons;
	// }

	/**
	 * @param idx
	 * @return output neuron at position <code>idx</code>
	 */
	public Neuron getOutputNeuron(int idx) {
		return outNeurons.get(idx);
	}

	/**
	 * @param fromIdx
	 * @param toIdx
	 * @return output neurons from position <code>toIdx</code> (inclusive) to <code>fromIdx</code> (exclusive)
	 */
	public List<Neuron> getOutputNeurons(int fromIdx, int toIdx) {
		return outNeurons.subList(fromIdx, toIdx);
	}

	/**
	 * @param fromIdx
	 * @param toIdx
	 * @return input neurons from position <code>toIdx</code> (inclusive) to <code>fromIdx</code> (exclusive)
	 */
	public List<Neuron> getInputNeurons(int fromIdx, int toIdx) {
		return inNeurons.subList(fromIdx, toIdx);
	}

	/**
	 * @return number output neurons
	 */
	public int getOutputDimension() {
		return outNeurons.size();
	}

	/**
	 * @return <code>Collection</code> contains recurrent <code>Connection</code> objects
	 */
	public Collection<CacheNeuronConnection> getRecurrentConns() {
		return recurrentConns;
	}

	/**
	 * @see java.lang.Object#toString()
	 */

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		HashMap<Neuron, Integer> neuronIndexMap = new HashMap<Neuron, Integer>();
		int connCount = 0;
		for (int n = 0; n < allNeurons.size(); n++) {
			neuronIndexMap.put(allNeurons.get(n), n);
			if (!inNeurons.contains(allNeurons.get(n))) {
				connCount += allNeurons.get(n).getIncomingConns().size();
			}
		}
		
		DecimalFormat nf = new DecimalFormat(" 0.00;-0.00");
		StringBuilder out = new StringBuilder();
		if (getName() != null) out.append(getName());
		out.append("\nNeuron count: " + allNeurons.size());
		out.append("\nSynapse count: " + connCount);
		out.append("\nTopology type: " + (isRecurrent() ? "Reccurent" : "Feed-forward"));
		
		out.append("\n\nNeurons:\n\t\ttype\tfunc\tbias");
		for (int n = 0; n < allNeurons.size(); n++) {
			out.append("\n\t" + n + "\t" + getType(allNeurons.get(n)) + "\t" + allNeurons.get(n).getFunc() + "\t" + allNeurons.get(n).getBias());
		}

		out.append("\n\nConnections:");
		out.append("\n\tpre > post\tweight");
		
		for (int ni = 0; ni < allNeurons.size(); ni++) {
			Neuron n = allNeurons.get(ni);
			for (Connection c : n.getIncomingConns()) {
				if (c instanceof NeuronConnection) {
					NeuronConnection nc = (NeuronConnection) c;
					Neuron src = nc.getIncomingNode();
					int pre = src != null ? neuronIndexMap.get(src) : -1;
					int post = ni;
					String preType = src != null ? getType(src) : "-";
					String postType = getType(n);
					out.append("\n\t" + preType + ":" + pre + " > " + postType + ":" + post + "\t" + nc.getWeight());
				}
			}
		}

		return out.toString();
	}
	
	private String getType(Neuron n) {
		if (inNeurons.contains(n)) return "i";
		if (outNeurons.contains(n)) return "o";
		return "h";
	}

	/**
	 * @return the name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * indicates a time step has passed
	 */
	public void step() {
		try {
			// populate cache connections with values from previous step
			// We don't use the Collections iterator functionality because it's slower for small collections.
			for (int i = 0 ; i < recurrentConns.size(); i++) {
				recurrentConns.get(i).step();
				//System.out.println("recurrent! " + name);
			}
			for (int i = 0 ; i < allNeurons.size(); i++) {
				allNeurons.get(i).step();
			}
		}
		catch (StackOverflowError e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.err.println("step() AnjiNet:\n" + toXml());
		}
	}

	/**
	 * make sure all neurons have been activated for the current cycle; this is to catch neurons with no forward outputs
	 */
	public void fullyActivate() {
		try {
			// We don't use the Collections iterator functionality because it's slower for small collections.
			for (int i = 0 ; i < allNeurons.size(); i++) {
				allNeurons.get(i).getValue();
			}
		}
		catch (StackOverflowError e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.err.println("fA() AnjiNet:\n" + toXml());
		}
	}

	/**
	 * clear all memory in network, including neurons and recurrent connections
	 */
	public void reset() {
		// We don't use the Collections iterator functionality because it's slower for small collections.
		for (int i = 0 ; i < allNeurons.size(); i++) {
			allNeurons.get(i).reset();
		}
		for (int i = 0 ; i < recurrentConns.size(); i++) {
			recurrentConns.get(i).reset();
		}
	}

	/**
	 * @return <code>String</code> XML representation
	 */
	public String toXml() {
		StringBuffer result = new StringBuffer();
		result.append("<").append(XML_TAG).append(">\n");
		result.append("<title>").append(getName()).append("</title>\n");
		Neuron.appendToXml(allNeurons, outNeurons, result);
		NeuronConnection.appendToXml(allNeurons, result);
		result.append("</").append(XML_TAG).append(">\n");

		return result.toString();
	}

	/**
	 * @return true if network contains any recurrent connections, false otherwise
	 */
	public boolean isRecurrent() {
		return !recurrentConns.isEmpty();
	}

	public void setName(String string) {
		name = string;
	}

}
