package com.ojcoleman.ahni.experiments.mr2d;

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.text.DecimalFormat;
import java.text.NumberFormat;

class ObjectType {
	private static final NumberFormat nf = new DecimalFormat("0.00");
	public static final double DEFAULT_SIZE = 0.1;
	public static final double DEFAULT_MASS = 1;
	
	public final boolean isAgent;
	public final double colour;
	public final double size;
	public final double mass;
	public final boolean collectible;
	public final double collectReward;
	public final Color symbolColour;
	public final RectangularShape symbolShape;

	/**
	 * @param colour If true then this type represents an agent.
	 * @param colour The "colour" of the object, which essentially provides a way for an agent to distinguish object types.
	 * @param size The radius of the object.
	 * @param mass The mass of the object. A mass of 0 causes the object to be static (immovable).
	 * @param collectible If true then the object will be collected (disappearing from the environment) when an agent collides with it.
	 * @param collectReward The reward the agent receives upon collecting the object.
	 */
	public ObjectType(boolean isAgent, double colour, double size, double mass, boolean collectable, double collectReward) {
		this.isAgent = isAgent;
		this.colour = colour;
		this.size = size;
		this.collectible = collectable;
		this.collectReward = collectReward;
		this.mass = mass;
		if (!collectable) {
			this.symbolColour = Color.LIGHT_GRAY;
		} else {
			this.symbolColour = new Color(collectReward < 0 ? (float) -collectReward : 0, collectReward > 0 ? (float) collectReward : 0, 0);
		}
		this.symbolShape = new Ellipse2D.Double(-size/4, -size/4, size/2, size/2);
	}

	/**
	 * @param isAgent If true then this type represents an agent.
	 * @param colour The "colour" of the object, which essentially provides a way for an agent to distinguish object types.
	 * @param size The radius of the object.
	 * @param mass The mass of the object. A mass of 0 causes the object to be static (immovable).
	 * @param collectible If true then the object will be collected (disappearing from the environment) when an agent collides with it.
	 * @param collectReward The reward the agent receives upon collecting the object.
	 * @param symbolColour The colour of the symbol indicating the characteristics of this type.
	 * @param symbolColour The shape of the symbol indicating the characteristics of this type.
	 */
	public ObjectType(boolean isAgent, double colour, double size, double mass, boolean collectable, double collectReward, Color visualColour, RectangularShape visualShape) {
		this.isAgent = isAgent;
		this.colour = colour;
		this.size = size;
		this.collectible = collectable;
		this.collectReward = collectReward;
		this.mass = mass;
		this.symbolColour = visualColour;
		this.symbolShape = visualShape;
	}

	/**
	 * Convenience method to create object types that are collectible food or poison types.
	 */
	public static ObjectType newFoodPoisonType(double colour, double collectReward, boolean pushable) {
		return new ObjectType(false, colour, DEFAULT_SIZE, pushable ? DEFAULT_MASS : 0, true, collectReward);
	}

	/**
	 * Convenience method to create object types that can be pushed around.
	 */
	public static ObjectType newPushableType(double colour) {
		return new ObjectType(false, colour, DEFAULT_SIZE, DEFAULT_MASS, false, 0);
	}
	
	public static ObjectType newAgentType() {
		return new ObjectType(true, 0, DEFAULT_SIZE, DEFAULT_MASS, false, 0, Color.LIGHT_GRAY, new Rectangle2D.Double(-DEFAULT_SIZE/8, DEFAULT_SIZE/8, DEFAULT_SIZE/4, DEFAULT_SIZE/4));
	}
	
	/**
	 * Get the number of dimensions of a {@link com.ojcoleman.ahni.evaluation.novelty.Behaviour} descriptor for {@link com.ojcoleman.ahni.evaluation.novelty.NoveltySearch}
	 * for this object type. Used by {@link EnvironmentDescription#getNoveltyDescriptionLength()}.
	 */
	public int getNoveltyDescriptionLength(MobileRobot2D mobileRobot2D2) {
		int length = 0;
		// If movable then novelty description will record envDesc.mobileRobot2D.getNoveltySearchSamplesPerEnv() locations. 
		if (mass > 0 || isAgent) length += 2 * mobileRobot2D2.getNoveltySearchSamplesPerEnv();
		 // If collectible then include order in which object is collected.
		if (collectible) length += 1;
		return length;
	}
	
	@Override
	public String toString() {
		return "ObjectType: " + (isAgent ? "agent, " : "") + (isAgent ? "collectible (" + nf.format(collectReward) + "), " : "") + "C:" + nf.format(colour) + ", S:" + nf.format(size) + ", M:" + nf.format(mass);
	}
}
