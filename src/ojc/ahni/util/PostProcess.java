package ojc.ahni.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import ojc.ahni.hyperneat.Properties;

import com.beust.jcommander.Parameter;

/**
 * Contains utility methods to perform post-processing on the results of a run or multiple runs.
 */
public class PostProcess {
	/**
	 * Utility method to process command line arguments, set-up file IO, and call required post-processing method.
	 */
	public static void main(String[] argsArray) {
		Deque<String> args = new ArrayDeque<String>();
		for (String a : argsArray)
			args.add(a);

		if (args.isEmpty()) {
			printUsageAndExit();
		}

		String op = args.removeFirst();
		if (op.equals("compressAverage") || op.equals("ca") || op.equals("generateStats") || op.equals("gs")) {
			try {
				BufferedReader resultsReader = new BufferedReader(new FileReader(new File(args.removeFirst())));
				BufferedWriter resultsWriter = new BufferedWriter(new FileWriter(new File(args.removeFirst())));
	
				if (op.equals("compressAverage") || op.equals("ca")) {
					compressAverage(resultsReader, resultsWriter, args.isEmpty() ? 100 : Integer.parseInt(args.removeFirst()));
				} else if (op.equals("generateStats") || op.equals("gs")) {
					generateStats(resultsReader, resultsWriter);
				}
	
				resultsReader.close();
				resultsWriter.close();
			} catch (FileNotFoundException e) {
				System.err.println("Error opening input file.");
				e.printStackTrace();
			} catch (IOException e) {
				System.err.println("Error reading from intput file, or opening or writing to output file:");
				e.printStackTrace();
			}
		}
		else {
			printUsageAndExit();
		}
	}

	private static void printUsageAndExit() {
		System.out.println("The post process utility performs post processing on a result file from a set of runs.");
		System.out.println("Usage:\n<post process command> <op> <input file> [<output file>] [options]");
		System.out.println("  op may be generateStats (ga) or compressAverage (ca).");
		System.out.println("    gs: calculates basic statistics over all runs for each generation.");
		System.out.println("    ca: For each run, computes averages over some number of generations (the window size) within the run.");
		System.out.println("        The default number of result values for each run in the output file is 100,");
		System.out.println("        but a different number of result values can be specified after the name of the output file.");
		System.exit(0);
	}

	/**
	 * Given a result file from a set of runs, for each run computes averages over some number of generations (the
	 * window size) within the run. This is useful if the results are to be plotted and the plotting software can't
	 * handle many thousands of data points.
	 * 
	 * @param resultsReader input stream to read data from file containing the original results.
	 * @param resultsWriter output stream to write generated data.
	 * @param size the desired size of the output file (number of result averages). The window size is then computed as
	 *            ([number of original generations] / size).
	 * @throws IOException
	 */
	public static void compressAverage(BufferedReader resultsReader, BufferedWriter resultsWriter, int size) throws IOException {
		double[][] data = readResults(resultsReader);
		for (int run = 0; run < data.length; run++) {
			int window = data[run].length / size;
			float accum = 0;
			int count = 0;
			for (int i = 0; i < data[run].length; i++) {
				accum += data[run][i];
				count++;
				if (count == window || i == data[run].length - 1) {
					resultsWriter.write((i >= window ? ", " : "") + (accum / count));
					accum = 0;
					count = 0;
				}
			}
			resultsWriter.write("\n");
		}
	}

	/**
	 * Given a result file from a set of runs, create a new file that contains basic statistics over the runs for each
	 * generation.
	 * 
	 * @param resultsReader input stream to read data from file containing the original results.
	 * @param resultsWriter output stream to write generated data.
	 * @throws IOException
	 */
	public static void generateStats(BufferedReader resultsReader, BufferedWriter resultsWriter) throws IOException {
		double[][] rawData = readResults(resultsReader);
		resultsWriter.write(generateStats(rawData));
	}

	/**
	 * Given the results from a set of runs, return a String that contains basic statistics over the runs for each
	 * generation.
	 * 
	 * @param rawData The raw result data, in the format [generation][run].
	 */
	public static String generateStats(double[][] rawData) {
		Statistics data = new Statistics(rawData);
		DecimalFormat nf = new DecimalFormat("0.0000");
		StringBuilder output = new StringBuilder();
		output.append("Mean, ");
		output.append(ArrayUtil.toString(data.getMean(), ", ", nf));
		output.append("\nStd. Dev., ");
		output.append(ArrayUtil.toString(data.getStandardDeviation(), ", ", nf));
		output.append("\nMinimum, ");
		output.append(ArrayUtil.toString(data.getMin(), ", ", nf));
		output.append("\nMaximum, ");
		output.append(ArrayUtil.toString(data.getMax(), ", ", nf));
		output.append("\n25th pct., ");
		output.append(ArrayUtil.toString(data.getPercentile(25), ", ", nf));
		output.append("\n50th pct., ");
		output.append(ArrayUtil.toString(data.getPercentile(50), ", ", nf));
		output.append("\n75th pct., ");
		output.append(ArrayUtil.toString(data.getPercentile(75), ", ", nf));

		output.append("\n");
		return output.toString();
	}

	/**
	 * Read and return the results from a set of runs.
	 * 
	 * @return The result data, in the format [generation][run].
	 */
	public static double[][] readResults(BufferedReader resultsReader) throws IOException {
		ArrayList<String[]> stringData = new ArrayList<String[]>();
		String line;
		while ((line = resultsReader.readLine()) != null) {
			stringData.add(line.replaceAll(" ", "").split(","));
		}
		int runs = stringData.size();
		int gens = stringData.get(0).length;
		double[][] results = new double[gens][runs];
		for (int r = 0; r < runs; r++) {
			String[] data = stringData.get(r);
			for (int g = 0; g < gens; g++) {
				results[g][r] = Double.parseDouble(data[g]);
			}
		}
		return results;
	}
}
