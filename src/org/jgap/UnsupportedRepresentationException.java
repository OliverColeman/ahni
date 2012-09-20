/*
 * Copyright 2001-2003 Neil Rotstan
 *
 * This file is part of JGAP.
 *
 * JGAP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * JGAP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with JGAP; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jgap;


/**
 * This exception is typically thrown when the
 * setValueFromPersistentRepresentation() method of a Gene class is unable
 * to process the string representation it has been given, either because that
 * representation is not supported by that Gene implementation or because
 * the representation is corrupt.
 */
public class UnsupportedRepresentationException extends Exception
{
    /**
     * Constructs a new UnsupportedRepresentationException instance with the
     * given error message.
     *
     * @param a_message An error message describing the reason this exception
     *                  is being thrown.
     */
    public UnsupportedRepresentationException( String a_message )
    {
        super( a_message );
    }
}

