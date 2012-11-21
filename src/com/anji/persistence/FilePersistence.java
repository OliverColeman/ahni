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
 * created by Philip Tucker on May 17, 2003
 */
package com.anji.persistence;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.Configuration;
import org.jgapcustomised.Genotype;
import org.jgapcustomised.Species;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.anji.integration.Activator;
import com.anji.integration.Generation;
import com.anji.integration.XmlPersistableChromosome;
import com.anji.integration.XmlPersistableAllele;
import com.anji.integration.XmlPersistableRun;
import com.anji.run.Run;
import com.anji.util.Properties;
import com.anji.util.XmlPersistable;

/**
 * Simple file-based implementation of persistence layer. All files are stored in a file named <code>baseDir</code>/
 * <code>type</code>/<code>type</code>+<code>key</code>. All objects stores must implement <code>XmlPersistable</code>.
 * <code>type</code> is derived from <code>XmlPersistable.getXmlRootTag()</code>, and <code>key</code> is derived from
 * <code>XmlPersistable.getId()</code>.
 * 
 * @author Philip Tucker
 */
public class FilePersistence implements Persistence {

	private final static Logger logger = Logger.getLogger(FilePersistence.class);

	/**
	 * properties key, base directory for persistence storage
	 */
	public final static String BASE_DIR_KEY = "persistence.base.dir";

	Properties props;

	private File baseDir = null;

	private String runId = null;

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param props configuration parameters
	 */
	public void init(Properties props) {
		this.props = props;
		String baseDirStr = props.getProperty(BASE_DIR_KEY);
		baseDir = new File(baseDirStr);
		baseDir.mkdirs();
		if (!baseDir.exists())
			throw new IllegalArgumentException("base directory does not exist: " + baseDirStr);
		if (!baseDir.isDirectory())
			throw new IllegalArgumentException("base directory is a file: " + baseDirStr);
		if (!baseDir.canWrite())
			throw new IllegalArgumentException("base directory not writable: " + baseDirStr);
	}

	/**
	 * @param type
	 * @param key
	 * @return streamed data of resource
	 * @throws IOException
	 */
	private InputStream loadStream(String type, String key) throws IOException {
		return new FileInputStream(fullPath(type, key));
	}

	private void storeXml(XmlPersistable xp) throws IOException {
		FileOutputStream out = null;

		try {
			out = new FileOutputStream(fullPath(xp.getXmlRootTag(), xp.getXmld()));
			out.write(xp.toXml().getBytes());
			out.close();
		} finally {
			if (out != null)
				out.close();
		}
	}

	/**
	 * Construct full path of file based on <code>type</code> and <code>key</code>.
	 * 
	 * @param type
	 * @param key
	 * @return String resulting path
	 */
	protected String fullPath(String type, String key) {
		StringBuffer result = new StringBuffer(baseDir.getAbsolutePath());
		result.append(File.separatorChar).append(type);

		File collectionDir = new File(result.toString());
		if (collectionDir.isDirectory() == false) {
			if (collectionDir.exists())
				throw new IllegalArgumentException(result.toString() + " is a file");
			collectionDir.mkdir();
		}

		result.append(File.separatorChar).append(type).append(key).append(".xml");
		return result.toString();
	}

	/**
	 * @see com.anji.persistence.Persistence#reset()
	 */
	public void reset() {
		reset(Priority.DEBUG);
	}

	private void reset(Priority pri) {
		File[] dirs = baseDir.listFiles();
		for (int i = 0; i < dirs.length; ++i) {
			File dir = dirs[i];
			if (dir.isDirectory()) {
				File[] files = dir.listFiles();
				for (int j = 0; j < files.length; ++j) {
					File file = files[j];
					// String msg = file.delete() ? "file deleted: " : "error deleting file: ";
					// msg += file.getAbsolutePath();
					// logger.log( pri, msg );
				}
			}
		}
	}

	private void deleteXml(String type, String key) throws Exception {
		File file = new File(fullPath(type, key));
		if (file.exists())
			file.delete();
	}

	/**
	 * @see com.anji.persistence.Persistence#store(org.jgapcustomised.Chromosome)
	 */
	public void store(Chromosome c) throws Exception {
		storeXml(new XmlPersistableChromosome(c));
	}

	/**
	 * @see com.anji.persistence.Persistence#store(com.anji.integration.Activator)
	 */
	public void store(Activator a) throws Exception {
		storeXml(a);
	}

	/**
	 * @see com.anji.persistence.Persistence#store(com.anji.run.Run)
	 */
	public void store(Run r) throws Exception {
		storeXml(new XmlPersistableRun(r));
	}

	/**
	 * @param config
	 * @param xml
	 * @return chromosome constructed from xml
	 * @throws Exception
	 */
	public static Chromosome chromosomeFromXml(Configuration config, String xml) throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes());
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = builder.parse(in);
		return chromosomeFromXml(config, doc.getFirstChild());
	}

	/**
	 * @param config
	 * @param chromNode
	 * @return chromosome constructed from xml
	 */
	public static Chromosome chromosomeFromXml(Configuration config, Node chromNode) {
		// TODO - refactor such that Configuration is not necessary parameter, and simplify
		// exceptions

		if (XmlPersistableChromosome.XML_CHROMOSOME_TAG.equals(chromNode.getNodeName()) == false)
			throw new IllegalArgumentException("node name not " + XmlPersistableChromosome.XML_CHROMOSOME_TAG);

		List genes = new ArrayList();
		NodeList geneNodes = chromNode.getChildNodes();
		for (int i = 0; i < geneNodes.getLength(); ++i) {
			Node geneNode = geneNodes.item(i);
			if (XmlPersistableAllele.NEURON_XML_TAG.equals(geneNode.getNodeName()))
				genes.add(XmlPersistableAllele.neuronFromXml(geneNode));
			else if (XmlPersistableAllele.CONN_XML_TAG.equals(geneNode.getNodeName()))
				genes.add(XmlPersistableAllele.connectionFromXml(geneNode));
		}

		Long id = null;
		Node idNode = chromNode.getAttributes().getNamedItem(XmlPersistableChromosome.XML_CHROMOSOME_ID_TAG);
		if (idNode != null) {
			String idStr = idNode.getNodeValue();
			if ((idStr != null) && (idStr.length() > 0))
				id = Long.valueOf(idStr);
		}

		Long primaryParentId = null;
		idNode = chromNode.getAttributes().getNamedItem(XmlPersistableChromosome.XML_CHROMOSOME_PRIMARY_PARENT_ID_TAG);
		if (idNode != null) {
			String idStr = idNode.getNodeValue();
			if ((idStr != null) && (idStr.length() > 0))
				primaryParentId = Long.valueOf(idStr);
		}

		Long secondaryParentId = null;
		idNode = chromNode.getAttributes().getNamedItem(XmlPersistableChromosome.XML_CHROMOSOME_SECONDARY_PARENT_ID_TAG);
		if (idNode != null) {
			String idStr = idNode.getNodeValue();
			if ((idStr != null) && (idStr.length() > 0))
				secondaryParentId = Long.valueOf(idStr);
		}

		ChromosomeMaterial material = new ChromosomeMaterial(genes, primaryParentId, secondaryParentId);
		return (id == null) ? new Chromosome(material, config.nextChromosomeId()) : new Chromosome(material, id);
	}

	/**
	 * @see com.anji.persistence.Persistence#loadChromosome(java.lang.String, org.jgapcustomised.Configuration)
	 */
	public Chromosome loadChromosome(String id, Configuration config) {
		try {
			InputStream in = loadStream(XmlPersistableChromosome.XML_CHROMOSOME_TAG, id);
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.parse(in);
			return chromosomeFromXml(config, doc.getFirstChild());
		} catch (FileNotFoundException e) {
			return null;
		} catch (Exception e) {
			String msg = "error loading chromosome " + id;
			logger.error(msg, e);
			throw new IllegalStateException(msg + ": " + e);
		}
	}

	/**
	 * @see com.anji.persistence.Persistence#deleteChromosome(java.lang.String)
	 */
	public void deleteChromosome(String id) throws Exception {
		deleteXml(XmlPersistableChromosome.XML_CHROMOSOME_TAG, id);
	}

	/**
	 * @see com.anji.persistence.Persistence#loadGenotype(org.jgapcustomised.Configuration)
	 */
	public Genotype loadGenotype(Configuration config) {
		try {
			InputStream in = loadStream(XmlPersistableRun.RUN_TAG, runId);
			return genotypeFromRunXml(in, config);
		} catch (FileNotFoundException e) {
			return null;
		} catch (Exception e) {
			String msg = "error loading run " + runId;
			logger.error(msg, e);
			throw new IllegalStateException(msg + ": " + e);
		}
	}

	/**
	 * Construct new <code>Genotype</code> object from population in specified run XML data.
	 * 
	 * @param runXml XML data from which to construct initial population
	 * @return new <code>Genotype</code>
	 * @throws Exception
	 */
	private Genotype genotypeFromRunXml(InputStream runXml, Configuration config) throws Exception {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = builder.parse(runXml, baseDir.getAbsolutePath());
		Node runNode = doc.getFirstChild();
		if (XmlPersistableRun.RUN_TAG.equals(runNode.getNodeName()) == false)
			throw new IllegalArgumentException("node name not " + XmlPersistableRun.RUN_TAG);

		// loop through list to find last generation
		Node generationNode = null;
		for (int i = 0; i < runNode.getChildNodes().getLength(); ++i) {
			Node nextNode = runNode.getChildNodes().item(i);
			if (Generation.GENERATION_TAG.equals(nextNode.getNodeName()))
				generationNode = nextNode;
		}

		return genotypeFromXml(props, generationNode, config, this);
	}

	/**
	 * Create a <code>Genotype</code> from XML.
	 * 
	 * @param generationNode XML
	 * @param config
	 * @param db persistence repository from which to read chromosomes
	 * @return new genotype
	 * @throws Exception
	 */
	private static Genotype genotypeFromXml(Properties props, Node generationNode, Configuration config, Persistence db) throws Exception {
		if (Generation.GENERATION_TAG.equals(generationNode.getNodeName()) == false)
			throw new IllegalArgumentException("node name not " + Generation.GENERATION_TAG);

		// loop through list to find chromosomes
		ArrayList chroms = new ArrayList();
		for (int generationChildIdx = 0; generationChildIdx < generationNode.getChildNodes().getLength(); ++generationChildIdx) {
			Node specieNode = generationNode.getChildNodes().item(generationChildIdx);
			if (Species.SPECIE_TAG.equals(specieNode.getNodeName())) {
				// for each specie ...
				NamedNodeMap specieAttrs = specieNode.getAttributes();
				if (specieAttrs == null)
					throw new IllegalArgumentException("missing specie attributes");

				// ... and loop through chromosomes
				for (int specieChildIdx = 0; specieChildIdx < specieNode.getChildNodes().getLength(); ++specieChildIdx) {
					Node chromNode = specieNode.getChildNodes().item(specieChildIdx);
					if (Species.CHROMOSOME_TAG.equals(chromNode.getNodeName())) {
						NamedNodeMap chromAttrs = chromNode.getAttributes();
						if (chromAttrs == null)
							throw new IllegalArgumentException("missing chromosome attributes");
						Node chromIdNode = chromAttrs.getNamedItem(Species.ID_TAG);
						if (chromIdNode == null)
							throw new IllegalArgumentException("missing chromosome id");

						// get id and load chromosome from persistence (skip if representative since its
						// already been added
						Long chromId = Long.valueOf(chromIdNode.getNodeValue());
						Chromosome c = db.loadChromosome(chromId.toString(), config);
						if (c != null)
							chroms.add(c);
						else
							logger.warn("chromosome in run not found: " + chromId);
					}
				}
			}
		}

		// don't return empty genotype
		if (chroms.size() <= 0)
			return null;

		// sort in order of id so that they will be added in proper order (age)
		Collections.sort(chroms);
		return new Genotype(props, config, chroms);
	}

	/**
	 * @see com.anji.persistence.Persistence#startRun(java.lang.String)
	 */
	public void startRun(String aRunId) {
		runId = aRunId;
	}

}
