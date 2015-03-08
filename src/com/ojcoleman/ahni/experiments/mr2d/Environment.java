package com.ojcoleman.ahni.experiments.mr2d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.NNAdaptor;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.Range;

/**
 * Defines a base class for environments used in Mobile Robot 2D simulations.
 */
abstract class Environment {
	private static final NumberFormat nf = new DecimalFormat("0.00");
	private static final Range agentOutputRange = new Range(-1, 1);

	/**
	 * Return value for {@link #processContactEvent(ObjectInstance, ObjectInstance)}. Indicates the contact event should be ignored (no objects removed).
	 */
	public static final int CONTACT_IGNORE = 0;
	/**
	 * Return value for {@link #processContactEvent(ObjectInstance, ObjectInstance)}. Indicates the first object should be removed from the simulation.
	 */
	public static final int CONTACT_REMOVE_FIRST = 1;
	/**
	 * Return value for {@link #processContactEvent(ObjectInstance, ObjectInstance)}. Indicates the second object should be removed from the simulation.
	 */
	public static final int CONTACT_REMOVE_SECOND = 2;
	
	protected int id;
	protected MobileRobot2D mobileRobot2D;
	protected EnvironmentDescription description;
	protected List<ObjectInstance> objects;
	protected int collectedCount;
	protected Range minMaxReward;
	private double rewardFromLastStep;
	private double[] input;
	private double[] output;
	private Activator previousSubstrate;
	private long stepCount;

	/**
	 * Initialise this Environment. This method should only be called once. {@link #reset()} should be called after this.
	 * Subclasses should override this method if they define their own structures that need initialising. This method should be called from overriding methods.
	 */
	public void init(Properties props, MobileRobot2D mobileRobot2D, EnvironmentDescription description) {
		this.mobileRobot2D = mobileRobot2D;
		this.description = description;
		objects = description.createObjectInstances();
	}
	
	/**
	 * Get the description for this environment.
	 */
	public EnvironmentDescription getDescription() {
		return description;
	}
	
	/**
	 * Completely reset the environment back to it's initial state, generally according to a {@link EnvironmentDescription} specified at the time of instantiation.
	 * Subclasses should override this method if they define their own state variables that need to be reset before a simulation starts. This method should be called from overriding methods.
	 */
	public void reset() {
		for (ObjectInstance object : objects) {
			object.reset();
		}
		rewardFromLastStep = 0;
		previousSubstrate = null;
		input = new double[mobileRobot2D.getAgentSensorCount()*2+1];
		output = new double[2];
		stepCount = 0;
		collectedCount = 0;
	}
	
	/**
	 * Get the list of object instances being used in the simulation.
	 */
	public List<ObjectInstance> getObjects() {
		return objects;
	}
	
	/**
	 * Perform one simulation step.
	 * @param substrate An Activator representing the agent.
	 */ 
	public void step(Activator substrate) {
		assert previousSubstrate == null || substrate.equals(previousSubstrate) : "Only one Activator should ever control the agent in an Environment between calls to reset().";
		previousSubstrate = substrate;

		// Set up inputs to agent.
		double[][] sensorData = getSensorData();
		for (int si = 0; si < mobileRobot2D.getAgentSensorCount(); si++) {
			input[si*2] = sensorData[0][si];
			input[si*2+1] = sensorData[1][si];
		}
		input[mobileRobot2D.getAgentSensorCount()*2] = rewardFromLastStep;
		
		// Provide input to and get output from agent.
		if (substrate instanceof NNAdaptor) {
			((NNAdaptor) substrate).next(input, output);
		}
		else {
			output = substrate.next(input);
		}
		
		rewardFromLastStep = 0;
		
		stepPhysics(agentOutputRange.clamp(output[0]) * mobileRobot2D.getAgentSpeedLinearFactor(), agentOutputRange.clamp(output[1]) * mobileRobot2D.getAgentSpeedRotationFactor());
		
		stepCount++;
	}
	
	
	/**
	 * Update the physics of the simulation. Implementations of this method MUST:<ul>
	 * <li>call {@link #processContactEvent(ObjectInstance, ObjectInstance)} when any two bodies make contact during the simulation step.</li>
	 * <li>update the {@link ObjectInstance#currentPosition} and {@link ObjectInstance#currentRotation} of
	 * all objects in {@link #objects} after the simulation stepping has completed.</li>
	 * </ul> 
	 * @param acceleration The output from the agent that specifies linear acceleration to apply to the agent body (backwards and forwards).
	 * @param rotation The output from the agent that specifies angular acceleration to apply to the agent body (turning).
	 */
	public abstract void stepPhysics(double acceleration, double rotation);
	
	/**
	 * Returns the total elapsed time in the simulation.
	 */
	public double getElapsedTime() {
		return stepCount * mobileRobot2D.getEnvStepPeriod();
	}
	
	/**
	 * @return The reward received by the agent in the most recent simulation step. This is updated by {@link #processContactEvent(ObjectInstance, ObjectInstance)}. 
	 */
	public double getRewardFromLastStep() {
		return rewardFromLastStep;
	}
	
	/**
	 * Get the input that the agent received in the last step.
	 * @return An array containing the input that the agent received at the beginning of the last step. The values in the array should not be modified.
	 */
	public double[] getInputForLastStep() {
		return input;
	}

	/**
	 * Get the output that the agent produced in the last step.
	 * @return An array containing the output produced by the agent during the last step. The values in the array should not be modified.
	 */
	public double[] getOutputForLastStep() {
		return output;
	}

	/**
	 * Get the current sensor data from the agent body.
	 * @param sensorData An array to put the sensor data into, format is [range, colour][sensor1..sensorN].
	 */
	public abstract double[][] getSensorData();

	/**
	 * This method should be called by subclasses when a collision occurs between any two objects.
	 * @param  o1 One of the objects involved in the collision.
	 * @param  o2 The other object involved in the collision.
	 * @return One of {@link #CONTACT_IGNORE}, {@link #CONTACT_REMOVE_FIRST}, {@link #CONTACT_REMOVE_SECOND}. 
	 */
	public int processContactEvent(ObjectInstance o1, ObjectInstance o2) {
		ObjectType obj1Type = o1.description.type;
		ObjectType obj2Type = o2.description.type;
		// If one of the objects is an agent and the other is collectible.
		if ((obj1Type.isAgent && obj2Type.collectible)
				|| (obj2Type.isAgent && obj1Type.collectible)) {
			if (obj1Type.collectible) {
				collectedCount++;
				o1.setCollected(collectedCount);
				rewardFromLastStep += obj1Type.collectReward;
				return CONTACT_REMOVE_FIRST;
			}
			else {
				collectedCount++;
				o2.setCollected(collectedCount);
				rewardFromLastStep += obj2Type.collectReward;
				return CONTACT_REMOVE_SECOND;
			}
		}
		return CONTACT_IGNORE;
	}
	
	/**
	 * Generates a novelty behaviour description for the current state of this environment and puts it in the given vector starting at the given index.
	 * @param finalState True indicates that we're recording the final state of the environment and may need to record some additional information.
	 * @return the next index position after the description for this environment.
	 */
	public int getNoveltyDescription(ArrayRealVector n, int index, boolean finalState) {
		for (ObjectInstance object : objects) {
			index = object.getNoveltyDescription(n, index, finalState);
		}
		return index;
	}
	
	/**
	 * Render the current state of the environment.
	 * @param g The graphics object to render to.
	 * @param b The bounds of the rendering.
	 */
	public void render(Graphics2D g) {
		int imgSize = Math.min(g.getClipBounds().width, g.getClipBounds().height);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imgSize, imgSize);

		g = (Graphics2D) g.create();
		// Allow for radius of objects.
		int scale = Math.min(imgSize, imgSize);
		g.scale(scale, scale);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		Stroke normalStroke = new BasicStroke(2f / imgSize);
		Stroke dashStroke = new BasicStroke(1f / imgSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1, new float[] {3f / imgSize, 3f / imgSize}, 0);
		g.setStroke(normalStroke);

		Ellipse2D.Double objShape = new Ellipse2D.Double();
		double s = 0;
		AffineTransform t = new AffineTransform();
		int sensorCount = mobileRobot2D.getAgentSensorCount();
		for (ObjectInstance object : objects) {
			ObjectType type = object.description.type;
			// Draw object as agent sees it.
			s = type.size;
			objShape.x = object.currentPosition.x - s*0.5;
			objShape.y = object.currentPosition.y - s*0.5;
			objShape.width = s;
			objShape.height = s;
			if (type.isAgent) {
				g.setColor(Color.DARK_GRAY);
			}
			else {
				g.setColor(Color.getHSBColor((float)type.colour, object.isCollected() ? 0.25f : 1, 1));
			}
			g.fill(objShape);
			
			// Indicate object characteristics inside.
			t.setToTranslation(object.currentPosition.x, object.currentPosition.y);
			t.rotate(object.currentRotation);
			Shape objSymbol = t.createTransformedShape(type.symbolShape);
			g.setColor(object.isCollected() ? type.symbolColour.brighter().brighter() : type.symbolColour);
			g.fill(objSymbol);
			g.setColor(Color.DARK_GRAY);
			g.draw(objSymbol);
			
			if (type.isAgent) {
				double[][] sensorData = getSensorData();
				g.setStroke(dashStroke);
				t.rotate(-mobileRobot2D.getAgentSensorViewAngle()/2);
				for (int si = 0; si < sensorCount; si++) {
					Line2D sensor = new Line2D.Double(0, type.size/2, 0, sensorData[0][si]);
					g.setColor(sensorData[1][si] == -1 ? Color.LIGHT_GRAY : Color.getHSBColor((float) sensorData[1][si], 1, 1));
					Shape sensorT = t.createTransformedShape(sensor);
					g.draw(sensorT);
					t.rotate(mobileRobot2D.getAgentSensorViewAngle() / (sensorCount-1));
				}
				g.setStroke(normalStroke);
			}
		}
		
		g.scale(1.0/imgSize, 1.0/imgSize);
		g.setFont(new Font("Arial", Font.PLAIN, 10));
		g.setColor(Color.DARK_GRAY);
		g.drawString(nf.format(getElapsedTime()), 3, 11);
	}
}
