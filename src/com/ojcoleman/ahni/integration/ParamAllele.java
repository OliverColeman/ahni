package com.ojcoleman.ahni.integration;

import java.text.DecimalFormat;
import java.util.Random;

import org.jgapcustomised.Allele;
import org.jgapcustomised.Gene;

/**
 * Generic parameter Allele that defines a single double floating point value.
 * 
 * @see ParamGene
 */
public class ParamAllele extends Allele {
	private static final DecimalFormat nf = new DecimalFormat(" 0.0000;-0.0000");

	/**
	 * Standard deviation of perturbations to values. This is generally set by FloatValueMutationOperator according to
	 * the loaded properties file.
	 */
	public static double RANDOM_STD_DEV = 1;

	private double value = 0;

	public String toString() {
		return "G-" + getGene().toString() + " [" + nf.format(value) + "]";
	}

	/**
	 * for hibernate
	 */
	private ParamAllele() {
		super();
	}

	/**
	 * @param paramGene
	 */
	public ParamAllele(ParamGene paramGene) {
		super(paramGene);
	}

	/**
	 * @see org.jgapcustomised.Allele#cloneAllele()
	 */
	public Allele cloneAllele() {
		ParamAllele allele = new ParamAllele(getGene());
		allele.setValue(value);
		return allele;
	}

	/**
	 * Set value to random value from a Gaussian distribution determined by {@link #RANDOM_STD_DEV}
	 * 
	 * @param a_numberGenerator
	 * @param onlyPerturbFromCurrentValue if true then the value is perturbed from its current value.
	 */
	public void setToRandomValue(Random a_numberGenerator, boolean onlyPerturbFromCurrentValue) {
		if (onlyPerturbFromCurrentValue)
			value += a_numberGenerator.nextGaussian() * RANDOM_STD_DEV;
		// value += (a_numberGenerator.nextBoolean() ? 1 : -1) * a_numberGenerator.nextDouble() * RANDOM_STD_DEV;
		else
			value = a_numberGenerator.nextGaussian() * RANDOM_STD_DEV;
		// value = (a_numberGenerator.nextBoolean() ? 1 : -1) * a_numberGenerator.nextDouble() * RANDOM_STD_DEV;

	}

	@Override
	public boolean isEquivalent(Allele otherAllele) {
		if (!(otherAllele instanceof ParamAllele))
			return false;
		ParamAllele other = (ParamAllele) otherAllele;
		return value == other.value;
	}

	/**
	 * Gets the value value.
	 */
	@Override
	public double getValue() {
		return value;
	}

	/**
	 * Sets the value value.
	 */
	@Override
	public void setValue(double aValue) {
		value = aValue;
	}

	public ParamGene getGene() {
		return (ParamGene) super.getGene();
	}
}
