package ojc.ahni.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Contains utility methods to perform post-processing on the results of a run or multiple runs.
 */
public class PostProcess {
	public static void process(String[] argsArray) {
		Deque<String> args = new ArrayDeque<String>();
		for (String a : argsArray) args.add(a);
		args.removeFirst(); // Remove "pp" or "postProcess" command.
		
		System.out.println("Running post process with commands " + args);
		
		String op = args.removeFirst();
		try {
			BufferedReader resultsReader = new BufferedReader(new FileReader(new File(args.removeFirst())));
			BufferedWriter resultsWriter = new BufferedWriter(new FileWriter(new File(args.removeFirst())));
			
			if (op.equals("compressAverage") || op.equals("ca")) {
				compressAverage(resultsReader, resultsWriter, args.isEmpty() ? 100 : Integer.parseInt(args.removeFirst()));
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
	
	/**
	 * Given a comma-separated list of fitness or performance results for each generation, compute the average over some window and create a new file consisting of these averages.
	 * 
	 * @param resultsFile the File containing the original results.
	 * @param size the desired size of the output file (number of results). The window size is then computed as ([number of original generations] / size).
	 * @throws IOException 
	 */ 
	public static void compressAverage(BufferedReader resultsReader, BufferedWriter resultsWriter, int size) throws IOException {
		String[] data = resultsReader.readLine().replaceAll(" ", "").split(",");
		int window = data.length / size;
		float accum = 0;
		int count = 0;
		for (int i = 0; i < data.length; i++) {
			accum += Float.parseFloat(data[i]);
			count++;

			if (count == window || i == data.length-1) {
				resultsWriter.write((i >= window ? ", " : "") + (accum/count));
				accum = 0;
				count = 0;
			}
		}
		resultsWriter.write("\n");
	}
}
