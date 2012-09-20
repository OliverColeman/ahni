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
 * Modified on Feb 3, 2003 by Philip Tucker
 */
package org.jgap;

import java.io.Serializable;

/**
 * Genes represent the discrete components of a potential solution (the Chromosome). This
 * abstract class exists so that custom gene implementations can be easily plugged-in, which can
 * add a great deal of flexibility and convenience for many applications. Note that it's very
 * important that implementations of this interface also implement the <code>equals()</code>
 * method. Without a proper implementation of <code>equals()</code>, some genetic operations
 * will fail to work properly. Innovation ID is used as a historical marker and aids in
 * crossover and determining "distance" between 2 chromosomes for speciation. Not the difference
 * between <code>compareTo()</code> and <code>distance()</code>:<code>compareTo()</code>
 * is used by container classes to sort genes by innovation ID, <code>distance()</code> is the
 * genetic distance between genes with the same innovation ID.
 */
public class Gene implements Comparable, Serializable {

/**
 * for hibernate
 */
private Long id;

private Long innovationId = null;

/**
 * Comparison based on innovation ID.
 * 
 * @see Comparable#compareTo(java.lang.Object)
 */
public int compareTo( Object o ) {
	Gene other = (Gene) o;
	return innovationId.compareTo( other.innovationId );
}

/**
 * @return true if same innovation ID
 * @see java.lang.Object#equals(Object)
 */
public boolean equals( Object o ) {
	return ( compareTo( o ) == 0 );
}

/**
 * Innovation ID is analagous to the locus of a real gene. Genes with the same innovation ID in
 * 2 chromosomes are considered to be 2 alleles of the same gene.
 * 
 * @return unique identifier; see <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT </a> methodology for
 * uses of innovation ID as a historical marker.
 */
public Long getInnovationId() {
	return innovationId;
}

/**
 * Uses hash of innovation ID.
 * 
 * @see Object#hashCode()
 */
public int hashCode() {
	return innovationId.hashCode();
}

/**
 * for hibernate
 */
protected Gene() {
	super();
}

/**
 * Create new gene.
 * 
 * @param anInnovationId
 */
public Gene( Long anInnovationId ) {
	innovationId = anInnovationId;
}

/**
 * @param aInnovationId
 */
protected void setInnovationId( Long aInnovationId ) {
	innovationId = aInnovationId;
}

/**
 * @see java.lang.Object#toString()
 */
public String toString() {
	return innovationId.toString();
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
