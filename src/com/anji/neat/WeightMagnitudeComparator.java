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
 * Created on Jul 9, 2005 by Philip Tucker
 */
package com.anji.neat;

import java.util.Comparator;

/**
 * sorts ConnectionGenes in ascending order of weight magnitude
 */
public class WeightMagnitudeComparator implements Comparator {

private static WeightMagnitudeComparator instance = null;

private WeightMagnitudeComparator() {
	super();
}

/**
 * @return singleton
 */
public static WeightMagnitudeComparator getInstance() {
	if ( instance == null )
		instance = new WeightMagnitudeComparator();
	return instance;
}

/**
 * @param o1
 * @param o2
 * @return compare absolute value of weights of <code>ConnectionAllele</code> objects; return
 * 1 if <code>o1</code>><code>o2</code>, 0 if <code>o1</code>==<code>o2</code>; -1
 * otherwise
 */
public int compare( Object o1, Object o2 ) {
	ConnectionAllele connAllele1 = (ConnectionAllele) o1;
	ConnectionAllele connAllele2 = (ConnectionAllele) o2;
	double result = Math.abs( connAllele1.getWeight() ) - Math.abs( connAllele2.getWeight() );
	if ( result > 0 )
		return 1;
	else if ( result < 0 )
		return -1;
	else
		return 0;
}
}