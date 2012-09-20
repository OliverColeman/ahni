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
 * created by Philip Tucker on Jun 4, 2003
 */
package com.anji.neat;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumerated type representing flavors of neurons: input, output, hidden. Values returned in
 * <code>toString()</code> correspond to values in <a href="http://nevt.sourceforge.net/">NEVT
 * </a> XML data model.
 * 
 * @author Philip Tucker
 */
public class NeuronType {

/**
 * for hibernate
 */
private Long id;

private String name = null;

private static Map types = null;

/**
 * input neuron
 */
public final static NeuronType INPUT = new NeuronType( "in" );

/**
 * hidden neuron
 */
public final static NeuronType HIDDEN = new NeuronType( "hid" );

/**
 * output neuron
 */
public final static NeuronType OUTPUT = new NeuronType( "out" );

/**
 * @param newName id of type
 */
private NeuronType( String newName ) {
	name = newName;
}

/**
 * @param name id of type
 * @return <code>NeuronType</code> enumerated type corresponding to <code>name</code>
 */
public static NeuronType valueOf( String name ) {
	if ( types == null ) {
		types = new HashMap();
		types.put( NeuronType.INPUT.toString(), NeuronType.INPUT );
		types.put( NeuronType.HIDDEN.toString(), NeuronType.HIDDEN );
		types.put( NeuronType.OUTPUT.toString(), NeuronType.OUTPUT );
	}
	return (NeuronType) types.get( name );
}

/**
 * @see Object#equals(java.lang.Object)
 */
public boolean equals( Object o ) {
	return ( this == o );
}

/**
 * @see Object#toString()
 */
public String toString() {
	return name;
}

/**
 * define this so objects may be used in hash tables
 * 
 * @see Object#hashCode()
 */
public int hashCode() {
	return name.hashCode();
}

/**
 * for hibernate
 * @return unique id
 */
private Long getId() {
	return id;
}

/**
 * for hibernate
 * @param aId
 */
private void setId( Long aId ) {
	id = aId;
}
}
