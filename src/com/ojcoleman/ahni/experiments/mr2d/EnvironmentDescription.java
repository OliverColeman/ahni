package com.ojcoleman.ahni.experiments.mr2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import com.ojcoleman.ahni.hyperneat.Configurable;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.Point2DImmut;
import com.ojcoleman.ahni.util.Range;
import com.ojcoleman.ahni.hyperneat.Properties;

/**
 * Describes an environment for a Mobile Robot 2D simulation. An experiment run typically creates a singleton instance
 * of this class (initialised via {@link #init(Properties)}) which contains some pre-calculated values and then
 * instances used for simulations are generated via the {@link #generateInstance(int, Random, MobileRobot2D)} method.
 */
public class EnvironmentDescription implements Configurable {
	// singleton variables.
	int objectTypeCount;
	String objectTypeFoodPoison;
	int objectTypePushableCount;
	Properties props;
		
	// instance variables.
	final int id;
	final List<ObjectType> objectTypes;
	final List<ObjectDescription> objectDescriptions;
	final Range minMaxReward;
	final Range minMaxRewardPossible;
	
	/** 
	 * This constructor is only for creating a singleton instance configured via {@link #init(Properties)}.
	 * EnvironmentDescription 
	 */ 
	public EnvironmentDescription() {
		id = -1;
		objectTypes = null;
		objectDescriptions = null;
		minMaxReward = null;
		minMaxRewardPossible = null;
	}
	
	@Override
	public void init(Properties props) throws Exception {
		this.props = props;
		
		objectTypeFoodPoison = props.getProperty(MobileRobot2D.ENVIRONMENT_OBJECT_TYPE_FOOD_POISON, "simple").toLowerCase();
		objectTypePushableCount = props.getIntProperty(MobileRobot2D.ENVIRONMENT_OBJECT_TYPE_PUSHABLE, 0);
		
		// Work out the total number of object types so we can make up unique colours for each.
		objectTypeCount = 1; // First type is agent.
		if (objectTypeFoodPoison.equals("simple")) {
			objectTypeCount += 2;
		}
		else if (objectTypeFoodPoison.equals("complex")) {
			objectTypeCount += 5;
		} else if (!objectTypeFoodPoison.equals("none")) {
			throw new IllegalArgumentException("Invalid value provided for property " + MobileRobot2D.ENVIRONMENT_OBJECT_TYPE_FOOD_POISON);
		}
		
		if (objectTypePushableCount < 0) objectTypePushableCount = 0;
		objectTypeCount += objectTypePushableCount;
	}

	private EnvironmentDescription(int id, List<ObjectType> objectTypes, List<ObjectDescription> objectDescriptions) {
		this.id = id;
		this.objectTypes = Collections.unmodifiableList(objectTypes);
		this.objectDescriptions = Collections.unmodifiableList(objectDescriptions);
		
		// Determine maximum possible reward receivable.
		double minReward = 0;
		double maxReward = 0;
		double sumOfNegativeRewardTypes = 0; // Account for having to try each object type.
		HashSet<ObjectType> includedNegativeRewardTypes = new HashSet<ObjectType>();
		for (ObjectDescription object : objectDescriptions) {
			if (object.type.collectible) {
				double reward = object.type.collectReward;
				if (reward < 0) {
					minReward += reward;
							
					if (!includedNegativeRewardTypes.contains(object.type)) {
						includedNegativeRewardTypes.add(object.type);
						sumOfNegativeRewardTypes += reward;
					}
				}
				else if (reward > 0) {
					maxReward += reward;
				}
			}
		}
		minMaxReward = new Range(minReward, maxReward + sumOfNegativeRewardTypes);
		minMaxRewardPossible = new Range(minReward, maxReward);
	}

	/**
	 * Randomly generate a new set of object types and other environment properties.
	 */
	public EnvironmentDescription generateInstance(int id, Random random, MobileRobot2D mr2D) {
		List<ObjectType> objectTypes = new ArrayList<ObjectType>();
		
		// Create object types (-1 to ignore agent type).
		double[] colours = generateRandomColourSet(objectTypeCount-1, random);
		int colourIndex = 0;
		
		// Food object types.
		if (objectTypeFoodPoison.equals("simple")) {
			objectTypes.add(ObjectType.newFoodPoisonType(colours[colourIndex++], -1, objectTypePushableCount > 0));
			objectTypes.add(ObjectType.newFoodPoisonType(colours[colourIndex++], 1, objectTypePushableCount > 0));
		}
		if (objectTypeFoodPoison.equals("complex")) {
			for (int i = 0; i < 5; i++) {
				objectTypes.add(ObjectType.newFoodPoisonType(colours[colourIndex++], (i / 2.0) - 1, objectTypePushableCount > 0));
			}
		}
		
		// Pushable object types.
		for (int i = 0; i < objectTypePushableCount; i++) {
			objectTypes.add(ObjectType.newPushableType(colours[colourIndex++]));
		}
		
		// Agent type is last.
		objectTypes.add(ObjectType.newAgentType());
		
		// Create object instance descriptions.
		int objectCount = props.getIntProperty(MobileRobot2D.ENVIRONMENT_OBJECT_COUNT);
		
		List<ObjectDescription> objectDescriptions = new ArrayList<ObjectDescription>();
		int[] typeCount = new int[objectTypeCount-1]; // Make sure we include at least two of each type.
		while (ArrayUtil.getMinValue(typeCount) < 2) {
			objectDescriptions = new ArrayList<ObjectDescription>();
			// Agent.
			ObjectType type = objectTypes.get(objectTypes.size()-1);
			//objectDescriptions.add(new ObjectDescription(type, new Point2DImmut(random.nextDouble() * (1-type.size) + type.size*0.5, random.nextDouble() * (1-type.size) + type.size*0.5), random.nextDouble() * Math.PI * 2));
			objectDescriptions.add(new ObjectDescription(type, new Point2DImmut(0.5, 0.5), -Math.PI * 0.75));
			Point2DImmut initialLocation = null;
			
			Arrays.fill(typeCount, 0);
			for (int i = 0; i < objectCount; i++) {
				int typeIndex = random.nextInt(objectTypes.size()-1);
				typeCount[typeIndex]++;
				type = objectTypes.get(typeIndex);
				
				while (initialLocation == null) {
					//initialLocation = new Point2DImmut(random.nextDouble() * (1-type.size) + type.size*0.5, random.nextDouble() * (1-type.size) + type.size*0.5);
					initialLocation = new Point2DImmut((random.nextDouble() * (1-type.size) + type.size*0.5)*0.5+0.25, (random.nextDouble() * (1-type.size) + type.size*0.5)*0.5+0.25);
					// Check for collisions with other objects already placed.
					for (int j = 0; j <= i; j++) {
						ObjectDescription otherObj = objectDescriptions.get(j);
						// Assume objects are circles with radius type.size / 2 centred on initialPosition.
						if (initialLocation.distance(otherObj.initialPosition) - (otherObj.type.size + type.size) * 0.55 < 0.0) {
							initialLocation = null;
							break;
						}
					}
				}
				objectDescriptions.add(new ObjectDescription(type, initialLocation, random.nextDouble() * Math.PI * 2));
				initialLocation = null;
			}
		}
		return new EnvironmentDescription(id, objectTypes, objectDescriptions);
	}
	
	/** 
	 * Get an immutable list of the object types in this environment description.
	 */ 
	public List<ObjectType> getObjectTypes() {
		return objectTypes;
	}

	/** 
	 * Get an immutable list of the object instance descriptions in this environment description.
	 */ 
	public List<ObjectDescription> getObjectDescriptions() {
		return objectDescriptions;
	}

	private double[] generateRandomColourSet(int size, Random random) {
		double[] colours = new double[size];
		double delta = 1.0 / size;
		double noise = delta * 0.67;
		int[] ri = ArrayUtil.newRandomIndexing(size, random);
		for (int i = 0; i < size; i++) {
			//colours[ri[i]] = i * delta + random.nextDouble() * noise; // randomised association with noise.
			colours[ri[i]] = i * delta; //randomised association.
			//colours[i] = i * delta; // fixed association.
		}
		return colours;
	}

	/**
	 * Creates and returns a new set of ObjectInstances matching this environment description.
	 */
	public List<ObjectInstance> createObjectInstances() {
		ArrayList<ObjectInstance> inst = new ArrayList<ObjectInstance>(objectDescriptions.size());
		for (ObjectDescription desc : objectDescriptions) {
			inst.add(new ObjectInstance(this, desc));
		}
		return inst;
	}
	
	/**
	 * Get the total number of dimensions of a {@link com.ojcoleman.ahni.evaluation.novelty.Behaviour} descriptor for {@link com.ojcoleman.ahni.evaluation.novelty.NoveltySearch}
	 * @param mobileRobot2D2 
	 */
	public int getNoveltyDescriptionLength(MobileRobot2D mobileRobot2D2) {
		int length = 0;
		for (ObjectDescription desc : objectDescriptions) {
			int l = desc.type.getNoveltyDescriptionLength(mobileRobot2D2);
			length += l;
		}
		return length;
	}
	
	/**
	 * @return The minimum and maximum reward receivable from this environment, accounting for having to 
	 *   try each collectible object type to determine if it provides a positive reward.
	 */
	public Range getReasonableRewardRange() {
		return minMaxReward;
	}
	/**
	 * @return The minimum and maximum possible reward receivable from this environment.
	 */
	public Range getRewardRange() {
		return minMaxRewardPossible;
	}
}
