package com.ojcoleman.ahni.experiments.objectrecognition;

import java.awt.geom.AffineTransform;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.*;
import com.anji.nn.*;
import com.ojcoleman.ahni.*;
import com.ojcoleman.ahni.evaluation.HyperNEATFitnessFunction;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.GridNet;
import com.ojcoleman.ahni.transcriber.HyperNEATTranscriber;

/**
 * Corresponds to tasks 1.1 and 1.2 in Oliver J. Coleman, "Evolving Neural Networks for Visual Processing",
 * Undergraduate Honours Thesis (Bachelor of Computer Science), 2010. The number of small squares is hard coded in
 * variable numSmallSquares.
 */
public class ObjectRecognitionFitnessFunction2 extends HyperNEATFitnessFunction {
	private static Logger logger = Logger.getLogger(ObjectRecognitionFitnessFunction2.class);

	private double[][][] stimuli;
	private int[][] targetCoords;
	private int maxFitnessValue;

	private final static int numTrials = 100;
	private final static int numSmallSquares = 1;
	private int smallSquareSize = 1;
	private int largeSquareSize = smallSquareSize * 3;

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param props configuration parameters
	 */
	public void init(Properties props) {
		super.init(props);
		setMaxFitnessValue();
	}

	private void setMaxFitnessValue() {
		int deltaAdjust = 1 + largeSquareSize / 2; // max delta (in x or y dimension) is width or height of the field -1
													// - min distance the centre of the large square can be from the
													// edge of the board
		int maxXDelta = inputWidth - deltaAdjust;
		int maxYDelta = inputHeight - deltaAdjust;
		double maxDistance = (double) Math.sqrt(maxXDelta * maxXDelta + maxYDelta * maxYDelta);
		maxFitnessValue = (int) Math.ceil(maxDistance * 100000); // fitness is given by maxFitnessValue - avg of distances
																// * 100000
	}

	public void initialiseEvaluation() {
		// generate trials
		stimuli = new double[numTrials][inputHeight][inputWidth];
		targetCoords = new int[numTrials][2];

		for (int t = 0; t < numTrials; t++) {
			// randomly place large square
			int xpos = random.nextInt(inputWidth - largeSquareSize);
			int ypos = random.nextInt(inputHeight - largeSquareSize);
			for (int y = ypos; y < ypos + largeSquareSize; y++) {
				for (int x = xpos; x < xpos + largeSquareSize; x++) {
					stimuli[t][y][x] = 1;
				}
			}
			targetCoords[t][0] = xpos + (int) (largeSquareSize - 1) / 2; // assumes odd size
			targetCoords[t][1] = ypos + (int) (largeSquareSize - 1) / 2;

			// randomly place small squares to not overlap large square or each other
			for (int s = 0; s < numSmallSquares; s++) {
				while (true) {
					xpos = random.nextInt(inputWidth);
					ypos = random.nextInt(inputHeight);
					if (stimuli[t][ypos][xpos] == 0) { // if not overlapping
						stimuli[t][ypos][xpos] = 1;
						break;
					}
				}
			}
		}
	}

	/**
	 * Evaluate given individual by presenting the stimuli to the network in a random order to ensure the underlying
	 * network is not memorising the sequence of inputs. Calculation of the fitness based on error is delegated to the
	 * subclass. This method adjusts fitness for network size, based on configuration.
	 */
	protected double evaluate(Chromosome genotype, Activator activator, int threadIndex) {
		double[][][] responses = activator.nextSequence(stimuli);

		double totalDists = 0;
		for (int t = 0; t < numTrials; t++) {
			// find output with highest response
			int xh = 0;
			int yh = 0;
			for (int y = 0; y < inputHeight; y++) {
				for (int x = 0; x < inputWidth; x++) {
					if (responses[t][y][x] > responses[t][yh][xh]) {
						xh = x;
						yh = y;
					}
				}
			}

			int deltaX = xh - targetCoords[t][0];
			int deltaY = yh - targetCoords[t][1];
			int sqrDist = deltaX * deltaX + deltaY * deltaY;
			totalDists += Math.sqrt(sqrDist);
		}
		double fitness = (maxFitnessValue - (totalDists / numTrials) * 100000) / maxFitnessValue;
		genotype.setPerformanceValue(fitness);
		//if (fitness > 0.85) System.err.println((totalDists / numTrials) + " : " + fitness);
		return fitness;
	}

	@Override
	protected void scale(int scaleCount, int scaleFactor, HyperNEATTranscriber transcriber) {
		// adjust shape size
		largeSquareSize *= scaleFactor;
		smallSquareSize *= scaleFactor;

		int[] width = transcriber.getWidth();
		int[] height = transcriber.getWidth();
		int connectionRange = transcriber.getConnectionRange();

		// get ratio of shape size to image size (this should be maintained during scale).
		double ratioW = (double) inputWidth / largeSquareSize;
		double ratioH = (double) inputHeight / largeSquareSize;

		// adjust shape size
		if (scaleFactor % 2 == 0 && largeSquareSize % 2 == 1) // if scaleFactor is even but shapeSize is odd
			largeSquareSize = (largeSquareSize / 2) * scaleFactor * 2 + 1; // preserve oddness of conn range
		else
			largeSquareSize *= scaleFactor;

		for (int l = 0; l < width.length; l++) {
			width[l] = (int) Math.round(largeSquareSize * ratioW);
			height[l] = (int) Math.round(largeSquareSize * ratioH);
		}
		connectionRange = largeSquareSize / 2;

		inputWidth = width[0];
		inputHeight = height[0];
		outputWidth = width[width.length - 1];
		outputHeight = height[height.length - 1];

		transcriber.resize(width, height, connectionRange);

		setMaxFitnessValue();

		logger.info("Scale performed: image size: " + inputWidth + "x" + inputHeight + ", large square size: " + largeSquareSize + ", conn range: " + connectionRange);
	}
}
