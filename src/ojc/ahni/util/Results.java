package ojc.ahni.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;

/** 
 * Storage for results data.
 */
public class Results {
	private static DecimalFormat nf = new DecimalFormat("0.0000");
	
	private int seriesCount;
	private int itemCount;
	private String[] labels;
	private double[][] data;
	
	/**
	 * Creates an empty Results. Data can be added via the {@link #add(Results)} or {@link #add(double[], String)} methods. 
	 * 
	 * @param data The raw data in the format [series][item];
	 * @param labels Labels for each data series, null should be given if no labels are available.
	 */ 
	public Results() {
	}

	/**
	 * Creates a new Results with the given data.
	 * 
	 * @param data The raw data in the format [series][item]. This is copied by reference.
	 * @param labels Labels for each data series, null can be given if no labels are available. This is copied by reference.
	 */ 
	public Results(double[][] data, String[] labels) {
		itemCount = data[0].length;
		seriesCount = data.length;
		this.data = data;
		this.labels = labels == null ? new String[seriesCount] : labels;
	}
	
	/**
	 * Copy constructor to create a new Results copied from the given Results.
	 * 
	 * @param results The Results to copy the data from.
	 */ 
	public Results(Results results) {
		itemCount = results.itemCount;
		seriesCount = results.seriesCount;
		data = new double[seriesCount][];
		for (int s = 0; s < seriesCount; s++) {
			data[s] = results.data[s].clone();
		}
		labels = results.labels.clone();
	}
	
	/**
	 * Creates a Results from data read in from a CSV formatted text data stream.
	 * The CSV formatted text data stream should have each data series as a column.
	 */
	public Results(BufferedReader resultsReader) throws IOException {
		ArrayList<String[]> stringData = new ArrayList<String[]>();
		String line = resultsReader.readLine();
		String[] lineSplit = line.replaceAll(" ", "").split(",");
		if (NumberUtils.isNumber(lineSplit[0])) {
			stringData.add(lineSplit);
		}
		else {
			labels = lineSplit;
		}
		while ((line = resultsReader.readLine()) != null) {
			stringData.add(line.replaceAll(" ", "").split(","));
		}
		seriesCount = stringData.get(0).length;
		itemCount = stringData.size();
		data = new double[seriesCount][itemCount];
		for (int i = 0; i < itemCount; i++) {
			String[] itemData = stringData.get(i);
			for (int s = 0; s < seriesCount; s++) {
				data[s][i] = Double.parseDouble(itemData[s]);
			}
		}
	}
	
	/**
	 * @return 	The number of data series.
	 */
	public int getSeriesCount() {
		return seriesCount;
	}

	/**
	 * @return The number of data items in each series.
	 */
	public int getItemCount() {
		return itemCount;
	}

	/**
	 * @return True iff any of the data series has a label (is not null or an empty String).
	 */
	public boolean hasLabels() {
		for (int s = 0; s < seriesCount; s++)
			if (labels[s] != null && !labels[s].trim().isEmpty()) return true;
		return false;
	}

	/**
	 * @return The labels for the each series.
	 */
	public String[] getLabels() {
		return labels;
	}

	/**
	 * @return The data for the given series and item.
	 */
	public double getData(int series, int item) {
		return data[series][item];
	}

	/**
	 * @return The data for the given series.
	 */
	public double[] getData(int series) {
		return data[series];
	}

	/**
	 * @return The underlying data array as a reference, in the format [series][item].
	 */
	public double[][] getData() {
		return data;
	}
	
	/**
	 * Returns a CSV string representation of this Results with a column for each series.
	 */
	@Override
	public String toString() {
		StringBuilder output = new StringBuilder();
		if (hasLabels()) {
			output.append(ArrayUtil.toString(labels, ", ") + "\n");
		}
		for (int item = 0; item < itemCount; item++) {
			output.append(nf.format(data[0][item]));
			for (int series = 1; series < seriesCount; series++) {
				output.append(", " + nf.format(data[series][item]));
			}
			output.append("\n");
		}
		return output.toString();
	}
	
	
	/**
	 * Adds (concatenates) the data series from the given Results to this Results.
	 * The total number of series in this Results will be increased by the number of series in the given Results.
	 * @param results The data to add. The internal data is copied by reference, if independent changes need to be
	 *   made to either Results object then the Results passed by this parameter should be a copy, e.g. via 
	 *   the copy constructor {@link #Results(Results)}.
	 */
	public void add(Results results) {
		if (seriesCount == 0) { // Bit pointless but may as well handle it.
			data = results.data;
			labels = results.labels;
			itemCount = results.itemCount;
			seriesCount = results.seriesCount;
		}
		else {
			if (itemCount != results.itemCount) {
				throw new IllegalArgumentException("Item counts must be the same when adding Results objects.");
			}
			labels = ArrayUtils.addAll(labels, results.labels);
			data = ArrayUtils.addAll(data, results.data);
			seriesCount += results.seriesCount;
		}
	}
	
	/**
	 * Adds (concatenates) the data series from the given Results to this Results.
	 * The total number of series in this Results will be increased by the number of series in the given Results.
	 * @param results The data to add. This is only copied by reference.
	 * @param results The label for the added data series. May be null.
	 */
	public void add(double[] seriesData, String label) {
		if (seriesCount == 0) {
			seriesCount = 1;
			itemCount = seriesData.length;
			data = new double[1][];
			data[0] = seriesData;
			labels = new String[]{label};
		}
		else {
			if (itemCount != seriesData.length) {
				throw new IllegalArgumentException("Item counts must be the same when adding Results objects.");
			}
			labels = ArrayUtils.add(labels, label);
			data = ArrayUtils.add(data, seriesData);
			seriesCount ++;
		}
	}
}