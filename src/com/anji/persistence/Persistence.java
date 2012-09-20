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
 * created by Philip Tucker on May 17, 2003
 */
package com.anji.persistence;

import org.jgap.Chromosome;
import org.jgap.Configuration;
import org.jgap.Genotype;

import com.anji.integration.Activator;
import com.anji.run.Run;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Abstract interface to persistence layer.
 * 
 * @author Philip Tucker
 */
public interface Persistence extends Configurable {

/**
 * properties key; persistence class implementation
 */
public final static String PERSISTENCE_CLASS_KEY = "persistence";

/**
 * See <a href=" {@docRoot}/params.htm" target="anji_params">Parameter Details </a> for
 * specific property settings.
 * @param newProps configuration parameters
 */
public void init( Properties newProps );

/**
 * remove all data from storage
 */
public void reset();

/**
 * @param c chromosome to store
 * @throws Exception
 */
public void store( Chromosome c ) throws Exception;

/**
 * @param a activator to store
 * @throws Exception
 */
public void store( Activator a ) throws Exception;

/**
 * @param r run to store
 * @throws Exception
 */
public void store( Run r ) throws Exception;

/**
 * delete chromosome identified by by <code>id</code>
 * 
 * @param id id of chromosome to delete
 * @throws Exception
 */
public void deleteChromosome( String id ) throws Exception;

/**
 * @param id
 * @param config
 * @return chromosome from persistence
 */
public Chromosome loadChromosome( String id, Configuration config );

/**
 * loads genotype as of latest generation in run
 * @param config
 * @return genotype; null if there is no previous run
 */
public Genotype loadGenotype( Configuration config );

/**
 * Begin run.  All successive calls to this persistance object will be in the context of this run.
 * @param runId
 */
public void startRun( String runId );

}
