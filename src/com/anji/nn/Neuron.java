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
 * 
 * Modified 2010-06-16 by Oliver Coleman to allow for non-integrating activation functions
 */
package com.anji.nn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.anji.neat.NeuronType;
import com.anji.nn.activationfunction.ActivationFunction;
import com.anji.nn.activationfunction.ActivationFunctionNonIntegrating;
import com.anji.util.XmlPersistable;

/**
 * Neuron component of an artificial neural network. Performs an activation function on a sum of
 * inputs.
 * 
 * @author Philip Tucker
 */
public class Neuron implements XmlPersistable {

/**
 * Wrapper class around neuron to include neuron values plus other data that aid visualization.
 * 
 * @author Philip Tucker
 */
private static class NeuronMetaData {

private Neuron neuron;

private double hintPosition;

private int hintLayer;

private NeuronType type;

/**
 * @param aNeuron neuron this meta data describes
 */
public NeuronMetaData( Neuron aNeuron ) {
	neuron = aNeuron;
}

/**
 * @return int hint layer
 */
public int getHintLayer() {
	return hintLayer;
}

/**
 * @return double hint position in layer
 */
public double getHintPosition() {
	return hintPosition;
}

/**
 * @param i new hint layer
 */
public void setHintLayer( int i ) {
	hintLayer = i;
}

/**
 * @param f new hint position in layer
 */
public void setHintPosition( double f ) {
	hintPosition = f;
}

/**
 * @return type of neuron
 */
public NeuronType getType() {
	return type;
}

/**
 * @param aType type of neuron
 */
public void setType( NeuronType aType ) {
	this.type = aType;
}

/**
 * @return long innovation ID
 */
public long getId() {
	return neuron.getId();
}

/**
 * @return boolean true if neuron's value has not been updated for current time step
 */
public boolean isDirty() {
	return neuron.isDirty();
}

/**
 * @return ActivationFunction
 */
public ActivationFunction getActivationFunction() {
	return neuron.getFunc();
}

/**
 * @return double activation value of neuron
 */
public double getValue() {
	return neuron.getValue();
}
}

/**
 * Sorts <code>NeuronMetaData</code> objects based on position in layer.
 * 
 * @author Philip Tucker
 */
private static class NeuronMetaDataPositionComparator implements Comparator {

private static NeuronMetaDataPositionComparator instance = null;

private NeuronMetaDataPositionComparator() {
	// noop
}

/**
 * @return singleton instance
 */
public static NeuronMetaDataPositionComparator getInstance() {
	if ( instance == null )
		instance = new NeuronMetaDataPositionComparator();
	return instance;
}

/**
 * @see Comparator#compare(java.lang.Object, java.lang.Object)
 */
public int compare( Object o1, Object o2 ) {
	NeuronMetaData hc1 = (NeuronMetaData) o1;
	NeuronMetaData hc2 = (NeuronMetaData) o2;
	double result = hc1.getHintPosition() - hc2.getHintPosition();
	if ( result > 0 )
		return 1;
	else if ( result < 0 )
		return -1;
	else
		return 0;
}
}

/**
 * base XML tag
 */
public final static String XML_TAG = "neuron";

private long id = hashCode();

/**
 * protected for <code>TestNeuron</code> only
 */
protected double value;

private boolean dirty; // indicates value has not been updated for the current

// time step

private Collection incomingConns = new ArrayList();

private ActivationFunction func = null;

/**
 * Create neuron with <code>aFunc</code> activation function.
 * 
 * @param aFunc
 * @throws IllegalArgumentException
 */
public Neuron( ActivationFunction aFunc ) throws IllegalArgumentException {
	super();
	if ( aFunc == null )
		throw new IllegalArgumentException( "activation function can not be null" );
	func = aFunc;
	reset();
}

/**
 * Add incoming connection <code>c</code>.
 * 
 * @param c
 */
public void addIncomingConnection( Connection c ) {
	incomingConns.add( c );
}

/**
 * indicates a time step has passed
 */
public void step() {
	// force value to be updated this time step
	dirty = true;
}

/**
 * only calculate value if dirty == false; otherwise, returned cached value
 * 
 * @return double activation value for current time step
 */
public double getValue() {
	if ( dirty ) {
		if (func instanceof ActivationFunctionNonIntegrating) {
			ActivationFunctionNonIntegrating func2 = (ActivationFunctionNonIntegrating) func;
			double[] input = new double[incomingConns.size()];
			Iterator it = incomingConns.iterator();
			int i = 0;
			while (it.hasNext()) {
				Connection conn = (Connection) it.next();
				input[i++] = conn.read();
			}
			value = func2.apply(input);
		}
		else {
			double sum = 0.0f;
			Iterator it = incomingConns.iterator();
			while ( it.hasNext() ) {
				Connection conn = (Connection) it.next();
				sum += conn.read();
			}
			value = func.apply(sum);
		}
		dirty = false;
	}

	return value;
}

/**
 * clear current value
 */
public void reset() {
	value = 0;
	dirty = true;
}

/**
 * @return String representation of object
 */
public String toString() {
	return Long.toString( id );
}

/**
 * XML representation is consistent with <a href="http://nevt.sourceforge.net/">NEVT </a>.
 * 
 * @return String XML representation of object
 */
public String toXml() {
	NeuronMetaData layout = new NeuronMetaData( this );
	return toXml( layout );
}

/**
 * XML representation is consistent with <a href="http://nevt.sourceforge.net/">NEVT </a>.
 * 
 * @param layout
 * @return String XML representation of <code>layout</code>
 */
public static String toXml( NeuronMetaData layout ) {
	StringBuffer result = new StringBuffer();
	result.append( "<" ).append( XML_TAG ).append( " id=\"" ).append( layout.getId() );
	if ( layout.getType() != null )
		result.append( "\" type=\"" ).append( layout.getType().toString() );
	if ( layout != null ) {
		result.append( "\" hint-layer=\"" ).append( layout.getHintLayer() );
		result.append( "\" hint-position=\"" ).append( layout.getHintPosition() );
	}
	result.append( "\" value=\"" ).append( layout.getValue() );
	result.append( "\" step-pending=\"" ).append( layout.isDirty() );
	result.append( "\" activation=\"" ).append( layout.getActivationFunction().toString() )
			.append( "\" />\n" );

	return result.toString();
}

/**
 * XML representation is consistent with <a href="http://nevt.sourceforge.net/">NEVT </a>.
 * Utility method to convert collection of neurons, presumably an entire ANN, to XML.
 * 
 * @param allNeurons
 * @param outputNeurons
 * @param result destination to which XML is appended
 */
public static void appendToXml( Collection allNeurons, List outputNeurons, StringBuffer result ) {
	SortedMap layerToCoords = new TreeMap();
	int maxLayer = 1;

	// iterate through neurons ...
	Collection nonOuputNeurons = new ArrayList( allNeurons );
	nonOuputNeurons.removeAll( outputNeurons );
	Map allMetaData = new HashMap();

	Iterator neuronIter = nonOuputNeurons.iterator();
	while ( neuronIter.hasNext() ) {
		// ... and for each neuron ...
		Neuron n = (Neuron) neuronIter.next();

		// ... determine approximate coordinates ...
		NeuronMetaData metaData = createMetaData( n, allMetaData );
		if ( metaData != null ) {
			maxLayer = Math.max( maxLayer, metaData.getHintLayer() );

			// ... and save to be used for recalculating positions
			Long layerNum = new Long( metaData.getHintLayer() );
			List layer = (List) layerToCoords.get( layerNum );
			if ( layer == null ) {
				layer = new ArrayList();
				layerToCoords.put( layerNum, layer );
			}
			layer.add( metaData );
		}
	}

	// output layer to xml
	Long outLayerNum = new Long( maxLayer + 1 );
	List outLayer = new ArrayList();
	layerToCoords.put( outLayerNum, outLayer );
	neuronIter = outputNeurons.iterator();
	while ( neuronIter.hasNext() ) {
		Neuron n = (Neuron) neuronIter.next();
		NeuronMetaData metaData = new NeuronMetaData( n );
		metaData.setHintLayer( outLayerNum.intValue() );
		metaData.setHintPosition( n.getId() );
		metaData.setType( NeuronType.OUTPUT );
		outLayer.add( metaData );
	}

	// for each layer, recalculate positions to be integers and write to xml ...
	Iterator layerIter = layerToCoords.keySet().iterator();
	while ( layerIter.hasNext() ) {
		Long layerNum = (Long) layerIter.next();
		List layer = (List) layerToCoords.get( layerNum );
		Collections.sort( layer, NeuronMetaDataPositionComparator.getInstance() );

		for ( int i = 0; i < layer.size(); ++i ) {
			NeuronMetaData metaData = (NeuronMetaData) layer.get( i );
			metaData.setHintPosition( i + 1 ); // layout is 1-based
			result.append( toXml( metaData ) );
		}
	}
}

/**
 * Calculates position in input layer based on average of position of inputs connected into this
 * neuron; i.e., if a this neuron has 2 input connections from input neurons at positions 2 and
 * 3, this neurons position is 2.5.
 * 
 * @param n
 * @return position of Neuron in input layer, -1 if neuron not in input layer
 */
private static int inputPos( Neuron n ) {
	int numInputs = 0;
	int totalPos = 0;
	Iterator connIter = n.getIncomingConns().iterator();
	while ( connIter.hasNext() ) {
		Connection c = (Connection) connIter.next();
		if ( c instanceof Pattern.PatternConnection ) {
			Pattern.PatternConnection pc = (Pattern.PatternConnection) c;
			++numInputs;
			totalPos += pc.getIdx();
		}
	}

	return ( numInputs == 0 ) ? -1 : ( totalPos / numInputs );
}

/**
 * Constructs meta data basedon neuron's relative position to all other neurons.
 * 
 * @param neuron
 * @param allMetaData <code>Map</code> contains <code>Long</code> key innovation ID,
 * <code>NeuronMetaData</code> value
 * @return NeuronMetaData best-guess layout position of neuron, or null if neuron is on a
 * recursive path
 */
private static NeuronMetaData createMetaData( Neuron neuron, Map allMetaData ) {
	Long id = new Long( neuron.getId() );
	NeuronMetaData result = (NeuronMetaData) allMetaData.get( id );
	if ( result != null )
		return result;

	result = new NeuronMetaData( neuron );

	int anInputPos = inputPos( neuron );
	if ( anInputPos > -1 ) {
		// terminating condition, input neuron
		result.setHintLayer( 1 );
		result.setHintPosition( anInputPos );
		result.setType( NeuronType.INPUT );
	}
	else if ( neuron.incomingConns.isEmpty() ) {
		// terminating condition, neuron with no inputs
		result = null;
	}
	else {
		// recurse, calculate position of incoming neurons
		int maxIncomingLayer = 0;
		int numInputs = 0;
		double totalInputPos = 0;
		Iterator iter = neuron.incomingConns.iterator();
		while ( iter.hasNext() ) {
			Connection conn = (Connection) iter.next();
			if ( conn instanceof NeuronConnection ) {
				NeuronConnection nConn = (NeuronConnection) conn;
				Neuron inNeuron = nConn.getIncomingNode();
				NeuronMetaData inMetaData = createMetaData( inNeuron, allMetaData );
				if ( inMetaData != null ) {
					maxIncomingLayer = Math.max( inMetaData.getHintLayer(), maxIncomingLayer );
					++numInputs;
					totalInputPos += inMetaData.getHintPosition();
				}
			}
		}
		if ( numInputs == 0 ) {
			// terminating condition, no valid inputs
			result = null;
		}
		else {
			result.setHintLayer( maxIncomingLayer + 1 );
			result.setHintPosition( totalInputPos / numInputs );
			result.setType( NeuronType.HIDDEN );
		}
	}

	allMetaData.put( id, result );
	return result;
}

/**
 * used by NeuronConnection.appendToXml() only
 * 
 * @return Collection contains Connection objects
 */
protected Collection getIncomingConns() {
	return incomingConns;
}

/**
 * @return boolean true if at least one incoming connection is recurrent
 */
public boolean isRecurrent() {
	Iterator iter = getIncomingConns().iterator();
	while ( iter.hasNext() ) {
		Connection c = (Connection) iter.next();
		if ( c instanceof CacheNeuronConnection )
			return true;
	}
	return false;
}

/**
 * @param l new ID
 */
public void setId( long l ) {
	id = l;
}

/**
 * for logging, tracking, debugging; this ID usually will be the innovation ID of the
 * corresponding gene
 * 
 * @return long id
 */
public long getId() {
	return id;
}

/**
 * @return true if this neuron's value has not been updated for the current time step
 */
public boolean isDirty() {
	return dirty;
}

/**
 * @return ActivationFunction
 */
public ActivationFunction getFunc() {
	return func;
}

/**
 * @return number corresponding to cost of activation in resources
 */
public long cost() {
	long result = 315; // cost of neuron w/out any connections
	Iterator it = incomingConns.iterator();
	while ( it.hasNext() ) {
		Connection c = (Connection) it.next();
		result += c.cost();
		result += 115; // cost of iteration per neuron
	}
	result += func.cost();
	return result;
}

/**
 * @see com.anji.util.XmlPersistable#getXmlRootTag()
 */
public String getXmlRootTag() {
	return XML_TAG;
}

/**
 * @see com.anji.util.XmlPersistable#getXmld()
 */
public String getXmld() {
	return Long.toString( id );
}
}
