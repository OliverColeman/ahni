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
 * Created on Apr 4, 2004 by Philip Tucker
 */
package com.anji.run;

import java.util.Set;

/**
 * Hibernate-able run object.
 * @author Philip Tucker
 */
public class Domain {

private String name;

private Set populations;

private Set representations;

/**
 * ctor for hibernate
 */
private Domain() {
	// no-op
}

/**
 * @param aName
 */
public Domain( String aName ) {
	name = aName;
}

/**
 * Add new population to domain.
 * @param aPopulation
 */
public void addPopulation( Population aPopulation ) {
	populations.add( aPopulation );
}

/**
 * Add new representation to domain.
 * @param representation
 */
public void addRepresentation( Representation representation ) {
	representations.add( representation );
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return name;
}

/**
 * @return unique run ID
 */
public String getName() {
	return name;
}

/**
 * @return <code>Set</code> contains <code>Population</code> objects
 */
public Set getPopulations() {
	return populations;
}

/**
 * for hibernate
 * @param aPopulations
 */
private void setPopulations( Set aPopulations ) {
	populations = aPopulations;
}

/**
 * for hibernate
 * @param aName
 */
private void setName( String aName ) {
	name = aName;
}

/**
 * @return <code>Set</code> contains <code>Representation</code> objects
 */
public Set getRepresentations() {
	return representations;
}

/**
 * for hibernate
 * @param aRepresentations
 */
private void setRepresentations( Set aRepresentations ) {
	representations = aRepresentations;
}
}
