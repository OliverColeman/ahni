package ojc.ahni.util;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
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

	/**
	 * Creates a new empty Statistics for run results consisting of the given number of generations.
	 */
	public Statistics(int generationCount) {
		this.data = new DescriptiveStatistics[generationCount];
		for (int g = 0; g < gens; g++) {
			data[g] = new DescriptiveStatistics();
		}
		runs = 0;
		gens = generationCount;
	}

	/**
	 * Creates a new Statistics based on the given results.
	 * @param results The results to generate statistics for.
	 */
	public Statistics(Results results) {
		runs = results.getSeriesCount();
		gens = results.getItemCount();
		this.data = new DescriptiveStatistics[gens];
		// Get data in format [generation][run].
		double[][] dataTranspose = ((Array2DRowRealMatrix) (new Array2DRowRealMatrix(results.getData(), false)).transpose()).getDataRef();
		for (int g = 0; g < gens; g++) {
			data[g] = new DescriptiveStatistics(dataTranspose[g]);
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
	 * @param runData An array containing the result from each generation of the run. The length of the array is expected
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
	 * @param p The requested percentile, in the range (0, 100].
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
	
	/**
	 * Returns a Results object that contains basic statistics, including mean, standard deviation, minimum, maximum
	 * and estimates of 25th, 50th and 75th percentiles.
	 */
	public Results getBasicStats() {
		String[] labels = new String[]{"Mean", "Std. Dev.", "Minimum", "25th pct.", "50th pct.", "75th pct.", "Maximum"};
		int seriesCount = labels.length;
		double[][] stats = new double[seriesCount][];
		stats[0] = getMean();
		stats[1] = getStandardDeviation();
		stats[2] = getMin();
		stats[3] = getPercentile(25);
		stats[4] = getPercentile(50);
		stats[5] = getPercentile(75);
		stats[6] = getMax();
		return new Results(stats, labels);
	}
}
