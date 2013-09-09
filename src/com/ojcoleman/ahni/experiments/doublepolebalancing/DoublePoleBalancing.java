package com.ojcoleman.ahni.experiments.doublepolebalancing;

import java.util.Arrays;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;

/**
 * Implements the Markovian double pole balancing task (with velocities included in inputs) as described in:
 * <blockquote> Gruau, F., Whitley, D., and Pyeatt, L. (1996). A comparison between cellular encoding and direct encoding for genetic neural networks. 
 * In Genetic Programming 1996: Proceedings of the First Annual Conference, pages 81–89, MIT Press, Cambridge, Massachusetts </blockquote>
 * 
 * This code was adapted from SharpNEAT by Colin Green (http://sharpneat.sourceforge.net/).
 */
public class DoublePoleBalancing extends BulkFitnessFunctionMT {
	// Some physical model constants.
	protected static final double Gravity = -9.8;
	protected static final double MassCart = 1.0;
	protected static final double Length1 = 0.5;
	/* actually half the pole's length */
	protected static final double MassPole1 = 0.1;
	protected static final double Length2 = 0.05;
	protected static final double MassPole2 = 0.01;
	protected static final double ForceMag = 10.0;
	/**
	 * Time increment interval in seconds.
	 */
	public static final double TimeDelta = 0.01;
	protected static final double FourThirds = 4.0 / 3.0;
	/**
	 * Uplifting moment?
	 */
	protected static final double MUP = 0.000002;
	// Some useful angle constants.
	protected static final double OneDegree = Math.PI / 180.0;
	// = 0.0174532;
	protected static final double FourDegrees = Math.PI / 45.0;
	// = 0.06981317;
	protected static final double SixDegrees = Math.PI / 30.0;
	// = 0.1047192;
	protected static final double TwelveDegrees = Math.PI / 15.0;
	// = 0.2094384;
	protected static final double EighteenDegrees = Math.PI / 10.0;
	// = 0.3141592;
	protected static final double TwentyFourDegrees = Math.PI / 7.5;
	// = 0.4188790;
	protected static final double ThirtySixDegrees = Math.PI / 5.0;
	// = 0.628329;
	protected static final double FiftyDegrees = Math.PI / 3.6;
	// = 0.87266;
	protected static final double SeventyTwoDegrees = Math.PI / 2.5;
	// = 1.256637;
	// Domain parameters.
	double _trackLength;
	protected double _trackLengthHalf;
	protected int _maxTimesteps;
	protected double _poleAngleThreshold;
	
	boolean biasViaInput = false;

	/**
	 * Construct evaluator with default task arguments/variables.
	 */
	public DoublePoleBalancing() {
		this(4.8, 100000, ThirtySixDegrees);
	}

	/**
	 * Construct evaluator with the provided task arguments/variables.
	 */
	public DoublePoleBalancing(double trackLength, int maxTimesteps, double poleAngleThreshold) {
		_trackLength = trackLength;
		_trackLengthHalf = trackLength / 2.0;
		_maxTimesteps = maxTimesteps;
		_poleAngleThreshold = poleAngleThreshold;
	}

	@Override
	protected void evaluate(Chromosome genotype, Activator substrate, int evalThreadIndex, double[] fitnessValues, Behaviour[] behaviours) {
		_evaluate(genotype, substrate, null, false, false, fitnessValues, behaviours);
	}
	
	@Override
	public void evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage) {
		_evaluate(genotype, substrate, baseFileName, logText, logImage, null, null);
	}
	
	public void _evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues, Behaviour[] behaviours) {
		// [0] - Cart Position (meters).
		// [1] - Cart velocity (m/s).
		// [2] - Pole 1 angle (radians)
		// [3] - Pole 1 angular velocity (radians/sec).
		// [4] - Pole 2 angle (radians)
		// [5] - Pole 2 angular velocity (radians/sec).
		double[] state = new double[6];
		state[2] = FourDegrees;
		// Run the pole-balancing simulation.
		int timestep = 0;
		double[] input = new double[6 + (biasViaInput ? 1 : 0)];
		if (biasViaInput) input[6] = 0.5;
		for (; timestep < _maxTimesteps; timestep++) {
			// Provide state info to the network (normalised to +-1.0). Markovian (With velocity info)
			// Cart Position is +-trackLengthHalfed
			input[0] = state[0] / _trackLengthHalf;
			// Cart velocity is typically +-0.75
			input[1] = state[1] / 0.75;
			// Pole Angle is +-thirtysix_degrees. Values outside of this range stop the simulation.
			input[2] = state[2] / ThirtySixDegrees;
			// Pole angular velocity is typically +-1.0 radians. No scaling required.
			input[3] = state[3];
			// Pole Angle is +-thirtysix_degrees. Values outside of this range stop the simulation.
			input[4] = state[4] / ThirtySixDegrees;
			// Pole angular velocity is typically +-1.0 radians. No scaling required.
			input[5] = state[5];
			
			// Activate the network.
			double[] output = substrate.next(input);
			// Get network response and calc next timestep state.
			performAction(state, output[0]);
			// Check for failure state. Has the cart run off the ends of the track or has the pole
			// angle gone beyond the threshold.
			if ((state[0] < -_trackLengthHalf) || (state[0] > _trackLengthHalf) || (state[2] > _poleAngleThreshold) || (state[2] < -_poleAngleThreshold) || (state[4] > _poleAngleThreshold) || (state[4] < -_poleAngleThreshold)) {
				break;
			}
		}

		// The controller's fitness is defined as the number of timesteps that elapse before failure.
		if (fitnessValues != null) {
			fitnessValues[0] = (double) timestep / _maxTimesteps;
		}
	}

	/**
	 * Calculates a state update for the next timestep using current model state and a single action from the
	 * controller. The action is a continuous variable with range [0:1]. 0 -> push left, 1 -> push right.
	 * 
	 * @param state Model state.
	 * @param output Push force.
	 */
	protected void performAction(double[] state, double output) {
		int i;
		double[] dydx = new double[6];
		for (i = 0; i < 2; ++i) {
			// Apply action to the simulated cart-pole
			// Runge-Kutta 4th order integration method
			dydx[0] = state[1];
			dydx[2] = state[3];
			dydx[4] = state[5];
			step(output, state, dydx);
			rk4(output, state, dydx);
		}
	}

	private void step(double action, double[] st, double[] derivs) {
		double force, costheta_1, costheta_2, sintheta_1, sintheta_2, gsintheta_1, gsintheta_2, temp_1, temp_2, ml_1, ml_2, fi_1, fi_2, mi_1, mi_2;
		force = (action - 0.5) * ForceMag * 2;
		costheta_1 = Math.cos(st[2]);
		sintheta_1 = Math.sin(st[2]);
		gsintheta_1 = Gravity * sintheta_1;
		costheta_2 = Math.cos(st[4]);
		sintheta_2 = Math.sin(st[4]);
		gsintheta_2 = Gravity * sintheta_2;
		ml_1 = Length1 * MassPole1;
		ml_2 = Length2 * MassPole2;
		temp_1 = MUP * st[3] / ml_1;
		temp_2 = MUP * st[5] / ml_2;
		fi_1 = (ml_1 * st[3] * st[3] * sintheta_1) + (0.75 * MassPole1 * costheta_1 * (temp_1 + gsintheta_1));
		fi_2 = (ml_2 * st[5] * st[5] * sintheta_2) + (0.75 * MassPole2 * costheta_2 * (temp_2 + gsintheta_2));
		mi_1 = MassPole1 * (1 - (0.75 * costheta_1 * costheta_1));
		mi_2 = MassPole2 * (1 - (0.75 * costheta_2 * costheta_2));
		derivs[1] = (force + fi_1 + fi_2) / (mi_1 + mi_2 + MassCart);
		derivs[3] = -0.75 * (derivs[1] * costheta_1 + gsintheta_1 + temp_1) / Length1;
		derivs[5] = -0.75 * (derivs[1] * costheta_2 + gsintheta_2 + temp_2) / Length2;
	}

	private void rk4(double f, double[] y, double[] dydx) {
		int i;
		double hh, h6;
		double[] dym = new double[6];
		double[] dyt = new double[6];
		double[] yt = new double[6];
		hh = TimeDelta * 0.5;
		h6 = TimeDelta / 6.0;
		for (i = 0; i <= 5; i++) {
			yt[i] = y[i] + (hh * dydx[i]);
		}
		step(f, yt, dyt);
		dyt[0] = yt[1];
		dyt[2] = yt[3];
		dyt[4] = yt[5];
		for (i = 0; i <= 5; i++) {
			yt[i] = y[i] + (hh * dyt[i]);
		}
		step(f, yt, dym);
		dym[0] = yt[1];
		dym[2] = yt[3];
		dym[4] = yt[5];
		for (i = 0; i <= 5; i++) {
			yt[i] = y[i] + (TimeDelta * dym[i]);
			dym[i] = dym[i] + dyt[i];
		}
		step(f, yt, dyt);
		dyt[0] = yt[1];
		dyt[2] = yt[3];
		dyt[4] = yt[5];

		for (i = 0; i <= 5; i++) {
			y[i] = y[i] + h6 * (dydx[i] + dyt[i] + 2.0 * dym[i]);
		}
	}
	
	@Override
	public int fitnessObjectivesCount() {
		return 1;
	}

	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) // Input layer.
			return new int[] { 6 + (biasViaInput ? 1 : 0), 1 };
		else if (layer == totalLayerCount - 1) // Output layer.
			return new int[] { 1, 1 };
		return null;
	}
}
