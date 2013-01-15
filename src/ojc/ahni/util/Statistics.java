package ojc.ahni.util;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Provides methods to perform statistical analysis on a set of results.
 * TODO: provide methods to determine statistical significance between result sets.
 */
public class Statistics {
	/**
	 * Stores the raw result data, format is [generation][run].
	 */
	protected DescriptiveStatistics[] data;

	private int runs; // Number of runs represented.
	private int gens; // Number of generations represented.

	public Statistics(int generationCount) {
		this.data = new DescriptiveStatistics[generationCount];
		for (int g = 0; g < gens; g++) {
			data[g] = new DescriptiveStatistics();
		}
		runs = 0;
		gens = generationCount;
	}

	/**
	 * Creates a new set of results with the given data.
	 * 
	 * @param rawData The raw result data, in the format [generation][run].
	 */
	public Statistics(double[][] rawData) {
		runs = rawData[0].length;
		gens = rawData.length;
		this.data = new DescriptiveStatistics[gens];
		for (int g = 0; g < gens; g++) {
			data[g] = new DescriptiveStatistics(rawData[g]);
		}
	}

	/**
	 * Get the number of runs represented.
	 */
	public int getRunCount() {
		return runs;
	}

	/**
	 * Get the number of generations represented.
	 */
	public int getGenerationCount() {
		return gens;
	}

	/**
	 * Adds a run to this set of results.
	 * 
	 * @param data An array containing the result from each generation of the run. The length of the array is expected
	 *            to be the same as getGenerationCount().
	 */
	public void addRun(double[] runData) {
		for (int g = 0; g < gens; g++) {
			data[g].addValue(runData[g]);
		}
		runs++;
	}

	/**
	 * Returns the mean (average) over the set of runs at the given generation number.
	 */
	public double getMean(int generation) {
		return data[generation].getMean();
	}

	/**
	 * Returns the standard deviation over the set of runs at the given generation number.
	 */
	public double getStandardDeviation(int generation) {
		return data[generation].getStandardDeviation();
	}

	/**
	 * Returns the variance over the set of runs at the given generation number.
	 */
	public double getVariance(int generation) {
		return data[generation].getVariance();
	}

	/**
	 * Returns an estimate for the pth percentile over the set of runs at the given generation number.
	 * 
	 * @param generation The generation number to retrieve statistics for.
	 * @param The requested percentile, in the range (0, 100].
	 */
	public double getPercentile(int generation, double p) {
		return data[generation].getPercentile(p);
	}

	/**
	 * Returns the minimum value over the set of runs at the given generation number.
	 * 
	 * @param generation The generation number to retrieve statistics for.
	 */
	public double getMin(int generation) {
		return data[generation].getMin();
	}

	/**
	 * Returns the maximum value over the set of runs at the given generation number.
	 * 
	 * @param generation The generation number to retrieve statistics for.
	 */
	public double getMax(int generation) {
		return data[generation].getMax();
	}

	/**
	 * Returns the underlying DescriptiveStatistics used to store and calculate statistics for the set of runs at the
	 * given generation number. This can be used to perform statistical calculations other than those provided.
	 */
	public DescriptiveStatistics getData(int generation) {
		return data[generation];
	}

	/**
	 * Returns an array containing the mean (average) over the set of runs for each generation.
	 */
	public double[] getMean() {
		double[] result = new double[gens];
		for (int g = 0; g < gens; g++) {
			result[g] = data[g].getMean();
		}
		return result;
	}

	/**
	 * Returns an array containing the standard deviation over the set of runs for each generation.
	 */
	public double[] getStandardDeviation() {
		double[] result = new double[gens];
		for (int g = 0; g < gens; g++) {
			result[g] = data[g].getStandardDeviation();
		}
		return result;
	}

	/**
	 * Returns an array containing the variance over the set of runs for each generation.
	 */
	public double[] getVariance() {
		double[] result = new double[gens];
		for (int g = 0; g < gens; g++) {
			result[g] = data[g].getVariance();
		}
		return result;
	}

	/**
	 * Returns an array containing an estimate for the pth percentile over the set of runs for each generation.
	 * 
	 * @param p The requested percentile, in the range (0, 100].
	 */
	public double[] getPercentile(double p) {
		double[] result = new double[gens];
		for (int g = 0; g < gens; g++) {
			result[g] = data[g].getPercentile(p);
		}
		return result;
	}

	/**
	 * Returns an array containing the variance over the set of runs for each generation.
	 */
	public double[] getMin() {
		double[] result = new double[gens];
		for (int g = 0; g < gens; g++) {
			result[g] = data[g].getMin();
		}
		return result;
	}

	/**
	 * Returns an array containing the variance over the set of runs for each generation.
	 */
	public double[] getMax() {
		double[] result = new double[gens];
		for (int g = 0; g < gens; g++) {
			result[g] = data[g].getMax();
		}
		return result;
	}
}
