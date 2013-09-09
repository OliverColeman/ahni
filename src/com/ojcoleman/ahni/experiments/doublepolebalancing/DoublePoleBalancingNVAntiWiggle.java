package com.ojcoleman.ahni.experiments.doublepolebalancing;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.ojcoleman.ahni.evaluation.novelty.Behaviour;

/**
 * <p>Implements the non-Markovian double pole balancing task (velocities NOT included in inputs) with the anti-oscillation component as described in:
 * <blockquote> Gruau, F., Whitley, D., and Pyeatt, L. (1996). A comparison between cellular encoding and direct encoding for genetic neural networks. 
 * In Genetic Programming 1996: Proceedings of the First Annual Conference, pages 81–89, MIT Press, Cambridge, Massachusetts </blockquote>
 * minus the generalisation tests.</p>
 * 
 * <p>This code was adapted from SharpNEAT by Colin Green (http://sharpneat.sourceforge.net/) and then modified to reflect the fitness function
 * described in the above paper.</p>
 */
public class DoublePoleBalancingNVAntiWiggle extends DoublePoleBalancing {
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
		if (biasViaInput) input[3] = 0.5;
		
	    JiggleBuffer jiggleBuffer1 = new JiggleBuffer(100);
	    //JiggleBuffer jiggleBuffer2 = new JiggleBuffer(100);
		
		// Run the pole-balancing simulation.
		int timestep = 0;
		for (; timestep < _maxTimesteps; timestep++) {
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
			
			// Jiggle buffer updates..
			//if (100 == jiggleBuffer1.getLength()) {
				// Feed an old value from buffer 1 into buffer2.
			//	jiggleBuffer2.enqueue(jiggleBuffer1.dequeue());
			//}

			// Place the latest jiggle value into buffer1.
			jiggleBuffer1.enqueue(Math.abs(state[0]) + Math.abs(state[1]) + Math.abs(state[2]) + Math.abs(state[3]));
			
			// Check for failure state. Has the cart run off the ends of the track or has the pole
			// angle gone beyond the threshold.
			if ((state[0] < -_trackLengthHalf) || (state[0] > _trackLengthHalf) || 
					(state[2] > _poleAngleThreshold) || (state[2] < -_poleAngleThreshold) || 
					(state[4] > _poleAngleThreshold) || (state[4] < -_poleAngleThreshold)) {
				break;
			}

			// Give the simulation at least 500 timesteps(5secs) to stabilise before penalising instability.
			//if (timestep > 499 && jiggleBuffer2.getTotal() > 30.0) {
				// Too much wriggling. Stop simulation early (30 was an experimentally determined value).
			//    break;
			//}
		}
		
		/*if (timestep > 499 && timestep < 600) {
			// For the 100(1 sec) steps after the 500(5 secs) mark we punish wriggling based
			// on the values from the 1 sec just gone. This is on the basis that the values
			// in jiggleBuffer2 (from 2 to 1 sec ago) will reflect the large amount of
			// wriggling that occurs at the start of the simulation when the system is still stabilising.
			timestep += (10.0 / Math.max(1.0, jiggleBuffer1.getTotal()));
		} else if (timestep > 599) {
			// After 600 steps we use jiggleBuffer2 to punish wriggling, this contains data from between
			// 2 and 1 secs ago. This is on the basis that when the system becomes unstable and causes
			// the simulation to terminate prematurely, the immediately prior 1 secs data will reflect that
			// instability, which may not be indicative of the overall stability of the system up to that time.
			timestep += (10.0 / Math.max(1.0, jiggleBuffer2.getTotal()));
		}*/
		
		// Max fitness is # of timesteps + max anti-wriggle factor of 10 (which is actually impossible - some wriggle is
		// necessary to balance the poles).
		if (fitnessValues != null) {
			double f1 = (double) timestep / _maxTimesteps;
			double f2 = timestep < 100 ? 0 : 0.75 / jiggleBuffer1.getTotal();
			fitnessValues[0] = 0.1*f1 + 0.9*f2;
			genotype.setPerformanceValue(f1);
			
			//fitnessValues[0] = (double) timestep / (_maxTimesteps+10);
		}

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
