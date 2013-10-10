package com.anji.neat;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeFitnessComparator;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.Genotype;
import org.jgapcustomised.SpeciationParms;
import org.jgapcustomised.SpeciationStrategy;
import org.jgapcustomised.Species;

/**
 * <p>Implements the original speciation strategy for NEAT as described in:<br />
 * EVOLVING NEURAL NETWORKS THROUGH AUGMENTING TOPOLOGIES<br />
 * Kenneth O. Stanley and Risto Miikkulainen <br />
 * Department of Computer Sciences, The University of Texas at Austin<br />
 * Evolutionary Computation 10(2):99-127, 2002.
 * </p>
 */ 
public class SpeciationStrategyOriginal implements SpeciationStrategy {
	private static Logger logger = Logger.getLogger(SpeciationStrategyOriginal.class);

	protected int lastGenChangedSpeciesCompatThreshold = 0;

	@Override
	public void respeciate(List<Chromosome> genomeList, List<Species> speciesList, Genotype genotype) {
		speciesList.clear();
		for (Chromosome chrom : genomeList) {
			chrom.resetSpecie();
		}
		speciate(genomeList, speciesList, genotype);
	}

	@Override
	public void speciate(List<Chromosome> genomeList, List<Species> speciesList, Genotype genotype) {
		SpeciationParms specParms = genotype.getConfiguration().getSpeciationParms();
		
		// sort so fittest are first as it's probably best to use the fittest as the representative 
		// of a species in case of creating new species.
		Collections.sort(genomeList, new ChromosomeFitnessComparator(false, false));
		//Collections.shuffle(genomeList, m_activeConfiguration.getRandomGenerator());

		// First determine new species for each chromosome (but don't assign yet).
		for (Chromosome chrom : genomeList) {
			if (chrom.getSpecie() == null) {
				boolean added = false;
				for	(Species species : speciesList) {
					if (match(species, chrom.getMaterial(), specParms)) {
						chrom.setSpecie(species);
						added = true;
						break;
					}
				}
				if (!added) {
					// this also sets the species of chrom to the new species.
					Species species = new Species(specParms, chrom); 
					speciesList.add(species);
					// System.out.println("Added new species");
				}
			}
		}

		// remove chromosomes from all species and record previous fittest
		for	(Species species : speciesList) {
			species.setPreviousBestPerforming(species.getBestPerforming());
			species.clear();
		}
		
		// then (re)assign chromosomes to their correct species
		for (Chromosome chrom : genomeList) {
			Species newSpecies = chrom.getSpecie();
			chrom.resetSpecie(); 
			newSpecies.add(chrom); // Also updates species reference in chrom.
		}
		
		// Remove any empty species.
		Iterator<Species> speciesIter = speciesList.iterator();
		while (speciesIter.hasNext()) {
			Species s = speciesIter.next();
			if (s.isEmpty()) {
				s.originalSize = 0;
				speciesIter.remove();
			}
		}
		
		// Attempt to maintain species count target, don't change threshold too frequently.
		int targetSpeciesCount = specParms.getSpeciationTarget();
		int maxAdjustFreq = (int) Math.round(Math.pow(genotype.getConfiguration().getPopulationSize(), 0.333));
		if (targetSpeciesCount > 0 && speciesList.size() != targetSpeciesCount && (genotype.getGeneration() - lastGenChangedSpeciesCompatThreshold > maxAdjustFreq)) {
			double ratio = (double) speciesList.size() / targetSpeciesCount;
			double factor = (ratio - 1) * 0.2 + 1;
			double newSpecThresh = specParms.getSpeciationThreshold() * factor;
			if (newSpecThresh < specParms.getSpeciationThresholdMin()) newSpecThresh = specParms.getSpeciationThresholdMin();
			if (newSpecThresh > specParms.getSpeciationThresholdMax()) newSpecThresh = specParms.getSpeciationThresholdMax();
			specParms.setSpeciationThreshold(newSpecThresh);
			lastGenChangedSpeciesCompatThreshold = genotype.getGeneration();
		}
	}
	
	/**
	 * @param aChromosome
	 * @return boolean true iff compatibility difference between <code>aChromosome</code? and representative is less
	 *         than speciation threshold
	 */
	private boolean match(Species species, ChromosomeMaterial chromosome, SpeciationParms speciationParms) {
		return (species.getRepresentative().distance(chromosome, speciationParms) < speciationParms.getSpeciationThreshold());
	}
}
