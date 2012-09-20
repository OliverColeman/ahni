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
package com.anji.nn;

import java.util.HashMap;
import java.util.Map;

import com.anji.util.Properties;

/**
 * Enumerated type representing policies for how to handle recurrency.
 * 
 * @author Philip Tucker
 */
public class RecurrencyPolicy {

	/**
	 * properties key
	 */
	public final static String KEY = "recurrent";

	private String name = null;

	private static Map policies = null;

	/**
	 * no recurrency allowed; no loops in network
	 */
	public final static RecurrencyPolicy DISALLOWED = new RecurrencyPolicy( "disallowed" );

	// TODO
	// recurrency allowed; recurrent connections only where required, to break a potential
	// deadlock loop in network 
	// public final static RecurrencyPolicy MINIMUM = new RecurrencyPolicy( "minimum" );

	/**
	 * recurrency allowed; recurrent connections where it seems they might be necessary
	 */
	public final static RecurrencyPolicy BEST_GUESS = new RecurrencyPolicy( "best_guess" );

	/**
	 * recurrency allowed; treat all connections as if they might be recurrent
	 */
	public final static RecurrencyPolicy LAZY = new RecurrencyPolicy( "lazy" );

	private RecurrencyPolicy( String newName ) {
		name = newName;
	}

	/**
	 * @param name
	 * @return RecurrencyPolicy corresponding to <code>name</code>
	 */
	public static RecurrencyPolicy valueOf( String name ) {
		if ( policies == null ) {
			policies = new HashMap();
			policies.put( RecurrencyPolicy.DISALLOWED.toString(), RecurrencyPolicy.DISALLOWED );
			// TODO policies.put( RecurrencyPolicy.MINIMUM.toString(), RecurrencyPolicy.MINIMUM );
			policies.put( RecurrencyPolicy.BEST_GUESS.toString(), RecurrencyPolicy.BEST_GUESS );
			policies.put( RecurrencyPolicy.LAZY.toString(), RecurrencyPolicy.LAZY );
		}
		return (RecurrencyPolicy) policies.get( name );
	}

	/**
	 * See <a href=" {@docRoot}/params.htm" target="anji_params">Parameter Details </a> for
	 * <code>recurrent</code> property settings.
	 * @param props configuration parameter
	 * @return policy specified by <code>props</code>
	 */
	public static RecurrencyPolicy load( Properties props ) {
		String value = props.getProperty( KEY, RecurrencyPolicy.BEST_GUESS.toString() );
		return valueOf( value );
	}

	/**
	 * @see Object#equals(java.lang.Object)
	 */
	public boolean equals( Object o ) {
		return this == o;
	}

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return name;
	}

	/**
	 * @see Object#hashCode()
	 */
	public int hashCode() {
		return name.hashCode();
	}

}
