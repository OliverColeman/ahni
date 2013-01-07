package ojc.ahni.transcriber;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import ojc.ahni.hyperneat.Configurable;
import ojc.ahni.hyperneat.Properties;
import ojc.ahni.nn.BainNN;
import ojc.bain.NeuralNetwork;
import ojc.bain.base.ComponentCollection;
import ojc.bain.base.NeuronCollection;
import ojc.bain.base.SynapseCollection;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.amd.aparapi.Kernel;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.neat.ConnectionAllele;
import com.anji.neat.NeatChromosomeUtility;
import com.anji.neat.NeuronAllele;
import com.anji.neat.NeuronType;

import com.anji.nn.RecurrencyPolicy;

/**
 * <p>
 * Constructs a <a href="https://github.com/OliverColeman/bain">Bain</a> neural network from a chromosome using the NEAT
 * encoding scheme. An {@link com.anji.integration.ActivatorTranscriber} should be used to construct an instance of this
 * class. {@link com.anji.integration.ActivatorTranscriber#newActivator(Chromosome)} is then used to get the resulting
 * network.
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
public class NEATTranscriberBain implements Transcriber<BainNN>, Configurable {
	private final static Logger logger = Logger.getLogger(NEATTranscriberBain.class);

	public static final String SIMULATION_RESOLUTION_KEY = "ann.bain.resolution";
	public static final String EXECUTION_MODE_KEY = "ann.bain.executionmode";
	public static final String NEURON_MODEL_KEY = "ann.bain.neuron.model";
	public static final String SYNAPSE_MODEL_KEY = "ann.bain.synapse.model";
	/**
	 * For networks with recurrent connections, the number of activation cycles to perform each time the network is
	 * presented with new input and queried for its output.
	 */
	public final static String RECURRENT_CYCLES_KEY = "ann.recurrent.cycles";

	private Properties props;
	private RecurrencyPolicy recurrencyPolicy = RecurrencyPolicy.BEST_GUESS;

	public NEATTranscriberBain() {
	}

	public NEATTranscriberBain(Properties props) {
		init(props);
	}

	/**
	 * @see Configurable#init(Properties)
	 */
	public void init(Properties props) {
		this.props = props;
		recurrencyPolicy = RecurrencyPolicy.load(props);
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
		// Map from innovation ID to neuron ID in Bain network.
		Map<Long, Integer> allNeurons = new HashMap<Long, Integer>();
		int bainNeuronID = 0;

		// Input neurons
		SortedMap<Long, NeuronAllele> inNeuronAlleles = (SortedMap<Long, NeuronAllele>) NeatChromosomeUtility.getNeuronMap(genotype.getAlleles(), NeuronType.INPUT);
		Iterator<NeuronAllele> nit = inNeuronAlleles.values().iterator();
		while (nit.hasNext()) {
			NeuronAllele neuronAllele = nit.next();
			allNeurons.put(neuronAllele.getInnovationId(), bainNeuronID);
			bainNeuronID++;
		}

		// Hidden neurons
		SortedMap<Long, NeuronAllele> hiddenNeuronAlleles = (SortedMap<Long, NeuronAllele>) NeatChromosomeUtility.getNeuronMap(genotype.getAlleles(), NeuronType.HIDDEN);
		nit = hiddenNeuronAlleles.values().iterator();
		while (nit.hasNext()) {
			NeuronAllele neuronAllele = nit.next();
			allNeurons.put(neuronAllele.getInnovationId(), bainNeuronID);
			bainNeuronID++;
		}

		// Output neurons
		SortedMap<Long, NeuronAllele> outNeuronAlleles = (SortedMap<Long, NeuronAllele>) NeatChromosomeUtility.getNeuronMap(genotype.getAlleles(), NeuronType.OUTPUT);
		HashSet<Integer> outNeuronIDs = new HashSet<Integer>();
		nit = outNeuronAlleles.values().iterator();
		while (nit.hasNext()) {
			NeuronAllele neuronAllele = nit.next();
			outNeuronIDs.add(bainNeuronID);
			allNeurons.put(neuronAllele.getInnovationId(), bainNeuronID);
			bainNeuronID++;
		}

		List<ConnectionAllele> remainingConnAlleles = NeatChromosomeUtility.getConnectionList(genotype.getAlleles());

		int neuronCount = allNeurons.size();
		int synapseCount = remainingConnAlleles.size();

		NeuronCollection neurons = null;
		SynapseCollection synapses = null;
		String neuronModelClass = props.getProperty(NEURON_MODEL_KEY, "ojc.bain.neuron.rate.SigmoidNeuronCollection");
		String synapseModelClass = props.getProperty(SYNAPSE_MODEL_KEY, "ojc.bain.synapse.rate.FixedSynapseCollection");
		try {
			neurons = (NeuronCollection) ComponentCollection.createCollection(neuronModelClass, neuronCount);
			// If the neuron collection is configurable and the configuration has a default preset.
			if (neurons.getConfigSingleton() != null && neurons.getConfigSingleton().getPreset(0) != null) {
				neurons.addConfiguration(neurons.getConfigSingleton().getPreset(0));
			}
		} catch (Exception e) {
			throw new TranscriberException("Error creating neurons for Bain neural network. Have you specified the name of the neuron collection class correctly, including the containing packages?", e);
		}
		try {
			synapses = (SynapseCollection) ComponentCollection.createCollection(synapseModelClass, synapseCount);
			// If the synapse collection is configurable and the configuration has a default preset.
			if (synapses.getConfigSingleton() != null && synapses.getConfigSingleton().getPreset(0) != null) {
				synapses.addConfiguration(neurons.getConfigSingleton().getPreset(0));
			}
		} catch (Exception e) {
			throw new TranscriberException("Error creating synapses for Bain neural network. Have you specified the name of the synapse collection class correctly, including the containing packages?", e);
		}

		// For each neuron store the list of neurons which have connections to it.
		Map<Integer, Set<Integer>> neuronSourceIDs = new HashMap<Integer, Set<Integer>>();
		for (Integer id : allNeurons.values()) {
			neuronSourceIDs.put(id, new HashSet<Integer>());
		}

		// Connections.
		Set<Long> currentNeuronInnovationIds = new HashSet<Long>(outNeuronAlleles.keySet());
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
				neuronSourceIDs.get(dest).add(src);
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

		int simRes = props.getIntProperty(SIMULATION_RESOLUTION_KEY, 1000);
		// If feed-forward, cycles per step is depth-1.
		String execModeName = props.getProperty(EXECUTION_MODE_KEY, null);
		Kernel.EXECUTION_MODE execMode = execModeName == null ? null : Kernel.EXECUTION_MODE.valueOf(execModeName);
		NeuralNetwork nn = new NeuralNetwork(simRes, neurons, synapses, execMode);
		int[] inputDims = new int[] { inNeuronAlleles.size() };
		int[] outputDims = new int[] { outNeuronAlleles.size() };
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
}
