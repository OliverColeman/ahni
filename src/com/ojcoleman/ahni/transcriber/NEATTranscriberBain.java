package com.ojcoleman.ahni.transcriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.ojcoleman.bain.NeuralNetwork;
import com.ojcoleman.bain.base.ComponentCollection;
import com.ojcoleman.bain.base.NeuronCollection;
import com.ojcoleman.bain.base.SynapseCollection;
import com.ojcoleman.bain.base.SynapseConfiguration;
import com.ojcoleman.bain.neuron.rate.NeuronCollectionWithBias;

import org.apache.log4j.Logger;
import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;

import com.amd.aparapi.Kernel;
import com.anji.integration.Activator;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.neat.ConnectionAllele;
import com.anji.neat.NeatChromosomeUtility;
import com.anji.neat.NeatConfiguration;
import com.anji.neat.NeuronAllele;
import com.anji.neat.NeuronType;

import com.anji.nn.RecurrencyPolicy;
import com.ojcoleman.ahni.evaluation.AHNIFitnessFunction;
import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.ahni.nn.NNAdaptor;

/**
 * <p>
 * Constructs a <a href="https://github.com/OliverColeman/bain">Bain</a> neural network from a chromosome using the NEAT
 * encoding scheme. An {@link com.anji.integration.ActivatorTranscriber} should be used to construct an instance of this
 * class. {@link com.anji.integration.ActivatorTranscriber#newActivator(Chromosome)} is then used to get the resulting
 * network. To use the HyperNEAT encoding scheme see {@link HyperNEATTranscriberBain}.
 * </p>
 * <p>
 * Bain neural networks are not well suited to feed-forward networks as every neuron and synapse must be
 * activated every simulation step. Bain is intended for recurrent networks, however the implementations of 
 * {@link com.anji.integration.Activator#nextSequence(double[][])} and {@link com.anji.integration.Activator#nextSequence(double[][][])}
 * in the {@link BainNN} wrapper are optimised to provide amortised performance over the number of input sequences for layered feed-forward networks.
 * </p>
 * 
 * @author Oliver Coleman
 */
public class NEATTranscriberBain extends TranscriberAdaptor<BainNN> implements Configurable {
	private final static Logger logger = Logger.getLogger(NEATTranscriberBain.class);

	/**
	 * For networks with recurrent connections, the number of activation cycles to perform each time the network is
	 * presented with new input and queried for its output.
	 */
	public final static String RECURRENT_CYCLES_KEY = "ann.recurrent.cycles";

	private Properties props;
	private RecurrencyPolicy recurrencyPolicy = RecurrencyPolicy.BEST_GUESS;
	
	private int inputSize = -1, outputSize = -1;

	public NEATTranscriberBain() {
	}

	public NEATTranscriberBain(Properties props) {
		init(props);
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		super.init(props);
		
		this.props = props;
		recurrencyPolicy = RecurrencyPolicy.load(props);
		
		// If this appears to be a "HyperNEAT compatible" properties file, attempt to determine substrate 
		// input and output size from layer dimensions unless these are explicitly specified in properties. 
		if (props.containsKey(HyperNEATTranscriber.SUBSTRATE_DEPTH) && !(props.containsKey(NeatConfiguration.STIMULUS_SIZE_KEY) || props.containsKey(NeatConfiguration.RESPONSE_SIZE_KEY))) {
			int depth = props.getIntProperty(HyperNEATTranscriber.SUBSTRATE_DEPTH);
			int[] width = HyperNEATTranscriber.getProvisionalLayerSize(props, HyperNEATTranscriber.SUBSTRATE_WIDTH);
			int[] height = HyperNEATTranscriber.getProvisionalLayerSize(props, HyperNEATTranscriber.SUBSTRATE_HEIGHT);
			
			// Determine width and height of input and output layer if available.
			BulkFitnessFunction bulkFitnessFunc = (BulkFitnessFunction) props.singletonObjectProperty(HyperNEATEvolver.FITNESS_FUNCTION_CLASS_KEY);
			AHNIFitnessFunction ahniFitnessFunc = (bulkFitnessFunc instanceof AHNIFitnessFunction) ? (AHNIFitnessFunction) bulkFitnessFunc : null;
			for (int layer = 0; layer < depth; layer += depth-1) {
				// If the fitness function is to define this layer.
				if (width[layer] == -1 || height[layer] == -1) {
					int[] layerDims = ahniFitnessFunc != null ? ahniFitnessFunc.getLayerDimensions(layer, depth) : null;
					if (layerDims != null) {
						if (width[layer] == -1) {
							width[layer] = layerDims[0];
						}
						if (height[layer] == -1) {
							height[layer] = layerDims[1];
						}
						logger.info("Fitness function defines dimensions for layer " + layer + ": " + width[layer] + "x" + height[layer]);
					} else {
						throw new IllegalArgumentException("Properties specify that fitness function should specify layer dimensions for layer " + layer + " but fitness function does not specify this.");
					}
				}
			}
			
			inputSize = width[0] * height[0];
			outputSize = width[depth-1] * height[depth-1];
		}
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	public BainNN transcribe(Chromosome genotype) throws TranscriberException {
		return newBainNN(genotype);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome, Activator) Note: this method has been added to conform with the
	 *      Transcriber interface, but does not use the substrate argument for performance gains.
	 */
	public BainNN transcribe(Chromosome genotype, BainNN substrate) throws TranscriberException {
		return newBainNN(genotype);
	}

	/**
	 * create new <code>AnjiNet</code> from <code>genotype</code>
	 * 
	 * @param genotype chromosome to transcribe
	 * @return phenotype
	 * @throws TranscriberException
	 */
	public BainNN newBainNN(Chromosome genotype) throws TranscriberException {
		List<NeuronAllele> neuronAlleles = new LinkedList<NeuronAllele>();
		List<NeuronAllele> inputNeuronAlleles = NeatChromosomeUtility.getNeuronList(genotype.getAlleles(), NeuronType.INPUT);
		List<NeuronAllele> outputNeuronAlleles = NeatChromosomeUtility.getNeuronList(genotype.getAlleles(), NeuronType.OUTPUT);
		
		// Collect together all neuron alleles, with input first, hidden next, and output last (this is the order than Bain networks should be in).
		neuronAlleles.addAll(inputNeuronAlleles);
		neuronAlleles.addAll(NeatChromosomeUtility.getNeuronList(genotype.getAlleles(), NeuronType.HIDDEN));
		neuronAlleles.addAll(outputNeuronAlleles);
		
		// Get all connection alleles.
		List<ConnectionAllele> remainingConnAlleles = NeatChromosomeUtility.getConnectionList(genotype.getAlleles());
		
		int neuronCount = neuronAlleles.size();
		int synapseCount = remainingConnAlleles.size();
		
		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		String neuronModelClass = props.getProperty(TranscriberAdaptor.SUBSTRATE_NEURON_MODEL, "com.ojcoleman.bain.neuron.rate.SigmoidNeuronCollection");
		String synapseModelClass = props.getProperty(TranscriberAdaptor.SUBSTRATE_SYNAPSE_MODEL, "com.ojcoleman.bain.synapse.rate.FixedSynapseCollection");
		try {
			neurons = BainNN.createNeuronCollection(neuronModelClass, neuronCount, true, neuronTypesEnabled, neuronParamsEnabled);
		} catch (Exception e) {
			e.printStackTrace();
			throw new TranscriberException("Error creating neurons for Bain neural network. Have you specified the name of the neuron collection class correctly, including the containing packages?", e);
		}
		try {
			synapses = BainNN.createSynapseCollection(synapseModelClass, synapseCount, synapseTypesEnabled, synapseParamsEnabled, connectionWeightMin, connectionWeightMax);
		} catch (Exception e) {
			e.printStackTrace();
			throw new TranscriberException("Error creating synapses for Bain neural network. Have you specified the name of the synapse collection class correctly, including the containing packages?", e);
		}

		Map<Long, Integer> allNeurons = new HashMap<Long, Integer>(); // Map from innovation ID to neuron ID in Bain network, and get bias values.
		Collection<Long> outputInnoIDs = new ArrayList<Long>(); // Collect a list of output neuron innovation IDs for later use.
		int bainNeuronID = 0;
		Iterator<NeuronAllele> nit = neuronAlleles.iterator();
		boolean biasNeuronModel = neurons instanceof NeuronCollectionWithBias;
		while (nit.hasNext()) {
			NeuronAllele neuronAllele = nit.next();
			allNeurons.put(neuronAllele.getInnovationId(), bainNeuronID);

			if (biasNeuronModel) {
				((NeuronCollectionWithBias) neurons).setBias(bainNeuronID, neuronAllele.getBias());
			}
			
			if (neuronAllele.getType() == NeuronType.OUTPUT) {
				outputInnoIDs.add(neuronAllele.getInnovationId());
			}
			
			bainNeuronID++;
		}

		// Connections.
		Set<Long> currentNeuronInnovationIds = new HashSet<Long>(outputInnoIDs);
		Set<Long> nextNeuronInnovationIds = new HashSet<Long>();
		Iterator<ConnectionAllele> cit;
		int bainConnectionID = 0;

		while (!remainingConnAlleles.isEmpty() && !currentNeuronInnovationIds.isEmpty()) {
			nextNeuronInnovationIds.clear();
			Collection<ConnectionAllele> connAlleles = NeatChromosomeUtility.extractConnectionAllelesForDestNeurons(remainingConnAlleles, currentNeuronInnovationIds);
			cit = connAlleles.iterator();
			while (cit.hasNext()) {
				ConnectionAllele connAllele = cit.next();
				int src = allNeurons.get(connAllele.getSrcNeuronId());
				int dest = allNeurons.get(connAllele.getDestNeuronId());
				synapses.setPreAndPostNeurons(bainConnectionID, src, dest);
				synapses.setEfficacy(bainConnectionID, connAllele.getWeight());
				nextNeuronInnovationIds.add(connAllele.getSrcNeuronId());
				bainConnectionID++;
			}
			currentNeuronInnovationIds.clear();
			currentNeuronInnovationIds.addAll(nextNeuronInnovationIds);
			remainingConnAlleles.removeAll(connAlleles);
		}

		if (!remainingConnAlleles.isEmpty()) {
			logger.warn("Not all connection genes handled: " + genotype.toString());
		}

		int cyclesPerStep = 0;
		BainNN.Topology topology;
		// For feed-forward networks determine the depth of the network to determine the number of activation cycles.
		if (recurrencyPolicy.equals(RecurrencyPolicy.DISALLOWED)) {
			topology = BainNN.Topology.FEED_FORWARD_NONLAYERED;
			cyclesPerStep = 0; // Calculated by BainNN.
		} else {
			topology = BainNN.Topology.RECURRENT;
			cyclesPerStep = props.getIntProperty(RECURRENT_CYCLES_KEY, 1);
		}

		int simRes = props.getIntProperty(BainNN.SUBSTRATE_SIMULATION_RESOLUTION, 1000);
		// If feed-forward, cycles per step is depth-1.
		String execModeName = props.getProperty(BainNN.SUBSTRATE_EXECUTION_MODE, null);
		Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
		NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
		int[] inputDims = new int[] { inputNeuronAlleles.size() };
		int[] outputDims = new int[] { outputNeuronAlleles.size() };
		try {
			return new BainNN(nn, inputDims, outputDims, cyclesPerStep, topology, "network " + genotype.getId(), 1000);
		} catch (Exception e) {
			throw new TranscriberException(e);
		}
	}

	/**
	 * @see com.anji.integration.Transcriber#getPhenotypeClass()
	 */
	public Class getPhenotypeClass() {
		return BainNN.class;
	}
	
	@Override
	public int getChromosomeInputNeuronCount() {
		if (inputSize == -1) return super.getChromosomeInputNeuronCount(); 
		return inputSize;
	}
	
	@Override
	public int getChromosomeOutputNeuronCount() {
		if (outputSize == -1) return super.getChromosomeOutputNeuronCount(); 
		return outputSize;
	}
	
}
