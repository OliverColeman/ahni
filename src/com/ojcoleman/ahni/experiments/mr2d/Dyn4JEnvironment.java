package com.ojcoleman.ahni.experiments.mr2d;

import java.awt.Graphics2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.dyn4j.geometry.Transform;
import org.dyn4j.Listener;
import org.dyn4j.collision.AxisAlignedBounds;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.RaycastResult;
import org.dyn4j.dynamics.Settings;
import org.dyn4j.dynamics.World;
import org.dyn4j.dynamics.contact.ContactListener;
import org.dyn4j.dynamics.contact.ContactPoint;
import org.dyn4j.dynamics.contact.PersistedContactPoint;
import org.dyn4j.dynamics.contact.SolvedContactPoint;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Rectangle;
import org.dyn4j.geometry.Mass;
import org.dyn4j.geometry.Ray;
import org.dyn4j.geometry.Segment;
import org.dyn4j.geometry.Vector2;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.NNAdaptor;
import com.ojcoleman.ahni.util.ArrayUtil;
import com.ojcoleman.ahni.util.Range;

class Dyn4JEnvironment extends Environment implements ContactListener {
	private static final NumberFormat nf = new DecimalFormat("0.00");

	private World world;
	Settings settings;
	private List<ObjectBody> objectBodies;
	
	private Vector2 linearImpulse = new Vector2();
	private List<RaycastResult> sensorResults;
	private double[][] sensorData;
	private boolean sensorDataStale;
	
	@Override
	public void init(Properties props, MobileRobot2D mobileRobot2D, EnvironmentDescription description) {
		super.init(props, mobileRobot2D, description);
		
		// Replace ObjectInstances with ObjectBodys
		objectBodies = new ArrayList<ObjectBody>();
		for (int i = 0; i < objects.size(); i++) {
			ObjectInstance object = objects.get(i);
			ObjectBody objBody = new ObjectBody(object);
			objects.set(i, objBody);
			objectBodies.add(objBody);
		}

		sensorResults = new ArrayList<RaycastResult>(mobileRobot2D.getAgentSensorCount());
		sensorData = new double[2][mobileRobot2D.getAgentSensorCount()];
		
		settings = new Settings();
		settings.setStepFrequency(mobileRobot2D.getEnvStepPeriod());
	}
	
	@Override
	public void reset() {
		super.reset();
		
		world = new World();
		world.addBody(makeWallBody(-0.1, 0, 0, 1));
		world.addBody(makeWallBody(0, 1, 1, 1.1));
		world.addBody(makeWallBody(1, 1, 1.1, 0));
		world.addBody(makeWallBody(1, 0, 0, -0.1));
		//world.setGravity(World.EARTH_GRAVITY);
		world.setGravity(World.ZERO_GRAVITY);
		world.addListener(this);
		world.setSettings(settings);
		
		for (ObjectBody object : objectBodies) {
			Body body = new Body(1);
			//double density = object.description.type.mass == 0 ? Double.POSITIVE_INFINITY : object.description.type.mass / object.description.type.size;
			//double density = 1;
			BodyFixture fixture = body.addFixture(new Circle(object.description.type.size * 0.5));
			if (object.description.type.mass > 0) {
				fixture.setDensity(object.description.type.mass / object.description.type.size);
				fixture.setFriction(0.5);
			}
			body.getTransform().setTranslation(object.description.initialPosition.x, object.description.initialPosition.y);
			body.getTransform().setRotation(object.description.initialRotation);
			if (object.description.type.mass <= 0) {
				body.setMass(Mass.Type.INFINITE);
			}
			else {
				body.setMass();
			}
			
			world.addBody(body);
			object.body = body;
			body.setUserData(object);
		}
		
		sensorDataStale = true;
	}
	
	private Body makeWallBody(double x1, double y1, double x2, double y2) {
		Body body = new Body(1);
		Rectangle w = new Rectangle(Math.abs(x2-x1), Math.abs(y2-y1));
		BodyFixture fixture = body.addFixture(w);
		body.setMass(Mass.Type.INFINITE);
		body.translate((x2+x1)/2, (y2+y1)/2);
		return body;
	}

	@Override
	public void stepPhysics(double distance, double rotation) {
		// Apply actions specified by agent output.
		assert objectBodies.get(0).description.type.isAgent;
		Body agent = objectBodies.get(0).body;
		objectBodies.get(0).body.setAsleep(false);
		double agentRotation = objectBodies.get(0).body.getTransform().getRotation() + Math.PI/2;;
		objectBodies.get(0).body.setLinearVelocity(distance * Math.cos(agentRotation), distance * Math.sin(agentRotation));
		objectBodies.get(0).body.setAngularVelocity(rotation);
		
		// Step the world.
		world.step(1);
		
		// Update object properties in Environment from Dyn4J simulation.
		for (ObjectBody object : objectBodies) {
			object.currentPosition.x = object.body.getTransform().getTranslationX();
			object.currentPosition.y = object.body.getTransform().getTranslationY();
			object.currentRotation = object.body.getTransform().getRotation();
		}
		
		sensorDataStale = true;
	}
	

	@Override
	public double[][] getSensorData() {
		if (sensorDataStale) {
			Body agentBody = objectBodies.get(0).body;
			assert ((ObjectInstance) agentBody.getUserData()).description.type.isAgent;
			double rotation = agentBody.getTransform().getRotation();
			Vector2 location = agentBody.getTransform().getTranslation();
			
			int sensorCount = mobileRobot2D.getAgentSensorCount();
			double rayAngle = rotation - mobileRobot2D.getAgentSensorViewAngle() / 2 + Math.PI/2;
			double rayAngleDelta = mobileRobot2D.getAgentSensorViewAngle() / (sensorCount-1);
			Ray ray = new Ray(location, rayAngle);
			for (int si = 0; si < sensorCount; si++) {
				sensorResults.clear();
				world.raycast(ray, 100, true, false, sensorResults);
				if (sensorResults.isEmpty()) {
					sensorData[0][si] = 10;
					sensorData[1][si] = -1;
				}
				else if (sensorResults.get(0).getBody().getUserData() == null) {
					sensorData[0][si] = sensorResults.get(0).getRaycast().getDistance();
					sensorData[1][si] = -1;
				}
				else {
					sensorData[0][si] = sensorResults.get(0).getRaycast().getDistance();
					sensorData[1][si] = ((ObjectBody) sensorResults.get(0).getBody().getUserData()).description.type.colour;
				}
				rayAngle += rayAngleDelta;
				ray.setDirection(rayAngle);
			}
			sensorDataStale = false;
		}
		return sensorData;
	}
	
	private class ObjectBody extends ObjectInstance {
		Body body;
		public ObjectBody(ObjectInstance obj) {
			super(obj.environmentDescription, obj.description);
		}
	}
	
	
	
	/////////// ContactListener interface methods

	@Override
	public boolean begin(ContactPoint cp) {
		if (cp.getBody1().getUserData() != null && cp.getBody2().getUserData() != null) {
			int action = this.processContactEvent((ObjectInstance) cp.getBody1().getUserData(), (ObjectInstance) cp.getBody2().getUserData());
			if (action == CONTACT_REMOVE_FIRST) {
				world.removeBody(cp.getBody1(), false);
				world.setUpdateRequired(true);
				return false;
			}
			if (action == CONTACT_REMOVE_SECOND) {
				world.removeBody(cp.getBody2(), false);
				world.setUpdateRequired(true);
				return false;
			}
		}
		return true;
	}
	@Override
	public void end(ContactPoint arg0) {
	}
	@Override
	public boolean persist(PersistedContactPoint arg0) {
		return true;
	}
	@Override
	public void postSolve(SolvedContactPoint arg0) {
	}
	@Override
	public boolean preSolve(ContactPoint arg0) {
		return true;
	}
	@Override
	public void sensed(ContactPoint arg0) {
	}
}

