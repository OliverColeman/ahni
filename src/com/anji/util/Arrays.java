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
 * Created on Sep 3, 2005 by Philip Tucker
 */
package com.anji.util;

/**
 * @author Philip Tucker
 */
public class Arrays {

/**
 * @param a
 * @return <code>String</code> representation of array, in brackets with comma separators
 */
public static String toString( int[] a ) {
	StringBuffer result = new StringBuffer();
	result.append( "[" );
	if ( a.length > 0 )
		result.append( a[ 0 ] );
	for ( int i = 1; i < a.length; ++i )
		result.append( "," ).append( a[ 0 ] );
	result.append( "]" );
	return result.toString();
}

/**
 * @param a
 * @return <code>String</code> representation of array, in brackets with comma separators
 */
public static String toString( double[] a ) {
	StringBuffer result = new StringBuffer();
	result.append( "[" );
	if ( a.length > 0 )
		result.append( a[ 0 ] );
	for ( int i = 1; i < a.length; ++i )
		result.append( "," ).append( a[ 0 ] );
	result.append( "]" );
	return result.toString();
}

}
