package com.ojcoleman.ahni.evaluation.novelty;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.jgapcustomised.Chromosome;

import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * Representation of a behaviour as real-valued vector. All values in the vector should be in the range [0, 1].
 */
public class RealVectorBehaviour extends Behaviour {
	public static final NumberFormat nf = new DecimalFormat("0.000");
	public ArrayRealVector p;
	double maxDist;
	
	public RealVectorBehaviour(ArrayRealVector p) {
		this.p = p;
		//maxDist = Math.sqrt(p.getDimension());
		maxDist = p.getDimension();
		assert p.getMaxValue() <= 1 && p.getMinValue() >= 0 : "Values in RealVectorBehaviour must be in the range [0, 1] but " + p + " was given.";
	}
	
	@Override
	public double distanceFrom(Behaviour b) {
		return p.getL1Distance(((RealVectorBehaviour) b).p) / maxDist;
	}
	
	@Override
	public String toString() {
		return ArrayUtil.toString(p.getDataRef(), "  ", nf);
	}

	@Override
	public double defaultThreshold() {
		return 1.0 / p.getDimension();
	}
	
	@Override
	public void renderArchive(List<Behaviour> archive, String fileName) {
		if (archive.isEmpty()) return;
		
		int dims = ((RealVectorBehaviour) archive.get(0)).p.getDimension();
		
		// If only 2 dimensions render as scatter plot.
		if (dims == 2) {
			int imageSize = 254;
			BufferedImage image = new BufferedImage(imageSize + 2, imageSize + 2, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g = image.createGraphics();
			
			g.setColor(Color.WHITE);
			for (Behaviour b : archive) {
				RealVectorBehaviour brv = (RealVectorBehaviour) b;
				g.fillRect((int) Math.round(brv.p.getEntry(0) * imageSize), (int) Math.round(brv.p.getEntry(1) * imageSize), 1, 1);
			}
			File outputfile = new File(fileName);
			try {
				ImageIO.write(image, "png", outputfile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else { // Render as intensity plot.
			int imageScale = 1;
			int size = archive.size();
			BufferedImage image = new BufferedImage(size * imageScale, dims * imageScale, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g = image.createGraphics();

			for (int i = 0; i < size; i++) {
				RealVectorBehaviour brv = (RealVectorBehaviour) archive.get(i);
				for (int j = 0; j < dims; j++) {
					float c = (float) brv.p.getEntry(j);
					g.setColor(new Color(c, c, c));
					g.fillRect(i * imageScale, j * imageScale, imageScale, imageScale);
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
}
