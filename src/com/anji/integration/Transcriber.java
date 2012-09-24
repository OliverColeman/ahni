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
 * created by Philip Tucker on Mar 9, 2003
 * 
 * Modified by Oliver Coleman in May 2010:
 * added generics and transcribe(Chromosome c, T substrate) method.
 */
package com.anji.integration;

import org.jgapcustomised.Chromosome;

/**
 * To "transcribe" is to construct a phenotype from a genotype.
 * 
 * @author Philip Tucker
 */
public interface Transcriber<T> {

/**
 * Sub-classes must implement this method to convert the genotype to a phenotype.
 * @param c chromosome to transcribe
 * @return phenotype
 * @throws TranscriberException
 */
public T transcribe(Chromosome c) throws TranscriberException;

/**
 * Sub-classes must implement this method to convert the genotype to a phenotype,
 * using an existing substrate object to allow performance gains by avoiding
 * destroying and creating large objects or arrays.
 * @param c chromosome to transcribe
 * @param substrate An existing phenotype substrate to reuse
 * @return phenotype
 * @throws TranscriberException
 */
public T transcribe(Chromosome c, T substrate) throws TranscriberException;

/**
 * @return class of phenotype returned by <code>transcribe()</code>
 */
public Class getPhenotypeClass();
}
