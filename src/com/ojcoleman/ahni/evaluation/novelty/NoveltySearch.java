package com.ojcoleman.ahni.evaluation.novelty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.anji.neat.NeatConfiguration;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.hyperneat.Properties;

public class NoveltySearch implements Configurable {
	private static Logger logger = Logger.getLogger(NoveltySearch.class);
	
	/**
	 * The number of nearest neighbours to consider when determining the sparseness in a region and so whether to add a
	 * new individual to the archive. Default is 30.
	 */
	public static final String K = "fitness.function.novelty.k";
	/**
	 * The novelty threshold to determine whether an individual is novel enough to add to the archive. The novelty
	 * of an individual is always in the range [0, 1], thus the threshold should also be within this range. Default
	 * is 0.05.
	 */
	public static final String ARCHIVE_THRESHOLD = "fitness.function.novelty.threshold";
	/**
	 * The minimum value to decrease the novelty threshold to (the threshold is slowly reduced if no individuals are
	 * added in a generation). Default is 0.05 * fitness.function.novelty.threshold.
	 */
	public static final String ARCHIVE_THRESHOLD_MIN = "fitness.function.novelty.threshold.min";

	int k = 30;
	double archiveThreshold = 0.05;
	double archiveThresholdMin;

	public List<Behaviour> archive;
	List<Behaviour> currentPop;
	List<Behaviour> toArchive;
	int noNewArchiveCount; // count number of generations in a row for which no individual added to archive.
	int noNewArchiveGenerationsThreshold = 10;
	int tooManyArchiveAdditionsThreshold;

	public NoveltySearch() {
	}

	@Override
	public void init(Properties props) {
		k = props.getIntProperty(K, k);
		archiveThreshold = props.getDoubleProperty(ARCHIVE_THRESHOLD, archiveThreshold);
		archiveThresholdMin = props.getDoubleProperty(ARCHIVE_THRESHOLD_MIN, archiveThreshold * 0.05);
		// Adjust threshold so that around 2% of population is added at a time.
		tooManyArchiveAdditionsThreshold = Math.max(1, (int) Math.round(props.getIntProperty(NeatConfiguration.POPUL_SIZE_KEY) * 0.02));
		logger.info("Target maximum additions count to novelty archive per generation: " + tooManyArchiveAdditionsThreshold);
		reset();
	}

	/**
	 * Reset this archive. This empties the archive and resets all state variables.
	 */
	public void reset() {
		archive = new ArrayList<Behaviour>(k);
		toArchive = Collections.synchronizedList(new ArrayList<Behaviour>());
		currentPop = new ArrayList<Behaviour>();
		noNewArchiveCount = 0;
	}

	/**
	 * Determine the novelty of the given behaviour. This method can be called by multiple threads asynchronously.
	 * 
	 * @param b The behaviour to test.
	 * @return The novelty, a value in the range [0, 1].
	 */
	public double testNovelty(Behaviour b) {
		//System.err.println(b);
		double[] dist = new double[archive.size() + currentPop.size()];
		int i = 0;
		for (Behaviour b2 : archive) {
			dist[i] = b.distanceFrom(b2);
			assert (dist[i] >= 0 && dist[i] <= 1) : "Values returned by implementations of Behaviour.distanceFrom() must be in the range [0, 1] but a value of " + dist[i] + " was found.";
			i++;
		}
		assert currentPop.size() > 0 : "The current population in NoveltySearch has zero size.";
		for (Behaviour b2 : currentPop) {
			dist[i] = b.distanceFrom(b2);
			assert (dist[i] >= 0 && dist[i] <= 1) : "Values returned by implementations of Behaviour.distanceFrom() must be in the range [0, 1] but a value of " + dist[i] + " was found.";
			i++;
		}
		Arrays.sort(dist);
		double avgDist = 0;
		for (i = 0; i < k; i++) {
			avgDist += dist[i];
		}
		avgDist /= k;
		assert (avgDist >= 0 && avgDist <= 1) : "Values returned by testNovelty must be in the range [0, 1] but a value of " + avgDist + " was found.";
		
		// If the archive and toArchive queue don't contain a similar behaviour, add it to the archive.
		if (!containsSimilar(toArchive, b, archiveThreshold) && !containsSimilar(archive, b, archiveThreshold)) {
			toArchive.add(b);
		}
		
		return avgDist;
	}
	
	private boolean containsSimilar(List<Behaviour> behaviours, Behaviour b, double threshold) {
		if (behaviours.isEmpty())
			return false;
		synchronized (behaviours) {
			for (Behaviour b2 : behaviours) {
				if (b.distanceFrom(b2) < threshold)
					return true;
			}
		}
		return false;
	}

	/**
	 * This may be called before evaluating individuals from a population to allow determining novelty based on the
	 * archive and the current population.
	 */
	public synchronized void setCurrentPopulation(List<Behaviour> behaviours) {
		currentPop = behaviours;
	}

	/**
	 * This may be called for each member of a population before evaluating individuals from the population to allow
	 * determining novelty based on the archive and the current population.
	 */
	public synchronized void addToCurrentPopulation(Behaviour b) {
		currentPop.add(b);
	}

	/**
	 * This method must be called when the population has been evaluated. Individuals with novelty greater than the
	 * threshold (see {@link NoveltySearch#ARCHIVE_THRESHOLD}) will be added to the archive. The archive threshold is
	 * adjusted if no new individuals have been added for 10 generations or if more than (popSize/100) individuals have
	 * been added. The record of behaviours for the current population is cleared.
	 */
	public void finishedEvaluation() {
		if (toArchive.isEmpty()) {
			noNewArchiveCount++;
			if (noNewArchiveCount == noNewArchiveGenerationsThreshold) {
				archiveThreshold *= 0.95;
				if (archiveThreshold < archiveThresholdMin)
					archiveThreshold = archiveThresholdMin;
				noNewArchiveCount = 0;
				//System.err.println("atd: " + archiveThreshold + "    (" + archiveThresholdMin + ")");
			}
		} else {
			noNewArchiveCount = 0;
			if (toArchive.size() > tooManyArchiveAdditionsThreshold) {
				archiveThreshold *= 1.2;
				//System.err.println("ati: " + archiveThreshold);
			}
			archive.addAll(toArchive);
			logger.info("Novelty archive size is now " + archive.size() + "  (archive threshold is " + archiveThreshold + ").");
		}
		toArchive.clear();
		currentPop = new ArrayList<Behaviour>();
	}
	
	public int getArchiveSize() {
		return archive.size();
	}
}
