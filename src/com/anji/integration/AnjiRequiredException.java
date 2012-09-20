/*
 * Copyright (C) 2004  Derek James and Philip Tucker
 *
 * This file is part of ANJI (Another NEAT Java Implementation).
 *
 * ANJI is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * created by Philip Tucker on Mar 14, 2003
 */
package com.anji.integration;

/**
 * Thrown if ANJI-specific JGAP objects receive a non-ANJI configuration.
 * @author Philip Tucker
 */
public class AnjiRequiredException extends RuntimeException {
	
	/**
	 * @see AnjiRequiredException#AnjiRequiredException(String)
	 */
	public AnjiRequiredException() {
		this("");
	}

	/**
	 * @param message
	 * @see RuntimeException#RuntimeException(java.lang.String)
	 */
	public AnjiRequiredException(String message) {
		super("com.anji.* classes required: " + message);
	}

	/**
	 * @param message
	 * @param cause
	 * @see RuntimeException#RuntimeException(java.lang.String, java.lang.Throwable)
	 */
	public AnjiRequiredException(String message, Throwable cause) {
		super("com.anji.* classes required: " + message, cause);
	}

	/**
	 * @param cause
	 * @see AnjiRequiredException#AnjiRequiredException(String, Throwable)
	 */
	public AnjiRequiredException(Throwable cause) {
		this("", cause);
	}
}

