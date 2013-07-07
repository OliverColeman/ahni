package com.ojcoleman.ahni.util;

/**
 * Represents a numeric range.
 */
public class Range {
	double start = 0, end = 1, range = 1;

	/**
	 * Create unit range [0, 1].
	 */
	public Range() {
	}

	public Range(double s, double e) {
		start = s;
		end = e;
		range = e - s;
	}

	public double getStart() {
		return start;
	}

	public double getEnd() {
		return end;
	}

	/**
	 * @return a value equivalent to getEnd() - getStart().
	 */
	public double getRange() {
		return range;
	}

	/**
	 * Translate a value from the unit range [0, 1] to the corresponding value from this range.
	 */
	public double translateFromUnit(double p) {
		return start + p * range;
	}

	/**
	 * Translate a value from this range to the corresponding value from the unit range [0, 1].
	 */
	public double translateToUnit(double p) {
		return (p - start) / range;
	}

	/**
	 * Ensure the given value is in the unit range.
	 * 
	 * @param v The value to check.
	 * @param label If not null and the value given is not within the range [0, 1] then an exception thrown using the
	 *            given label as identifier.
	 * @return true iff the given value is in the range [0, 1], otherwise false if label == null, otherwise an
	 *         IllegalArgumentException is thrown indicating that the value identified by label is not in the correct
	 *         range.
	 */
	public static boolean checkUnitRange(double v, String label) {
		if (v >= 0 && v <= 1)
			return true;
		if (label == null)
			return false;
		throw new IllegalArgumentException(label + " must be in the range [0, 1] but " + v + " was given.");
	}
}