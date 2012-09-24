/*
 * Copyright 2001-2003 Neil Rotstan
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
 * Modified on Feb 3, 2003 by Philip Tucker
 */
package org.jgapcustomised;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Natural selectors are responsible for actually selecting a specified number
 * of Chromosome specimens from a population, using the fitness values as a
 * guide. Usually fitness is treated as a statistic probability of survival,
 * not as the sole determining factor. Therefore, Chromosomes with higher
 * fitness values are more likely to survive than those with lesser fitness
 * values, but it's not guaranteed.
 */
public abstract class NaturalSelector {
    private int numChromosomes;
    private double survivalRate = 0;
    private double elitismProportion = 0.1f;
    private int elitismMinToSelect = 1;
    private int elitismMinSpeciesSize = 5;
    private int maxStagnantGenerations = 0;
    private int minAge = 10;
    protected boolean speciatedFitness = true;
    private List<Chromosome> elite = new ArrayList<Chromosome>();

    /**
     * If elitism is enabled, places appropriate chromosomes in <code>elite</code> list.  Elitism follows
     * methodology in <a href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT</a>.  Passes
     * everything else to subclass <code>add( Configuration config, Chromosome c )</code> method.
     * @param config
     * @param chroms <code>List</code> contains Chromosome objects
     */
    public final void add(Configuration config, List<Species> species, List<Chromosome> chroms) {
        numChromosomes += chroms.size();
        
        //determine elites for each species
        Iterator<Species> speciesIter = species.iterator();
        while (speciesIter.hasNext()) {
            Species s = speciesIter.next();
            if (s.getStagnantGenerationsCount() < maxStagnantGenerations) {
                if ((elitismProportion > 0 || elitismMinToSelect > 0) && (s.size() >= elitismMinSpeciesSize || s.getAge() < minAge)) {
                    List<Chromosome> elites = s.getElite(elitismProportion, elitismMinToSelect);
                    elite.addAll(elites);
                    //System.out.println("Adding " + elites.size() + " elites from species: " + s.getID());
                }
            }
            //else {
            //    System.out.println("Not adding species, too stagnant, ID: " + s.getID());
            //}
        }

        Iterator<Chromosome> chromIter = chroms.iterator();
        while (chromIter.hasNext()) {
            Chromosome c = chromIter.next();
            Species specie = c.getSpecie();

            //don't add if it's already in the list of elites
            if (!c.isElite) {
                //if this individual's species hasn't been stagnant for too long add it to the pool
                if (specie == null || (specie != null && specie.getStagnantGenerationsCount() < maxStagnantGenerations)) {
                    add(config, c);
                }
            }
            //else {
            //    System.out.println("Not adding chrom: already in elites");
            //}
        }

        //System.out.println("selected " + elite.size() + " chroms as elite");
    }

    /**
     * @param config
     * @param c chromosome to add to selection pool
     */
    protected abstract void add(Configuration config, Chromosome c);

    /**
     * Select a given number of Chromosomes from the pool that will move on
     * to the next generation population. This selection should be guided by
     * the fitness values.
     * Elite chromosomes always survivie, unless there are more elite than the survival rate permits.  In this case,
     * elite with highest fitness are chosen.  Remainder of survivors are determined by subclass
     * <code>select( Configuration config, int numToSurvive )</code> method.
     * @param config
     * @return List contains Chromosome objects
     */
    public List<Chromosome> select(Configuration config) {
        //start with elites
    	List<Chromosome> result = new ArrayList<Chromosome>(elite);

        int numToSelect = (int) ((numChromosomes * getSurvivalRate()) + 0.5);
    
        if (result.size() > numToSelect) {
        	//remove least fittest (not species fitness sharing) from selected
            Collections.sort(result, new ChromosomeFitnessComparator<Chromosome>(true /* asc */, false /* speciated fitness */));
            int numToRemove = result.size() - numToSelect;
            for (int i = 0; i < numToRemove; ++i) {
                result.remove(0);
            }
        } else if (result.size() < numToSelect) {
            int moreToSelect = numToSelect - result.size();
            List<Chromosome> more = select(config, moreToSelect);
            result.addAll(more);
        }

        return result;
    }

    /**
     * @param config
     * @param numToSurvive
     * @return <code>List</code> contains <code>Chromosome</code> objects, those that have survived; size
     * of this list should be <code>numToSurvive</code>, unless fewer than that number of chromosomes have
     * been added to selector
     */
    protected abstract List<Chromosome> select(Configuration config, int numToSurvive);

    /**
     * clear pool of candidate chromosomes
     * @see NaturalSelector#emptyImpl()
     */
    public void empty() {
        numChromosomes = 0;
        elite.clear();
        emptyImpl();
    }

    /**
     * @see NaturalSelector#empty()
     */
    protected abstract void emptyImpl();

    /**
     * @return double survival rate
     */
    public double getSurvivalRate() {
        return survivalRate;
    }

    /**
     * @param aSurvivalRate
     */
    public void setSurvivalRate(double aSurvivalRate) {
        if (aSurvivalRate < 0.0 || aSurvivalRate > 1.0) {
            throw new IllegalArgumentException("0.0 <= survivalRate <= 1.0");
        }
        this.survivalRate = aSurvivalRate;
    }

    /**
     * @return minimum size a specie must be to support an elite chromosome
     */
    public int getElitismMinSpeciesSize() {
        return elitismMinSpeciesSize;
    }

    /**
     * @param i minimum size a specie must be to support an elite chromosome
     */
    public void setElitismMinSpeciesSize(int i) {
        elitismMinSpeciesSize = i;
    }

    /**
     * @param b true if elitisim is to be enabled
     */
    public void setElitismProportion(double p) {
        elitismProportion = p;
    }

    /**
     * @param i minimum size a specie must be to support an elite chromosome
     */
    public void setElitismMinToSelect(int i) {
        elitismMinToSelect = i;
    }

    /**
     * @param i minimum size a specie must be to support an elite chromosome
     */
    public void setSpeciatedFitness(boolean sf) {
        speciatedFitness = sf;
    }

    /**
     * @param i maximum number of generations a species can survive without increasing fitness
     */
    public void setMaxStagnantGenerations(int i) {
        maxStagnantGenerations = i;
    }

	public void setMinAge(int minAge) {
		this.minAge = minAge;
	}
}

