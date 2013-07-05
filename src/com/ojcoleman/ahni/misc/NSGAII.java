package com.ojcoleman.ahni.misc;

import java.util.*;

import org.jgapcustomised.Chromosome;

/**
 * This class implements the non-dominated sorting method selection method (according to rank and then crowding
 * comparison operator) based on the multi-objective genetic algorithm NSGA-II as described in DEB, Kalyanmoy ; PRATAP,
 * Amrit ; AGARWAL, Sameer A. ; MEYARIVAN, T.: "A Fast and Elitist Multiobjective Genetic Algorithm: NSGA-II". In: IEEE
 * Transactions on Evolutionary Computation, vol. 6, no. 2, April 2002, pp. 182-197.
 * 
 * This code is based on JNSGA2 by Joachim Melcher, Institut AIFB, Universitaet Karlsruhe (TH), Germany
 * http://sourceforge.net/projects/jnsga2
 */
public class NSGAII {
	/**
	 * Performs a fast non-domination sort of the specified individuals. The method returns the different domination
	 * fronts in ascending order by their rank and sets their rank value.
	 * 
	 * @param individuals individuals to sort
	 * @return domination fronts in ascending order by their rank
	 */
	public static List<List<Chromosome>> fastNonDominatedSort(List<Chromosome> individuals) {
		List<List<Chromosome>> dominationFronts = new ArrayList<List<Chromosome>>();

		HashMap<Chromosome, List<Chromosome>> individual2DominatedChromosomes = new HashMap<Chromosome, List<Chromosome>>();
		HashMap<Chromosome, Integer> individual2NumberOfDominatingChromosomes = new HashMap<Chromosome, Integer>();

		for (Chromosome individualP : individuals) {
			individual2DominatedChromosomes.put(individualP, new ArrayList<Chromosome>());
			individual2NumberOfDominatingChromosomes.put(individualP, 0);

			for (Chromosome individualQ : individuals) {
				if (individualP.dominates(individualQ)) {
					individual2DominatedChromosomes.get(individualP).add(individualQ);
				} else {
					if (individualQ.dominates(individualP)) {
						individual2NumberOfDominatingChromosomes.put(individualP, individual2NumberOfDominatingChromosomes.get(individualP) + 1);
					}
				}
			}

			if (individual2NumberOfDominatingChromosomes.get(individualP) == 0) {
				// p belongs to the first front
				if (dominationFronts.isEmpty()) {
					List<Chromosome> firstDominationFront = new ArrayList<Chromosome>();
					firstDominationFront.add(individualP);
					dominationFronts.add(firstDominationFront);
				} else {
					List<Chromosome> firstDominationFront = dominationFronts.get(0);
					firstDominationFront.add(individualP);
				}
			}
		}

		int i = 1;
		while (dominationFronts.size() == i) {
			List<Chromosome> nextDominationFront = new ArrayList<Chromosome>();
			for (Chromosome individualP : dominationFronts.get(i - 1)) {
				for (Chromosome individualQ : individual2DominatedChromosomes.get(individualP)) {
					individual2NumberOfDominatingChromosomes.put(individualQ, individual2NumberOfDominatingChromosomes.get(individualQ) - 1);
					if (individual2NumberOfDominatingChromosomes.get(individualQ) == 0) {
						nextDominationFront.add(individualQ);
					}
				}
			}
			i++;
			if (!nextDominationFront.isEmpty()) {
				dominationFronts.add(nextDominationFront);
			}
		}

		return dominationFronts;
	}

	public static List<Chromosome> getTop(List<List<Chromosome>> fronts, int numToSelect) {
		// Add all members from each successive rank until the next rank to add would go over the desired size.
		ArrayList<Chromosome> top = new ArrayList<Chromosome>();
		int i = 0;
		while (i < fronts.size() && top.size() + fronts.get(i).size() <= numToSelect) {
			// crowdingDistanceAssignment(fronts.get(i)); This was used in tournament for selection of parents in JNSGA2
			top.addAll(fronts.get(i));
			i++;
		}

		// If we haven't reached the desired size, add individuals according to crowded comparison operator.
		if (i < fronts.size() && top.size() != numToSelect) {
			List<Chromosome> front = fronts.get(i);
			sortByCrowdedComparison(front);
			int numberOfMissingIndividuals = numToSelect - top.size();
			top.addAll(front.subList(0, numberOfMissingIndividuals));
		}

		return top;
	}

	private static void sortByCrowdedComparison(List<Chromosome> individuals) {
		// Reset crowding distances.
		for (Chromosome c : individuals) {
			c.crowdingDistance = 0;
		}
		int last = individuals.size() - 1;
		int numberOfObjectives = individuals.get(0).getObjectiveCount();
		for (int m = 0; m < numberOfObjectives; m++) {
			// sort using m-th objective value
			Collections.sort(individuals, new FitnessValueComparator(m));

			// so that boundary points are always selected
			individuals.get(0).crowdingDistance = Double.POSITIVE_INFINITY; // Elites always first.
			// Don't replace an infinity value.
			individuals.get(last).crowdingDistance = Math.max(individuals.get(last).crowdingDistance, Double.MAX_VALUE);

			// If minimal and maximal fitness value for this objective are equal, do not change crowding distance
			if (individuals.get(0).getFitnessValue(m) != individuals.get(last).getFitnessValue(m)) {
				double range = individuals.get(last).getFitnessValue(m) - individuals.get(0).getFitnessValue(m);
				for (int i = 1; i < last; i++) {
					individuals.get(i).crowdingDistance += (individuals.get(i + 1).getFitnessValue(m) - individuals.get(i - 1).getFitnessValue(m)) / range;
				}
			}
		}
		Collections.sort(individuals, new CrowdedComparisonOperatorComparator());
		
		//for (Chromosome c : individuals) {
		//	System.out.println(c.getId() + "  " + c.crowdingDistance + "  " + Arrays.toString(c.getFitnessValues()));
		//}
		//System.out.println();
	}

	/**
	 * This inner class implements a comparator using the index-th objective fitness value of two individuals.
	 */
	private static class FitnessValueComparator implements Comparator<Chromosome> {
		private int indexObjective;

		/**
		 * @param indexObjective objective/fitness index to sort on.
		 */
		private FitnessValueComparator(int indexObjective) {
			this.indexObjective = indexObjective;
		}

		public int compare(Chromosome individual1, Chromosome individual2) {
			if (individual1.getFitnessValue(indexObjective) < individual2.getFitnessValue(indexObjective)) {
				return -1;
			}
			if (individual1.getFitnessValue(indexObjective) > individual2.getFitnessValue(indexObjective)) {
				return 1;
			}
			// compare IDs if fitness is the same to keep the ordering stable.
			return (int) Math.signum(individual1.getId() - individual2.getId());
		}
	}

	/**
	 * This inner class implements a comparator using the crowded comparison operator. A higher crowding value is
	 * considered to be smaller in terms of ordering, such that in a descending sort the Chromosomes with lower crowding
	 * distance come first.
	 */
	private static class CrowdedComparisonOperatorComparator implements Comparator<Chromosome> {
		public int compare(Chromosome individual1, Chromosome individual2) {
			if (individual1.crowdingDistance > individual2.crowdingDistance) {
				return -1;
			}
			if (individual1.crowdingDistance < individual2.crowdingDistance) {
				return 1;
			}
			// compare IDs if fitness is the same to keep the ordering stable.
			return (int) Math.signum(individual1.getId() - individual2.getId());
		}
	}
}