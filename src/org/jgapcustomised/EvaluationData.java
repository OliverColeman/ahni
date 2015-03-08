package org.jgapcustomised;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.ojcoleman.ahni.evaluation.novelty.Behaviour;
import com.ojcoleman.ahni.util.ArrayUtil;

public class EvaluationData implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final String PRIMARY_PERFORMANCE_KEY = "Performance";
	
	public EvaluationData(int objectiveCount, int behaviourCount) {
		m_fitnessValue = new double[objectiveCount];
		Arrays.fill(m_fitnessValue, Double.NaN);
		if (behaviourCount > 0) {
			behaviours = new Behaviour[behaviourCount];
		}
		m_performanceValue = new TreeMap<String, Double>();
	}
	
	/**
	 * Stores the fitness value(s) of this Chromosome as determined by the active fitness function(s). A value of
	 * Double.NaN indicates that a fitness value has not yet been set.
	 */
	protected double[] m_fitnessValue;

	/**
	 * Stores the performance value(s) of this Chromosome as determined by the active fitness function(s). A performance
	 * value is a measure of the performance of a Chromosome that is not used to determine the fitness of an individual
	 * in the evolutionary process. A value of Double.NaN indicates that a performance value has not yet been set.
	 */
	protected TreeMap<String, Double> m_performanceValue;

	/**
	 * Stores the overall fitness value of this Chromosome, either as determined by the active fitness function or as
	 * determined by the {@link org.jgapcustomised.NaturalSelector} based on the fitness values over all objectives. A
	 * value of Double.NaN indicates that a fitness value has not yet been set.
	 */
	protected double m_overallFitnessValue = Double.NaN;
	
	public int rank = 0;

	/**
	 * May be used by implementations of novelty search.
	 * 
	 * @see com.ojcoleman.ahni.evaluation.novelty.NoveltySearch
	 */
	public double novelty = 0;

	/**
	 * May be used by implementations of novelty search. An array is used so that multiple behaviours may be defined.
	 * 
	 * @see com.ojcoleman.ahni.evaluation.novelty.NoveltySearch
	 */
	public Behaviour[] behaviours;

	/**
	 * Used by selection algorithms to store a value representing how crowded the fitness space is relative to other
	 * individuals with the same rank (on the same Pareto front), most useful for multi-objective selection algorithms
	 * such as NSGA-II.
	 */
	public double crowdingDistance;
	

	protected boolean evaluationDataStable = false;
	

	/**
	 * Returns the overall fitness value of this Chromosome, either as determined by the active fitness function or as
	 * determined by the {@link org.jgapcustomised.NaturalSelector} based on the fitness values over all objectives.
	 * Fitness values are in the range [0, 1], however the value Double.NaN indicates that a fitness hasn't been set
	 * yet.
	 */
	public double getFitnessValue() {
		return m_overallFitnessValue;
	}

	/**
	 * Returns the fitness value of this Chromosome for the given objective, as determined by the active fitness
	 * function. Fitness values are in the range [0, 1], however the value Double.NaN indicates that a fitness hasn't
	 * been set yet.
	 */
	public double getFitnessValue(int objective) {
		return m_fitnessValue[objective];
	}

	/**
	 * Returns a reference to the array of fitness values for this Chromosome for each objective, as determined by the
	 * active fitness function. Fitness values are in the range [0, 1], however the value Double.NaN indicates that a
	 * fitness hasn't been set yet.
	 */
	public double[] getFitnessValues() {
		return m_fitnessValue;
	}
	

	/**
	 * Sets the overall fitness value of this Chromosome. This method is for use by bulk fitness functions and should
	 * not be invoked from anything else. This is the raw fitness value, before species fitness sharing. Fitness values
	 * must be in the range [0, 1].
	 * 
	 * @param a_newFitnessValue the fitness of this Chromosome.
	 */
	public void setFitnessValue(double a_newFitnessValue) {
		if (a_newFitnessValue < 0 || a_newFitnessValue > 1) {
			throw new IllegalArgumentException("Fitness values must be in the range [0, 1], or have value Double.NaN.");
		}
		m_overallFitnessValue = a_newFitnessValue;
	}

	/**
	 * Sets the fitness value of this Chromosome for the given objective. This method is for use by bulk fitness
	 * functions and should not be invoked from anything else. Fitness values must be in the range [0, 1].
	 * 
	 * @param a_newFitnessValue the fitness of this Chromosome.
	 */
	public void setFitnessValue(double a_newFitnessValue, int objective) {
		if (a_newFitnessValue < 0 || a_newFitnessValue > 1) {
			throw new IllegalArgumentException("Fitness values must be in the range [0, 1], or have value Double.NaN, but " + a_newFitnessValue + " was given for objective " + objective + ".");
		}
		m_fitnessValue[objective] = a_newFitnessValue;
	}

	/**
	 * Sets all the fitness values of this Chromosome. This method is for use by bulk fitness
	 * functions and should not be invoked from anything else. Fitness values must be in the range [0, 1].
	 * 
	 * @param values new fitness values for this Chromosome.
	 */
	public void setFitnessValues(double[] values) {
		if (values.length != m_fitnessValue.length) {
			throw new IllegalArgumentException("Specified fitness value array length does not match number of fitness values.");
		}
		if (ArrayUtil.getMinValue(values) < 0 || ArrayUtil.getMaxValue(values) > 1) {
			throw new IllegalArgumentException("Fitness values must be in the range [0, 1], or have value Double.NaN, but values " + ArrayUtil.toString(values, ", ", null) + " were given.");
		}
		System.arraycopy(values, 0, m_fitnessValue, 0, values.length);
	}
	
	/**
	 * Returns the primary performance value of this Chromosome as determined by the active fitness function. The
	 * primary performance is either the performance value with key {@link #PRIMARY_PERFORMANCE_KEY} if it exists,
	 * otherwise the value for the first key (keys are sorted alphabetically). Performance values are in the range [0,
	 * 1], however the value Double.NaN indicates that a performance value hasn't been set yet.
	 */
	public double getPerformanceValue() {
		if (m_performanceValue.containsKey(PRIMARY_PERFORMANCE_KEY))
			return m_performanceValue.get(PRIMARY_PERFORMANCE_KEY);
		else
			return m_performanceValue.firstEntry().getValue();
	}

	/**
	 * Returns the specified performance value of this Chromosome as determined by the active fitness function.
	 * Performance values are in the range [0, 1], however the value Double.NaN indicates that a performance value
	 * hasn't been set yet.
	 */
	public double getPerformanceValue(String key) {
		Double val = m_performanceValue.get(key);
		return (val == null) ? Double.NaN : val;
	}

	/**
	 * Returns all of the performance value(s) of this Chromosome as determined by the active fitness function(s).
	 * Performance values are in the range [0, 1], however the value Double.NaN indicates that a performance value
	 * hasn't been set yet. The keys of the returned Map are the labels for the performance metric. The Map is sorted
	 * alphabetically.
	 */
	public Map<String, Double> getAllPerformanceValues() {
		return Collections.unmodifiableMap(m_performanceValue);
	}

	/**
	 * Sets the primary performance value of this Chromosome. This method is for use by bulk fitness functions and
	 * should not be invoked from anything else. Performance values must be in the range [0, 1].
	 * 
	 * @param aPerformanceValue the fitness of this Chromosome.
	 */
	public void setPerformanceValue(double aPerformanceValue) {
		if (aPerformanceValue < 0 || aPerformanceValue > 1) {
			throw new IllegalArgumentException("Performance values must be in the range [0, 1], but " + aPerformanceValue + " was given.");
		}
		m_performanceValue.put(PRIMARY_PERFORMANCE_KEY, aPerformanceValue);
	}

	/**
	 * Sets the specified performance value of this Chromosome. This method is for use by bulk fitness functions and
	 * should not be invoked from anything else. Performance values must be in the range [0, 1].
	 * 
	 * @param key the identifier of the performance value to set.
	 * @param aPerformanceValue the new performance value.
	 */
	public void setPerformanceValue(String key, double aPerformanceValue) {
		if (aPerformanceValue < 0 || aPerformanceValue > 1) {
			throw new IllegalArgumentException("Performance values must be in the range [0, 1], but " + aPerformanceValue + " was given.");
		}
		m_performanceValue.put(key, aPerformanceValue);
	}
	
	/**
	 * Sets the specified performance values of this Chromosome. This method is for use by bulk fitness functions and
	 * should not be invoked from anything else. Performance values must be in the range [0, 1].
	 * 
	 * @param values The new performance values to set.
	 */
	public void setPerformanceValues(Map<String, Double> values) {
		for (Map.Entry<String, Double> e : values.entrySet()) {
			this.setPerformanceValue(e.getKey(), e.getValue());
		}
	}

	/**
	 * Resets the performance value of this Chromosome to Double.NaN.
	 */
	public void resetPerformanceValues() {
		if (!isEvaluationDataStable()) {
			m_performanceValue.clear();
		}
	}

	/**
	 * Resets the fitness value(s) of this Chromosome to Double.NaN.
	 */
	public void resetFitnessValues() {
		if (!isEvaluationDataStable()) {
			m_overallFitnessValue = Double.NaN;
			for (int i = 0; i < m_fitnessValue.length; i++) {
				m_fitnessValue[i] = Double.NaN;
			}
		}
	}

	/**
	 * Resets the performance, fitness value(s) and recorded novelty behaviours(s) of this Chromosome.
	 */
	public void resetEvaluationData() {
		if (!isEvaluationDataStable()) {
			resetPerformanceValues();
			resetFitnessValues();
			if (behaviours != null) {
				Arrays.fill(behaviours, null);
			}
		}
	}

	/**
	 * Indicate that the evaluation data (e.g. fitness, performance, behaviours) is stable: it won't change in future
	 * evaluations. This only has an effect if the fitness function pays attention to this value, and the effect that it
	 * has may vary between implementations.
	 */
	public void setEvaluationDataStable() {
		evaluationDataStable = true;
	}

	public boolean isEvaluationDataStable() {
		return evaluationDataStable;
	}
}
