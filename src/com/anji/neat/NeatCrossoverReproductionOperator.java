package com.anji.neat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jgapcustomised.Allele;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.CrossoverReproductionOperator;

import com.anji.integration.AnjiRequiredException;

/**
 * Implements NEAT crossover reproduction according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">Evolving Neural Networks through Augmenting
 * Topologies </a>.
 * 
 * @author Oliver Coleman
 */
public class NeatCrossoverReproductionOperator extends CrossoverReproductionOperator {
	/**
	 * Crossover according to <a href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT </a> crossover
	 * methodology.
	 * 
	 * @param jgapConfig
	 * @param parent1 (possibly) dominant parent
	 * @param parent2 recessive parent
	 * @return ChromosomeMaterial offspring
	 */
	protected ChromosomeMaterial reproduce(Configuration jgapConfig, Chromosome parent1, Chromosome parent2) {
		if ((jgapConfig instanceof NeatConfiguration) == false)
			throw new AnjiRequiredException("com.anji.neat.NeatConfiguration");
		NeatConfiguration config = (NeatConfiguration) jgapConfig;

		ChromosomeMaterial child = null;
		
		// If parent1 dominates parent2 (the superclass already performs a check and makes sure that parent2 does not dominate parent1).
		if (true) { //parent1.dominates(parent2)) {
			// Child inherits all structure/genes from dominant parent.
			child = parent1.cloneMaterial();
			child.setSecondaryParentId(parent2.getId());

			// Values (eg weights) for genes are mixture of those from both parents (where the non-dominant parent has a matching gene).
			for (Allele allele1 : child.getAlleles()) {
				Allele allele2 = parent2.findMatchingGene(allele1);
				if (allele2 != null) { // if rec chrom has allele with same id
					int valueSwitch = config.getRandomGenerator().nextInt(3);
					// valueSwitch == 0 means we use parent1 allele value, nothing to do.
					if (valueSwitch == 1) {
						// Use parent2 allele value.
						allele1.setValue(allele2.getValue());
					}
					else if (valueSwitch == 2) {
						// Use value somewhere between those from both parents.
						double s = config.getRandomGenerator().nextDouble();
						allele1.setValue(allele1.getValue() * s + allele2.getValue() * (1-s));
					} 
				}
			}
		}
		// Neither parent dominates the other. 
		else {
			SortedSet<Allele> childAlleles = new TreeSet<Allele>();
			ChromosomeMaterial m1 = parent1.getMaterial();
			ChromosomeMaterial m2 = parent2.getMaterial();
			Iterator<Allele> itrP1 = m1.getAlleles().iterator();
			Iterator<Allele> itrP2 = m2.getAlleles().iterator();
			Allele allele1 = itrP1.next();
			Allele allele2 = itrP2.next();
			List<ConnectionAllele> addedConns = new ArrayList<ConnectionAllele>(Math.max(parent1.size(), parent2.size()));
			
			// Iterate through alleles from both parents, adding them to child as we go.
			do {
				Allele childAllele = null;
				// If we have not reached excess alleles yet.
				if (allele1 != null && allele2 != null) {
					// Add allele that is missing from one or the other (or that both parents have in which case we default to parent 1).
					childAllele = allele1.getInnovationId() <= allele2.getInnovationId() ? allele1.cloneAllele() : allele2.cloneAllele();
					
					// If both parents have this allele, allow using value from one or the other or a blended value.
					if (allele1.getInnovationId() == allele2.getInnovationId()) {
						int valueSwitch = config.getRandomGenerator().nextInt(3);
						// valueSwitch == 0 means we use parent1 allele value, nothing to do.
						if (valueSwitch == 1) {
							// Use parent2 allele value.
							childAllele.setValue(allele2.getValue());
						}
						else if (valueSwitch == 2) {
							// Use value somewhere between those from both parents.
							double s = config.getRandomGenerator().nextDouble();
							childAllele.setValue(allele1.getValue() * s + allele2.getValue() * (1-s));
						}
					}
					
					// If both chromosomes have this allele, iterate to next on both.
					if (allele1.getInnovationId() == allele2.getInnovationId()) {
						allele1 = itrP1.hasNext() ? itrP1.next() : null;
						allele2 = itrP2.hasNext() ? itrP2.next() : null;
					}
					// Otherwise iterate on whichever had the disjoint allele.
					else if (allele1.getInnovationId() < allele2.getInnovationId()) {
						allele1 = itrP1.hasNext() ? itrP1.next() : null;
					}
					else {
						allele2 = itrP2.hasNext() ? itrP2.next() : null;
					}
				}
				// Iterating over excess alleles.
				else {
					// parent1 has the allele.
					if (allele1 != null) {
						childAllele = allele1.cloneAllele();
						allele1 = itrP1.hasNext() ? itrP1.next() : null;
					}
					// parent2 has the allele.
					else {
						childAllele = allele2.cloneAllele();
						allele2 = itrP2.hasNext() ? itrP2.next() : null;
					}
				}
				
				if (childAllele != null) {
					// If this is a connection allele make sure adding it won't create a cycle if only feed-forward networks are allowed.
					// This may leave neurons with only incoming or outgoing connections; the prune mutation operator will clean it up.
					boolean add = true;
					if (childAllele instanceof ConnectionAllele) {
						ConnectionAllele conn = ((ConnectionAllele) childAllele);
						if (!NeatChromosomeUtility.connectionAllowed(config, conn.getSrcNeuronId(), conn.getDestNeuronId(), addedConns)) {
							add = false;
						}
						else {
							addedConns.add(conn);
						}
					}
					if (add) {
						childAlleles.add(childAllele);
					}
				}
			} while (allele1 != null || allele2 != null);
			
			child = new ChromosomeMaterial(childAlleles, parent1.getId(), parent2.getId());
			
			// Make sure there are no dangling or isolated structures in the resultant network
			// (eg if connections that would cause recurrence have not been included).
			Set<Allele> unusedAllelles = PruneMutationOperator.getAllelesToRemove(child);
			child.getAlleles().removeAll(unusedAllelles);
		}

		return child;
	}
}
