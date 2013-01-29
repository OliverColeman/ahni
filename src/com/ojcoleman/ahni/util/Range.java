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
		range = e-s;
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
}