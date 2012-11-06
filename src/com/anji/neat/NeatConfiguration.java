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
 * created by Philip Tucker on Feb 22, 2003
 */
package com.anji.neat;

import java.io.IOException;
import java.util.Arrays;

import ojc.ahni.hyperneat.HyperNEATTranscriberGridNet;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.IdFactory;
import org.jgapcustomised.InvalidConfigurationException;
import org.jgapcustomised.NaturalSelector;
import org.jgapcustomised.event.EventManager;
import org.jgapcustomised.impl.CloneReproductionOperator;
import org.jgapcustomised.impl.WeightedRouletteSelector;

import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.SimpleSelector;
import com.anji.nn.activationfunction.ActivationFunction;
import com.anji.nn.activationfunction.ActivationFunctionFactory;
import com.anji.util.Properties;
import com.anji.util.Randomizer;

/**
 * Extension of JGAP configuration with NEAT-specific features added.
 * 
 * @author Philip Tucker
 */
public class NeatConfiguration extends Configuration {
	private static final Logger logger = Logger.getLogger(NeatConfiguration.class);
	protected static final String PERSIST_ENABLE_KEY = "persist.enable";
	/**
	 * properties key, file in which unique ID sequence number is stored
	 */
	public static final String ID_FACTORY_KEY = "id.file";
	public static final short DEFAULT_STIMULUS_SIZE = 3;
	protected static final short DEFAULT_INITIAL_HIDDEN_SIZE = 0;
	public static final short DEFAULT_RESPONSE_SIZE = 3;
	/**
	 * default survival rate
	 */
	public static final double DEFAULT_SURVIVAL_RATE = 0.20f;
	/**
	 * default crossover proportion
	 */
	public static final double DEFAULT_CROSSOVER_PROPORTION = 0.5f;
	/**
	 * default population size
	 */
	public static final int DEFAULT_POPUL_SIZE = 100;
	/**
	 * properties key, dimension of neural net stimulus
	 */
	public static final String STIMULUS_SIZE_KEY = "stimulus.size";
	/**
	 * properties key, dimension of neural net response
	 */
	public static final String RESPONSE_SIZE_KEY = "response.size";
	/**
	 * properties key, survival rate
	 */
	public static final String SURVIVAL_RATE_KEY = "survival.rate";
	/**
	 * properties key, survival rate
	 */
	public static final String CROSSOVER_PROPORTION_KEY = "crossover.proportion";
	/**
	 * properties key, topology mutation type; if true, use "classic" method where at most a single topological mutation
	 * occurs per generation per individual
	 */
	public static final String TOPOLOGY_MUTATION_CLASSIC_KEY = "topology.mutation.classic";
	/**
	 * properties key, maximum connection weight
	 */
	public static final String WEIGHT_MAX_KEY = "weight.max";
	/**
	 * properties key, minimum connection weight
	 */
	public static final String WEIGHT_MIN_KEY = "weight.min";
	/**
	 * properties key, population size
	 */
	public static final String POPUL_SIZE_KEY = "popul.size";
	/**
	 * properties key, speciation chromosome compatibility excess coefficient
	 */
	public final static String CHROM_COMPAT_EXCESS_COEFF_KEY = "chrom.compat.excess.coeff";
	/**
	 * properties key, speciation chromosome compatibility disjoint coefficient
	 */
	public final static String CHROM_COMPAT_DISJOINT_COEFF_KEY = "chrom.compat.disjoint.coeff";
	/**
	 * properties key, speciation chromosome compatibility common coefficient
	 */
	public final static String CHROM_COMPAT_COMMON_COEFF_KEY = "chrom.compat.common.coeff";
	/**
	 * properties key, speciation threshold
	 */
	public final static String SPECIATION_THRESHOLD_KEY = "speciation.threshold";
	/**
	 * properties key, speciation target (# species)
	 */
	public final static String SPECIATION_TARGET_KEY = "speciation.target";
	/**
	 * properties key, amount to change speciation threshold to maintain speciation target
	 */
	public final static String SPECIATION_THRESHOLD_CHANGE_KEY = "speciation.threshold.change";
	/**
	 * properties key, elitism proportion
	 */
	public final static String ELITISM_PROPORTION_KEY = "selector.elitism.proportion";
	/**
	 * properties key, minimum number of elite members to select from a species
	 */
	public final static String ELITISM_MIN_TO_SELECT_KEY = "selector.elitism.min.to.select";
	/**
	 * properties key, minimum size a specie must be to produce an elite member
	 */
	public final static String ELITISM_MIN_SPECIE_SIZE_KEY = "selector.elitism.min.specie.size";
	public final static String SPECIATED_FITNESS_KEY = "selector.speciated.fitness";
	public final static String MAX_STAGNANT_GENERATIONS_KEY = "selector.max.stagnant.generations";
	public final static String MINIMUM_AGE_KEY = "selector.min.generations";

	/**
	 * properties key, enable weighted selection process
	 */
	public final static String WEIGHTED_SELECTOR_KEY = "selector.roulette";
	/**
	 * properties key, enable fully connected initial topologies
	 */
	public final static String INITIAL_TOPOLOGY_FULLY_CONNECTED_KEY = "initial.topology.fully.connected";
	/**
	 * properties key, number of hidden neurons in initial topology
	 */
	public final static String INITIAL_TOPOLOGY_NUM_HIDDEN_NEURONS_KEY = "initial.topology.num.hidden.neurons";
	/**
	 * properties key, activation function type of neurons
	 */
	public final static String INITIAL_TOPOLOGY_ACTIVATION_KEY = "initial.topology.activation";
	/**
	 * properties key, activation function type of input neurons
	 */
	public final static String INITIAL_TOPOLOGY_ACTIVATION_INPUT_KEY = "initial.topology.activation.input";
	/**
	 * properties key, activation function type of output neurons
	 */
	public final static String INITIAL_TOPOLOGY_ACTIVATION_OUTPUT_KEY = "initial.topology.activation.output";
	/**
	 * properties key, allowed activation function types if the INITIAL_TOPOLOGY_ACTIVATION_KEY is "random".
	 */
	public final static String INITIAL_TOPOLOGY_ACTIVATION_RANDOM_ALLOWED_KEY = "initial.topology.activation.random.allowed";

	private Properties props;
	protected CloneReproductionOperator cloneOper = null;
	protected NeatCrossoverReproductionOperator crossoverOper = null;
	protected double maxConnectionWeight = Float.MAX_VALUE;
	protected double minConnectionWeight = -Float.MAX_VALUE;
	protected String inputActivationType;
	protected String outputActivationType;
	protected String hiddenActivationType;
	protected String[] hiddenActivationTypeRandomAllowed;
	private NeatIdMap neatIdMap;

	/**
	 * Initialize mutation operators.
	 * 
	 * @throws InvalidConfigurationException
	 */
	protected void initMutation() throws InvalidConfigurationException {
		// remove connection
		RemoveConnectionMutationOperator removeOperator = (RemoveConnectionMutationOperator) props.singletonObjectProperty(RemoveConnectionMutationOperator.class);
		if ((removeOperator.getMutationRate() > 0.0f) && (removeOperator.getMaxWeightRemoved() > 0.0f)) {
			addMutationOperator(removeOperator);
		}

		// add topology
		boolean isTopologyMutationClassic = props.getBooleanProperty(TOPOLOGY_MUTATION_CLASSIC_KEY, false);
		if (isTopologyMutationClassic) {
			SingleTopologicalMutationOperator singleOperator = (SingleTopologicalMutationOperator) props.singletonObjectProperty(SingleTopologicalMutationOperator.class);
			if (singleOperator.getMutationRate() > 0.0f) {
				addMutationOperator(singleOperator);
			}
		} else {
			// add connection
			AddConnectionMutationOperator addConnOperator = (AddConnectionMutationOperator) props.singletonObjectProperty(AddConnectionMutationOperator.class);
			if (addConnOperator.getMutationRate() > 0.0f) {
				addMutationOperator(addConnOperator);
			}

			// add neuron
			AddNeuronMutationOperator addNeuronOperator = (AddNeuronMutationOperator) props.singletonObjectProperty(AddNeuronMutationOperator.class);
			if (addNeuronOperator.getMutationRate() > 0.0f) {
				addMutationOperator(addNeuronOperator);
			}
		}

		// modify weight
		WeightMutationOperator weightOperator = (WeightMutationOperator) props.singletonObjectProperty(WeightMutationOperator.class);
		if (weightOperator.getMutationRate() > 0.0f) {
			addMutationOperator(weightOperator);
		}

		// prune
		PruneMutationOperator pruneOperator = (PruneMutationOperator) props.singletonObjectProperty(PruneMutationOperator.class);
		if (pruneOperator.getMutationRate() > 0.0f) {
			addMutationOperator(pruneOperator);
		}
	}

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param newProps configuration parameters; newProps[SURVIVAL_RATE_KEY] should be < 0.50f
	 * @throws InvalidConfigurationException
	 */
	public void init(Properties newProps) throws InvalidConfigurationException {
		props = newProps;

		Randomizer r = (Randomizer) props.singletonObjectProperty(Randomizer.class);
		setRandomGenerator(r.getRand());
		setEventManager(new EventManager());

		// id persistence
		String s = props.getProperty(ID_FACTORY_KEY, null);
		try {
			if (s != null) {
				setIdFactory(new IdFactory(s));
			}
		} catch (IOException e) {
			String msg = "could not load IDs";
			logger.error(msg, e);
			throw new InvalidConfigurationException(msg);
		}

		// make sure numbers add up
		double survivalRate = props.getDoubleProperty(SURVIVAL_RATE_KEY, DEFAULT_SURVIVAL_RATE);
		// double crossoverSlice = 1.0f - ( 2.0f * survivalRate );
		// if ( crossoverSlice < 0.0f )
		// throw new InvalidConfigurationException( "survival rate too large: " + survivalRate );
		// logger.info( "Crossover proportion: " + crossoverSlice);
		double crossoverProportion = props.getDoubleProperty(CROSSOVER_PROPORTION_KEY, DEFAULT_CROSSOVER_PROPORTION);

		// selector
		NaturalSelector selector = null;
		if (props.getBooleanProperty(WEIGHTED_SELECTOR_KEY, false)) {
			selector = new WeightedRouletteSelector();
		} else {
			selector = new SimpleSelector();
		}
		selector.setSurvivalRate(survivalRate);
		selector.setElitismProportion(props.getFloatProperty(ELITISM_PROPORTION_KEY, 0.1f));
		selector.setElitismMinToSelect(props.getIntProperty(ELITISM_MIN_TO_SELECT_KEY, 1));
		selector.setElitismMinSpeciesSize(props.getIntProperty(ELITISM_MIN_SPECIE_SIZE_KEY, 5));
		selector.setSpeciatedFitness(props.getBooleanProperty(SPECIATED_FITNESS_KEY, true));
		selector.setMaxStagnantGenerations(props.getIntProperty(MAX_STAGNANT_GENERATIONS_KEY, 999999));
		selector.setMinAge(props.getIntProperty(MINIMUM_AGE_KEY, 10));
		setNaturalSelector(selector);

		// reproduction
		// double reproductionSlice = 1 - survivalRate; //if a certain percentage of pop survives next to generation.
		double reproductionSlice = 1; // if only elites survive to next generation. exact number of elites changes so
										// handle at reproduction time.
		cloneOper = new CloneReproductionOperator();
		crossoverOper = new NeatCrossoverReproductionOperator();
		getCloneOperator().setSlice(reproductionSlice * (1 - crossoverProportion));
		getCrossoverOperator().setSlice(reproductionSlice * crossoverProportion);
		addReproductionOperator(getCloneOperator());
		addReproductionOperator(getCrossoverOperator());

		// mutation
		initMutation();

		// population
		setPopulationSize(props.getIntProperty(POPUL_SIZE_KEY, DEFAULT_POPUL_SIZE));
		hiddenActivationType = props.getProperty(INITIAL_TOPOLOGY_ACTIVATION_KEY, "sigmoid");

		if (hiddenActivationType.equals("random")) {
			hiddenActivationTypeRandomAllowed = props.getProperty(INITIAL_TOPOLOGY_ACTIVATION_RANDOM_ALLOWED_KEY, "sigmoid, gaussian, absolute, sine").split(",");
			for (int i = 0; i < hiddenActivationTypeRandomAllowed.length; i++) {
				hiddenActivationTypeRandomAllowed[i] = hiddenActivationTypeRandomAllowed[i].trim().toLowerCase();
			}
		}

		inputActivationType = props.getProperty(INITIAL_TOPOLOGY_ACTIVATION_INPUT_KEY, hiddenActivationType);
		outputActivationType = props.getProperty(INITIAL_TOPOLOGY_ACTIVATION_OUTPUT_KEY, hiddenActivationType);

		// System.out.println(props.getProperty(INITIAL_TOPOLOGY_ACTIVATION_INPUT_KEY, null ));
		// System.out.println(inputActivationType + ", " + hiddenActivationType + ", " + outputActivationType);

		load();

		ChromosomeMaterial sample = NeatChromosomeUtility.newSampleChromosomeMaterial(props.getShortProperty(STIMULUS_SIZE_KEY, DEFAULT_STIMULUS_SIZE), props.getShortProperty(INITIAL_TOPOLOGY_NUM_HIDDEN_NEURONS_KEY, DEFAULT_INITIAL_HIDDEN_SIZE), props.getShortProperty(RESPONSE_SIZE_KEY, DEFAULT_RESPONSE_SIZE), this, props.getBooleanProperty(INITIAL_TOPOLOGY_FULLY_CONNECTED_KEY, true));
		setSampleChromosomeMaterial(sample);

		if (props.getBooleanProperty(PERSIST_ENABLE_KEY, false)) {
			store();
		}

		// weight bounds
		minConnectionWeight = props.getFloatProperty(WEIGHT_MIN_KEY, -Float.MAX_VALUE);
		maxConnectionWeight = props.getFloatProperty(WEIGHT_MAX_KEY, Float.MAX_VALUE);

		// speciation parameters
		initSpeciationParms();
	}

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param newProps
	 * @see NeatConfiguration#init(Properties)
	 * @throws InvalidConfigurationException
	 */
	public NeatConfiguration(Properties newProps) throws InvalidConfigurationException {
		super();
		init(newProps);
	}

	protected void initSpeciationParms() {
		try {
			getSpeciationParms().setSpecieCompatExcessCoeff(props.getFloatProperty(CHROM_COMPAT_EXCESS_COEFF_KEY));
		} catch (RuntimeException e) {
			logger.info("no speciation compatibility threshold specified", e);
		}
		try {
			getSpeciationParms().setSpecieCompatDisjointCoeff(props.getFloatProperty(CHROM_COMPAT_DISJOINT_COEFF_KEY));
		} catch (RuntimeException e) {
			logger.info("no speciation compatibility threshold specified", e);
		}
		try {
			getSpeciationParms().setSpecieCompatCommonCoeff(props.getFloatProperty(CHROM_COMPAT_COMMON_COEFF_KEY));
		} catch (RuntimeException e) {
			logger.info("no speciation compatibility threshold specified", e);
		}
		try {
			getSpeciationParms().setSpeciationThreshold(props.getFloatProperty(SPECIATION_THRESHOLD_KEY));
		} catch (RuntimeException e) {
			logger.info("no speciation compatibility threshold specified", e);
		}

		getSpeciationParms().setSpeciationTarget(props.getIntProperty(SPECIATION_TARGET_KEY));

		getSpeciationParms().setSpeciationThresholdChange(props.getFloatProperty(SPECIATION_THRESHOLD_CHANGE_KEY));

	}

	/**
	 * factory method to construct new neuron allele with unique innovation ID of specified <code>type</code>
	 * 
	 * @param type
	 * @return NeuronAllele
	 */
	public NeuronAllele newNeuronAllele(NeuronType type) {
		String funcType;
		if (NeuronType.INPUT.equals(type)) {
			funcType = inputActivationType;
		} else if (NeuronType.OUTPUT.equals(type)) {
			funcType = outputActivationType;
		} else {
			funcType = hiddenActivationType;
		}

		if (funcType.equals("random")) {
			funcType = hiddenActivationTypeRandomAllowed[getRandomGenerator().nextInt(hiddenActivationTypeRandomAllowed.length)];
		}

		NeuronGene gene = new NeuronGene(type, nextInnovationId(), funcType);
		return new NeuronAllele(gene);
	}

	/**
	 * Factory method to construct new neuron allele which has replaced connection <code>connectionId</code> according
	 * to NEAT add neuron mutation. If a previous mutation has occurred adding a neuron on connection connectionId,
	 * returns a neuron with that id - otherwise, a new id.
	 * 
	 * @param connectionId
	 * @return NeuronAllele
	 */
	public NeuronAllele newNeuronAllele(Long connectionId) {
		Long id = neatIdMap.findNeuronId(connectionId);
		if (id == null) {
			id = nextInnovationId();
			neatIdMap.putNeuronId(connectionId, id);
		}

		String funcType = hiddenActivationType;
		ActivationFunction function;
		if (funcType.equals("random")) {
			funcType = hiddenActivationTypeRandomAllowed[getRandomGenerator().nextInt(hiddenActivationTypeRandomAllowed.length)];
		}

		NeuronGene gene = new NeuronGene(NeuronType.HIDDEN, id, funcType);
		return new NeuronAllele(gene);
	}

	/**
	 * factory method to construct new connection allele from neuron <code>srcNeuronId</code> to neuron
	 * <code>destNeuronId</code> according to NEAT add connection mutation; if a previous mutation has occurred adding a
	 * connection between srcNeuronId and destNeuronId, returns connection with that id; otherwise, new innovation id
	 * 
	 * @param srcNeuronId
	 * @param destNeuronId
	 * @return ConnectionAllele
	 */
	public ConnectionAllele newConnectionAllele(Long srcNeuronId, Long destNeuronId) {
		Long id = neatIdMap.findConnectionId(srcNeuronId, destNeuronId);
		if (id == null) {
			id = nextInnovationId();
			neatIdMap.putConnectionId(srcNeuronId, destNeuronId, id);
		}
		ConnectionGene gene = new ConnectionGene(id, srcNeuronId, destNeuronId);
		return new ConnectionAllele(gene);
	}

	/**
	 * @return clone reproduction operator used to create mutated asexual offspring
	 */
	public CloneReproductionOperator getCloneOperator() {
		return cloneOper;
	}

	/**
	 * @return crossover reproduction operator used to create mutated sexual offspring
	 */
	public NeatCrossoverReproductionOperator getCrossoverOperator() {
		return crossoverOper;
	}

	/**
	 * @return maximum conneciton weight
	 */
	public double getMaxConnectionWeight() {
		return maxConnectionWeight;
	}

	/**
	 * @return minimum conneciton weight
	 */
	public double getMinConnectionWeight() {
		return minConnectionWeight;
	}

	/**
	 * Load from persistence.
	 * 
	 * @throws InvalidConfigurationException
	 */
	public void load() throws InvalidConfigurationException {
		if (neatIdMap == null) {
			neatIdMap = new NeatIdMap(props);
			try {
				neatIdMap.load();
			} catch (IOException e) {
				String msg = "error loading ID map";
				logger.error(msg, e);
				throw new InvalidConfigurationException(msg);
			}
		}
	}

	/**
	 * Store to persistence.
	 * 
	 * @throws InvalidConfigurationException
	 */
	public void store() throws InvalidConfigurationException {
		System.out.println("\n\nhere4\n");

		try {
			getIdFactory().store();
			if (neatIdMap.store()) {
				neatIdMap = null;
			}
		} catch (IOException e) {
			String msg = "error storing ID map";
			logger.error(msg, e);
			throw new InvalidConfigurationException(msg);
		}
	}

	/**
	 * log stats for id maps
	 * 
	 * @param aLogger
	 * @param pri priority
	 */
	public void logIdMaps(Logger aLogger, Priority pri) {
		neatIdMap.log(aLogger, pri);
	}
}
