package com.ojcoleman.ahni.experiments.doublepolebalancing;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;

/**
 * <p>
 * Implements the non-Markovian double pole balancing task (velocities NOT included in inputs) with the anti-oscillation
 * component as described in: <blockquote> Gruau, F., Whitley, D., and Pyeatt, L. (1996). A comparison between cellular
 * encoding and direct encoding for genetic neural networks. In Genetic Programming 1996: Proceedings of the First
 * Annual Conference, pages 81–89, MIT Press, Cambridge, Massachusetts </blockquote>.
 * </p>
 * 
 * <p>
 * This code was adapted from SharpNEAT by Colin Green (http://sharpneat.sourceforge.net/) and then modified to reflect
 * the fitness function described in the above paper.
 * </p>
 */
public class DoublePoleBalancingNVAntiWiggle extends DoublePoleBalancing {
	// For generalisation test.
	static final double[] _statevals = new double[] { 0.05, 0.25, 0.5, 0.75, 0.95 };
	static final int _generalisationSteps = 100000;

	/**
	 * Construct evaluator with default task arguments/variables.
	 */
	public DoublePoleBalancingNVAntiWiggle() {
		super(4.8, 1000, ThirtySixDegrees);
	}

	/**
	 * Construct evaluator with the provided task arguments/variables.
	 */
	public DoublePoleBalancingNVAntiWiggle(double trackLength, int maxTimesteps, double poleAngleThreshold) {
		super(trackLength, maxTimesteps, poleAngleThreshold);
	}

	@Override
	public void _evaluate(Chromosome genotype, Activator substrate, String baseFileName, boolean logText, boolean logImage, double[] fitnessValues, Behaviour[] behaviours) {
		// [0] - Cart Position (meters).
		// [1] - Cart velocity (m/s).
		// [2] - Pole 1 angle (radians)
		// [3] - Pole 1 angular velocity (radians/sec).
		// [4] - Pole 2 angle (radians)
		// [5] - Pole 2 angular velocity (radians/sec).
		double[] state = new double[6];
		state[2] = FourDegrees;

		double[] input = new double[3 + (biasViaInput ? 1 : 0)];
		if (biasViaInput)
			input[3] = 0.5;

		JiggleBuffer jiggleBuffer1 = new JiggleBuffer(100);

		// Run the pole-balancing simulation.
		int timestep = _simulate(substrate, state, input, jiggleBuffer1, _maxTimesteps);

		double f1 = (double) timestep / _maxTimesteps;
		double f2 = timestep < 100 ? 0 : 0.75 / jiggleBuffer1.getTotal();
		double fitness = 0.1 * f1 + 0.9 * f2;
		fitness *= 0.33;
		double perf = 0;

		// Do generalisation tests if successfully balanced for 1000 time steps.
		if (timestep == _maxTimesteps) {
			// Continue simulation until 100,000 time steps.
			timestep += _simulate(substrate, state, input, null, _generalisationSteps - _maxTimesteps);
			
			fitness += 0.33 * ((double) timestep / _generalisationSteps);

			// If passed 100,000 time step test.
			if (timestep == _generalisationSteps) {
				// Test from 625 different initial starting positions.
				int score = 0;
				for (int s0c = 0; s0c <= 4; ++s0c) {
					for (int s1c = 0; s1c <= 4; ++s1c) {
						for (int s2c = 0; s2c <= 4; ++s2c) {
							for (int s3c = 0; s3c <= 4; ++s3c) {
								state[0] = _statevals[s0c] * 4.32 - 2.16;
								state[1] = _statevals[s1c] * 2.70 - 1.35;
								state[2] = _statevals[s2c] * 0.12566304 - 0.06283152;
								/* 0.06283152 = 3.6 degrees */
								state[3] = _statevals[s3c] * 0.30019504 - 0.15009752;
								/* 00.15009752 = 8.6 degrees */
								state[4] = 0.0;
								state[5] = 0.0;

								substrate.reset();
								timestep = _simulate(substrate, state, input, null, 1000);
								if (timestep == 1000) {
									score++;
								}
							}
						}
					}
				}
				perf = score/625.0;
				
				fitness += 0.33 * (score / 625.0);
			}
		}

		if (fitnessValues != null) {
			fitnessValues[0] = fitness;
			genotype.setPerformanceValue(perf);
		}
	}

	private int _simulate(Activator substrate, double[] state, double[] input, JiggleBuffer jiggleBuffer1, int simSteps) {
		for (int timestep = 0; timestep < _maxTimesteps; timestep++) {
			// Provide state info to the network (normalised to +-1.0). Non-Markovian (Without velocity info)
			// Cart Position is +-trackLengthHalfed
			input[0] = state[0] / _trackLengthHalf;
			// Pole Angle is +-thirtysix_degrees. Values outside of this range stop the simulation.
			input[1] = state[2] / ThirtySixDegrees;
			// Pole Angle is +-thirtysix_degrees. Values outside of this range stop the simulation.
			input[2] = state[4] / ThirtySixDegrees;

			// Activate the network.
			double[] output = substrate.next(input);

			// Get network response and calc next timestep state.
			performAction(state, output[0]);

			// Place the latest jiggle value into buffer1.
			if (jiggleBuffer1 != null) {
				jiggleBuffer1.enqueue(Math.abs(state[0]) + Math.abs(state[1]) + Math.abs(state[2]) + Math.abs(state[3]));
			}

			// Check for failure state. Has the cart run off the ends of the track or has the pole
			// angle gone beyond the threshold.
			if ((state[0] < -_trackLengthHalf) || (state[0] > _trackLengthHalf) || (state[2] > _poleAngleThreshold) || (state[2] < -_poleAngleThreshold) || (state[4] > _poleAngleThreshold) || (state[4] < -_poleAngleThreshold)) {
				return timestep;
			}
		}
		return simSteps;
	}

	@Override
	public int[] getLayerDimensions(int layer, int totalLayerCount) {
		if (layer == 0) // Input layer.
			return new int[] { 3 + (biasViaInput ? 1 : 0), 1 };
		else if (layer == totalLayerCount - 1) // Output layer.
			return new int[] { 1, 1 };
		return null;
	}
}
