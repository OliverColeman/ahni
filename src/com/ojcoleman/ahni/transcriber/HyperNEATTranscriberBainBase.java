package com.ojcoleman.ahni.transcriber;

import com.ojcoleman.bain.base.ComponentConfiguration;
import com.ojcoleman.bain.base.NeuronCollection;
import com.ojcoleman.bain.base.SynapseCollection;
import com.ojcoleman.bain.base.SynapseConfiguration;
import com.ojcoleman.bain.neuron.rate.NeuronCollectionWithBias;

import com.ojcoleman.ahni.nn.BainNN;
import com.ojcoleman.ahni.util.Point;

/**
 * Base class for HyperNEAT-based transcribers that create BainNN neural network phenotypes. Includes a few useful
 * methods for setting parameters for BainNN neurons and synapses.
 * 
 * @author Oliver Coleman
 */
public abstract class HyperNEATTranscriberBainBase extends HyperNEATTranscriber<BainNN> {
	/**
	 * If synapse parameter classes are being used (see {@link TranscriberAdaptor#SUBSTRATE_SYNAPSE_MODEL_PARAMS_CLASSES}, {@link #synapseDisableParamName}) and 
	 * a synapse disable parameter has been specified (see {@link #SUBSTRATE_SYNAPSE_MODEL_DISABLE_PARAM}), then this should
	 * be set to the index of a {@link SynapseConfiguration} in the {@link SynapseCollection} passed to {@link #setSynapseParameters(SynapseCollection, int, com.ojcoleman.ahni.transcriber.HyperNEATTranscriber.CPPN, boolean, boolean)}
	 * that has the disable parameter set to 0, otherwise it should be left with the value -1.
	 */ 
	protected int synapseDisableParamClassIndex = -1;
	
	/**
	 * Set the parameters for a neuron, specifying the neuron coordinates directly (see
	 * {@link HyperNEATTranscriber.CPPN#setTargetCoordinates(double, double, double)}).
	 * 
	 * @param x The x coordinate, range should be [0, 1].
	 * @param y The y coordinate, range should be [0, 1].
	 * @param z The z coordinate, range should be [0, 1].
	 * @param neurons The neuron collection to set parameters for.
	 * @param bainIndex The index into the neuron collection to specify the neuron to set parameters for.
	 * @param cppn The CPPN to use to generate parameter values for the given neuron.
	 *  @param addNewConfig Whether to add a new configuration object to the neuron collection (Set to TRUE if creating a new neuron collection).
	 */
	protected void setNeuronParameters(double x, double y, double z, NeuronCollection neurons, int bainIndex, CPPN cppn, boolean addNewConfig) {
		if (enableBias || neuronTypesEnabled || neuronParamsEnabled) {
			cppn.setTargetCoordinates(x, y, z);
			cppn.resetSourceCoordinates();
			cppn.query();
			setNeuronParameters(neurons, bainIndex, cppn, addNewConfig);
		}
	}

	/**
	 * Set the parameters for a neuron, specifying the neuron coordinates with grid indices into the substrate (see
	 * {@link HyperNEATTranscriber.CPPN#setTargetCoordinatesFromGridIndices(int, int, int)}).
	 * 
	 * @param x The index of the target neuron in the X dimension.
	 * @param y The index of the target neuron in the Y dimension.
	 * @param z The index of the target neuron in the Z dimension (the layer index).
	 * @param neurons The neuron collection to set parameters for.
	 * @param bainIndex The index into the neuron collection to specify the neuron to set parameters for.
	 * @param cppn The CPPN to use to generate parameter values for the given neuron.
	 *  @param addNewConfig Whether to add a new configuration object to the neuron collection (Set to TRUE if creating a new neuron collection).
	 */
	protected void setNeuronParameters(int x, int y, int z, NeuronCollection neurons, int bainIndex, CPPN cppn, boolean addNewConfig) {
		if (enableBias || neuronTypesEnabled || neuronParamsEnabled) {
			cppn.setTargetCoordinatesFromGridIndices(x, y, z);
			cppn.resetSourceCoordinates();
			cppn.query();
			setNeuronParameters(neurons, bainIndex, cppn, addNewConfig);
		}
	}

	/**
	 * Set the parameters for a neuron, specifying the neuron coordinates with a Point (see
	 * {@link HyperNEATTranscriber.CPPN#setTargetCoordinates(Point)}).
	 * 
	 * @param point The coordinates for the neuron.
	 * @param neurons The neuron collection to set parameters for.
	 * @param bainIndex The index into the neuron collection to specify the neuron to set parameters for.
	 * @param cppn The CPPN to use to generate parameter values for the given neuron.
	 *  @param addNewConfig Whether to add a new configuration object to the neuron collection (Set to TRUE if creating a new neuron collection).
	 */
	protected void setNeuronParameters(Point point, NeuronCollection neurons, int bainIndex, CPPN cppn, boolean addNewConfig) {
		if (enableBias || neuronTypesEnabled || neuronParamsEnabled) {
			cppn.setTargetCoordinates(point);
			cppn.resetSourceCoordinates();
			cppn.query();
			setNeuronParameters(neurons, bainIndex, cppn, addNewConfig);
		}
	}
	
	/**
	 * Set the parameters for a neuron. NOTE: It is assumed that the source and target coordinates have already been
	 * set for the CPPN and that {@link HyperNEATTranscriber.CPPN#query()} or one of the other query methods has been called.
	 * 
	 * @param neurons The neuron collection to set parameters for.
	 * @param bainIndex The index into the neuron collection to specify the neuron to set parameters for.
	 * @param cppn The CPPN to use to generate parameter values for the given neuron.
	 * @param addNewConfig Whether to add a new configuration object to the neuron collection (Set to TRUE if creating a new neuron collection).
	 */
	public void setNeuronParameters(NeuronCollection neurons, int bainIndex, CPPN cppn, boolean addNewConfig) {
		int neuronType = cppn.getNeuronTypeIndex();

		if (enableBias) {
			((NeuronCollectionWithBias) neurons).setBias(bainIndex, cppn.getRangedBiasWeight(neuronType));
		}

		// Type and/or parameters for neuron.
		if (neuronTypesEnabled || neuronParamsEnabled) {
			// Each neuron has its own configuration object.
			ComponentConfiguration c = addNewConfig ? neurons.getConfigSingleton().createConfiguration() : neurons.getComponentConfiguration(bainIndex);
			

			if (neuronTypesEnabled) {
				c.setParameterValue(neuronModelTypeParam, neuronModelTypes[neuronType], true);
			}

			if (neuronParamsEnabled) {
				if (neuronModelParamClassCount > 0) {
					int classIndex = cppn.getNeuronParamClassIndex();
					neurons.setComponentConfiguration(bainIndex, classIndex);
				}
				else {
					// Set parameters for the config.
					for (int p = 0; p < neuronParamNames.length; p++) {
						double v = cppn.getRangedNeuronParam(neuronType, p);
						c.setParameterValue(neuronParamNames[p], v, true);
					}
				}
			}
			
			if (addNewConfig) {
				assert neuronModelParamClassCount == 0 : "Shouldn't be adding new neuron configs when using parameter classes.";
						
				// Add the configuration to the neuron collection.
				neurons.addConfiguration(c);
				// Set the current neuron to use the new configuration.
				neurons.setComponentConfiguration(bainIndex, bainIndex);
			}
		}
	}
	
	/**
	 * Set the parameters for a synapse. NOTE: It is assumed that the source and target coordinates have already been
	 * set for the CPPN and that {@link HyperNEATTranscriber.CPPN#query()} or one of the other query methods has been called.
	 * 
	 * @param synapses The synapse collection to set parameters for.
	 * @param bainIndex The index into the synapse collection to specify the synapse to set parameters for.
	 * @param cppn The CPPN to use to generate parameter values for the given synapse.
	 * @param disabled Whether the synapse should be disabled (by setting the parameter specified by {@link #synapseDisableParamName} to 0).
	 * @param addNewConfig Whether to add a new configuration object to the synapse collection (Set to TRUE if creating a new synapse collection).
	 */
	public void setSynapseParameters(SynapseCollection synapses, int bainIndex, CPPN cppn, boolean disabled, boolean addNewConfig) {
		if (synapseParamsEnabled || synapseTypesEnabled) {			
			if (synapseModelParamClassCount > 0) {
				int classIndex = 0;
				// If this synapse should be disabled and a specific parameter indicates this.
				if (disabled && synapseDisableParamClassIndex >= 0) {
					// Use config that has synapseDisableParamName set to 0.
					classIndex = synapseDisableParamClassIndex;
				}
				else {
					classIndex = cppn.getSynapseParamClassIndex();
				}
				synapses.setComponentConfiguration(bainIndex, classIndex);
			}
			else {
				// Each synapse has its own configuration object.
				SynapseConfiguration c = (SynapseConfiguration) (addNewConfig ? synapses.getConfigSingleton().createConfiguration() : synapses.getComponentConfiguration(bainIndex));

				int synapseType = cppn.getSynapseTypeIndex();
				if (synapseTypesEnabled) {
					c.setParameterValue(synapseModelTypeParam, synapseModelTypes[synapseType], true);
				}
				
				// Set parameters for the config.
				for (int p = 0; p < synapseParamNames.length; p++) {
					double v = cppn.getRangedSynapseParam(synapseType, p);
					c.setParameterValue(synapseParamNames[p], v, true);
				}
				if (synapseDisableParamName != null && disabled) {
					c.setParameterValue(synapseDisableParamName, 0, true);
				}
				
				if (addNewConfig) {
					c.minimumEfficacy = connectionWeightMin;
					c.maximumEfficacy = connectionWeightMax;

					// Add the configuration to the synapse collection.
					synapses.addConfiguration(c);
					// Set the current synapse to use the new configuration.
					synapses.setComponentConfiguration(bainIndex, bainIndex);
				}
			}
		}
	}
}
