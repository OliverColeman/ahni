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
 * created by Philip Tucker on Mar 9, 2003
 * 
 * Modified May 2010 by Oliver Coleman to conform with updated Transcriber interface.
 */
package com.anji.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.anji.neat.ConnectionAllele;
import com.anji.neat.NeatChromosomeUtility;
import com.anji.neat.NeuronAllele;
import com.anji.neat.NeuronType;
import com.anji.nn.AnjiNet;
import com.anji.nn.CacheNeuronConnection;
import com.anji.nn.Neuron;
import com.anji.nn.NeuronConnection;
import com.anji.nn.RecurrencyPolicy;
import com.anji.nn.activationfunction.ActivationFunctionFactory;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * The purpose of this class is to construct a neural net object (<code>AnjiNet</code>) from a chromosome.
 * <code>TranscriberFactory</code> should be used to construct an <code>AnjiNetTranscriber</code>, given a chromosome.
 * <code>getNet()</code> or <code>getPhenotype()</code> returns the resulting network.
 * 
 * @see com.anji.nn.AnjiNet
 * @author Philip Tucker
 */
public class AnjiNetTranscriber implements Transcriber<AnjiActivator>, Configurable {
	/**
	 * # recurrent cycles properties key
	 */
	public final static String RECURRENT_CYCLES_KEY = "recurrent.cycles";

	private int recurrentCycles;

	private final static Logger logger = Logger.getLogger(AnjiNetTranscriber.class);

	private RecurrencyPolicy recurrencyPolicy = RecurrencyPolicy.BEST_GUESS;

	/**
	 * ctor
	 */
	public AnjiNetTranscriber() {
		this(RecurrencyPolicy.BEST_GUESS);
	}

	/**
	 * ctor
	 * 
	 * @param aPolicy
	 */
	public AnjiNetTranscriber(RecurrencyPolicy aPolicy) {
		recurrencyPolicy = aPolicy;
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		recurrencyPolicy = RecurrencyPolicy.load(props);
		recurrentCycles = props.getIntProperty(RECURRENT_CYCLES_KEY, 1);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	public AnjiActivator transcribe(Chromosome genotype) throws TranscriberException {
		return new AnjiActivator(newAnjiNet(genotype), recurrentCycles);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome, Activator) Note: this method has been added to conform with the
	 *      Transcriber interface, but does not use the substrate argument for performance gains.
	 */
	public AnjiActivator transcribe(Chromosome genotype, AnjiActivator substrate) throws TranscriberException {
		return new AnjiActivator(newAnjiNet(genotype), recurrentCycles);
	}

	/**
	 * create new <code>AnjiNet</code> from <code>genotype</code>
	 * 
	 * @param genotype chromosome to transcribe
	 * @return phenotype
	 * @throws TranscriberException
	 */
	public AnjiNet newAnjiNet(Chromosome genotype) throws TranscriberException {
		if (genotype.getAlleles().isEmpty()) throw new IllegalArgumentException("Genotype has no alleles...");

		Map<Long, Neuron> allNeurons = new HashMap<Long, Neuron>();

		// input neurons
		SortedMap<Long, NeuronAllele> inNeuronAlleles = NeatChromosomeUtility.getNeuronMap(genotype.getAlleles(), NeuronType.INPUT);
		if (inNeuronAlleles.isEmpty()) throw new IllegalArgumentException("An AnjiNet must have at least one input neuron.");
		List<Neuron> inNeurons = new ArrayList<Neuron>();
		for (NeuronAllele neuronAllele : inNeuronAlleles.values()) {
			Neuron n = new Neuron(ActivationFunctionFactory.getInstance().get(neuronAllele.getActivationType()), neuronAllele.getBias());
			n.setId(neuronAllele.getInnovationId().longValue());
			inNeurons.add(n);
			allNeurons.put(neuronAllele.getInnovationId(), n);
		}

		// output neurons
		SortedMap<Long, NeuronAllele> outNeuronAlleles = NeatChromosomeUtility.getNeuronMap(genotype.getAlleles(), NeuronType.OUTPUT);
		if (outNeuronAlleles.isEmpty()) throw new IllegalArgumentException("An AnjiNet must have at least one output neuron.");
		List<Neuron> outNeurons = new ArrayList<Neuron>();
		for (NeuronAllele neuronAllele : outNeuronAlleles.values()) {
			Neuron n = new Neuron(ActivationFunctionFactory.getInstance().get(neuronAllele.getActivationType()), neuronAllele.getBias());
			n.setId(neuronAllele.getInnovationId().longValue());
			outNeurons.add(n);
			allNeurons.put(neuronAllele.getInnovationId(), n);
		}

		// hidden neurons
		SortedMap<Long, NeuronAllele> hiddenNeuronAlleles = NeatChromosomeUtility.getNeuronMap(genotype.getAlleles(), NeuronType.HIDDEN);
		for (NeuronAllele neuronAllele : hiddenNeuronAlleles.values()) {
			Neuron n = new Neuron(ActivationFunctionFactory.valueOf(neuronAllele.getActivationType()), neuronAllele.getBias());
			n.setId(neuronAllele.getInnovationId().longValue());
			allNeurons.put(neuronAllele.getInnovationId(), n);
		}

		// connections
		//
		// Starting with output layer, gather connections and neurons to which it is immediately
		// connected, creating a logical layer of neurons. Assign each connection its input (source)
		// neuron, and each destination neuron its input connections. Recurrency is handled depending
		// on policy:
		//
		// RecurrencyPolicy.LAZY - all connections CacheNeuronConnection
		//
		// RecurrencyPolicy.DISALLOWED - no connections CacheNeuronConnection (assumes topology sans
		// loops)
		//
		// RecurrencyPolicy.BEST_GUESS - any connection where the source neuron is in the same or
		// later (i.e., nearer output layer) as the destination is a CacheNeuronConnection
		List<CacheNeuronConnection> recurrentConns = new ArrayList<CacheNeuronConnection>();
		List<ConnectionAllele> remainingConnAlleles = NeatChromosomeUtility.getConnectionList(genotype.getAlleles());
		Set<Long> currentNeuronInnovationIds = new HashSet<Long>(outNeuronAlleles.keySet());
		Set<Long> traversedNeuronInnovationIds = new HashSet<Long>(currentNeuronInnovationIds);
		Set<Long> nextNeuronInnovationIds = new HashSet<Long>();
		while (!remainingConnAlleles.isEmpty() && !currentNeuronInnovationIds.isEmpty()) {
			nextNeuronInnovationIds.clear();
			Collection<ConnectionAllele> connAlleles = NeatChromosomeUtility.extractConnectionAllelesForDestNeurons(remainingConnAlleles, currentNeuronInnovationIds);
			for (ConnectionAllele connAllele : connAlleles) {
				Neuron src = allNeurons.get(connAllele.getSrcNeuronId());
				Neuron dest = allNeurons.get(connAllele.getDestNeuronId());
				if (src == null)
					throw new TranscriberException("connection with missing src neuron: " + connAllele.toString());
				if (dest == null)
					throw new TranscriberException("connection with missing dest neuron: " + connAllele.toString());

				// handle recurrency processing
				boolean cached = false;
				if (RecurrencyPolicy.LAZY.equals(recurrencyPolicy))
					cached = true;
				else if (RecurrencyPolicy.BEST_GUESS.equals(recurrencyPolicy)) {
					boolean maybeRecurrent = (traversedNeuronInnovationIds.contains(connAllele.getSrcNeuronId()));
					cached = maybeRecurrent || recurrencyPolicy.equals(RecurrencyPolicy.LAZY);
				}
				NeuronConnection conn = null;
				if (cached) {					
					conn = new CacheNeuronConnection(src, (float) connAllele.getWeight());
					recurrentConns.add((CacheNeuronConnection) conn);
				} else {
					conn = new NeuronConnection(src, (float) connAllele.getWeight());
				}

				conn.setId(connAllele.getInnovationId().longValue());
				dest.addIncomingConnection(conn);
				nextNeuronInnovationIds.add(connAllele.getSrcNeuronId());
			}
			traversedNeuronInnovationIds.addAll(nextNeuronInnovationIds);
			currentNeuronInnovationIds.clear();
			currentNeuronInnovationIds.addAll(nextNeuronInnovationIds);
			remainingConnAlleles.removeAll(connAlleles);
		}

		// make sure we traversed all connections and nodes; input neurons are automatically
		// considered "traversed" since they should be realized regardless of their connectivity to
		// the rest of the network
		if (!remainingConnAlleles.isEmpty()) {
			logger.warn("not all connection genes handled: " + genotype.toString() + (genotype.getMaterial().pruned ? "  " : "  not") + " pruned");
		}
		traversedNeuronInnovationIds.addAll(inNeuronAlleles.keySet());
		if (traversedNeuronInnovationIds.size() != allNeurons.size()) {
			logger.warn("did not traverse all neurons: " + genotype.toString() + (genotype.getMaterial().pruned ? "  " : "  not") + " pruned");
		}

		// build network

		Collection<Neuron> allNeuronsCol = allNeurons.values();
		String id = genotype.getId().toString();
		return new AnjiNet(allNeuronsCol, inNeurons, outNeurons, recurrentConns, id);
	}

	/**
	 * @see com.anji.integration.Transcriber#getPhenotypeClass()
	 */
	public Class getPhenotypeClass() {
		return AnjiNet.class;
	}
}
