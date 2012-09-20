/*
 * Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of ANJI (Another NEAT Java Implementation).
 * 
 * ANJI is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Created on Mar 27, 2005 by Philip Tucker
 */
package org.jgapcusomised;

/**
 * @author Philip Tucker
 */
public class SpeciationParms {

/**
 * default ctor
 */
public SpeciationParms() {
	// no-op
}

private final static float DEFAULT_COMPATIBILITY_EXCESS_COEFF = 1;

private final static float DEFAULT_COMPATIBILITY_DISJOINT_COEFF = 1;

private final static float DEFAULT_COMPATIBILITY_COMMON_COEFF = 0.4f;

/**
 * default speciation threshold
 */
public final static float DEFAULT_SPECIATION_THRESHOLD = 3;

public final static int DEFAULT_SPECIATION_TARGET = 0; //don't try to maintain a specific number of species

public final static float DEFAULT_SPECIATION_THRESHOLD_CHANGE = 0.1f;


private float compatExcessCoeff = DEFAULT_COMPATIBILITY_EXCESS_COEFF;

private float compatDisjointCoeff = DEFAULT_COMPATIBILITY_DISJOINT_COEFF;

private float compatCommonCoeff = DEFAULT_COMPATIBILITY_COMMON_COEFF;

private float speciationThreshold = DEFAULT_SPECIATION_THRESHOLD;

private int speciationTarget = DEFAULT_SPECIATION_TARGET;

private float speciationThresholdChange = DEFAULT_SPECIATION_THRESHOLD_CHANGE;

/**
 * @return float coefficient for species compatibility based on common genes; see <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section 3.3 of primary NEAT
 * paper </a> for details.
 */
public float getSpecieCompatCommonCoeff() {
	return compatCommonCoeff;
}

/**
 * @return float coefficient for species compatibility based on disjoint genes; see <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section 3.3 of primary NEAT
 * paper </a> for details.
 */
public float getSpecieCompatDisjointCoeff() {
	return compatDisjointCoeff;
}

/**
 * @return float coefficient for species compatibility based on excess genes; see <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section 3.3 of primary NEAT
 * paper </a> for details.
 */
public float getSpecieCompatExcessCoeff() {
	return compatExcessCoeff;
}

/**
 * @param d coefficient for species compatibility based on common genes; see <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section 3.3 of primary NEAT
 * paper </a> for details.
 */
public void setSpecieCompatCommonCoeff( float d ) {
	compatCommonCoeff = d;
}

/**
 * @param d coefficient for species compatibility based on disjoint genes; see <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section 3.3 of primary NEAT
 * paper </a> for details.
 */
public void setSpecieCompatDisjointCoeff( float d ) {
	compatDisjointCoeff = d;
}

/**
 * @param d coefficient for species compatibility based on excess genes; see <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section 3.3 of primary NEAT
 * paper </a> for details.
 */
public void setSpecieCompatExcessCoeff( float d ) {
	compatExcessCoeff = d;
}

/**
 * @return threshold below which the difference between 2 chromosomes dictates they are in the
 * same species; see <a href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section
 * 3.3 of primary NEAT paper </a> for details.
 */
public float getSpeciationThreshold() {
	return speciationThreshold;
}

/**
 * @param d threshold below which the difference between 2 chromosomes dictates they are in the
 * same species; see <a href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">section
 * 3.3 of primary NEAT paper </a> for details.
 */
public void setSpeciationThreshold( float d ) {
	speciationThreshold = d;
}

/**
 * @return target number of species to try and maintain by altering speciation threshold.
 */
public int getSpeciationTarget() {
	return speciationTarget;
}

/**
 * @param d target number of species to try and maintain by altering speciation threshold.
 */
public void setSpeciationTarget( int d ) {
	speciationTarget = d;
}


/**
 * @return amount to change speciation threshold to maintain speciation target.
 */
public float getSpeciationThresholdChange() {
	return speciationThresholdChange;
}

/**
 * @param d amount to change speciation threshold to maintain speciation target.
 */
public void setSpeciationThresholdChange( float d ) {
	speciationThresholdChange = d;
}

}
