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
 * Created on Feb 26, 2004 by Philip Tucker
 */
package com.anji.nn;

/**
 * A connection between neurons that caches its value from previous timestep. This is used to
 * avoid deadlock when activating neurons and connections in a loop.
 * 
 * @author Philip Tucker
 */
public class CacheNeuronConnection extends NeuronConnection {

	private float value;

	/**
	 * @see NeuronConnection#NeuronConnection(Neuron)
	 */
	public CacheNeuronConnection( Neuron anIncoming ) {
		super( anIncoming );
		reset();
	}

	/**
	 * @see NeuronConnection#NeuronConnection(Neuron, float)
	 */
	public CacheNeuronConnection( Neuron anIncoming, float aWeight ) {
		super( anIncoming, aWeight );
		reset();
	}

	/**
	 * @return float cached value
	 */
	public float read() {
		return value;
	}

	/**
	 * update value
	 */
	public void step() {
		value = Math.min(
				Math.max( getWeight() * getIncomingNode().getValue(), -Float.MAX_VALUE ),
				Float.MAX_VALUE );
	}

	/**
	 * @return String XML representation of object
	 */
	public String toXml() {
		StringBuffer result = new StringBuffer();
		result.append( "<" ).append( XML_TAG ).append( " id=\"" ).append( getId() );
		result.append( "\" src-id=\"" ).append( getIncomingNode().getId() );
		result.append( "\" weight=\"" ).append( getWeight() ).append( "\" />" );
		result.append( "\" recurrent=\"true\" />" );

		return result.toString();
	}

	/**
	 * clear cached value
	 */
	public void reset() {
		value = 0;
	}

	/**
	 * @return true
	 */
	public boolean isRecurrent() {
		return true;
	}

	/**
	 * @see com.anji.nn.Connection#cost()
	 */
	public long cost() {
		return 159;
	}


}
