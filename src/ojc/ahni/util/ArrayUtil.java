package ojc.ahni.util;

public class ArrayUtil {
	/**
	 * @param a The array to print.
	 * @param prefix A string to prefix to the start of each new line/element.
	 * @return String representation of the given array with each element on a new line.
	 */
	public static String toString(int[] a, String prefix) {
		StringBuffer result = new StringBuffer();
		if (a.length > 0)
			result.append(prefix).append(a[0]);
		for (int i = 1; i < a.length; i++)
			result.append("\n").append(prefix).append(a[i]);
		return result.toString();
	}

	/**
	 * @param a The array to print.
	 * @param prefix A string to prefix to the start of each new line/element.
	 * @return String representation of the given array with each element on a new line.
	 */
	public static String toString(double[] a, String prefix) {
		StringBuffer result = new StringBuffer();
		if (a.length > 0)
			result.append(prefix).append(a[0]);
		for (int i = 1; i < a.length; i++)
			result.append("\n").append(prefix).append(a[i]);
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
}
