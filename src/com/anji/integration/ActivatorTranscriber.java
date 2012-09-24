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
 */
package com.anji.integration;

import ojc.ahni.hyperneat.GridNet;
import ojc.ahni.hyperneat.HyperNEATTranscriberGridNet;

import org.jgapcustomised.Chromosome;

import com.anji.nn.AnjiNet;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Factory interface to abstract construction of neural network objects. JOONE
 * implementation is not currently supported, but the guts of the code remain to
 * be re-addressed later.
 * 
 * @author Philip Tucker
 */
public class ActivatorTranscriber implements Configurable, Transcriber<Object> {

	/**
	 * neural network type properties key
	 */
	public final static String TYPE_KEY = "ann.type";

	/**
	 * # recurrent cycles properties key
	 */
	public final static String RECURRENT_CYCLES_KEY = "recurrent.cycles";

	/**
	 * enumerated type constant for ANJI ANN
	 */
	public final static String ANJI_TYPE = "anji";

	/**
	 * enumerated type constant for HyperNEAT ANJI ANN
	 */
	public final static String HYPERNEAT_TYPE = "hyperneat";

	/**
	 * enumerated type constant for JOONE ANN
	 */
	public final static String JOONE_TYPE = "joone";

	private String type = ANJI_TYPE;

	private int recurrentCycles;

	private Transcriber transcriber;

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter
	 * Details </a> for specific property settings.
	 * 
	 * @param props configuration parameters
	 * @throws TranscriberException
	 */
	public void init(Properties props) throws TranscriberException {
		type = props.getProperty(TYPE_KEY, ANJI_TYPE);
		recurrentCycles = props.getIntProperty(RECURRENT_CYCLES_KEY, 1);
		if (ANJI_TYPE.equals(type)) {
			transcriber = (Transcriber) props.singletonObjectProperty(AnjiNetTranscriber.class);
		} else if (HYPERNEAT_TYPE.equals(type)) {
			transcriber = (Transcriber) props.singletonObjectProperty(HyperNEATTranscriberGridNet.class);
		} else if (JOONE_TYPE.equals(type)) {
			throw new TranscriberException("JOONE not implemented");
		} else
			throw new IllegalStateException("invalid type: " + type);

	}

	/**
	 * Constructs <code>Activator</code> phenotype from <code>Chromosome</code>
	 * genotype. The specific implementatrion of Activator is determined by
	 * configuration parameters.
	 * 
	 * @param ch <code>Chromosome</code> from which activator will be built
	 * @return Activator phenotype built from <code>Chromosome</code> genotype
	 * @throws TranscriberException
	 */
	public Activator newActivator(Chromosome ch) throws TranscriberException {
		Activator result = null;
		if (ANJI_TYPE.equals(type)) {
			result = new AnjiActivator((AnjiNet) transcriber.transcribe(ch), recurrentCycles);
		} else if (HYPERNEAT_TYPE.equals(type)) {
			result = (GridNet) transcriber.transcribe(ch);
		} else if (JOONE_TYPE.equals(type)) {
			throw new TranscriberException("JOONE not implemented");
		} else
			throw new IllegalStateException("invalid type: " + type);

		return result;
	}

	/**
	 * @see com.anji.integration.Transcriber#getPhenotypeClass()
	 */
	public Class getPhenotypeClass() {
		return transcriber.getPhenotypeClass();
	}

	/**
	 * @see Transcriber#transcribe(Chromosome)
	 */
	public Object transcribe(Chromosome c) throws TranscriberException {
		return newActivator(c);
	}

	/**
	 * @see Transcriber#transcribe(Chromosome, Object)
	 */
	public Object transcribe(Chromosome c, Object s) throws TranscriberException {
		return newActivator(c);
	}
}
