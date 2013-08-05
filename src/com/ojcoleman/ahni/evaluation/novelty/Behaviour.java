package com.ojcoleman.ahni.evaluation.novelty;

/**
 * Interface for a behaviour in {@link NoveltySearch}.
 */
public abstract class Behaviour {
	/**
	 * Determine the distance, in behaviour space, between this Behaviour and the given Behaviour.
	 * All distances should be in the range [0, 1].
	 */
	public abstract double distanceFrom(Behaviour b);
	
	/**
	 * Provide a default/suggested threshold. Should be in the range [0, 1].
	 */
	public abstract double defaultThreshold();
}
