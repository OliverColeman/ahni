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
 * Created on Feb 27, 2004 by Philip Tucker
 */
package com.anji.nn;

/**
 * Abstract connection to carry data into neurons.
 * 
 * @author Philip Tucker
 */
public interface Connection {

	/**
	 * base XML tag
	 */
	public final static String XML_TAG = "connection";

	/**
	 * @return double value carried on this connection from incoming neuron
	 */
	public double read();

	/**
	 * @return String representation of object
	 */
	public String toXml();

	/**
	 * @return number corresponding to cost of activation in resources
	 */
	public long cost();
}
