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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.jgap.Genotype;
import org.jgap.event.GeneticEvent;
import org.jgap.event.GeneticEventListener;

import com.anji.integration.Generation;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Hibernate-able run object.
 * @author Philip Tucker
 */
public class Run implements GeneticEventListener, Configurable {

private static final String RUN_KEY = "run.name";

private int currentGenerationNumber = 1;

/**
 * for hibernate
 */
private Long id;

private String name;

private List generations = new ArrayList();

private Properties props;

private Calendar startTime = Calendar.getInstance();

// TODO population

/**
 * @see java.lang.Object#hashCode()
 */
public int hashCode() {
	return id.hashCode();
}

/**
 * @see java.lang.Object#equals(java.lang.Object)
 */
public boolean equals( Object o ) {
	Run other = (Run) o;
	return id.equals( other.id );
}

/**
 * should call <code>init()</code> after this ctor, unless it's called from hibernate
 */
public Run() {
	// no-op
}

/**
 * @param aName
 */
public Run( String aName ) {
	name = aName;
}

/**
 * Add new generation to run.
 * 
 * @param genotype
 */
public void addGeneration( Genotype genotype ) {
	generations.add( new Generation( genotype, currentGenerationNumber++ ) );
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return name;
}

/**
 * @see org.jgap.event.GeneticEventListener#geneticEventFired(org.jgap.event.GeneticEvent)
 */
public void geneticEventFired( GeneticEvent event ) {
	Genotype genotype = (Genotype) event.getSource();
	if ( GeneticEvent.GENOTYPE_EVALUATED_EVENT.equals( event.getEventName() ) )
		addGeneration( genotype );
}

/**
 * @return unique run ID
 */
public String getName() {
	return name;
}

/**
 * @return generations orderd by generation number
 */
public List getGenerations() {
	return generations;
}

/**
 * for hibernate
 * @param aGenerations
 */
private void setGenerations( List aGenerations ) {
	generations = aGenerations;
}

/**
 * for hibernate
 * @param aName
 */
private void setName( String aName ) {
	name = aName;
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

/**
 * @see com.anji.util.Configurable#init(com.anji.util.Properties)
 */
public void init( Properties aProps ) throws Exception {
	props = aProps;
	name = props.getProperty( RUN_KEY );
}

/**
 * @return properties
 */
public Properties getProps() {
	return props;
}

/**
 * @return time when this object was created
 */
public Calendar getStartTime() {
	return startTime;
}
}
