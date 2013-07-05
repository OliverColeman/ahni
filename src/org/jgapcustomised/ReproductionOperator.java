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
package org.jgapcustomised;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jgapcustomised.impl.CloneReproductionOperator;

/**
 * Abstract class for reproduction operators. Handles intra-species breeding and someday will handle inter-species
 * breeding. Each specie gets a number of offspring relative to its fitness, following <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT</a> paradigm.
 * 
 * @author Philip Tucker
 */
public abstract class ReproductionOperator {
	private double slice = 0.0f;
	// The probability that individuals produced by this operator will be a candidate for having mutations applied to them (independent of the mutation rates of MutationOperators).
	private double mutateProbability = 1;


	/**
	 * The reproduce method will be invoked on each of the reproduction operators referenced by the current
	 * Configuration object during the evolution phase. Operators are given an opportunity to run in the order they are
	 * added to the Configuration. Iterates over species and determines each one's number of offspring based on fitness,
	 * then passes control to subclass <code>reproduce( final Configuration config,
	 * final List parents, int numOffspring, List offspring )</code> method to perform specific reproduction.
	 * 
	 * @param config The current active genetic configuration.
	 * @param parentSpecies <code>List</code> contains <code>Species</code> objects containing parent chromosomes from
	 *            which to produce offspring.
	 * @param offspring <code>List</code> contains offspring <code>ChromosomeMaterial</code> objects; this method adds
	 *            new offspring to this list
	 * @throws InvalidConfigurationException
	 * @see ReproductionOperator#reproduce(Configuration, List, int, List)
	 */
	final public void reproduce(final Configuration config, final List<Species> parentSpecies, List<ChromosomeMaterial> offspring) throws InvalidConfigurationException {
		int targetNewOffspringCount = (int) Math.round(config.getPopulationSize() * getSlice());

		if (targetNewOffspringCount > 0) {
			if (parentSpecies.isEmpty()) {
				throw new IllegalStateException("no parent species from which to produce offspring");
			}
			List<ChromosomeMaterial> newOffspring = new ArrayList<ChromosomeMaterial>(targetNewOffspringCount);

			// calculate total fitness
			double totalSpeciesFitness = 0;
			for (Species specie : parentSpecies) {
				totalSpeciesFitness += specie.getAverageFitnessValue();
			}

			// reproduce from each specie relative to its percentage of total fitness
			for (Species specie : parentSpecies) {
				double percentFitness = specie.getAverageFitnessValue() / totalSpeciesFitness;
				int numSpecieOffspring =  (int) Math.round(percentFitness * targetNewOffspringCount) - specie.getEliteCount();
				// Always create at least one offspring with the clone operator, or any operator if it has more than 50% of the slice.
				// (Otherwise there's no point hanging on to a species).
				if (numSpecieOffspring <= 0 && (getSlice() > 0.5 || getClass().equals(CloneReproductionOperator.class)))
					numSpecieOffspring = 1;
				
				if (numSpecieOffspring > 0)
					reproduce(config, specie.getChromosomes(), numSpecieOffspring, newOffspring);
			}

			// Remove random offspring if we have too many.
			while (newOffspring.size() > targetNewOffspringCount) {
				Collections.shuffle(newOffspring, config.getRandomGenerator());
				newOffspring.remove(newOffspring.size()-1);
			}
			
			for (ChromosomeMaterial c : newOffspring) {
				c.setShouldMutate(mutateProbability > config.getRandomGenerator().nextDouble());
			}
			
			// Add clones of random offspring if we don't have enough.
			while (newOffspring.size() > 0 && newOffspring.size() < targetNewOffspringCount) {
				int idx = config.getRandomGenerator().nextInt(newOffspring.size());
				ChromosomeMaterial clonee = (ChromosomeMaterial) newOffspring.get(idx);
				ChromosomeMaterial c = clonee.clone(null);
				// Clones should always be mutated.
				c.setShouldMutate(true);
				newOffspring.add(c);
			}

			offspring.addAll(newOffspring);
		}
	}

	/**
	 * @param config
	 * @param parents List contains chromosome objects
	 * @param numOffspring # Chromosomes to return in List
	 * @param offspring List contains ChromosomeMaterial objects
	 * @throws InvalidConfigurationException
	 * @see ReproductionOperator#reproduce(Configuration, List, List)
	 */
	protected abstract void reproduce(final Configuration config, final List<Chromosome> parents, int numOffspring, List<ChromosomeMaterial> offspring) throws InvalidConfigurationException;

	/**
	 * @return double slice of population this reproduction operator will fill with offspring
	 */
	final public double getSlice() {
		return slice;
	}

	/**
	 * @param aSlice slice of population this reproduction operator will fill with offspring
	 */
	final public void setSlice(double aSlice) {
		this.slice = aSlice;
	}
	
	/**
	 * Set the probability that individuals produced by this operator will be a candidate for having mutations applied to them (independent of the mutation rates of MutationOperators).
	 * @param mutateRate the mutateRate to set
	 */
	public void setMutateRate(double mutateRate) {
		this.mutateProbability = mutateRate;
	}

}
