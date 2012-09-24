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
 * created by Philip Tucker on Sep 28, 2004
 */
package com.anji.nn;

/**
 * StepHourglassConnection Connection returning a value between 0 and 1, where 1 indicates the
 * fully allotted amount of time remaining and 0 indicates no time remainnig.
 */
public class StepHourglassConnection implements Connection {

private long maxSteps;

private long stepsRemaining;

/**
 * ctor
 */
public StepHourglassConnection() {
	super();
	reset( 1 );
}

/**
 * reset hourglass to have max time remaining
 * @param aMaxSteps maximum steps before hourglass is empty
 */
public void reset( int aMaxSteps ) {
	if ( aMaxSteps <= 0 )
		throw new IllegalArgumentException( "max steps must be > 0" );
	maxSteps = aMaxSteps;
	stepsRemaining = maxSteps;
}

/**
 * reset hourglass to have max time remaining
 */
public void reset() {
	stepsRemaining = maxSteps;
}

/**
 * returns ratio of time remaining to total time
 * @see com.anji.nn.Connection#read()
 */
public double read() {
	return ( stepsRemaining <= 0 ) ? 0 : ( (double) stepsRemaining / maxSteps );
}

/**
 * proceed one step closer to completion
 */
public void step() {
	--stepsRemaining;
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return new StringBuffer().append( stepsRemaining ).append( "/" ).append( maxSteps )
			.toString();
}

/**
 * @see com.anji.nn.Connection#toXml()
 */
public String toXml() {
	StringBuffer result = new StringBuffer();
	result.append( "<" ).append( XML_TAG ).append( " hourglass-steps-remaining=\"" );
	result.append( stepsRemaining ).append( "\" />" );
	return result.toString();
}

/**
 * @see com.anji.nn.Connection#cost()
 */
public long cost() {
	return 74;
}
}
