package com.ojcoleman.ahni.integration;

/**
 * A collection of {@link ParamGene}s or sub-ParamCollections. A ParamGene will have an index within its
 * ParamCollection, and if a ParamCollection has a super-ParamCollection it will have in index within it. The main
 * function of a ParamCollection is to define common properties of ParamGenes or collections thereof. Note that a
 * ParamCollection does not maintain references to its sub-collections or genes.
 */
public class ParamCollection {
	public static final int MIN_VALUE = 0;
	public static final int MAX_VALUE = 1;

	String label;
	ParamCollection superCollection;
	int index;

	double[] minMaxValue = null;
	double[][] minMaxValueForGeneAtIndex;

	/**
	 * Create a ParamCollection.
	 */
	public ParamCollection(String label) {
		this.label = label;
	}

	/**
	 * Create a ParamCollection belonging to the specified super collection.
	 */
	public ParamCollection(String label, ParamCollection superCollection, int index) {
		this.label = label;
		this.superCollection = superCollection;
		this.index = index;
	}

	/**
	 * Create a ParamCollection that defines a global min and max value.
	 */
	public ParamCollection(String label, double minValue, double maxValue) {
		this.label = label;
		this.minMaxValue = new double[] { minValue, maxValue };
	}

	/**
	 * Create a ParamCollection that defines a min and max value for each gene.
	 */
	public ParamCollection(String label, double[] minValueForGeneAtIndex, double[] maxValueForGeneAtIndex) {
		this.label = label;
		this.minMaxValueForGeneAtIndex = new double[][] { minValueForGeneAtIndex, maxValueForGeneAtIndex };
	}

	/**
	 * Create a ParamCollection belonging to the specified super collection and that defines a global min and max value.
	 */
	public ParamCollection(String label, ParamCollection superCollection, int index, double minValue, double maxValue) {
		this.label = label;
		this.superCollection = superCollection;
		this.index = index;
		this.minMaxValue = new double[] { minValue, maxValue };
	}

	/**
	 * Create a ParamCollection belonging to the specified super collection and that defines a min and max value for
	 * each gene.
	 */
	public ParamCollection(String label, ParamCollection superCollection, int index, double[] minValueForGeneAtIndex, double[] maxValueForGeneAtIndex) {
		this.label = label;
		this.superCollection = superCollection;
		this.index = index;
		this.minMaxValueForGeneAtIndex = new double[][] { minValueForGeneAtIndex, maxValueForGeneAtIndex };
	}

	public String getLabel() {
		return label;
	}

	public ParamCollection getSuperCollection() {
		return superCollection;
	}

	public int getIndexWithinSuperCollection() {
		return index;
	}

	/**
	 * Get the minimum allowable value for a gene at the given index within its collection (either this collection or a
	 * sub-collection of this collection).
	 * 
	 * @param index The index of the param gene in its collection.
	 */
	public double getMinValueForGeneAtIndex(int index) {
		return getMinOrMaxValueForGeneAtIndex(MIN_VALUE, index);
	}

	/**
	 * Get the maximum allowable value for a gene at the given index within its collection (either this collection or a
	 * sub-collection of this collection).
	 * 
	 * @param index The index of the param gene in its collection.
	 */
	public double getMaxValueForGeneAtIndex(int index) {
		return getMinOrMaxValueForGeneAtIndex(MAX_VALUE, index);
	}

	/**
	 * Get the minimum or maximum allowable value for a gene at the given index within its collection (either this
	 * collection or a sub-collection of this collection).
	 * 
	 * @param minOrMax Whether to get the minimum or maximum allowable value, specified by {@link #MIN_VALUE} and
	 *            {@link #MAX_VALUE}.
	 * @param index The index of the param gene within its collection.
	 * @throws IllegalStateException if this collection or its super collection.
	 */
	public double getMinOrMaxValueForGeneAtIndex(int minOrMax, int index) {
		if (minMaxValueForGeneAtIndex == null) {
			if (minMaxValue == null) {
				if (superCollection != null) {
					return superCollection.getMinOrMaxValueForGeneAtIndex(minOrMax, index);
				}
				throw new IllegalStateException("The ParamCollection " + label + " does not define a min or max value and does not have a super-collection.");
			}
			return minMaxValue[minOrMax];
		}
		return minMaxValueForGeneAtIndex[minOrMax][index];
	}
	
	public String toString() {
		return label + (superCollection != null ? (" : " + index + "->" + superCollection) : "");
	}
}
