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
 * created by Philip Tucker on Feb 16, 2003
 */
package com.anji.neat;

import java.util.Iterator;

import org.jgap.Allele;
import org.jgap.Chromosome;
import org.jgap.ChromosomeMaterial;
import org.jgap.Configuration;
import org.jgap.CrossoverReproductionOperator;

/**
 * Implements NEAT crossover reproduction according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">Evolving Neural Networks
 * through Augmenting Topologies </a>.
 * 
 * @author Philip Tucker
 */
public class NeatCrossoverReproductionOperator extends CrossoverReproductionOperator {
    /**
     * Crossover according to <a
     * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT </a> crossover
     * methodology.
     *
     * @param config
     * @param dominantChrom dominant parent
     * @param recessiveChrom recessive parent
     * @return ChromosomeMaterial offspring
     */
    protected ChromosomeMaterial reproduce(Configuration config, Chromosome dominantChrom, Chromosome recessiveChrom) {
        ChromosomeMaterial child = dominantChrom.cloneMaterial();
        child.setSecondaryParentId(recessiveChrom.getId());

        Iterator iter = child.getAlleles().iterator();
        while (iter.hasNext()) {
            Allele allele = (Allele) iter.next();
            if (allele instanceof ConnectionAllele) {
                ConnectionAllele dominantConnectionAllele = (ConnectionAllele) allele;
                ConnectionAllele recessiveConnectionAllele = (ConnectionAllele) recessiveChrom.findMatchingGene(dominantConnectionAllele);
                if (recessiveConnectionAllele != null) { //if rec chrom has connection with same id
                    // TODO blending?  (didn't seem to help)
            	    if (config.getRandomGenerator().nextBoolean()) {
                        dominantConnectionAllele.setWeight(recessiveConnectionAllele.getWeight());
                    }
            	    //dominantConnectionAllele.setWeight((dominantConnectionAllele.getWeight() + recessiveConnectionAllele.getWeight()) / 2);
                }
            }
        }

        return child;
    }
}
