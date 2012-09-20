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
 * This exception is typically thrown when an invalid value has been
 * passed to a Configuration object, an attempt is made to lock a Configuration
 * object before all required settings have been provided, or an attempt is
 * made to alter a setting in a Configuration object after it has been
 * successfully locked.
 * @see org.jgap.InvalidConfigurationRuntimeException
 */
public class InvalidConfigurationException extends Exception
{
    /**
     * Constructs a new InvalidConfigurationException instance with the
     * given error message.
     *
     * @param a_message An error message describing the reason this exception
     *                  is being thrown.
     */
    public InvalidConfigurationException( String a_message )
    {
        super( a_message );
    }
}

