package org.jgapcustomised;

import java.util.List;

/**
 * An interface for classes that implement a strategy for dividing genomes into distinct {@link Species}.
 */
public interface SpeciationStrategy {
	/**
	 * Speciates the genomes in genomeList into the provided species, starting from scratch.
	 * 
	 * @param genomeList The list of genomes to speciate, must contain all genomes (including those that are
	 * already members of species in specieList.
	 * @param specieList A list of species, which may be empty.
	 * @param genotype The Genotype object performing evolution, speciation parameters may be obtained with 
	 * genotype.{@link Genotype#getConfiguration()}.{@link Configuration#getSpeciationParms()}.
	 */
	void respeciate(List<Chromosome> genomeList, List<Species> specieList, Genotype genotype);

	/**
	 * Speciates the genomes in genomeList into the provided species. Species may be added or removed,
	 * depending on the implementation.
	 * 
	 * @param genomeList The list of genomes to speciate, must contain all genomes (including those that are
	 * already members of species in specieList.
	 * @param specieList A list of species, which may be empty.
	 * @param genotype The Genotype object performing evolution, speciation parameters may be obtained with 
	 * genotype.{@link Genotype#getConfiguration()}.{@link Configuration#getSpeciationParms()}.
	 */
	void speciate(List<Chromosome> genomeList, List<Species> specieList, Genotype genotype);
}
