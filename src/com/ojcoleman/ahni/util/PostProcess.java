package com.ojcoleman.ahni.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.esotericsoftware.wildcard.Paths;

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
		try {
			if (op.equals("compressAverage") || op.equals("ca") || op.equals("generateStats") || op.equals("gs")) {
				BufferedReader resultsReader = new BufferedReader(new FileReader(new File(args.removeFirst())));
				BufferedWriter resultsWriter = new BufferedWriter(new FileWriter(new File(args.removeFirst())));
	
				if (op.equals("compressAverage") || op.equals("ca")) {
					compressAverage(resultsReader, resultsWriter, args.isEmpty() ? 100 : Integer.parseInt(args.removeFirst()));
				} else if (op.equals("generateStats") || op.equals("gs")) {
					generateStats(resultsReader, resultsWriter);
				}
				
				resultsReader.close();
				resultsWriter.close();
			}
			else if (op.equals("combineResults") || op.equals("cr")) {
				if (args.size() != 2) {
					System.err.println("It looks like you have too many arguments, this is probably because the input result file glob pattern was not enclosed in quoation marks.\n");
					printUsageAndExit();
				}
				String inputFiles = args.removeFirst().replace("\"", "").replace("'", "");
				File output = new File(args.removeFirst());
				BufferedWriter resultsWriter = new BufferedWriter(new FileWriter(output));
				combineResults(inputFiles, resultsWriter);
				resultsWriter.close();
				System.out.println("Wrote combined results to " + output.getAbsolutePath());
			}
			else {
				printUsageAndExit();
			}
		} catch (FileNotFoundException e) {
			System.err.println("Error opening input file.");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Error reading from intput file, or opening or writing to output file:");
			e.printStackTrace();
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
		System.out.println("    cr:  Combine the result files from multiple runs into one file.");
		System.out.println("        The <input file> argument should be the path to the result files as a glob pattern. The pattern ");
		System.out.println("        supports the typical glob format, and in order to match more than one file must necessarily include");
		System.out.println("        wildcard characters such as ? or *. NOTE: the input file pattern should be enclosed in quotation");
		System.out.println("        marks to avoid the shell or Java expanding it automatically."); 
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
		Results resultsIn = new Results(resultsReader);
		Results resultsOut = compressAverage(resultsIn, size);
		resultsWriter.write(resultsOut.toString());
	}
	
	/**
	 * Given a result file from a set of runs, for each run computes averages over some number of generations (the
	 * window size) within the run. This is useful if the results are to be plotted and the plotting software can't
	 * handle many thousands of data points.
	 * 
	 * @param results results to compress.
	 * @param size the desired size of the output file (number of result averages). The window size is then computed as
	 *            ([number of original generations] / size).
	 * @return the compressed results.
	 */
	public static Results compressAverage(Results results, int size) {
		double[][] cData = new double[results.getSeriesCount()][size];
		for (int series = 0; series < results.getSeriesCount(); series++) {
			int window = results.getItemCount() / size;
			float accum = 0;
			int count = 0;
			int outItemIndex = 0;
			for (int item = 0; item < results.getItemCount(); item++) {
				accum += results.getData(series, item);
				count++;
				// If at the end of the window or end of the series.
				if (count == window || item == results.getItemCount() - 1) {
					cData[series][outItemIndex] = accum / count;
					accum = 0;
					count = 0;
					outItemIndex++;
				}
			}
		}
		return new Results(cData, results.getLabels());
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
		Statistics stats = new Statistics(new Results(resultsReader));
		resultsWriter.write(stats.getBasicStats().toString());
	}
	
	/**
	 * Combine the results from multiple runs into one file.
	 * @param filePattern The path to the result files. The pattern supports the typical glob format, and in 
	 *   order to match more than one file must necessarily include wildcard characters such as ? or *.
	 * @param resultsWriter A stream to write the results to.
	 */
	public static void combineResults(String filePattern, BufferedWriter resultsWriter) throws IOException {
		Results results = combineResults(filePattern, true);
		resultsWriter.write(results.toString());
	}
	
	/**
	 * Combine the results from multiple runs into one Results object.
	 * @param filePattern The path to the result files. The pattern supports the typical glob format, and in 
	 *   order to match more than one file must necessarily include wildcard characters such as ? or *.
	 * @return A Results object containing the combined results.
	 */
	public static Results combineResults(String filePattern, boolean verbose) throws IOException {
		String baseDir = "./";
		// If absolute path. TODO handle windows absolute path?
		if (filePattern.charAt(0) == File.separatorChar) {
			baseDir = File.separator;
			filePattern = filePattern.substring(1);
		}
		Paths paths = new Paths(baseDir, filePattern);
		List<File> files = paths.getFiles();
		Iterator<File> fileItr = files.iterator();

		File f = fileItr.next();
		if (verbose) System.out.println("Processing " + f.getAbsolutePath());
		Results results = new Results(new BufferedReader(new FileReader(f)));
		int resultCount = 1;
		
		while (fileItr.hasNext()) {
			f = fileItr.next();
			if (verbose) System.out.println("Processing " + f.getAbsolutePath());
			BufferedReader resultReader = new BufferedReader(new FileReader(f));
			results.add(new Results(resultReader));
			resultCount++;
		}
		if (verbose) System.out.println("Combined " + resultCount + " results.");
		return results;
	}
}
