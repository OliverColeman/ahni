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
 * Created on Mar 11, 2004 by Philip Tucker
 * Edited by Oliver Coleman
 */
package com.anji.integration;

import org.jgapcustomised.Chromosome;

import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Factory interface to abstract construction of neural network objects. JOONE implementation is not currently
 * supported, but the guts of the code remain to be re-addressed later.
 * 
 * @author Philip Tucker
 */
public class ActivatorTranscriber implements Configurable {
	/**
	 * The class (implementing {@link Transcriber}) that will be used to transcribe a network (implementing {@link Activator}) from a {@link org.jgapcustomised.Chromosome}.
	 */
	public final static String TRANSCRIBER_KEY = "ann.transcriber";

	private Transcriber transcriber;

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param props configuration parameters
	 * @throws TranscriberException
	 */
	public void init(Properties props) throws TranscriberException {
		transcriber = (Transcriber) props.singletonObjectProperty(TRANSCRIBER_KEY);
	}

	/**
	 * Constructs an {@link Activator} phenotype from a {@link org.jgapcustomised.Chromosome}.
	 * @param ch The Chromosome from which the phenotype will be decoded.
	 * @return Activator phenotype decoded from the Chromosome.
	 * @throws TranscriberException
	 */
	public Activator newActivator(Chromosome ch) throws TranscriberException {
		return transcriber.transcribe(ch);
	}

	/**
	 * @see com.anji.integration.Transcriber#getPhenotypeClass()
	 */
	public Class getPhenotypeClass() {
		return transcriber.getPhenotypeClass();
	}
}
