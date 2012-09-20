/**
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
import org.jgap.Chromosome;

/**
 * Provides a pooling mechanism for Chromosome instances so that
 * discarded Chromosome instances can be recycled, thus saving memory and the
 * overhead of constructing new ones from scratch each time.
 */
public class ChromosomePool
{
    /**
     * The internal pool in which the Chromosomes are stored.
     */
    private Pool m_chromosomePool;

    /**
     * Constructor.
     */
    public ChromosomePool()
    {
        m_chromosomePool = new Pool();
    }

    /**
     * Attempts to acquire an Chromosome instance from the chromosome pool.
     * It should be noted that nothing is guaranteed about the value of the
     * Chromosome's genes and they should be treated as undefined.
     *
     * @return A Chromosome instance from the pool or null if no
     *         Chromosome instances are available in the pool.
     */
    public synchronized Chromosome acquireChromosome()
    {
        return (Chromosome) m_chromosomePool.acquirePooledObject();
    } 

    /**
     * Releases a Chromosome to the pool. It's not required that the Chromosome
     * originated from the pool--any Chromosome can be released to it. This
     * method will invoke the cleanup() method on each of the Chromosome's
     * genes prior to adding it back to the pool.
     *
     * @param a_chromosome The Chromosome instance to be released into the pool.
     */
    public synchronized void releaseChromosome(Chromosome a_chromosome) {
        // Now add it to the pool.
        // -----------------------
        m_chromosomePool.releaseObject( a_chromosome );
    }

}




