/*
 * Copyright (C) 2004  Derek James and Philip Tucker
 *
 * This file is part of ANJI (Another NEAT Java Implementation).
 *
 * ANJI is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Created on Feb 25, 2004 by Philip Tucker
 */
package org.jgap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Factory for generating unique IDs across multiple runs.
 * @author Philip Tucker
 */
public class IdFactory {

	/**
	 * default base ID
	 */
	public final static long DEFAULT_BASE_ID = 0;

	/**
	 * XML base tag
	 */
	public final static String XML_TAG = "id";

private long nextId = DEFAULT_BASE_ID;
private String fileName = null;

/**
 * @return long next unique ID
 */
 public long next() {
	return nextId++;
 }

/**
 * construct new factory with default values
 */
public IdFactory() {
	// noop
}

/**
 * construct new factory from persisted file <code>aFileName</code>
 * @param aFileName
 * @throws IOException
 */
public IdFactory( String aFileName ) throws IOException {
	fileName = aFileName;
	FileInputStream in = null;
	try {
		File f = new File( aFileName );
		if ( f.exists() ) {
			in = new FileInputStream( fileName );
			nextId = fromXml( in );
		}
	}
	finally {
		if ( in != null )
			in.close();
	}
}

/**
 * load ID counter from XML
 * @param in XML representation of ID counter
 * @return long next unique ID
 * @throws IllegalArgumentException
 */
private static long fromXml( InputStream in ) throws IllegalArgumentException {
	try {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = builder.parse( in );

		Node node = doc.getFirstChild();
		if ( XML_TAG.equals( node.getNodeName() ) == false )
			throw new IllegalArgumentException( "tag != " + XML_TAG );

		node = node.getFirstChild();
		if ( node == null )
			throw new IllegalArgumentException( "empty id node" );

		String aNextIdStr = node.getNodeValue();
		if ( aNextIdStr == null || aNextIdStr.length() == 0 )
			throw new IllegalArgumentException( "empty id" );

		long id = Long.parseLong( aNextIdStr );
		return id;
	}
	catch ( Exception e ) {
		throw new IllegalArgumentException( "xml does not parse: " + e.getMessage() );
	}
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return toXml();
}

/**
 * @return String XML representation of object
 */
public String toXml() {
	StringBuffer result = new StringBuffer();
	result.append( "<id>" ).append( nextId ).append( "</id>" );
	return result.toString();
}

/**
 * persist object to file
 * @throws IOException
 */
public void store() throws IOException {
	if ( fileName != null ) {
		FileWriter out = null;
		try {
			out = new FileWriter( fileName );
			out.write( toXml() );
			out.flush();
		}
		finally {
			if ( out != null )
				out.close();
		}
	}
}

}

