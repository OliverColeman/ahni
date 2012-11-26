package ojc.ahni.experiments.objectrecognition;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.imageio.ImageIO;

import ojc.ahni.*;
import ojc.ahni.evaluation.HyperNEATFitnessFunction;
import ojc.ahni.event.AHNIRunProperties;
import ojc.ahni.hyperneat.HyperNEATEvolver;
import ojc.ahni.nn.GridNet;
import ojc.ahni.transcriber.HyperNEATTranscriberGridNet;

import org.apache.log4j.Logger;
import org.jgapcustomised.*;

import com.anji.integration.Activator;
import com.anji.neat.Evolver;

public class ObjectRecognitionFitnessFunction4 extends HyperNEATFitnessFunction {
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
	public static final String MIN_SCALE_KEY = "or.minscale";
	public static final String MAX_ROTATE_KEY = "or.maxrotate";

	private static Logger logger = Logger.getLogger(ObjectRecognitionFitnessFunction4.class);

	// unique directory for images for this run
	String imageDir;

	private static int maxFitnessValue = 1000000;
	private static int numTrials = 200;

	private AHNIRunProperties properties;
	private double bestPerformanceSoFar = 0;
	private double bestFitnessSoFar = 0;
	public double bestPCSoFar = 0;

	private double fitnessWeightPC = 1;
	private double fitnessWeightWSOSE = 1;
	private double fitnessWeightDist = 0;
	private double fitnessWeightInvDist = 0;
	private String perfMetric = FITNESS_WEIGHT_PC_KEY;

	private int shapeSize = 5;
	private String shapeType = "simple";
	private int numShapesInLib = 10;
	private int numEdges = 4;
	private int numNonTargetShapesShown = 1;
	private int targetIndex = -1;
	private boolean saveImages = false;
	private boolean printedFirst = false;

	private double minScale = 0.5f;
	private int maxRotate = 360;

	private double[][][] stimuli;
	// private Point[] targetCoords;
	private boolean[] targetPresent;
	private BufferedImage[] stimuliImages;

	private double maxDistance;
	private Path2D.Float[] shapes;
	private Path2D.Float target;

	int[] imageScaleActivation = { 6, 6, 24 }; // size (in pixels) of a square representing the activation of a neuron
												// (per layer)
	int[] imageScaleWeights = { 3, 12 }; // size (in pixels) of a square representing the value of a weight
	int imageNegDotSize = 1; // size of black square/dot in middle of weight value square to indicate negative value
	int imageSpacing = 5;

	double connectionWeightMin;
	double connectionWeightMax;

	public void init(com.anji.util.Properties props) {
		this.properties = (AHNIRunProperties) props;
		super.init(props);

		shapeSize = props.getIntProperty(SHAPE_SIZE_KEY, shapeSize);
		shapeType = props.getProperty(SHAPE_TYPE_KEY, shapeType);
		numEdges = props.getIntProperty(NUM_EDGES_KEY, numEdges);
		numNonTargetShapesShown = props.getIntProperty(NUM_SHOWN_KEY, numNonTargetShapesShown);
		saveImages = props.getBooleanProperty(SAVE_IMAGES_KEY, saveImages);
		numShapesInLib = props.getIntProperty(NUM_SHAPES_KEY, numShapesInLib);
		targetIndex = props.getIntProperty(TARGET_INDEX_KEY, targetIndex);
		minScale = props.getDoubleProperty(MIN_SCALE_KEY, minScale);
		maxRotate = props.getIntProperty(MAX_ROTATE_KEY, maxRotate);

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
		int maxXDelta = width[0] - deltaAdjust;
		int maxYDelta = height[0] - deltaAdjust;
		maxDistance = (double) Math.sqrt(maxXDelta * maxXDelta + maxYDelta * maxYDelta);

		if (saveImages) {
			// unique directory for images for this run
			imageDir = props.getProperty("output.dir");
		}

		// generate random shapes
		shapes = new Path2D.Float[numShapesInLib];
		Line2D.Float line;
		Point P1 = new Point(), P2 = new Point();
		AffineTransform centreTransform = AffineTransform.getTranslateInstance(-(double) shapeSize / 2, -(double) shapeSize / 2);
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
				writeImage(image, imageDir + "shapes", "shape-" + s);
			}

			shapes[s].transform(centreTransform);
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
	 * @return maximum possible fitness value for this function
	 */
	public int getMaxFitnessValue() {
		return maxFitnessValue;
	}

	/**
	 * Initialise data for the current evaluation run (for each generation).
	 */
	public void initialiseEvaluation() {
		// generate trials
		stimuli = new double[numTrials][height[0]][width[0]];
		// targetCoords = new Point[numTrials];
		targetPresent = new boolean[numTrials];
		if (saveImages)
			stimuliImages = new BufferedImage[numTrials];

		Point pos = new Point();
		pos.x = width[0] / 2;
		pos.y = height[0] / 2;

		// logger.info("init eval");
		double minDistFactor = (double) Math.sqrt(2) * 2; // no overlap for square shapes
		for (int t = 0; t < numTrials; t++) {
			BufferedImage image = new BufferedImage(width[0], height[0], BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D canvas = image.createGraphics();
			// canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			/*
			 * //randomly place target pos.x = random.nextInt(width[0]-shapeSize+1); pos.y =
			 * random.nextInt(height[0]-shapeSize+1); drawShape(canvas, pos, target); targetCoords[t] = new Point(pos.x
			 * + shapeSize/2, pos.y + shapeSize/2); //assumes odd size
			 * 
			 * //randomly place other shapes so they don't overlap the target Path2D.Float shape; for (int s = 0; s <
			 * numNonTargetShapesShown; s++) { //select a non-target shape do { shape =
			 * shapes[random.nextInt(numShapesInLib)]; } while (shape == target);
			 * 
			 * //find somewhere to put it that doesn't overlap too much with the target int tries = 0; do { pos.x =
			 * random.nextInt(width[0]-shapeSize+1); pos.y = random.nextInt(height[0]-shapeSize+1);
			 * 
			 * tries++; if (tries > 1000) { if (minDistFactor > 1) { minDistFactor *= 0.9f; if (minDistFactor < 1)
			 * minDistFactor = 1; tries = 0; } else {
			 * logger.error("OR3: unable to generate trial image, couldn't find anywhere to place non-target image.");
			 * System.exit(1); } } } while (targetCoords[t].distance(pos) < shapeSize * minDistFactor);
			 * 
			 * drawShape(canvas, pos, shape); }
			 */

			Path2D.Float shape = target;
			targetPresent[t] = true;
			if (random.nextBoolean()) {
				do {
					shape = shapes[random.nextInt(numShapesInLib)];
				} while (shape == target);
				targetPresent[t] = false;
			}
			double scale = random.nextFloat() * (1 - minScale) + minScale;
			double rotate = random.nextFloat() * (double) Math.toRadians(maxRotate);
			AffineTransform transform = AffineTransform.getRotateInstance(rotate);
			transform.concatenate(AffineTransform.getScaleInstance(scale, scale));

			// pos.x = Math.round((1-scale)*shapeSize/2);
			// pos.y = Math.round((1-scale)*shapeSize/2);
			drawShape(canvas, pos, (Path2D.Float) shape.createTransformedShape(transform));

			// draw image on NN input
			Raster raster = image.getData();
			for (int yi = 0; yi < height[0]; yi++) {
				for (int xi = 0; xi < width[0]; xi++) {
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

	protected int evaluate(Chromosome genotype, Activator activator, int threadIndex) {
		GridNet substrate = (GridNet) activator;
		double[][][] responses = substrate.nextSequence(stimuli);
		/*
		 * double avgDist = 0; double avgInvDist = 0; double percentCorrect = 0; double wsose = 0; for (int t = 0; t <
		 * numTrials; t++) { double targetOutputError = 0; //for wsoe double nonTargetOutputError = 0; //for wsoe
		 * 
		 * Point highest = new Point(0, 0); for (int y = 0; y < height[0]; y++) { for (int x = 0; x < width[0]; x++) {
		 * //find output with highest response if (responses[t][y][x] > responses[t][highest.y][highest.x])
		 * highest.setLocation(x, y);
		 * 
		 * //calculate wsose error if (y == targetCoords[t].y && x == targetCoords[t].x) targetOutputError = (double)
		 * Math.pow(1 - responses[t][y][x], 2); else nonTargetOutputError += (double) Math.pow(responses[t][y][x], 2); }
		 * }
		 * 
		 * avgDist += targetCoords[t].distance(highest); avgInvDist += 1 / (targetCoords[t].distance(highest) + 1);
		 * percentCorrect += targetCoords[t].equals(highest) ? 1 : 0; wsose += (targetOutputError +
		 * (nonTargetOutputError / (width[0]*height[0] - 1))) / 2; } avgDist /= numTrials; avgInvDist /= numTrials;
		 * percentCorrect /= numTrials; wsose /= numTrials;
		 * 
		 * 
		 * //calculate fitness according to fitness function type weightings double fitness = fitnessWeightPC *
		 * percentCorrect + fitnessWeightWSOSE * (1 - wsose) + fitnessWeightDist * (1 - (avgDist / maxDistance)) +
		 * fitnessWeightInvDist * avgInvDist; fitness /= fitnessWeightPC + fitnessWeightWSOSE + fitnessWeightDist +
		 * fitnessWeightInvDist;
		 */

		double fitness = 0;
		double percentCorrect = 0;
		for (int t = 0; t < numTrials; t++) {
			double target = targetPresent[t] ? 1 : 0;
			double error = Math.abs(target - responses[t][0][0]);
			fitness += Math.pow(1 - error, 2); // take square root of error

			if (error < 0.5)
				percentCorrect++;
		}
		fitness /= numTrials;
		percentCorrect /= numTrials;

		double performance;

		if (perfMetric.equals(FITNESS_WEIGHT_PC_KEY))
			performance = percentCorrect;
		// else if (perfMetric.equals(FITNESS_WEIGHT_WSOSE_KEY))
		// genotype.setPerformanceValue(wsose);
		// else if (perfMetric.equals(FITNESS_WEIGHT_DIST_KEY))
		// genotype.setPerformanceValue(avgDist);
		// else if (perfMetric.equals(FITNESS_WEIGHT_INV_DIST_KEY))
		// genotype.setPerformanceValue(avgInvDist);
		else
			performance = fitness;

		genotype.setPerformanceValue(performance);

		double nextNoteworthyFitnessFactor = 0.01f;
		double nextNoteworthyFitness = bestFitnessSoFar + (1 - bestFitnessSoFar) * nextNoteworthyFitnessFactor;
		boolean saveImagesNow = saveImages && ((((targetPerformanceType == 1 && performance >= bestPerformanceSoFar + 0.01f) || (targetPerformanceType == 0 && performance <= bestPerformanceSoFar - 0.01f)) || fitness >= nextNoteworthyFitness) || (lastBestChrom == genotype && ((targetPerformanceType == 1 && lastBestPerformance >= scalePerformance) || (targetPerformanceType == 0 && lastBestPerformance <= scalePerformance))) || !printedFirst);

		if ((targetPerformanceType == 1 && performance >= bestPerformanceSoFar + 0.01f) || (targetPerformanceType == 0 && performance <= bestPerformanceSoFar - 0.01f))
			bestPerformanceSoFar = performance;
		if (fitness >= nextNoteworthyFitness) {
			bestFitnessSoFar = fitness;
			System.out.println("next noteworthy fitness: " + (bestFitnessSoFar + (1 - bestFitnessSoFar) * nextNoteworthyFitnessFactor));
		}
		if (percentCorrect > bestPCSoFar)
			bestPCSoFar = percentCorrect;

		if (saveImagesNow) {
			System.out.println("saving images for " + genotype.getId() + ", performance: " + performance + ", fitness: " + fitness);

			printedFirst = true;

			double weightRange = connectionWeightMax - connectionWeightMin;
			int connectionRange = getConnectionRange();

			// Generate image for weights
			BufferedImage[] weightImage = new BufferedImage[depth - 1];
			int xOffset = 0, yOffset = 0;
			int imageWeightLayerMaxWidth = 0;
			int imageWeightLayerTotalHeight = 0;
			for (int tz = 1; tz < depth; tz++) { // tz-1 is source layer
				int imageWidth = width[tz] * (width[tz - 1] * imageScaleWeights[tz - 1] + imageSpacing / 2) - imageSpacing / 2;
				int imageHeight = height[tz] * (height[tz - 1] * imageScaleWeights[tz - 1] + imageSpacing / 2) - imageSpacing / 2;

				imageWeightLayerMaxWidth = Math.max(imageWeightLayerMaxWidth, imageWidth);
				imageWeightLayerTotalHeight += imageHeight + imageSpacing;

				weightImage[tz - 1] = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = weightImage[tz - 1].createGraphics();

				g.setColor(new Color(0, 0, 127));
				g.fillRect(0, 0, imageWidth, imageHeight);

				for (int ty = 0; ty < height[tz]; ty++) {
					for (int tx = 0; tx < width[tz]; tx++) {
						xOffset = tx * (width[tz - 1] * imageScaleWeights[tz - 1] + imageSpacing / 2);
						yOffset = ty * (height[tz - 1] * imageScaleWeights[tz - 1] + imageSpacing / 2);

						// initialise to 0 weight value color for case where layers are not fully connected
						// g.setColor(new Color(127, 127, 127));
						// g.fill(new Rectangle(xOffset, yOffset, width[tz-1] * imageScaleWeights[tz-1], height[tz-1] *
						// imageScaleWeights[tz-1]));

						// calculate dimensions of this weight target matrix (bounded by grid edges)
						int dy = Math.min(height[tz - 1] - 1, ty + connectionRange) - Math.max(0, ty - connectionRange) + 1;
						int dx = Math.min(width[tz - 1] - 1, tx + connectionRange) - Math.max(0, tx - connectionRange) + 1;
						double[][] w = substrate.getWeights()[tz - 1][ty][tx][0];

						for (int wy = 0, sy = Math.max(0, ty - connectionRange); wy < dy; wy++, sy++) {
							for (int wx = 0, sx = Math.max(0, tx - connectionRange); wx < dx; wx++, sx++) {
								int color = (int) (((w[wy][wx] - connectionWeightMin) / weightRange) * 255);
								g.setColor(new Color(color, color, color));
								g.fillRect(xOffset + sx * imageScaleWeights[tz - 1], yOffset + sy * imageScaleWeights[tz - 1], imageScaleWeights[tz - 1], imageScaleWeights[tz - 1]);
								// if weight value is negative indicate with a black dot
								if (w[wy][wx] < 0) {
									g.setColor(Color.black);
									g.fillRect(xOffset + sx * imageScaleWeights[tz - 1] + imageScaleWeights[tz - 1] / 2 - imageNegDotSize / 2, yOffset + sy * imageScaleWeights[tz - 1] + imageScaleWeights[tz - 1] / 2 - imageNegDotSize / 2, imageNegDotSize, imageNegDotSize);
								}
							}
						}
					}
				}
			}
			imageWeightLayerTotalHeight -= imageSpacing;

			int imageWidth = imageWeightLayerMaxWidth + imageSpacing * 2; // add border
			int imageHeight = imageWeightLayerTotalHeight + imageSpacing * 2;

			BufferedImage output = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = output.createGraphics();
			g.setColor(new Color(0, 0, 127));
			g.fillRect(0, 0, imageWidth, imageHeight);

			yOffset = imageSpacing;
			for (int layer = 0; layer < depth - 1; layer++) {
				g.drawImage(weightImage[layer], imageSpacing, yOffset, null);
				yOffset += weightImage[layer].getHeight() + imageSpacing;
			}

			writeImage(output, imageDir + "networks" + File.separatorChar + properties.getEvolver().getGeneration() + "-" + scaleCount + "-" + genotype.getId() + "-" + percentCorrect, "weights");

			// Generate image for activation levels for some trials
			for (int t = 0; t < 25; t++) {
				// individually reapply stimuli so we can capture activation values for all layers
				substrate.next(stimuli[t]);
				double[][][] activation = substrate.getActivation();
				BufferedImage[] activationImage = new BufferedImage[depth];
				int imageActivationLayerMaxWidth = 0;
				int imageActivationLayerTotalHeight = 0;
				for (int layer = 0; layer < depth; layer++) {
					imageWidth = width[layer] * imageScaleActivation[layer];
					imageHeight = height[layer] * imageScaleActivation[layer];

					imageActivationLayerMaxWidth = Math.max(imageActivationLayerMaxWidth, imageWidth);
					imageActivationLayerTotalHeight += imageHeight + imageSpacing;

					activationImage[layer] = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_BYTE_GRAY);
					Graphics2D canvas = activationImage[layer].createGraphics();

					for (int y = 0; y < height[layer]; y++) {
						for (int x = 0; x < width[layer]; x++) {
							int color = (int) (activation[layer][y][x] * 255); // assumes output range is [0, 1]
							canvas.setColor(new Color(color, color, color));
							canvas.fillRect(x * imageScaleActivation[layer], y * imageScaleActivation[layer], imageScaleActivation[layer], imageScaleActivation[layer]);
						}
					}
				}
				imageActivationLayerTotalHeight -= imageSpacing;

				imageWidth = imageActivationLayerMaxWidth + imageSpacing * 2; // add border
				imageHeight = imageActivationLayerTotalHeight + imageSpacing * 2;

				output = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
				g = output.createGraphics();
				g.setColor(new Color(0, 0, 127));
				g.fillRect(0, 0, imageWidth, imageHeight);

				yOffset = imageSpacing;
				// add activation images
				for (int layer = 0; layer < depth; layer++) {
					g.drawImage(activationImage[layer], imageSpacing, yOffset, null);
					yOffset += activationImage[layer].getHeight() + imageSpacing;
				}

				writeImage(output, imageDir + "networks" + File.separatorChar + properties.getEvolver().getGeneration() + "-" + scaleCount + "-" + genotype.getId() + "-" + percentCorrect, "activation-" + t);
			}
		}

		return (int) Math.round(fitness * maxFitnessValue);
	}

	protected void scale(int scaleCount, int scaleFactor) {
		// get ratio of shape size to image size (this should be maintained during scale).
		double ratioW[] = new double[depth];
		double ratioH[] = new double[depth];
		for (int l = 0; l < width.length - 1; l++) {
			ratioW[l] = (double) width[l] / shapeSize;
			ratioH[l] = (double) height[l] / shapeSize;
		}

		// System.out.println(ratioW + ", " + ratioH);

		// adjust shape size
		if (scaleFactor % 2 == 0 && shapeSize % 2 == 1) // if scaleFactor is even but shapeSize is odd
			shapeSize = (shapeSize / 2) * scaleFactor * 2 + 1; // preserve oddness of conn range
		else
			shapeSize *= scaleFactor;

		String layerSizeString = "";
		for (int l = 0; l < depth; l++) {
			width[l] = (int) Math.max(1, Math.round(shapeSize * ratioW[l]));
			height[l] = (int) Math.max(1, Math.round(shapeSize * ratioH[l]));

			layerSizeString += width[l] + "x" + height[l] + ", ";
		}
		// connectionRange = shapeSize/2;
		connectionRange = shapeSize;

		AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
		for (int s = 0; s < numShapesInLib; s++)
			shapes[s].transform(scaleTransform);

		logger.info("Scale performed: layer sizes: " + layerSizeString + "shape size: " + shapeSize + ", conn range: " + connectionRange);
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
