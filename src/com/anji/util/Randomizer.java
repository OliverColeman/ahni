/*
 * Copyright (C) 2004 Derek James and Philip Tucker This file is part of ANJI (Another NEAT Java
 * Implementation). ANJI is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA Created on Feb 6, 2004 by Philip Tucker
 */
package com.anji.util;

import java.util.Random;

/**
 * Singleton holder of <code>Random</code> object to ensure all of system is using same random
 * sequence. This is important for testing and diagnostics since it can guarantee
 * reproducability.
 * 
 * @author Philip Tucker
 */
public class Randomizer implements Configurable {

private static final String RANDOM_SEED_KEY = "random.seed";

private Random rand = new Random();

private long seed = 0;

/**
 * should call <code>init()</code> after ctor
 */
public Randomizer() {
	// noop
}

/**
 * See <a href=" {@docRoot}/params.htm" target="anji_params">Parameter Details </a> for
 * specific property settings.
 * 
 * @param props configuration parameters
 */
public synchronized void init( Properties props ) {
	seed = props.getLongProperty( RANDOM_SEED_KEY, System.currentTimeMillis() );
	rand.setSeed( seed );
}

/**
 * @return Random
 */
public Random getRand() {
	return rand;
}

/**
 * @return seed
 */
public long getSeed() {
	return seed;
}
}
