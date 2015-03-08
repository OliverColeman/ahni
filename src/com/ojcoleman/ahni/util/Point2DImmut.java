package com.ojcoleman.ahni.util;

import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * The <code>Point2DImmut</code> class defines a {@link Point2D} specified in
 * <code>double</code> precision whose coordinates immutable.
 * @since 1.2
 */
public class Point2DImmut extends Point2D implements Serializable {
    /**
     * The X coordinate of this <code>Point2D</code>.
     */
    public final double x;

    /**
     * The Y coordinate of this <code>Point2D</code>.
     */
    public final double y;

    /**
     * Constructs and initializes a <code>Point2D</code> with the
     * specified coordinates.
     *
     * @param x the X coordinate of the newly
     *          constructed <code>Point2D</code>
     * @param y the Y coordinate of the newly
     *          constructed <code>Point2D</code>
     */
    public Point2DImmut(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * {@inheritDoc}
     */
    public double getX() {
        return x;
    }

    /**
     * {@inheritDoc}
     */
    public double getY() {
        return y;
    }

    /**
     * {@inheritDoc}
     * Throws an IllegalStateException as the coordinates of a Point2DImmut are immutable and may not be modified. 
     */
    public void setLocation(double x, double y) {
    	throw new IllegalStateException("The coordinates of a Point2DImmut are immutable and may not be modified.");
    }

    /**
     * Returns a <code>String</code> that represents the value
     * of this <code>Point2D</code>.
     * @return a string representation of this <code>Point2D</code>.
     */
    public String toString() {
        return "Point2DImmut["+x+", "+y+"]";
    }

    /*
     * JDK 1.6 serialVersionUID
     */
    private static final long serialVersionUID = 1L;
}