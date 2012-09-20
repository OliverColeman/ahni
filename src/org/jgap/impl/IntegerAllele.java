/*
 * Copyright 2001-2003 Neil Rotstan
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
 */
package org.jgap.impl;

import java.util.Random;
import java.util.StringTokenizer;

import org.jgap.Allele;
import org.jgap.Configuration;
import org.jgap.Gene;
import org.jgap.UnsupportedRepresentationException;

/**
 * A Gene implementation that supports a integer values for its allele. Upper and lower bounds
 * may optionally be provided to restrict the range of legal values allowed by this Gene
 * instance.
 */
public class IntegerAllele extends Allele {

/**
 * @return XML representation of object
 */
public String toXml() {
	StringBuffer result = new StringBuffer();
	result.append( "<IntegerGene value=\"" ).append( m_value ).append( "\"/>" );
	return result.toString();
}

/**
 * @param target
 * @return compatibility distance based on intValue; always positive
 * @see Allele#distance(Allele)
 */
public float distance( Allele target ) {
	if ( target.getInnovationId().equals( getInnovationId() ) && ( target instanceof IntegerAllele ) )
		return Math.abs( intValue() - ( (IntegerAllele) target ).intValue() );
	return Float.MAX_VALUE;
}

/**
 * Represents the constant range of values supported by integers.
 */
protected final static long INTEGER_RANGE = (long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE;

/**
 * Represents the delimiter that is used to separate fields in the persistent representation of
 * IntegerGene instances.
 */
protected final static String PERSISTENT_FIELD_DELIMITER = ":";

/**
 * References the internal integer value (allele) of this Gene.
 */
protected Integer m_value = null;

/**
 * The upper bounds of values represented by this Gene. If not explicitly provided by the user,
 * this should be set to Integer.MAX_VALUE.
 */
protected int m_upperBounds;

/**
 * The lower bounds of values represented by this Gene. If not explicitly provided by the user,
 * this should be set to Integer.MIN_VALUE
 */
protected int m_lowerBounds;

/**
 * Stores the number of integer range units that a single bounds-range unit represents. For
 * example, if the integer range is -2 billion to +2 billion and the bounds range is -1 billion
 * to +1 billion, then each unit in the bounds range would map to 2 units in the integer range.
 * The value of this variable would therefore be 2. This mapping unit is used to map illegal
 * allele values that are outside of the bounds to legal allele values that are within the
 * bounds.
 */
protected long m_boundsUnitsToIntegerUnits;

/**
 * The current active configuration that is in use.
 */
protected Configuration m_activeConfiguration = null;

/**
 * Constructs a new IntegerGene with default settings. No bounds will be put into effect for
 * values (alleles) of this Gene instance, other than the standard range of integer values.
 */
public IntegerAllele() {
	this( null, Integer.MIN_VALUE, Integer.MAX_VALUE );
}

/**
 * Constructs a new IntegerGene with the specified lower and upper bounds for values (alleles)
 * of this Gene instance.
 * 
 * @param a_lowerBounds The lowest value that this Gene may possess, inclusive.
 * @param a_upperBounds The highest value that this Gene may possess, inclusive.
 */
public IntegerAllele( int a_lowerBounds, int a_upperBounds ) {
	this( null, a_lowerBounds, a_upperBounds );
}

/**
 * Constructs a new IntegerGene according to the given active configuration.
 * 
 * @param a_activeConfiguration The current active configuration.
 */
public IntegerAllele( Configuration a_activeConfiguration ) {
	this( a_activeConfiguration, Integer.MIN_VALUE, Integer.MAX_VALUE );
}

private IntegerAllele( Gene gene ) {
	super( gene );
	m_lowerBounds = Integer.MIN_VALUE;
	m_upperBounds = Integer.MAX_VALUE;
	calculateBoundsUnitsToIntegerUnitsRatio();
}

/**
 * Constructs a new IntegerGene with the given active configuration and the specified lower and
 * upper bounds for values represented by this Gene.
 * 
 * @param a_activeConfiguration The current active configuration.
 * @param a_lowerBounds The lowest value that this Gene may represent, inclusive.
 * @param a_upperBounds The highest value that this Gene may represent, inclusive.
 */
public IntegerAllele( Configuration a_activeConfiguration, int a_lowerBounds, int a_upperBounds ) {
	super( new Gene( ( a_activeConfiguration == null ) ? new Long( 0 ) : a_activeConfiguration
			.nextInnovationId() ) );
	m_activeConfiguration = a_activeConfiguration;
	m_lowerBounds = a_lowerBounds;
	m_upperBounds = a_upperBounds;
	calculateBoundsUnitsToIntegerUnitsRatio();
}

/**
 * Provides an implementation-independent means for creating new Gene
 * instances. The new instance that is created and returned should be
 * setup with any implementation-dependent configuration that this Gene
 * instance is setup with (aside from the actual value, of course). For
 * example, if this Gene were setup with bounds on its value, then the
 * Gene instance returned from this method should also be setup with
 * those same bounds. This is important, as the JGAP core will invoke this
 * method on each Gene in the sample Chromosome in order to create each
 * new Gene in the same respective gene position for a new Chromosome.
 * <p>
 * It should be noted that nothing is guaranteed about the actual value
 * of the returned Gene and it should therefore be considered to be
 * undefined.
 * @param a_activeConfiguration The current active configuration.
 * @return A new Gene instance of the same type and with the same
 * setup as this concrete Gene.
 */
public Allele newAllele( Configuration a_activeConfiguration ) {
	return new IntegerAllele( a_activeConfiguration, m_lowerBounds, m_upperBounds );
}

/**
 * @return clone
 * @see Allele#cloneAllele()
 */
public Allele cloneAllele() {
	IntegerAllele result = new IntegerAllele( getGene() );
	result.setValue( m_value );
	return result;
}

/**
 * Sets the value (allele) of this Gene to the new given value. This class
 * expects the value to be an Integer instance. If the value is above
 * or below the upper or lower bounds, it will be mappped to within
 * the allowable range.
 * @param a_newValue the new value of this Gene instance.
 */
public void setValue( Integer a_newValue ) {
	m_value = a_newValue;
	// If the value isn't between the upper and lower bounds of this
	// IntegerGene, map it to a value within those bounds.
	// -------------------------------------------------------------
	mapValueToWithinBounds();
}

/**
 * @return value
 */
public Integer getValue() {
	return m_value;
}

/**
 * 
 * Retrieves a string representation of this Gene that includes any
 * 
 * information required to reconstruct it at a later time, such as its
 * 
 * value and internal state. This string will be used to represent this
 * 
 * Gene in XML persistence. This is an optional method but, if not
 * 
 * implemented, XML persistence and possibly other features will not be
 * 
 * available. An UnsupportedOperationException should be thrown if no
 * 
 * implementation is provided.
 * 
 * @return A string representation of this Gene's current state.
 * 
 * @throws UnsupportedOperationException to indicate that no implementation
 * 
 * is provided for this method.
 *  
 */
public String getPersistentRepresentation() throws UnsupportedOperationException {
	// The persistent representation includes the value, lower bound,
	// and upper bound. Each is separated by a colon.
	// --------------------------------------------------------------
	return toString() + PERSISTENT_FIELD_DELIMITER + m_lowerBounds + PERSISTENT_FIELD_DELIMITER
			+ m_upperBounds;
}

/**
 * 
 * Sets the value and internal state of this Gene from the string
 * 
 * representation returned by a previous invocation of the
 * 
 * getPersistentRepresentation() method. This is an optional method but,
 * 
 * if not implemented, XML persistence and possibly other features will not
 * 
 * be available. An UnsupportedOperationException should be thrown if no
 * 
 * implementation is provided.
 * 
 * @param a_representation the string representation retrieved from a
 * 
 * prior call to the getPersistentRepresentation()
 * 
 * method.
 * 
 * 
 * 
 * @throws UnsupportedOperationException to indicate that no implementation
 * 
 * is provided for this method.
 * 
 * @throws UnsupportedRepresentationException if this Gene implementation
 * 
 * does not support the given string representation.
 *  
 */
public void setValueFromPersistentRepresentation( String a_representation )
		throws UnsupportedRepresentationException {
	if ( a_representation != null ) {
		StringTokenizer tokenizer = new StringTokenizer( a_representation,
				PERSISTENT_FIELD_DELIMITER );
		// Make sure the representation contains the correct number of
		// fields. If not, throw an exception.
		// -----------------------------------------------------------
		if ( tokenizer.countTokens() != 3 ) {
			throw new UnsupportedRepresentationException(
					"The format of the given persistent representation "
							+ "is not recognized: it does not contain three tokens." );
		}
		String valueRepresentation = tokenizer.nextToken();
		String lowerBoundRepresentation = tokenizer.nextToken();
		String upperBoundRepresentation = tokenizer.nextToken();
		// First parse and set the representation of the value.
		// ----------------------------------------------------
		if ( valueRepresentation.equals( "null" ) ) {
			m_value = null;
		}
		else {
			try {
				m_value = new Integer( Integer.parseInt( valueRepresentation ) );
			}
			catch ( NumberFormatException e ) {
				throw new UnsupportedRepresentationException(
						"The format of the given persistent representation "
								+ "is not recognized: field 1 does not appear to be " + "an integer value." );
			}
		}
		// Now parse and set the lower bound.
		// ----------------------------------
		try {
			m_lowerBounds = Integer.parseInt( lowerBoundRepresentation );
		}
		catch ( NumberFormatException e ) {
			throw new UnsupportedRepresentationException(
					"The format of the given persistent representation "
							+ "is not recognized: field 2 does not appear to be " + "an integer value." );
		}
		// Now parse and set the upper bound.
		// ----------------------------------
		try {
			m_upperBounds = Integer.parseInt( upperBoundRepresentation );
		}
		catch ( NumberFormatException e ) {
			throw new UnsupportedRepresentationException(
					"The format of the given persistent representation "
							+ "is not recognized: field 3 does not appear to be " + "an integer value." );
		}
		// We need to recalculate the bounds units to integer units
		// ratio since our lower and upper bounds have probably just
		// been changed.
		// -------------------------------------------------------------
		calculateBoundsUnitsToIntegerUnitsRatio();
	}
}

/**
 * 
 * Retrieves the int value of this Gene, which may be more convenient in
 * 
 * some cases than the more general getAllele() method.
 * 
 * @return the int value of this Gene.
 *  
 */
public int intValue() {
	return m_value.intValue();
}

/**
 * 
 * Sets the value (allele) of this Gene to a random Integer value between
 * 
 * the lower and upper bounds (if any) of this Gene.
 * 
 * @param a_numberGenerator The random number generator that should be
 * 
 * used to create any random values. It's important
 * 
 * to use this generator to maintain the user's
 * 
 * flexibility to configure the genetic engine
 * 
 * to use the random number generator of their
 * 
 * choice.
 *  
 */
public void setToRandomValue( Random a_numberGenerator ) {
	m_value = new Integer( a_numberGenerator.nextInt() );
	// If the value isn't between the upper and lower bounds of this
	// IntegerGene, map it to a value within those bounds.
	// -------------------------------------------------------------
	mapValueToWithinBounds();
}

//    /**
//
//     * Compares this IntegerGene with the specified object (which must also
//
//     * be an IntegerGene) for order, which is determined by the integer
//
//     * value of this Gene compared to the one provided for comparison.
//
//     *
//
//     * @param other the IntegerGene to be compared to this IntegerGene.
//
//     * @return a negative integer, zero, or a positive integer as this object
//
//     * is less than, equal to, or greater than the object provided for
//
//     * comparison.
//
//     *
//
//     * @throws ClassCastException if the specified object's type prevents it
//
//     * from being compared to this IntegerGene.
//
//     */
//
//    public int compareTo( Object other )
//
//    {
//
//        IntegerGene otherIntegerGene = (IntegerGene) other;
//
//
//
//        // First, if the other gene (or its value) is null, then this is
//
//        // the greater allele. Otherwise, just use the Integer's compareTo
//
//        // method to perform the comparison.
//
//        // ---------------------------------------------------------------
//
//        if( otherIntegerGene == null )
//
//        {
//
//            return 1;
//
//        }
//
//        else if( otherIntegerGene.m_value == null )
//
//        {
//
//            // If our value is also null, then we're the same. Otherwise,
//
//            // this is the greater gene.
//
//            // ----------------------------------------------------------
//
//            return m_value == null ? 0 : 1;
//
//        }
//
//        else
//
//        {
//
//            try
//
//            {
//
//                return m_value.compareTo( otherIntegerGene.m_value );
//
//            }
//
//            catch( ClassCastException e )
//
//            {
//
//                e.printStackTrace();
//
//                throw e;
//
//            }
//
//        }
//
//    }
//
//
//
//    /**
//
//     * Compares this IntegerGene with the given object and returns true if
//
//     * the other object is a IntegerGene and has the same value (allele) as
//
//     * this IntegerGene. Otherwise it returns false.
//
//     *
//
//     * @param other the object to compare to this IntegerGene for equality.
//
//     * @return true if this IntegerGene is equal to the given object,
//
//     * false otherwise.
//
//     */
//
//    public boolean equals( Object other )
//
//    {
//
//        try
//
//        {
//
//            return compareTo( other ) == 0;
//
//        }
//
//        catch( ClassCastException e )
//
//        {
//
//            // If the other object isn't an IntegerGene, then we're not
//
//            // equal.
//
//            // ----------------------------------------------------------
//
//            return false;
//
//        }
//
//    }
/**
 * 
 * Retrieves the hash code value for this IntegerGene.
 * 
 * @return this IntegerGene's hash code.
 *  
 */
public int hashCode() {
	// If our internal Integer is null, then return zero. Otherwise,
	// just return the hash code of the Integer.
	// -------------------------------------------------------------
	if ( m_value == null ) {
		return 0;
	}
	return m_value.hashCode();
}

/**
 * 
 * Retrieves a string representation of this IntegerGene's value that
 * 
 * may be useful for display purposes.
 * 
 * @return a string representation of this IntegerGene's value.
 *  
 */
public String toString() {
	if ( m_value == null ) {
		return "null";
	}
	return getInnovationId() + ":" + m_value.toString();
}

/**
 * 
 * Executed by the genetic engine when this Gene instance is no
 * 
 * longer needed and should perform any necessary resource cleanup.
 *  
 */
public void cleanup() {
	// No specific cleanup is necessary for this implementation.
	// ---------------------------------------------------------
}

/**
 * 
 * Maps the value of this IntegerGene to within the bounds specified by
 * 
 * the m_upperBounds and m_lowerBounds instance variables. The value's
 * 
 * relative position within the integer range will be preserved within the
 * 
 * bounds range (in other words, if the value is about halfway between the
 * 
 * integer max and min, then the resulting value will be about halfway
 * 
 * between the upper bounds and lower bounds). If the value is null or
 * 
 * is already within the bounds, it will be left unchanged.
 *  
 */
protected void mapValueToWithinBounds() {
	if ( m_value != null ) {
		// If the value exceeds either the upper or lower bounds, then
		// map the value to within the legal range. To do this, we basically
		// calculate the distance between the value and the integer min,
		// determine how many bounds units that represents, and then add
		// that number of units to the upper bound.
		// -----------------------------------------------------------------
		if ( m_value.intValue() > m_upperBounds || m_value.intValue() < m_lowerBounds ) {
			long differenceFromIntMin = (long) Integer.MIN_VALUE + (long) m_value.intValue();
			int differenceFromBoundsMin = (int) ( differenceFromIntMin / m_boundsUnitsToIntegerUnits );
			m_value = new Integer( m_upperBounds + differenceFromBoundsMin );
		}
	}
}

/**
 * 
 * Calculates and sets the m_boundsUnitsToIntegerUnits field based
 * 
 * on the current lower and upper bounds of this IntegerGene. For example,
 * 
 * if the integer range is -2 billion to +2 billion and the bounds range
 * 
 * is -1 billion to +1 billion, then each unit in the bounds range would
 * 
 * map to 2 units in the integer range. The m_boundsUnitsToIntegerUnits
 * 
 * field would therefore be 2. This mapping unit is used to map illegal
 * 
 * allele values that are outside of the bounds to legal allele values that
 * 
 * are within the bounds.
 *  
 */
protected void calculateBoundsUnitsToIntegerUnitsRatio() {
	int divisor = m_upperBounds - m_lowerBounds + 1;
	if ( divisor == 0 ) {
		m_boundsUnitsToIntegerUnits = INTEGER_RANGE;
	}
	else {
		m_boundsUnitsToIntegerUnits = INTEGER_RANGE / divisor;
	}
}

}
