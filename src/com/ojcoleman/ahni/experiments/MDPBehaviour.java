package com.ojcoleman.ahni.experiments;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.jgapcustomised.BulkFitnessFunction;

import com.ojcoleman.ahni.evaluation.novelty.Behaviour;
import com.ojcoleman.ahni.experiments.MDP.Environment;

/**
 * Behaviour for novelty search for MDP environment. The sequence of states visited is recorded and the difference
 * (novelty) between two agents is calculated as a factor of the number of the same states visited from the
 * beginning of a trial before the two agents diverge. TODO If the environment is non-deterministic then this
 * approach doesn't make sense, it would make more sense to compare what action(s) an agent performs in a given
 * state.
 */
class MDPBehaviour extends Behaviour {
	public int[][][] p;
	double maxDist;

	public MDPBehaviour(MDP mdp, int[][][] p) {
		this.p = p;
		maxDist = p.length;
	}

	@Override
	public double distanceFrom(Behaviour b) {
		int[][][] bp = ((MDPBehaviour) b).p;
		assert p.length == bp.length && p[0].length == bp[0].length && p[0][0].length == bp[0][0].length;
		double diff = 0;
		int envCount = p.length;
		int trialCount = p[0].length;
		int stepsPerTrial = p[0][0].length;
		for (int env = 0; env < envCount; env++) {
			for (int trial = 0; trial < trialCount; trial++) {
				int step = 0;
				for (step = 0; step < stepsPerTrial; step++) {
					if (p[env][trial][step] != bp[env][trial][step])
						break;
				}
				diff += (double) (stepsPerTrial - step) / stepsPerTrial;
			}
		}
		diff /= envCount * trialCount;
		
		return diff;
	}

	@Override
	public String toString() {
		return Arrays.deepToString(p);
	}

	@Override
	public double defaultThreshold() {
		return 1.0 / p.length * p[0].length * p[0][0].length;
	}

	@Override
	public void renderArchive(List<Behaviour> archive, String fileName, BulkFitnessFunction fitnessFunction) {
		if (archive.isEmpty())
			return;
		
		MDP mdp  = (MDP) fitnessFunction;
		MDPBehaviour brv = (MDPBehaviour) archive.get(0);
		int envCount = brv.p.length;
		int trialCount = brv.p[0].length;
		int stepsPerTrial = brv.p[0][0].length;

		int height = envCount * trialCount * stepsPerTrial + envCount * trialCount;
		int imageScale = 1;
		int size = archive.size();
		BufferedImage image = new BufferedImage(size * imageScale, (height + (envCount-1) + (trialCount - 1)) * imageScale, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = image.createGraphics();

		for (int i = 0; i < size; i++) {
			brv = (MDPBehaviour) archive.get(i);
			int y = 0;
			for (int env = 0; env < envCount; env++) {
				for (int trial = 0; trial < trialCount; trial++) {
					for (int step = 0; step < stepsPerTrial; step++) {
						float c = (float) brv.p[env][trial][step] / mdp.stateCountMax;
						//g.setColor(Color.getHSBColor(c, 1f, c * 0.7f + 0.3f));
						g.setColor(Color.getHSBColor(c, 1f, 1f));
						g.fillRect(i * imageScale, y * imageScale, imageScale, imageScale);
						y++;
					}
					y++;
				}
				y++;
			}
		}
		File outputfile = new File(fileName);
		try {
			ImageIO.write(image, "png", outputfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}