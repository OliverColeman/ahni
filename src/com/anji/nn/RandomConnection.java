/*
 * Copyright (C) 2004  Derek James and Philip Tucker
 *
 * This file is part of ANJI (Another NEAT Java Implementation).
 *
 * ANJI is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * created by Philip Tucker on Jun 12, 2004
 */

package com.anji.nn;

import java.util.Random;

/**
 * @author Philip Tucker
 */
public class RandomConnection implements Connection {

	private Random rand = new Random();

	private RandomConnection() {
		// no-op
	}

	private static RandomConnection instance;

	/**
	 * @param aRand new random generator
	 */
	public void setRand(Random aRand) {
		rand = aRand;
	}

	/**
	 * @return singleton instance
	 */
	public static RandomConnection getInstance() {
		if (instance == null)
			instance = new RandomConnection();
		return instance;
	}

	/**
	 * @see com.anji.nn.Connection#read()
	 */
	public double read() {
		return rand.nextFloat();
	}

	/**
	 * @see com.anji.nn.Connection#toXml()
	 */
	public String toXml() {
		StringBuffer result = new StringBuffer();
		result.append("<").append(Connection.XML_TAG);
		result.append("\" from-input=\"bias\" />");

		return result.toString();
	}

	/**
	 * @see com.anji.nn.Connection#cost()
	 */
	public long cost() {
		return 222;
	}

}
