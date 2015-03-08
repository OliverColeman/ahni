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
 * created by Philip Tucker on Feb 16, 2003
 */
package com.anji.neat;

import org.jgapcustomised.Gene;

/**
 * Gene corresponding to NEAT connection gene according to <a
 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf"> Evolving Neural Networks through Augmenting
 * Topologies </a>
 * 
 * @author Philip Tucker
 */
public class ConnectionGene extends Gene {
	private Long srcNeuronId = null;

	private Long destNeuronId = null;

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append(getInnovationId()).append(": ").append(srcNeuronId).append("->").append(destNeuronId);
		return result.toString();
	}

	/**
	 * for hibernate
	 */
	private ConnectionGene() {
		super();
	}

	/**
	 * Construct new ConnectionGene with given src, destination, and ID. Protected since this should only be constructed
	 * via factory methods in <code>ConnectionGene</code> and <code>NeatChromosomeUtility</code>
	 * 
	 * @param anInnovationId
	 * @param aSrcNeuronId
	 * @param aDestNeuronId
	 */
	public ConnectionGene(Long anInnovationId, Long aSrcNeuronId, Long aDestNeuronId) {
		super(anInnovationId);
		srcNeuronId = aSrcNeuronId;
		destNeuronId = aDestNeuronId;
	}

	/**
	 * @return innovation ID of destination neuron
	 */
	public Long getDestNeuronId() {
		return destNeuronId;
	}

	/**
	 * @return innovation ID of source neuron
	 */
	public Long getSrcNeuronId() {
		return srcNeuronId;
	}
}
