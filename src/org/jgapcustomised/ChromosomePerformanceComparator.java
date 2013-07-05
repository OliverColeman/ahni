package org.jgapcustomised;

import java.util.Comparator;

import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * Enables sorting of chromosomes by their performance.
 */
public class ChromosomePerformanceComparator<T> implements Comparator<T> {
	private boolean isAscending = true;

	public ChromosomePerformanceComparator() {
		this(true);
	}

	/**
	 * Enables sorting of chromosomes in order of fitness. Ascending order if <code>ascending</code> is true, descending
	 * otherwise. Uses fitness adjusted for species fitness sharing if <code>speciated</code> is true, raw fitness
	 * otherwise.
	 * 
	 * @param ascending
	 */
	public ChromosomePerformanceComparator(boolean ascending) {
		super();
		isAscending = ascending;
	}

	/**
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(T o1, T o2) {
		Chromosome c1 = (Chromosome) o1;
		Chromosome c2 = (Chromosome) o2;
		double perf1 = c1.getPerformanceValue();
		double perf2 = c2.getPerformanceValue();
		
		// If the performance is the same then sort by ID.
		if (perf1 == perf2) {
			return c1.getId().compareTo(c2.getId());
		}
		return (int) (isAscending ? Math.signum(perf1 - perf2) : Math.signum(perf2 - perf1));
	}

}
