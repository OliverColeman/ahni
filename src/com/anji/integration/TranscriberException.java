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
 * created by Philip Tucker on April 2, 2003
 */
package com.anji.integration;

/**
 * @author Philip Tucker
 */
public class TranscriberException extends Exception {

	private final static String PREFIX = "error transcribing JGAP/NEAT genotype to neural net phenotype";

	/**
	 * @see TranscriberException#TranscriberException(String)
	 */
	public TranscriberException() {
		this("");
	}

	/**
	 * @param message specific exception causing transcription to fail
	 */
	public TranscriberException(String message) {
		super(PREFIX + ": " + message);
	}

	/**
	 * @param message specific exception causing transcription to fail
	 * @param cause specific exception causing transcription to fail
	 */
	public TranscriberException(String message, Throwable cause) {
		super(PREFIX + ":" + message, cause);
	}

	/**
	 * @param cause
	 * @see TranscriberException#TranscriberException(String, Throwable)
	 */
	public TranscriberException(Throwable cause) {
		this("", cause);
	}

}

