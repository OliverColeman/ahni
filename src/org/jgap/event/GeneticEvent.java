/*
 * Copyright 2001-2003 Neil Rotstan Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of JGAP.
 * 
 * JGAP is free software; you can redistribute it and/or modify it under the terms of the GNU
 * Lesser Public License as published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * JGAP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License along with JGAP; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Modified on Feb 3, 2003 by Derek James
 */
package org.jgap.event;

import java.util.EventObject;

/**
 * Represents events that are fired via the EventManager when various genetic events occur. The
 * specific kind of event is conveyed through the event name. Standard event names are provided
 * as constants in this class.
 */
public class GeneticEvent extends EventObject {

/**
 * Public constant representing the name of the event that is fired each time a Genotype is
 * finished with a single evolution cycle.
 */
public static final String GENOTYPE_EVOLVED_EVENT = "genotype_evolved_event";

/**
 * Public constant representing the name of the event that is fired each time a Genotype is
 * finished evaluating population fitness.
 */
public static final String GENOTYPE_EVALUATED_EVENT = "genotype_evaluated_event";

/**
 * Public constant representing the name of the event that is fired each time a Genotype begins
 * genetic operators.
 */
public static final String GENOTYPE_START_GENETIC_OPERATORS_EVENT = "genotype_start_genetic_operators_event";

/**
 * Public constant representing the name of the event that is fired each time a Genotype begins
 * genetic operators.
 */
public static final String GENOTYPE_FINISH_GENETIC_OPERATORS_EVENT = "genotype_finish_genetic_operators_event";

/**
 * Public constant representing the name of the event that is fired each time a run is
 * completed.
 */
public static final String RUN_COMPLETED_EVENT = "run_completed_event";

/**
 * References the name of this event instance.
 */
private final String m_eventName;

/**
 * Constructs a new GeneticEvent of the given name.
 * 
 * @param a_eventName The name of the event.
 * @param a_source The genetic object that acted as the source of the event. The type of this
 * object will be dependent on the kind of event (which can be identified by the event name). It
 * may not be null.
 * 
 * @throws IllegalArgumentException if the given source object is null.
 */
public GeneticEvent( String a_eventName, Object a_source ) {
	super( a_source );
	m_eventName = a_eventName;
}

/**
 * Retrieves the name of this event, which can be used to identify the type of event.
 * 
 * @return the name of this GeneticEvent instance.
 */
public String getEventName() {
	return m_eventName;
}
}
