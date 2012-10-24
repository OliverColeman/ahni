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
 * Created on Jul 5, 2005 by Philip Tucker
 */
package com.anji.integration;

import org.jgapcustomised.Allele;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.anji.neat.ConnectionAllele;
import com.anji.neat.ConnectionGene;
import com.anji.neat.NeuronAllele;
import com.anji.neat.NeuronGene;
import com.anji.neat.NeuronType;
import com.anji.nn.activationfunction.ActivationFunction;
import com.anji.util.XmlPersistable;

/**
 * @author Philip Tucker
 */
public class XmlPersistableAllele implements XmlPersistable {

/**
 * neuron XML tag
 */
public final static String NEURON_XML_TAG = "neuron";

private final static String NEURON_XML_TYPE_TAG = "type";

private final static String NEURON_XML_ACTIVATION_TYPE_TAG = "activation";

/**
 * connection XML tag
 */
public final static String CONN_XML_TAG = "connection";

private final static String XML_ID_TAG = "id";

private final static String CONN_XML_SRCID_TAG = "src-id";

private final static String CONN_XML_DESTID_TAG = "dest-id";

private final static String CONN_XML_WEIGHT_TAG = "weight";

private final static String CONN_XML_RECURRENT_TAG = "recurrent";

private Allele allele;

/**
 * ctor
 * @param aAllele
 */
public XmlPersistableAllele( Allele aAllele ) {
	super();
	allele = aAllele;
}

/**
 * @see com.anji.util.XmlPersistable#toXml()
 */
public String toXml() {
	StringBuffer result = new StringBuffer();

	if ( allele instanceof NeuronAllele ) {
		NeuronAllele nAllele = (NeuronAllele) allele;
		result.append( "<" ).append( NEURON_XML_TAG ).append( " " );
		result.append( XML_ID_TAG ).append( "=\"" ).append( allele.getInnovationId() ).append(
				"\" " );
		result.append( NEURON_XML_TYPE_TAG ).append( "=\"" ).append( nAllele.getType().toString() )
				.append( "\" " );
		result.append( NEURON_XML_ACTIVATION_TYPE_TAG ).append( "=\"" ).append(
				nAllele.getActivationType() );
		result.append( "\"/>\n" );
	}
	else if ( allele instanceof ConnectionAllele ) {
		ConnectionAllele cAllele = (ConnectionAllele) allele;
		result.append( "<" ).append( CONN_XML_TAG ).append( " " );
		result.append( XML_ID_TAG ).append( "=\"" ).append( allele.getInnovationId() );
		result.append( "\" " ).append( CONN_XML_SRCID_TAG ).append( "=\"" ).append(
				cAllele.getSrcNeuronId() );
		result.append( "\" " ).append( CONN_XML_DESTID_TAG ).append( "=\"" ).append(
				cAllele.getDestNeuronId() );
		result.append( "\" " ).append( CONN_XML_WEIGHT_TAG ).append( "=\"" ).append(
				cAllele.getWeight() ).append( "\"/>\n" );
	}

	return result.toString();
}

/**
 * @see com.anji.util.XmlPersistable#getXmlRootTag()
 */
public String getXmlRootTag() {
	if ( allele instanceof NeuronAllele )
		return NEURON_XML_TAG;

	if ( allele instanceof ConnectionAllele )
		return CONN_XML_TAG;

	return null;
}

/**
 * @see com.anji.util.XmlPersistable#getXmld()
 */
public String getXmld() {
	return allele.getInnovationId().toString();
}

/**
 * Convert from XML to <code>NeuronGene</code> object
 * 
 * @param node
 * @return <code>NeuronAllele</code> constructed from XML <code>node</code>
 * @throws IllegalArgumentException
 */
public static NeuronAllele neuronFromXml( Node node ) throws IllegalArgumentException {
	if ( XmlPersistableAllele.NEURON_XML_TAG.equals( node.getNodeName() ) == false )
		throw new IllegalArgumentException( "tag != " + XmlPersistableAllele.NEURON_XML_TAG );
	if ( node.hasAttributes() == false )
		throw new IllegalArgumentException( "no attributes" );
	NamedNodeMap atts = node.getAttributes();

	String str = atts.getNamedItem( XmlPersistableAllele.NEURON_XML_TYPE_TAG ).getNodeValue();
	NeuronType type = NeuronType.valueOf( str );
	if ( type == null )
		throw new IllegalArgumentException( "invalid neuron type: " + str );

	str = atts.getNamedItem( XmlPersistableAllele.XML_ID_TAG ).getNodeValue();
	Long id = Long.valueOf( str );
	
	// Assume sigmoid if we can't determine it.
	String activationType = "sigmoid";
	Node actNode = atts.getNamedItem( XmlPersistableAllele.NEURON_XML_ACTIVATION_TYPE_TAG );
	if ( actNode != null ) {
		activationType = actNode.getNodeValue();
		if ( activationType == null )
			throw new IllegalArgumentException( "invalid activation function type: " + str );
	}

	return new NeuronAllele( new NeuronGene( type, id, activationType ) );
}

/**
 * Convert from XML to <code>ConnectionGene</code> object
 * 
 * @param node
 * @return <code>ConnectionAllele</code> constructed from XML <code>node</code>
 * @throws IllegalArgumentException
 */
public static ConnectionAllele connectionFromXml( Node node ) throws IllegalArgumentException {
	if ( XmlPersistableAllele.CONN_XML_TAG.equals( node.getNodeName() ) == false )
		throw new IllegalArgumentException( "tag != " + XmlPersistableAllele.CONN_XML_TAG );
	if ( node.hasAttributes() == false )
		throw new IllegalArgumentException( "no attributes" );
	NamedNodeMap atts = node.getAttributes();

	String idStr = atts.getNamedItem( XmlPersistableAllele.XML_ID_TAG ).getNodeValue();
	Long id = Long.valueOf( idStr );
	String srcIdStr = atts.getNamedItem( XmlPersistableAllele.CONN_XML_SRCID_TAG ).getNodeValue();
	Long srcId = Long.valueOf( srcIdStr );
	String destIdStr = atts.getNamedItem( XmlPersistableAllele.CONN_XML_DESTID_TAG )
			.getNodeValue();
	Long destId = Long.valueOf( destIdStr );
	ConnectionAllele result = new ConnectionAllele( new ConnectionGene( id, srcId, destId ) );

	String weightStr = atts.getNamedItem( XmlPersistableAllele.CONN_XML_WEIGHT_TAG )
			.getNodeValue();
	result.setWeight( Float.parseFloat( weightStr ) );

	return result;
}

}
