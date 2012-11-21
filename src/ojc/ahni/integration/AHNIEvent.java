package ojc.ahni.integration;

import ojc.ahni.hyperneat.HyperNEATEvolver;

/**
 * Class to encapsulate information about events that occur during the course of an evolutionary run in AHNI.
 * @see AHNIEventListener
 * @see ojc.ahni.hyperneat.HyperNEATEvolver
 * @see AHNIRunProperties
 */
public class AHNIEvent {
	/**
	 * Indicates the type of event that has occurred.
	 */
	public enum Type {
	  /**
	   * The run has started.
	   */
	  RUN_START,
	  /**
	   * The run has finished.
	   */
	  RUN_END,
	  /**
	   * A new generation has started.
	   */
	  GENERATION_START,
	  /**
	   * The current generation has finished.
	   */
	  GENERATION_END,
	  /**
	   * Fitness evaluation of each member of the population has started.
	   */
	  EVALUATION_START,
	  /**
	   * Fitness evaluation of each member of the population has finished.
	   */
	  EVALUATION_END,
	  /**
	   * Creation of a new population (based on fitness evaluations) has started.
	   */
	  REGENERATE_POPULATION_START,
	  /**
	   * Creation of a new population (based on fitness evaluations) has finished.
	   */
	  REGENERATE_POPULATION_END
  }
  
  private Type type;
  private Object source;
  private HyperNEATEvolver evolver;
  
  /**
   * Creates a new AHNIEvent.
   */
  public AHNIEvent(Type type, Object source, HyperNEATEvolver evolver) {
	  this.type = type;
	  this.source = source;
	  this.evolver = evolver;
  }
  
  /**
   * Returns the {@link AHNIEvent.Type} of this event.
   */
  public Type getType() {
	  return type;
  }
  /**
   * Returns the object that was the source of this event.
   */
  public Object getSource() {
	  return source;
  }
  /**
   * Returns the {@link ojc.ahni.hyperneat.HyperNEATEvolver} that is handling this run.
   */ 
  public HyperNEATEvolver getEvolver() {
	 return evolver; 
  }
}
