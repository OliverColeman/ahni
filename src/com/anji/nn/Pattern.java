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
 * Created on Feb 27, 2004 by Philip Tucker
 */
package com.anji.nn;

import java.util.Arrays;

/**
 * Converts double array stimuli to Connection objects.
 * 
 * @author Philip Tucker
 */
public class Pattern {

/**
 * base XML tag
 */
public final static String XML_TAG = "pattern";

/**
 * Connection between stimuli and input neurons.
 * 
 * @author Philip Tucker
 */
class PatternConnection implements Connection {

private int idx;

/**
 * Create connection between stimulus <code>anIdx</code>.
 * 
 * @param anIdx
 */
public PatternConnection( int anIdx ) {
	idx = anIdx;
}

/**
 * @return double stimulus value
 */
public double read() {
	return values[ idx ];
}

/**
 * @see Connection#toXml()
 */
public String toXml() {
	StringBuffer result = new StringBuffer();
	result.append( "<" ).append( Connection.XML_TAG );
	result.append( "\" from-input=\"" ).append( idx ).append( "\" />" );

	return result.toString();
}

/**
 * @return stimulus index
 */
int getIdx() {
	return idx;
}

/**
 * @see com.anji.nn.Connection#cost()
 */
public long cost() {
	return 41;
}

}

/**
 * <code>protected</code> visibility increases performance of
 * <code>PatternConnection.read()</code>
 */
protected double[] values = null;

private PatternConnection[] conns = null;

/**
 * Create Pattern with <code>dimension</code> inputs. This ctor should be called if
 * <code>Pattern</code> object is to manage array of values.
 * 
 * @param dimension
 * @throws IllegalArgumentException
 */
public Pattern( int dimension ) throws IllegalArgumentException {
	super();
	if ( dimension <= 0 )
		throw new IllegalArgumentException( "dimension must be > 0" );
	init( new double[ dimension ] );
}

/**
 * Create Pattern with <code>aValues</code> as inputs. This ctor should be called if caller is
 * to manager array of values;
 * 
 * @param aValues
 * @throws IllegalArgumentException
 */
public Pattern( double[] aValues ) throws IllegalArgumentException {
	super();
	init( aValues );
}

private void init( double[] aValues ) {
	values = aValues;
	conns = new PatternConnection[ aValues.length ];
}

/**
 * set values to 0
 */
public void clear() {
	Arrays.fill( values, 0 );
}

/**
 * Set stimulus values.
 * 
 * @param someValues
 * @throws IllegalArgumentException
 */
public void setValues( double[] someValues ) throws IllegalArgumentException {
	if ( someValues.length != values.length )
		throw new IllegalArgumentException( "can not change array dimension" );
	values = someValues;
}

/**
 * Set stimulus value at <code>idx</code> to <code>value</code>.
 * 
 * @param idx
 * @param value
 * @throws IllegalArgumentException
 */
public void setValue( int idx, double value ) throws IllegalArgumentException {
	try {
		values[ idx ] = value;
	}
	catch ( ArrayIndexOutOfBoundsException e ) {
		throw new IllegalArgumentException( "idx out of bounds" );
	}
}

/**
 * Get connection to stimulus <code>idx</code>.
 * 
 * @param idx
 * @return Connection
 * @throws IllegalArgumentException
 */
public Connection getConnection( int idx ) throws IllegalArgumentException {
	try {
		if ( conns[ idx ] == null )
			conns[ idx ] = new PatternConnection( idx );
		return conns[ idx ];
	}
	catch ( ArrayIndexOutOfBoundsException e ) {
		throw new IllegalArgumentException( "idx out of bounds" );
	}
}

/**
 * @return int dimension of stimuli
 */
public int getDimension() {
	return values.length;
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return com.anji.util.Arrays.toString( values );
}

/**
 * @return String XML respresentation of object
 */
public String toXml() {
	StringBuffer result = new StringBuffer();
	result.append( "<" ).append( XML_TAG ).append( ">\n" );
	for ( int i = 0; i < values.length; ++i )
		result.append( "<value index=\"" ).append( i ).append( "\" >" ).append( values[ i ] )
				.append( "</value>\n" );
	result.append( "</" ).append( XML_TAG ).append( ">\n" );

	return result.toString();
}

}
