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
package com.anji.integration;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;

import com.anji.neat.Evolver;
import com.anji.neat.NeatConfiguration;
import com.anji.run.Run;
import com.anji.util.Properties;
import com.anji.util.XmlPersistable;

/**
 * Converts run data to XML presentation format. Complexity, fitness, and speciation are
 * represented with the same XML structure, different stylesheets. A new
 * <code>XmlPersistableRun</code> object should be created new for each use, because it caches
 * the XML it produces, and does not automatically detect changes to the underlying
 * <code>Run</code> object.
 * 
 * @author Philip Tucker
 */
public class XmlPersistableRun implements XmlPersistable {

/**
 * base XML tag
 */
public final static String RUN_TAG = "run";

/**
 * XML parameters tag
 */
public final static String PARAMETERS_TAG = "parameters";

private Run run;

/**
 * XML parameter tag
 */
public final static String PARAMETER_TAG = "parameter";

private final static String vers = "<?xml version = \"1.0\" encoding = \"UTF-8\"?>\n";

private final static String dtd = "<!DOCTYPE run SYSTEM \"../run.dtd\">\n";

private final static String endTag = "</" + RUN_TAG + ">\n";

private final static String complexityStylesheet = "<?xml-stylesheet type=\"text/xsl\" href=\"./graphComplexity.xsl\" ?>\n";

private final static String complexityRefreshStylesheet = "<?xml-stylesheet type=\"text/xsl\" href=\"./graphComplexityRefresh.xsl\" ?>\n";

private final static String fitnessStylesheet = "<?xml-stylesheet type=\"text/xsl\" href=\"./graphFitness.xsl\" ?>\n";

private final static String fitnessRefreshStylesheet = "<?xml-stylesheet type=\"text/xsl\" href=\"./graphFitnessRefresh.xsl\" ?>\n";

private final static String speciesStylesheet = "<?xml-stylesheet type=\"text/xsl\" href=\"./graphSpecies.xsl\" ?>\n";

private final static String speciesRefreshStylesheet = "<?xml-stylesheet type=\"text/xsl\" href=\"./graphSpeciesRefresh.xsl\" ?>\n";

private StringBuffer params = new StringBuffer();

private String cachedRunXml;

/**
 * ctor; must call <code>init()</code> before using this object
 * @param aRun
 */
public XmlPersistableRun( Run aRun ) {
	run = aRun;

	// parameters
	Properties props = run.getProps();
	if ( props != null ) {
		params.append( "<search-parameters>\n" );
		params.append( "<population-size>" ).append(
				props.getIntProperty( NeatConfiguration.POPUL_SIZE_KEY ) ).append(
				"</population-size>\n" );
		params.append( "<generations>" )
				.append( props.getIntProperty( Evolver.NUM_GENERATIONS_KEY ) ).append(
						"</generations>\n" );
		params.append( "</search-parameters>\n" );
	}

	cachedRunXml = null;
}

/**
 * @param includeDtd include DTD tag if true
 * @param result representation of run
 */
protected void appendToString( boolean includeDtd, StringBuffer result ) {
	if ( cachedRunXml == null ) {
		StringBuffer cacheBuffer = new StringBuffer();
		if ( includeDtd )
			cacheBuffer.append( dtd );
		DateFormat fmt = new SimpleDateFormat( "yyyyMMdd HH:mm:ss" );
		String startTag = "<" + RUN_TAG + " name=\"" + run.getName() + "\" timedatestamp=\"["
				+ fmt.format( run.getStartTime().getTime() ) + " - "
				+ fmt.format( Calendar.getInstance().getTime() ) + "]\" >\n";
		cacheBuffer.append( startTag );
		cacheBuffer.append( params );

		Iterator it = run.getGenerations().iterator();
		while ( it.hasNext() ) {
			Generation g = (Generation) it.next();
			cacheBuffer.append( g.toXml() );
		}

		cacheBuffer.append( endTag );
		cachedRunXml = cacheBuffer.toString();
	}
	result.append( cachedRunXml );
}

/**
 * @param isRunCompleted <code>true</code> iff this is the last call to
 * @return XML representation of population's complexity throughout run
 */
public String toComplexityString( boolean isRunCompleted ) {
	StringBuffer result = new StringBuffer();
	result.append( vers );
	result.append( isRunCompleted ? complexityStylesheet : complexityRefreshStylesheet );
	appendToString( true, result );
	return result.toString();
}

/**
 * @param isRunCompleted <code>true</code> iff this is the last call to
 * @return XML representation of population's fitness throughout run
 */
public String toFitnessString( boolean isRunCompleted ) {
	StringBuffer result = new StringBuffer();
	result.append( vers );
	result.append( isRunCompleted ? fitnessStylesheet : fitnessRefreshStylesheet );
	appendToString( true, result );
	return result.toString();
}

/**
 * @param isRunCompleted <code>true</code> iff this is the last call to
 * @return XML representation of population's species and their size thorughout run
 */
public String toSpeciesString( boolean isRunCompleted ) {
	StringBuffer result = new StringBuffer();
	result.append( vers );
	result.append( isRunCompleted ? speciesStylesheet : speciesRefreshStylesheet );
	appendToString( true, result );
	return result.toString();
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return toXml();
}

/**
 * @see com.anji.util.XmlPersistable#toXml()
 */
public String toXml() {
	StringBuffer result = new StringBuffer();
	appendToString( false, result );
	return result.toString();
}

/**
 * @see com.anji.util.XmlPersistable#getXmlRootTag()
 */
public String getXmlRootTag() {
	return RUN_TAG;
}

/**
 * @see com.anji.util.XmlPersistable#getXmld()
 */
public String getXmld() {
	return run.getName();
}

/**
 * @return unique run ID
 */
public String getName() {
	return run.getName();
}
}
