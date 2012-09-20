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
package org.jgap.impl;

import java.util.Random;

import org.jgap.Configuration;
import org.jgap.InvalidConfigurationException;
import org.jgap.event.EventManager;


/**
 * The DefaultConfiguration class simplifies the JGAP configuration
 * process by providing default configuration values for many of the
 * configuration settings. The following values must still be provided:
 * the sample Chromosome, population size, and desired fitness function.
 * All other settings may also be changed in the normal fashion for
 * those who wish to specify other custom values.
 */
public class DefaultConfiguration extends Configuration
{
    /**
     * Constructs a new DefaultConfiguration instance with a number of
     * Configuration settings set to default values. It is still necessary
     * to set the sample Chromosome, population size, and desired fitness
     * function. Other settings may optionally be altered as desired.
     */
    public DefaultConfiguration()
    {
        super();

        try
        {
            setNaturalSelector( new WeightedRouletteSelector() );
            setRandomGenerator( new Random() );
            setEventManager( new EventManager() );
        }
        catch ( InvalidConfigurationException e )
        {
            throw new RuntimeException(
                "Fatal error: DefaultConfiguration class could not use its " +
                "own stock configuration values. This should never happen. " +
                "Please report this as a bug to the JGAP team." );
        }
    }
}

