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
 * Created on Jun 5, 2005 by Philip Tucker
 */
package com.anji.integration;

import java.util.Iterator;

import org.jgap.Allele;
import org.jgap.Chromosome;

import com.anji.util.XmlPersistable;

/**
 * @author Philip Tucker
 */
public class XmlPersistableChromosome implements XmlPersistable {

/**
 * base XML tag
 */
public final static String XML_CHROMOSOME_TAG = "chromosome";

/**
 * XML ID tag
 */
public final static String XML_CHROMOSOME_ID_TAG = "id";

/**
 * XML primary parent ID tag
 */
public final static String XML_CHROMOSOME_PRIMARY_PARENT_ID_TAG = "primary-parent-id";

/**
 * XML secondary parent ID tag
 */
public final static String XML_CHROMOSOME_SECONDARY_PARENT_ID_TAG = "secondary-parent-id";

private Chromosome chromosome;

/**
 * @param aChromosome
 */
public XmlPersistableChromosome( Chromosome aChromosome ) {
	super();
	chromosome = aChromosome;
}

/**
 * @see com.anji.util.XmlPersistable#toXml()
 */
public String toXml() {
	StringBuffer result = new StringBuffer();
	result.append( "<" ).append( XML_CHROMOSOME_TAG );
	result.append( " " ).append( XML_CHROMOSOME_ID_TAG ).append( "=\"" ).append(
			chromosome.getId() );
	if ( chromosome.getPrimaryParentId() != null ) {
		result.append( "\" " ).append( XML_CHROMOSOME_PRIMARY_PARENT_ID_TAG );
		result.append( "=\"" ).append( chromosome.getPrimaryParentId() );
	}
	if ( chromosome.getSecondaryParentId() != null ) {
		result.append( "\" " ).append( XML_CHROMOSOME_SECONDARY_PARENT_ID_TAG );
		result.append( "=\"" ).append( chromosome.getSecondaryParentId() );
	}
	result.append( "\">" );
	Iterator iter = chromosome.getAlleles().iterator();
	while ( iter.hasNext() ) {
		Allele allele = (Allele) iter.next();
		XmlPersistableAllele xmlAllele = new XmlPersistableAllele( allele );
		result.append( xmlAllele.toXml() );
	}
	result.append( "</" ).append( XML_CHROMOSOME_TAG ).append( ">" );
	return result.toString();
}

/**
 * @see com.anji.util.XmlPersistable#getXmlRootTag()
 */
public String getXmlRootTag() {
	return XML_CHROMOSOME_TAG;
}

/**
 * @see com.anji.util.XmlPersistable#getXmld()
 */
public String getXmld() {
	Long id = chromosome.getId();
	return ( id == null ) ? "" : id.toString();
}

}

