package com.ojcoleman.ahni.util;

import java.lang.reflect.Array;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class ArrayUtil {
	/**
	 * @param a The array to print.
	 * @param separator A string to separate each element.
	 * @param formatter A formatter to format the values. If null given the numbers will be printed raw.
	 * @return String representation of the given array.
	 */
	public static String toString(int[] a, String separator, NumberFormat formatter) {
		StringBuffer result = new StringBuffer();
		if (formatter != null) {
			if (a.length > 0)
				result.append(formatter.format(a[0]));
			for (int i = 1; i < a.length; i++)
				result.append(separator).append(formatter.format(a[i]));
		}
		else {
			if (a.length > 0)
				result.append(a[0]);
			for (int i = 1; i < a.length; i++)
				result.append(separator).append(a[i]);
		}
		return result.toString();
	}
	
	/**
	 * @param a The array to print.
	 * @param separator A string to separate each element.
	 * @param formatter A formatter to format the values. If null given the numbers will be printed raw.
	 * @return String representation of the given array.
	 */
	public static String toString(byte[] a, String separator, NumberFormat formatter) {
		StringBuffer result = new StringBuffer();
		if (formatter != null) {
			if (a.length > 0)
				result.append(formatter.format(a[0]));
			for (int i = 1; i < a.length; i++)
				result.append(separator).append(formatter.format(a[i]));
		}
		else {
			if (a.length > 0)
				result.append(a[0]);
			for (int i = 1; i < a.length; i++)
				result.append(separator).append(a[i]);
		}
		return result.toString();
	}
	
	/**
	 * @param a The array to print.
	 * @param separator A string to separate each element.
	 * @param formatter A formatter to format the values. If null given the numbers will be printed raw.
	 * @return String representation of the given array.
	 */
	public static String toString(double[] a, String separator, NumberFormat formatter) {
		StringBuffer result = new StringBuffer();
		if (formatter != null) {
			if (a.length > 0)
				result.append(formatter.format(a[0]));
			for (int i = 1; i < a.length; i++)
				result.append(separator).append(formatter.format(a[i]));
		}
		else {
			if (a.length > 0)
				result.append(a[0]);
			for (int i = 1; i < a.length; i++)
				result.append(separator).append(a[i]);
		}
		return result.toString();
	}
	
	/**
	 * @param a The array to print.
	 * @param separator A string to separate each element.
	 * @return String representation of the given array.
	 */
	public static<T> String toString(T[] a, String separator) {
		StringBuffer result = new StringBuffer();
		if (a.length > 0)
			result.append(a[0]);
		for (int i = 1; i < a.length; i++)
			result.append(separator).append(a[i]);
		return result.toString();
	}
	
	
	
	/**
	 * @param a The array to sum.
	 * @return The sum over all elements in the array.
	 */
	public static double sum(double[] a) {
		double sum = 0;
		for (int i = 0; i < a.length; i++)
			sum += a[i];
		return sum;
	}
	
	/**
	 * @param a The array to sum.
	 * @return The sum over all elements in the array.
	 */
	public static long sum(int[] a) {
		long sum = 0;
		for (int i = 0; i < a.length; i++)
			sum += a[i];
		return sum;
	}

	/**
	 * @param a The array to calculate the average for.
	 * @return The average over all elements in the array.
	 */
	public static double average(double[] a) {
		return sum(a) / a.length;
	}
	
	/**
	 * @param a The array to calculate the average for.
	 * @return The average over all elements in the array.
	 */
	public static double length(double[] a) {
		double c = 0;
		for (int i = 0; i < a.length; i++)
			c += a[i] * a[i];
		return Math.sqrt(c);
	}
	
	/**
	 * @param a The array to normalise (the values in this array are altered).
	 * @return The given array with normalised values such that length(a) will return 1.
	 */
	public static double[] normalise(double[] a) {
		double lengthInv = 1.0 / length(a);
		for (int i = 0; i < a.length; i++)
			a[i] *= lengthInv;
		return a;
	}
	
	/**
	 * @param a The array to normalise (the values in this array are altered).
	 * @return The given array with normalised values such that sum(a) will return 1.
	 */
	public static double[] normaliseSum(double[] a) {
		double sumInv = 1.0 / sum(a);
		for (int i = 0; i < a.length; i++)
			a[i] *= sumInv;
		return a;
	}

	/** 
	 * Create a new array containing random values in the range [0, 1).
	 */
	public static double[] newRandom(int size, Random r) {
		double[] a = new double[size];
		for (int i = 0; i < size; i++) {
			a[i] = r.nextDouble();
		}
		return a;
	}
	
	/** 
	 * Create a new array containing random values in the range [0, max).
	 */
	public static double[] newRandom(int size, Random r, double max) {
		double[] a = new double[size];
		for (int i = 0; i < size; i++) {
			a[i] = r.nextDouble() * max;
		}
		return a;
	}

	/** 
	 * Create a new array containing random values in the range [0, max).
	 */
	public static double[] newRandom(int size, Random r, double min, double max) {
		double[] a = new double[size];
		double range = max - min;
		for (int i = 0; i < size; i++) {
			a[i] = min + r.nextDouble() * range;
		}
		return a;
	}

	/**
	 * Returns the index of the element with the largest value in the given array.
	 */
	public static int getMaxIndex(double[] a) {
		int maxIndex = 0;
		for (int i = 1; i < a.length; i++) {
			if (a[i] > a[maxIndex])
				maxIndex = i;
		}
		return maxIndex;
	}
	
	/**
	 * Returns the largest value in the given array.
	 */
	public static double getMaxValue(double[] a) {
		return a[getMaxIndex(a)];
	}

	/** 
	 * Returns a new array that is the result of row-packing the given 2D array into a 1D array.
	 * @see #unpack(double[], int, int, int)
	 */
	public static double[] pack(double[][] unpacked) {
		int width = unpacked[0].length;
		int height = unpacked.length;
		double[] packed = new double[width * height];
		return pack(unpacked, packed);
	}
	
	/** 
	 * Row-packs the values in the given 2D array into the given 1D array.
	 * @param unpacked The unpacked array.
	 * @param packed The array to copy the result into.
	 * @return a reference to the array passed in for parameter packed.
	 * @see #unpack(double[], int, int, int)
	 */
	public static double[] pack(double[][] unpacked, double[] packed) {
		int width = unpacked[0].length;
		int height = unpacked.length;
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				packed[i++] = unpacked[y][x];
			}
		}
		return packed;
	}

	/** 
	 * Returns a new array that is the result of unpacking the given 1D array into a 2D array, row-first.
	 * @param packed The packed array.
	 * @param width The width, or number of columns, in the unpacked array.
	 * @param height The height, or number of rows, in the unpacked array.
	 * @param outputIndex The index to start reading from in the packed array.
	 * @see #pack(double[][])
	 */
	public static double[][] unpack(double[] packed, int width, int height, int outputIndex) {
		double[][] unpacked = new double[height][width];
		return unpack(packed, unpacked, outputIndex); 
	}
	
	/** 
	 * Unpacks the values in the given 1D array into the given 2D array, row-first.
	 * @param packed The packed array.
	 * @param unpacked an array to copy the result into. Should have dimensions [height/rows][width/columns].
	 * @param outputIndex The index to start reading from in the packed array.
	 * @return a reference to the array passed in for parameter unpacked.
	 * @see #pack(double[][])
	 */
	public static double[][] unpack(double[] packed, double[][] unpacked, int outputIndex) {
		int width = unpacked[0].length;
		int height = unpacked.length;
		int i = outputIndex;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				unpacked[y][x] = packed[i++];
			}
		}
		return unpacked;
	}
	
	public static double[] newArray(int length, double initialValues) {
		double[] a = new double[length];
		if (initialValues != 0) Arrays.fill(a, initialValues);
		return a;
	}
	public static int[] newArray(int length, int initialValues) {
		int[] a = new int[length];
		if (initialValues != 0) Arrays.fill(a, initialValues);
		return a;
	}
	
	/**
	 * Returns a new array that contains the negated values of the given array.
	 */ 
	public static double[] negate(double[] a) {
		double[] n = new double[a.length];
		for (int i = 0; i < a.length; i++) {
			n[i] = -a[i];
		}
		return n;
	}
	
	/**
	 * Calculates the distance between two points represented by (n-dimensional) vectors.
	 */
	public static double distance(double[] a, double[] b) {
		double dist = 0;
		for (int i = 0; i < a.length; i++) {
			double d = a[i] - b[i];
			dist += d*d;
		}
		return Math.sqrt(dist);
	}

	/**
	 * Creates a new array containing every integer value in the range [0, size) in a random order.
	 * All permutations occur with equal likelihood assuming that the source of randomness is fair.
	 */
	public static int[] newRandomIndexing(int size, Random random) {
		int[] index = new int[size];
		for (int i = 0; i < size; i++) index[i] = i;
		for (int i = size; i > 1; i--) {
			int ri = random.nextInt(i);
			int tmp = index[i-1];
			index[i-1] = index[ri];
			index[ri] = tmp;
		}
		return index;
	}
}
