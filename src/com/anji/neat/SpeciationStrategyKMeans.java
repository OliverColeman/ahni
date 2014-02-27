package com.anji.neat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.jgapcustomised.Allele;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.Genotype;
import org.jgapcustomised.SpeciationParms;
import org.jgapcustomised.SpeciationStrategy;
import org.jgapcustomised.Species;

import com.anji.util.Configurable;
import com.anji.util.Properties;
import com.ojcoleman.ahni.util.Parallel;
import com.ojcoleman.ahni.util.Parallel.Operation;

/**
 * Implements speciation using k-means clustering, as described in <a
 * href="https://sites.google.com/site/sharpneat/speciation/speciation-by-k-means-clustering"
 * >https://sites.google.com/site/sharpneat/speciation/speciation-by-k-means-clustering</a>
 * 
 * This code was adapted from SharpNEAT by Colin Green (see http://sharpneat.sourceforge.net/).
 */
public class SpeciationStrategyKMeans implements SpeciationStrategy, Configurable {
	private static Logger logger = Logger.getLogger(SpeciationStrategyKMeans.class);
	
	/**
	 * Whether to use multiple threads to execute the k-means algorithm. If exact reproduction of runs is required then this must be set to false
	 * as the ordering of assignment of individuals to species can affect the final clustering/speciation produced. Note that this must be set to false
	 * in the original and later runs for exact reproducibility. Default is true.
	 */
	static final String MULTI_THREADED = "speciation.kmeans.multithreaded";
	
	static final int MAX_KMEANS_LOOPS = 5;
	
	private boolean multiThreaded = true;
	
	@Override
	public void init(Properties props) throws Exception {
		multiThreaded = props.getBooleanProperty(MULTI_THREADED, true);
	}
	
	@Override
	public synchronized void respeciate(final List<Chromosome> genomeList, final List<Species> speciesList, final Genotype genotype) {
		SpeciationParms specParms = genotype.getConfiguration().getSpeciationParms();
		
		for (Chromosome c : genomeList) {
			c.resetSpecie();
		}
		if (speciesList.size() != specParms.getSpeciationTarget()) {
			// Create new set of species with randomly selected genomes as initial representatives.
			speciesList.clear();
			Collections.shuffle(genomeList, genotype.getConfiguration().getRandomGenerator());
			for (int i = 0; i < specParms.getSpeciationTarget(); i++) {
				speciesList.add(new Species(specParms, genomeList.get(i).getMaterial()));
			}
		}
		else {
			for (Species s : speciesList) {
				s.clear();
			}
		}
		speciate(genomeList, speciesList, genotype);
	}
	
	@Override
	public synchronized void speciate(final List<Chromosome> genomeList, final List<Species> speciesList, final Genotype genotype) {
		final Configuration config = genotype.getConfiguration();
		final SpeciationParms specParms = config.getSpeciationParms();
		
		// If this is the first speciation, or the desired number of species has changed.
		if (speciesList.size() != specParms.getSpeciationTarget()) {
			respeciate(genomeList, speciesList, genotype);
			return;
		}
		
		// Update the centroid of each species. If we're adding offspring this means that old genomes
		// have been removed from the population and therefore the centroids are out-of-date.
		calculateSpecieCentroids(speciesList);
		
		// Allocate each genome to the species it is closest to.
		Parallel.foreach(genomeList, 0, new Operation<Chromosome>() {
			@Override
			public void perform(Chromosome genome) {
				Species closestSpecies = findClosestSpecies(genome, speciesList, specParms);
				closestSpecies.addOrMoveFromCurrentSpecies(genome);
			}
		});
		
		assert testSpeciationIntegrity(genomeList, speciesList);
		
		// Recalculate each species centroid now that they contain additional genomes.
		calculateSpecieCentroids(speciesList);
		
		//double initialDistance = calculateAverageDistance(genomeList, specParms);
		
		// Perform the main k-means loop until convergence.
		speciateUntilConvergence(genomeList, speciesList, specParms);
		
		//System.err.println(initialDistance + "  ->  " + calculateAverageDistance(genomeList, specParms)); 
	}

	// / <summary>
	// / Perform the main k-means loop until no genome reallocations occur or some maximum number of loops
	// / has been performed. Theoretically a small number of reallocations may occur for a great many loops
	// / therefore we require the additional max loops threshold exit strategy - the clusters should be pretty
	// / stable and well defined after a few loops even if the the algorithm hasn't converged completely.
	// / </summary>
	private synchronized void speciateUntilConvergence(final List<Chromosome> genomeList, final List<Species> speciesList, final SpeciationParms speciationParms) {
		List<Species> emptySpeciesList = Collections.synchronizedList(new ArrayList<Species>());
		for (Species species : speciesList) {
			if (species.isEmpty()) {
				emptySpeciesList.add(species);
			}
		}
		
		assert testSpeciationIntegrity(genomeList, speciesList);

		// List of modified species (had genomes allocated to and/or from it).
		final Set<Species> speciesMod =  Collections.synchronizedSet(new HashSet<Species>());

		// Main k-means loop.
		boolean reallocationsOccurred = false;
		for (int loop = 0; loop < MAX_KMEANS_LOOPS; loop++) {
			//System.err.println("current average distance: " + calculateAverageDistance(genomeList, speciationParms)); 
			
			// Track number of reallocations made on each loop.
			reallocationsOccurred = false;
			
			assert testSpeciationIntegrity(genomeList, speciesList);

			// Loop over genomes. For each one find the species it is closest to; if it is not the species
			// it is currently in then reallocate it.
			Parallel.foreach(genomeList, 0, new Operation<Chromosome>() {
				@Override
				public void perform(Chromosome genome) {
					Species closestSpecies = findClosestSpecies(genome, speciesList, speciationParms);
					if (!genome.getSpecie().equals(closestSpecies)) {
						// Track which species have been modified.
						speciesMod.add(genome.getSpecie());
						speciesMod.add(closestSpecies);

						closestSpecies.moveFromCurrentSpecies(genome); // This removes the genome from its previous species.
						
						//assert testSpeciationIntegrity(genomeList, speciesList);
					}
				}
			});
			
			assert testSpeciationIntegrity(genomeList, speciesList);

			// Track empty species. We will allocate genomes to them after this loop.
			// This is necessary as some distance metrics can result in empty species occurring.
			for (Species species : speciesMod) {
				if (species.isEmpty() && !emptySpeciesList.contains(species)) {
					emptySpeciesList.add(species);
				}
			}
			
			// Recalculate centroid for all affected species.
			calculateSpecieCentroids(speciesMod);
			
			assert testSpeciationIntegrity(genomeList, speciesList);
			
			reallocationsOccurred |= !speciesMod.isEmpty(); 
			speciesMod.clear();
			
			
			// Check for empty species. We need to reallocate some genomes into the empty specieList to maintain the
			// required number of species.
			if (!emptySpeciesList.isEmpty()) {
				// We find the genomes in the population as a whole that are farthest from their containing species
				// centroid - we call these outlier genomes. We then move these genomes into the empty species to
				// act as the sole member and centroid of those species; These act as species seeds for the next k-means
				// loop.
				Chromosome[] genomesByDistance = getChromosomesByDistanceFromSpecies(genomeList, speciesList, speciationParms);
				
				assert testSpeciationIntegrity(genomeList, speciesList);
				
				// Reallocate each of the outlier genomes from their current species to an empty species.
				int emptySpeciesCount = emptySpeciesList.size();
				int outlierIdx = 0;
				for (int i = 0; i < emptySpeciesCount && outlierIdx < genomesByDistance.length; i++) {
					// Find the next outlier genome that can be re-allocated. Skip genomes that are the
					// only member of a species - that would just create another empty species.
					Chromosome genome;
					Species sourceSpecies;
					do {
						genome = genomesByDistance[outlierIdx++];
						sourceSpecies = genome.getSpecie();
					} while (sourceSpecies.size() == 1 && outlierIdx < genomesByDistance.length);
					
					// If the provided population is very small then it won't always be possible to fill all the empty species.
					if (outlierIdx < genomesByDistance.length) { 
						// Get ref to the empty specie and register both source and target specie with specieModArr.
						Species emptySpecies = emptySpeciesList.get(i);
						speciesMod.add(emptySpecies);
						speciesMod.add(sourceSpecies);
						
						emptySpecies.moveFromCurrentSpecies(genome);
						
						assert testSpeciationIntegrity(genomeList, speciesList);
					}
				}
				
				// Recalculate centroid for all affected species.
				calculateSpecieCentroids(speciesMod);
				
				assert testSpeciationIntegrity(genomeList, speciesList);
				
				reallocationsOccurred |= !speciesMod.isEmpty(); 
				speciesMod.clear();
				
				// Clear emptySpecieList after using it. Otherwise we are holding old references and thus creating
				// work for the garbage collector.
				emptySpeciesList.clear();
			}
			
			//for (Species species : speciesList) {
			//	System.err.print(species.size() + ", ");
			//}
			//System.err.println();
			
			// Exit the loop if no genome reallocations have occurred. The species are stable, speciation is completed.
			if (!reallocationsOccurred) {
				//System.err.println("stable");
				break;
			}
		}
	}
	
	private void calculateSpecieCentroids(Collection<Species> speciesList) {
		Parallel.foreach(speciesList, 0, new Operation<Species>() {
			@Override
			public void perform(Species species) {
				if (!species.isEmpty()) {
					species.setRepresentative(calculateSpecieCentroid(species));
				}
			}
		});
	}
	
	// / <summary>
	// / Recalculate the specie centroid based on the genomes currently in the specie.
	// / </summary>
	private ChromosomeMaterial calculateSpecieCentroid(Species species) {
		assert !species.isEmpty() : "Species can't be empty when calculating its centroid.";
		
		// Special case - 1 genome in specie (its position *is* the specie centroid).
		if (species.size() == 1) {
			return species.getChromosomes().get(0).getMaterial();
		}

		// Create a temp list containing all of the genome positions.
		List<Chromosome> genomeList = species.getChromosomes();
		int count = genomeList.size();
		List<ChromosomeMaterial> coordList = new ArrayList<ChromosomeMaterial>(count);
		for (int i = 0; i < count; i++) {
			coordList.add(genomeList.get(i).getMaterial());
		}

		// The centroid calculation is a function of the distance metric.
		return calculateCentroid(coordList);
	}

	// TODO: Determine mathematically correct centroid. This method calculates the Euclidean distance centroid and
	// is an approximation of the true centroid in L1 space (Manhattan distance).
	// Note. In practice this is possibly a near optimal centroid for all but small clusters.
	// / <summary>
	// / Calculates the centroid for the given set of points.
	// / The centroid is a central position within a set of points that minimises the sum of the squared distance
	// / between each of those points and the centroid. As such it can also be thought of as being an exemplar
	// / for a set of points.
	// /
	// / The centroid calculation is dependent on the distance metric, hence this method is defined on IDistanceMetric.
	// / For some distance metrics the centroid may not be a unique point, in those cases one of the possible centroids
	// / is returned.
	// /
	// / A centroid is used in k-means clustering to define the centre of a cluster.
	// / </summary>
	private ChromosomeMaterial calculateCentroid(List<ChromosomeMaterial> coordList) {
		assert !coordList.isEmpty() : "coordList can't be empty when calculating its centroid.";
		
		// Special case - one item in list, it *is* the centroid.
		if (1 == coordList.size()) {
			return coordList.get(0);
		}

		// Each coordinate element has an ID. Here we calculate the total for each ID across all CoordinateVectors,
		// then divide the totals by the number of CoordinateVectors to get the average for each ID. That is, we
		// calculate the component-wise mean.
		//
		// Coord elements within a CoordinateVector must be sorted by ID, therefore we use a SortedDictionary here
		// when building the centroid coordinate to eliminate the need to sort elements later.
		//
		// We use SortedDictionary and not SortedList for performance. SortedList is fastest for insertion
		// only if the inserts are in order (sorted). However, this is generally not the case here because although
		// coordinate IDs are sorted within the source CoordinateVectors, not all IDs exist within all CoordinateVectors
		// therefore a low ID may be presented to coordElemTotals after a higher ID.
		TreeMap<Allele, double[]> coordElemTotals = new TreeMap<Allele, double[]>();

		// Loop over coords.
		for (ChromosomeMaterial coord : coordList) {
			// Loop over each element within the current coord.
			for (Allele coordElem : coord.getAlleles()) {
				// If the ID has previously been encountered then add the current element value to it, otherwise
				// add a new double[1] to hold the value.
				// Note that we wrap the double value in an object so that we do not have to re-insert values
				// to increment them. In tests this approach was about 40% faster (including GC overhead).
				double[] doubleWrapper = coordElemTotals.get(coordElem);
				if (doubleWrapper != null) {
					doubleWrapper[0] += coordElem.getValue();
				} else {
					coordElemTotals.put(coordElem.cloneAllele(), new double[] { coordElem.getValue() });
				}
			}
		}

		// Put the unique coord elems from coordElemTotals into a list, dividing each element's value
		// by the total number of coords as we go.
		double coordCountReciprocol = 1.0 / coordList.size();
		ArrayList<Allele> centroidElem = new ArrayList<Allele>(coordElemTotals.size());
		for (Map.Entry<Allele, double[]> coordElem : coordElemTotals.entrySet()) { // For speed we multiply by
																					// reciprocol instead of dividing by
																					// coordCount.
			Allele allele = coordElem.getKey();
			allele.setValue(coordElem.getValue()[0] * coordCountReciprocol);
			centroidElem.add(allele);
		}

		// Use the new list of elements to construct a centroid CoordinateVector.
		ChromosomeMaterial m = new ChromosomeMaterial(centroidElem);
		//System.err.println("\n" + coordList);
		//System.err.println("----\n" + m);
		return m;
	}

	/**
	 * Gets an array of all genomes ordered by their distance from their current species.
	 */
	private Chromosome[] getChromosomesByDistanceFromSpecies(List<Chromosome> genomeList, List<Species> speciesList, SpeciationParms speciationParms) {
		// Build a list of all genomes paired with their distance from their centroid.
		GenomeDistancePair[] genomeDistanceArr = getGenomeDistancePairs(genomeList, speciesList, speciationParms);

		// Put the sorted genomes in an array and return it.
		Chromosome[] genomeArr = new Chromosome[genomeList.size()];
		for (int i = 0; i < genomeList.size(); i++) {
			genomeArr[i] = genomeDistanceArr[i]._genome;
		}

		return genomeArr;
	}
	
	/**
	 * Gets an array of GenomeDistancePairs ordered by their distance from their current species.
	 */
	private GenomeDistancePair[] getGenomeDistancePairs(List<Chromosome> genomeList, List<Species> speciesList, SpeciationParms speciationParms) {
		// Build a list of all genomes paired with their distance from their centroid.
		GenomeDistancePair[] genomeDistanceArr = new GenomeDistancePair[genomeList.size()];
		for (int i = 0; i < genomeList.size(); i++) {
			Chromosome genome = genomeList.get(i);
			double distance = genome.getMaterial().distance(genome.getSpecie().getRepresentative(), speciationParms);
			genomeDistanceArr[i] = new GenomeDistancePair(distance, genome);
		}

		// Sort list. Longest distance first.
		Arrays.sort(genomeDistanceArr);
		
		return genomeDistanceArr;
	}

	/**
	 * Find the species that a genome is closest to.
	 */
	private Species findClosestSpecies(Chromosome genome, List<Species> speciesList, SpeciationParms speciationParms) {
		Species closestSpecies = null;
		double closestDistance = Double.MAX_VALUE;

		// Find closest species.
		for (Species species : speciesList) {
			double distance = genome.getMaterial().distance(species.getRepresentative(), speciationParms);
			// All else being equal keep genome in same species. 
			if (distance < closestDistance || (distance == closestDistance && genome.getSpecie() != null && genome.getSpecie().equals(species))) {
				closestDistance = distance;
				closestSpecies = species;
			}
		}
		return closestSpecies;
	}
	
	private double calculateAverageDistance(List<Chromosome> genomeList, SpeciationParms speciationParms) {
		double totalDistance = 0;
		for (int i = 0; i < genomeList.size(); i++) {
			Chromosome genome = genomeList.get(i);
			double distance = genome.getMaterial().distance(genome.getSpecie().getRepresentative(), speciationParms);
			totalDistance += distance;
		}
		return totalDistance / genomeList.size();
	}
	
	private boolean testSpeciationIntegrity(List<Chromosome> genomeList, List<Species> speciesList) {
		Collections.sort(genomeList);
		
		Chromosome previous = null;
		for (Chromosome c : genomeList) {
			if (previous != null && previous.equals(c)) {
				System.err.println("Chromosome appears multiple times in population.");
				return false;
			}
			
			if (c.getSpecie() != null && !c.getSpecie().getChromosomes().contains(c)) {
				System.err.println("Chromosomes recorded species does not include the chromosome.");
				return false;
			}
			
			int genomeInAnySpeciesCount = 0;
			for (Species s : speciesList) {
				int genomeInThisSpeciesCount = 0;
				for (Chromosome sc : s.getChromosomes()) {
					if (c.equals(sc)) {
						genomeInThisSpeciesCount++;
						genomeInAnySpeciesCount++;
					}
				}
				if (genomeInThisSpeciesCount > 1) {
					System.err.println("Chromosome appears in same species " + genomeInThisSpeciesCount + " times");
				}
			}
			if (genomeInAnySpeciesCount != 1) {
				System.err.println("Chromosome appears in any species " + genomeInAnySpeciesCount + " times");
				return false;
			}
		}
		return true;
	}

	private class GenomeDistancePair implements Comparable<GenomeDistancePair> {
		double _distance;
		Chromosome _genome;

		GenomeDistancePair(double distance, Chromosome genome) {
			_distance = distance;
			_genome = genome;
		}

		public int compareTo(GenomeDistancePair other) {
			// Sorts in descending order.
			// Just remember, -1 means we don't change the order of x and y.
			if (_distance > other._distance) {
				return -1;
			}
			if (_distance < other._distance) {
				return 1;
			}
			return 0;
		}
	}
}