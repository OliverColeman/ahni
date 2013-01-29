package com.ojcoleman.ahni.util;

public class Point {
	public double x, y, z;

	public Point(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * @return true iff this Point and the given Point have exactly the same coordinates.
	 */
	@Override
	public boolean equals(Object o) {
		if (o instanceof Point) {
			Point p = (Point) o;
			return p.x == this.x && p.y == this.y && p.z == this.z;
		}
		return false;
	}

	/**
	 * Calculates a hash code based on the sum of this points coordinates. The coordinates are assumed to lie in the
	 * range [-1, 1].
	 */
	@Override
	public int hashCode() {
		// x, y and z should be between -1 and 1.
		return (int) ((x + y + z) * (Integer.MAX_VALUE / 3));
	}

	@Override
	public String toString() {
		return "(" + (float) x + "," + (float) y + "," + (float) z + ")";
	}

	/**
	 * Assuming this Point has coordinates in the ranges specified, translate the coordinates of this Point to the
	 * corresponding values in the unit range [0, 1].
	 */
	public void translateToUnit(Range rangeX, Range rangeY, Range rangeZ) {
		x = rangeX.translateToUnit(x);
		y = rangeY.translateToUnit(y);
		z = rangeZ.translateToUnit(z);
	}
	
	/**
	 * Assuming this Point has coordinates in the unit range [0, 1], translate the coordinates of this Point to the
	 * corresponding values in the ranges specified.
	 */
	public void translateFromUnit(Range rangeX, Range rangeY, Range rangeZ) {
		x = rangeX.translateFromUnit(x);
		y = rangeY.translateFromUnit(y);
		z = rangeZ.translateFromUnit(z);
	}
}