/*
 * Copyright (C) 2004  Derek James and Philip Tucker
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
 * 
 * Created on Feb 3, 2003 by Philip Tucker
 */
package org.jgap;

/**
 * Similar to <code>InvalidConfigurationException</code>, but in runtime form so it can be thrown from
 * methods that do not throw exceptions.
 * @author Philip Tucker
 * @see org.jgap.InvalidConfigurationException
 */
public class InvalidConfigurationRuntimeException extends RuntimeException {

	/**
	 * @see InvalidConfigurationRuntimeException#InvalidConfigurationRuntimeException(String)
	 */
	public InvalidConfigurationRuntimeException() {
		this("");
	}

	/**
	 * @param message
	 * @see RuntimeException#RuntimeException(java.lang.String)
	 */
	public InvalidConfigurationRuntimeException(String message) {
		super( "InvalidConfigurationRuntimeException: " + message );
	}

	/**
	 * @param message
	 * @param cause
	 * @see RuntimeException#RuntimeException(java.lang.String, java.lang.Throwable)
	 */
	public InvalidConfigurationRuntimeException( String message, Throwable cause ) {
		super( "InvalidConfigurationRuntimeException: " + message, cause );
	}

	/**
	 * @param cause
	 * @see InvalidConfigurationRuntimeException#InvalidConfigurationRuntimeException(String, Throwable)
	 */
	public InvalidConfigurationRuntimeException( Throwable cause ) {
		this( "", cause );
	}

}

