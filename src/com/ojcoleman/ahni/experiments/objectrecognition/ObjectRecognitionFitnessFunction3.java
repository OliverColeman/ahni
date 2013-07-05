package com.ojcoleman.ahni.experiments.objectrecognition;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.RenderingHints;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.imageio.ImageIO;


import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.Activator;
import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.AnjiActivator;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.TranscriberException;
import com.anji.neat.Evolver;
import com.anji.nn.AnjiNet;
import com.ojcoleman.ahni.*;
import com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT;
import com.ojcoleman.ahni.evaluation.HyperNEATFitnessFunction;
import com.ojcoleman.ahni.hyperneat.HyperNEATEvolver;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.nn.GridNet;
import com.ojcoleman.ahni.transcriber.HyperNEATTranscriber;
import com.ojcoleman.ahni.transcriber.HyperNEATTranscriberGridNet;

public class ObjectRecognitionFitnessFunction3 extends HyperNEATFitnessFunction {
	public static final String SHAPE_SIZE_KEY = "or.shapesize";
	public static final String SHAPE_TYPE_KEY = "or.shapetype";
	public static final String NUM_EDGES_KEY = "or.numedgesinshape";
	public static final String NUM_SHAPES_KEY = "or.numshapesinlibrary";
	public static final String NUM_SHOWN_KEY = "or.numnontargetshapesshown";
	public static final String TARGET_INDEX_KEY = "or.targetshapeindex";
	public static final String SAVE_IMAGES_KEY = "or.saveimages";
	public static final String FITNESS_WEIGHT_PC_KEY = "or.fitness.weight.percentcorrect";
	public static final String FITNESS_WEIGHT_WSOSE_KEY = "or.fitness.weight.wsose";
	public static final String FITNESS_WEIGHT_DIST_KEY = "or.fitness.weight.distance";
	public static final String FITNESS_WEIGHT_INV_DIST_KEY = "or.fitness.weight.distance.inverse";
	public static final String PERFORMANCE_METRIC_KEY = "or.performance.metric";
	public static final String NUM_TRIALS_KEY = "or.numtrials";

	private static Logger logger = Logger.getLogger(ObjectRecognitionFitnessFunction3.class);

	private String shapesImageDir;
	private String trialsImageDir;
	private String champsImageDir;
	private String compositesImageDir;

	private static int numTrials = 200;

	private double fitnessWeightPC = 1;
	private double fitnessWeightWSOSE = 1;
	private double fitnessWeightDist = 0;
	private double fitnessWeightInvDist = 0;
	private String perfMetric = FITNESS_WEIGHT_DIST_KEY;

	private int shapeSize = 5;
	private String shapeType = "simple";
	private int numShapesInLib = 10;
	private int numEdges = 4;
	private int numNonTargetShapesShown = 1;
	private int targetIndex = -1;
	private boolean saveImages = false;

	private double[][][] stimuli;
	private Point[] targetCoords;
	private BufferedImage[] stimuliImages;

	private double maxDistance;
	private Path2D.Float[] shapes;
	private Path2D.Float target;

	double connectionWeightMin;
	double connectionWeightMax;

	public void init(Properties props) {
		super.init(props);

		shapeSize = props.getIntProperty(SHAPE_SIZE_KEY, shapeSize);
		shapeType = props.getProperty(SHAPE_TYPE_KEY, shapeType);
		numEdges = props.getIntProperty(NUM_EDGES_KEY, numEdges);
		numNonTargetShapesShown = props.getIntProperty(NUM_SHOWN_KEY, numNonTargetShapesShown);
		saveImages = props.getBooleanProperty(SAVE_IMAGES_KEY, saveImages);
		numShapesInLib = props.getIntProperty(NUM_SHAPES_KEY, numShapesInLib);
		targetIndex = props.getIntProperty(TARGET_INDEX_KEY, targetIndex);

		if (shapeType.equals("simple"))
			numShapesInLib = Math.min(numShapesInLib, 4); // only 4 simple shapes available
		else if (shapeType.equals("squareandcs"))
			numShapesInLib = Math.min(numShapesInLib, 5); // only 5 simple shapes available
		else if (shapeType.equals("cs"))
			numShapesInLib = Math.min(numShapesInLib, 4); // only 4 C shapes available

		fitnessWeightPC = props.getDoubleProperty(FITNESS_WEIGHT_PC_KEY, fitnessWeightPC);
		fitnessWeightWSOSE = props.getDoubleProperty(FITNESS_WEIGHT_WSOSE_KEY, fitnessWeightWSOSE);
		fitnessWeightDist = props.getDoubleProperty(FITNESS_WEIGHT_DIST_KEY, fitnessWeightDist);
		fitnessWeightInvDist = props.getDoubleProperty(FITNESS_WEIGHT_INV_DIST_KEY, fitnessWeightInvDist);
		perfMetric = props.getProperty(PERFORMANCE_METRIC_KEY, perfMetric);
		numTrials = props.getIntProperty(NUM_TRIALS_KEY, numTrials);

		// calculate maximum possible distance between any point and the centre of the target shape
		int deltaAdjust = 1 + shapeSize / 2; // max delta (in x or y dimension) is width or height of the field -1 - min
												// distance the centre of the target shape can be from the edge of the
												// board
		int maxXDelta = inputWidth - deltaAdjust;
		int maxYDelta = inputHeight - deltaAdjust;
		maxDistance = (double) Math.sqrt(maxXDelta * maxXDelta + maxYDelta * maxYDelta);

		if (saveImages) {
			// create unique directory for images for this run
			// String imageDir = this.getClass().getName() + File.separatorChar + System.currentTimeMillis();
			String imageDir = props.getProperty("output.dir");
			System.out.println("image dir: " + imageDir);
			shapesImageDir = imageDir + File.separatorChar + "shapes";
			trialsImageDir = imageDir + File.separatorChar + "trials";
			champsImageDir = imageDir + File.separatorChar + "champs";
			compositesImageDir = imageDir + File.separatorChar + "composites";
		}

		// generate random shapes
		shapes = new Path2D.Float[numShapesInLib];
		Line2D.Float line;
		Point P1 = new Point(), P2 = new Point();
		for (int s = 0; s < numShapesInLib; s++) {
			shapes[s] = new Path2D.Float();

			/*
			 * //target is horizontal line, non-target is vertical if (s < numShapesInLib-1) line = new
			 * Line2D.Float((shapeSize-1)/2, 0,(shapeSize-1)/2, shapeSize-1); else line = new Line2D.Float(0,
			 * (shapeSize-1)/2, shapeSize-1,(shapeSize-1)/2); shapes[s].append(line, false);
			 */

			if (shapeType.equals("simple")) {
				// set of simple regular shapes
				switch (s) {
				case 0: // square
					shapes[s].append(new Rectangle(shapeSize - 1, shapeSize - 1), false);
					break;
				case 1: // X
					shapes[s].append(new Line2D.Float(0, 0, shapeSize - 1, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, 0, 0, shapeSize - 1), false);
					break;
				case 2: // circle
					shapes[s].append(new Ellipse2D.Float(0, 0, shapeSize - 1, shapeSize - 1), false);
					break;
				case 3: // triangle
					int[] xpoints = { 0, shapeSize / 2, shapeSize - 1 };
					int[] ypoints = { shapeSize - 1, 0, shapeSize - 1 };
					shapes[s].append(new Polygon(xpoints, ypoints, 3), false);
					break;
				}
			} else if (shapeType.equals("squareandcs")) {
				// set of square and 4 "C" shapes at 90 degree rotations
				switch (s) {
				case 0: // square
					shapes[s].append(new Rectangle(shapeSize - 1, shapeSize - 1), false);
					break;
				case 1: // C1
					shapes[s].append(new Line2D.Float(0, 0, shapeSize - 1, 0), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, 0, shapeSize - 1, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, shapeSize - 1, 0, shapeSize - 1), false);
					break;
				case 2: // C2
					shapes[s].append(new Line2D.Float(shapeSize - 1, 0, shapeSize - 1, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, shapeSize - 1, 0, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(0, shapeSize - 1, 0, 0), false);
					break;
				case 3: // C3
					shapes[s].append(new Line2D.Float(shapeSize - 1, shapeSize - 1, 0, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(0, shapeSize - 1, 0, 0), false);
					shapes[s].append(new Line2D.Float(0, 0, shapeSize - 1, 0), false);
					break;
				case 4: // C4
					shapes[s].append(new Line2D.Float(0, shapeSize - 1, 0, 0), false);
					shapes[s].append(new Line2D.Float(0, 0, shapeSize - 1, 0), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, 0, shapeSize - 1, shapeSize - 1), false);
					break;
				}
			} else if (shapeType.equals("cs")) {
				// set of square and 4 "C" shapes at 90 degree rotations
				switch (s) {
				case 0: // C1
					shapes[s].append(new Line2D.Float(0, 0, shapeSize - 1, 0), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, 0, shapeSize - 1, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, shapeSize - 1, 0, shapeSize - 1), false);
					break;
				case 1: // C2
					shapes[s].append(new Line2D.Float(shapeSize - 1, 0, shapeSize - 1, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, shapeSize - 1, 0, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(0, shapeSize - 1, 0, 0), false);
					break;
				case 2: // C3
					shapes[s].append(new Line2D.Float(shapeSize - 1, shapeSize - 1, 0, shapeSize - 1), false);
					shapes[s].append(new Line2D.Float(0, shapeSize - 1, 0, 0), false);
					shapes[s].append(new Line2D.Float(0, 0, shapeSize - 1, 0), false);
					break;
				case 3: // C4
					shapes[s].append(new Line2D.Float(0, shapeSize - 1, 0, 0), false);
					shapes[s].append(new Line2D.Float(0, 0, shapeSize - 1, 0), false);
					shapes[s].append(new Line2D.Float(shapeSize - 1, 0, shapeSize - 1, shapeSize - 1), false);
					break;
				}
			} else if (shapeType.equals("random vh")) {
				// shapes comprised of random length horizontal and vertical lines
				for (int e = 0; e < numEdges; e++) {
					P1.x = (int) (random.nextFloat() * shapeSize);
					P1.y = (int) (random.nextFloat() * shapeSize);
					do {
						int len = (int) ((random.nextFloat() * (shapeSize / 2f)) + shapeSize / 2f);
						if (random.nextBoolean())
							len = -len;
						if (random.nextBoolean()) {
							P2.x = P1.x + len;
							P2.y = P1.y;
						} else {
							P2.x = P1.x;
							P2.y = P1.y + len;
						}
					} while (P2.x > shapeSize - 1 || P2.x < 0 || P2.y > shapeSize - 1 || P2.y < 0);
					line = new Line2D.Float(P1, P2);
					shapes[s].append(line, false);
				}
			} else if (shapeType.equals("random")) {
				// shapes comprised of random angled and length lines
				for (int e = 0; e < numEdges; e++) {
					do {
						P1.x = (int) (random.nextFloat() * shapeSize);
						P1.y = (int) (random.nextFloat() * shapeSize);
						P2.x = (int) (random.nextFloat() * shapeSize);
						P2.y = (int) (random.nextFloat() * shapeSize);

					} while (P1.distance(P2) < shapeSize / 2f);
					line = new Line2D.Float(P1, P2);
					shapes[s].append(line, false);
				}
			}

			if (saveImages) {
				BufferedImage image = new BufferedImage(shapeSize, shapeSize, BufferedImage.TYPE_BYTE_GRAY);
				Graphics2D canvas = image.createGraphics();
				// canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				canvas.draw(shapes[s]);
				writeImage(image, shapesImageDir, "shape-" + s);
			}
		}

		// if we should choose the target randomly
		if (targetIndex == -1) {
			targetIndex = 0;
			if (!shapeType.equals("squareandcs"))
				targetIndex = random.nextInt(numShapesInLib);
		}
		target = shapes[targetIndex];
		logger.info("target shape index is " + targetIndex);

		connectionWeightMin = props.getFloatProperty(HyperNEATTranscriberGridNet.HYPERNEAT_CONNECTION_WEIGHT_MIN);
		connectionWeightMax = props.getFloatProperty(HyperNEATTranscriberGridNet.HYPERNEAT_CONNECTION_WEIGHT_MAX);
	}

	/**
	 * Initialise data for the current evaluation run (for each generation).
	 */
	public void initialiseEvaluation() {
		// generate trials
		stimuli = new double[numTrials][inputHeight][inputWidth];
		targetCoords = new Point[numTrials];
		if (saveImages)
			stimuliImages = new BufferedImage[numTrials];

		Point pos = new Point();

		// logger.info("init eval");
		double minDistFactor = (double) Math.sqrt(2) * 2; // no overlap for square shapes
		for (int t = 0; t < numTrials; t++) {
			BufferedImage image = new BufferedImage(inputWidth, inputHeight, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D canvas = image.createGraphics();
			// canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// randomly place target
			pos.x = random.nextInt(inputWidth - shapeSize + 1);
			pos.y = random.nextInt(inputHeight - shapeSize + 1);
			drawShape(canvas, pos, target);
			targetCoords[t] = new Point(pos.x + shapeSize / 2, pos.y + shapeSize / 2); // assumes odd size

			// randomly place other shapes so they don't overlap the target
			Path2D.Float shape;
			for (int s = 0; s < numNonTargetShapesShown; s++) {
				// select a non-target shape
				do {
					shape = shapes[random.nextInt(numShapesInLib)];
				} while (shape == target);

				// find somewhere to put it that doesn't overlap too much with the target
				int tries = 0;
				do {
					pos.x = random.nextInt(inputWidth - shapeSize + 1);
					pos.y = random.nextInt(inputHeight - shapeSize + 1);

					tries++;
					if (tries > 1000) {
						if (minDistFactor > 1) {
							minDistFactor *= 0.9f;
							if (minDistFactor < 1)
								minDistFactor = 1;
							tries = 0;
						} else {
							logger.error("OR3: unable to generate trial image, couldn't find anywhere to place non-target image.");
							System.exit(1);
						}
					}
				} while (targetCoords[t].distance(pos) < shapeSize * minDistFactor);

				drawShape(canvas, pos, shape);
			}

			// draw image on NN input
			Raster raster = image.getData();
			for (int yi = 0; yi < inputHeight; yi++) {
				for (int xi = 0; xi < inputWidth; xi++) {
					stimuli[t][yi][xi] = raster.getSampleFloat(xi, yi, 0) / 255f;
					// System.out.print((int) Math.round(stimuli[t][yi][xi] * 10) + " ");
				}
				// System.out.println();
			}
			// System.out.println();

			if (saveImages)
				stimuliImages[t] = image;
		}

		// logger.info("init eval end");
	}

	private void drawShape(Graphics2D canvas, Point pos, Path2D.Float shape) {
		Shape s = shape.createTransformedShape(AffineTransform.getTranslateInstance(pos.x, pos.y));
		canvas.draw(s);
	}

	protected double evaluate(Chromosome genotype, Activator activator, int threadIndex) {
		GridNet substrate = (GridNet) activator;
		double[][][] responses = substrate.nextSequence(stimuli);

		double avgDist = 0;
		double avgInvDist = 0;
		double percentCorrect = 0;
		double wsose = 0;
		for (int t = 0; t < numTrials; t++) {
			double targetOutputError = 0; // for wsoe
			double nonTargetOutputError = 0; // for wsoe

			Point highest = new Point(0, 0);
			for (int y = 0; y < inputHeight; y++) {
				for (int x = 0; x < inputWidth; x++) {
					// find output with highest response
					if (responses[t][y][x] > responses[t][highest.y][highest.x])
						highest.setLocation(x, y);

					// calculate wsose error
					if (y == targetCoords[t].y && x == targetCoords[t].x)
						targetOutputError = (double) Math.pow(1 - responses[t][y][x], 2);
					else
						nonTargetOutputError += (double) Math.pow(responses[t][y][x], 2);
				}
			}

			avgDist += targetCoords[t].distance(highest);
			avgInvDist += 1 / (targetCoords[t].distance(highest) + 1);
			percentCorrect += targetCoords[t].equals(highest) ? 1 : 0;
			wsose += (targetOutputError + (nonTargetOutputError / (inputWidth * inputHeight - 1))) / 2;
		}
		avgDist /= numTrials;
		avgInvDist /= numTrials;
		percentCorrect /= numTrials;
		wsose /= numTrials;

		// calculate fitness according to fitness function type weightings
		double fitness = fitnessWeightPC * percentCorrect + fitnessWeightWSOSE * (1 - wsose) + fitnessWeightDist * (1 - (avgDist / maxDistance)) + fitnessWeightInvDist * avgInvDist;
		fitness /= fitnessWeightPC + fitnessWeightWSOSE + fitnessWeightDist + fitnessWeightInvDist;

		if (perfMetric.equals(FITNESS_WEIGHT_PC_KEY))
			genotype.setPerformanceValue(percentCorrect);
		else if (perfMetric.equals(FITNESS_WEIGHT_WSOSE_KEY))
			genotype.setPerformanceValue(wsose);
		else if (perfMetric.equals(FITNESS_WEIGHT_DIST_KEY))
			genotype.setPerformanceValue(avgDist);
		else if (perfMetric.equals(FITNESS_WEIGHT_INV_DIST_KEY))
			genotype.setPerformanceValue(avgInvDist);
		else
			genotype.setPerformanceValue(fitness);

		// if performance has increased significantly then save some images of the weights and activation patterns for
		// some trials
		// if ((fitness >= scalePerformance || (lastBestChrom == genotype && lastBestPerformance >= scalePerformance))
		// && saveImages) {
		if ((genotype.getPerformanceValue() >= scalePerformance || (lastBestChrom == genotype && lastBestPerformance >= scalePerformance)) && saveImages) {
			// int imageScale = (int) Math.ceil(100/width); //make images at least 100 pixels in size
			int imageScale = 4;
			int imageSize = imageScale * inputWidth; // assumes square layers
			int negDotSize = imageScale / 4;
			double weightRange = connectionWeightMax - connectionWeightMin;
			int connectionRange = getConnectionRange();
			AffineTransform scaleTransform = AffineTransform.getScaleInstance(imageScale, imageScale);

			for (int t = 0; t < numTrials; t++) {
				// save trial images
				// writeImage(stimuliImages[t], trialsImageDir, "trial-" + t);

				BufferedImage image = new BufferedImage(imageSize * 3 + 2, imageSize, BufferedImage.TYPE_BYTE_GRAY);
				Graphics2D canvas = image.createGraphics();
				// draw alternating horizontal lines for background, only 1 pixel
				// wide gaps should be visible once target, weights and outputs are drawn
				canvas.setColor(new Color(255, 255, 255));
				for (int y = 0; y < imageSize; y += 2)
					canvas.draw(new Line2D.Float(0, y, imageSize * 3 + 2 - 1, y));

				canvas.drawImage(stimuliImages[t], scaleTransform, null);

				int tx = targetCoords[t].x;
				int ty = targetCoords[t].y;

				// generate image for weight matrix
				// BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_BYTE_GRAY);
				// Graphics2D canvas = image.createGraphics();
				canvas.setColor(new Color(127, 127, 127));
				int offset = imageSize + 1;
				// initialise to 0 weight value color for case where layers are not fully connected
				canvas.fill(new Rectangle(offset, 0, imageSize, imageSize));

				// calculate dimensions of this weight target matrix (bounded by grid edges)
				int dy = Math.min(inputHeight - 1, ty + connectionRange) - Math.max(0, ty - connectionRange) + 1;
				int dx = Math.min(inputWidth - 1, tx + connectionRange) - Math.max(0, tx - connectionRange) + 1;
				double[][] w = substrate.getWeights()[0][ty][tx][0];

				for (int wy = 0, sy = Math.max(0, ty - connectionRange); wy < dy; wy++, sy++) {
					for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
						int color = (int) (((w[wy][wx] - connectionWeightMin) / weightRange) * 255);
						canvas.setColor(new Color(color, color, color));
						canvas.fill(new Rectangle(offset + sx * imageScale, sy * imageScale, imageScale, imageScale));
						if (w[wy][wx] < 0) {
							canvas.setColor(Color.black);
							canvas.fill(new Rectangle(offset + sx * imageScale + imageScale / 2 - negDotSize / 2, sy * imageScale + imageScale / 2 - negDotSize / 2, negDotSize, negDotSize));
						}
					}
				}
				// writeImage(image, champsImageDir + File.separatorChar + genotype.getId() + "-weights", "weights-" + t
				// + "-" + tx + "," + ty);

				// generate image of output values
				offset = imageSize * 2 + 2;
				// image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_BYTE_GRAY);
				// canvas = image.createGraphics();
				canvas.setColor(new Color(127, 127, 127));
				for (int y = 0; y < inputHeight; y++) {
					for (int x = 0; x < inputWidth; x++) {
						int color = (int) (responses[t][y][x] * 255); // assumes output range is [0, 1]
						canvas.setColor(new Color(color, color, color));
						canvas.fill(new Rectangle(offset + x * imageScale, y * imageScale, imageScale, imageScale));
					}
				}
				// writeImage(image, champsImageDir + File.separatorChar + genotype.getId() + "-outputs", "outputs-" + t
				// + "-" + tx + "," + ty);

				writeImage(image, compositesImageDir + File.separatorChar + scaleCount + "-" + genotype.getId(), t + "-" + tx + "," + ty);
			}

			// spit out CPPN
			try {
				AnjiNetTranscriber cppnTranscriber = (AnjiNetTranscriber) props.singletonObjectProperty(AnjiNetTranscriber.class);
				AnjiNet cppn = ((AnjiActivator) cppnTranscriber.transcribe(genotype)).getAnjiNet();
				BufferedWriter cppnFile = new BufferedWriter(new FileWriter(compositesImageDir + File.separatorChar + scaleCount + "-" + genotype.getId() + File.separatorChar + "cppn.xml"));
				cppnFile.write(cppn.toXml());
				cppnFile.close();
			} catch (Exception e) {
				System.err.println("Error transcribing CPPN for display:\n" + e.getStackTrace());
			}

		}

		return fitness;
	}

	@Override
	protected void scale(int scaleCount, int scaleFactor, HyperNEATTranscriber transcriber) {
		int[] width = transcriber.getWidth();
		int[] height = transcriber.getWidth();
		int connectionRange = transcriber.getConnectionRange();
		
		// get ratio of shape size to image size (this should be maintained during scale).
		double ratioW = (double) inputWidth / shapeSize;
		double ratioH = (double) inputHeight / shapeSize;

		// adjust shape size
		if (scaleFactor % 2 == 0 && shapeSize % 2 == 1) // if scaleFactor is even but shapeSize is odd
			shapeSize = (shapeSize / 2) * scaleFactor * 2 + 1; // preserve oddness of conn range
		else
			shapeSize *= scaleFactor;

		for (int l = 0; l < width.length; l++) {
			width[l] = (int) Math.round(shapeSize * ratioW);
			height[l] = (int) Math.round(shapeSize * ratioH);
		}
		connectionRange = shapeSize / 2;

		AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
		for (int s = 0; s < numShapesInLib; s++)
			shapes[s].transform(scaleTransform);
		
		inputWidth = width[0];
		inputHeight = height[0];
		outputWidth = width[width.length - 1];
		outputHeight = height[height.length - 1];
		
		transcriber.resize(width, height, connectionRange);

		logger.info("Scale performed: image size: " + inputWidth + "x" + inputHeight + ", shape size: " + shapeSize + ", conn range: " + connectionRange);
	}

	private void writeImage(BufferedImage image, String dir, String name) {
		File dirFile = new File(dir);
		if (!dirFile.exists() && !dirFile.mkdirs()) {
			logger.error("Error creating directory: " + dir);
		} else {
			String fullPath = dir + File.separatorChar + name + ".png";
			try {
				ImageIO.write(image, "png", new File(fullPath));
			} catch (IOException e) {
				logger.error("Error writing image: " + fullPath);
			}
		}
	}
}
