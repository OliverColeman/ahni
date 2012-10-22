/*
 * Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of ANJI (Another NEAT Java Implementation).
 * 
 * ANJI is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Created on Mar 9, 2004 by Philip Tucker
 */
package com.anji.integration;

import com.anji.util.XmlPersistable;

/**
 * Although any object taking and returning double arrays can implement this, it is meant to be
 * a simple neural net interface where the arrays are input and output activation patterns.
 * @author Philip Tucker
 */
public interface Activator extends XmlPersistable {

/**
 * @return Object output array of type double with dimensions dependent on
 * implementation, given last provided input activation via
 * <code>nextSequence(double[])</code> or <code>nextSequence(double[][])</code>.
 * @see Activator#nextSequence(double[][])
 * @see Activator#nextSequence(double[][][])
 */
public Object next();

/**
 * @param stimuli
 * @return double[] output array given input <code>stimuli</code>.
 */
public double[] next( double[] stimuli );

/**
 * @param stimuli
 * @return double[][] sequence of output arrays given input sequence <code>stimult</code>.
 */
public double[][] nextSequence( double[][] stimuli );

/**
 * @param stimuli
 * @return double[][] output array given input <code>stimuli</code>.
 */
public double[][] next( double[][] stimuli );

/**
 * @param stimuli
 * @return double[][][] sequence of output arrays given input sequence <code>stimult</code>.
 */
public double[][][] nextSequence( double[][][] stimuli );

/**
 * @return String XML representation of object.
 */
public String toXml();

/**
 * reset object to initial state
 */
public void reset();

/**
 * @return String identifier, preferably unique, of object.
 */
public String getName();

/**
 * @return min response value
 */
public double getMinResponse();

/**
 * @return max response value
 */
public double getMaxResponse();

/**
 * @return dimension(s) of input array. If the input array is a single dimension vector then the returned array will have length 1; in general the returned array will have as many elements as there are dimensions in the input vector. 
 */
public int[] getInputDimension();

/**
 * @return dimension(s) of output array. If the output array is a single dimension vector then the returned array will have length 1; in general the returned array will have as many elements as there are dimensions in the output vector.
 */
public int[] getOutputDimension();
}


