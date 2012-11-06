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
 * created by Philip Tucker on Feb 22, 2003
 */
package com.anji.neat;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.anji.util.Properties;

/**
 * Extension of JGAP configuration with NEAT-specific features added.
 * 
 * @author Philip Tucker
 */
public class NeatIdMap {

	private static final Logger logger = Logger.getLogger(NeatIdMap.class);

	/**
	 * properties key, file containing mappings of NEAT innovation IDs
	 */
	public final static String NEAT_ID_MAP_FILE_KEY = "neat.id.file";

	/**
	 * base XML tag for NEAT ID mapping
	 */
	public final static String NEAT_ID_MAP_XML_TAG = "neat_id_map";

	/**
	 * XML tag for NEAT ID mapping, neuron ID
	 */
	public final static String NEURON_ID_MAP_XML_TAG = "neuron_id_map";

	/**
	 * XML tag for NEAT ID mapping,connection ID
	 */
	public final static String CONNECTION_ID_MAP_XML_TAG = "connection_id_map";

	/**
	 * XML tag for NEAT ID mapping, neuron
	 */
	public final static String CONNECTION_TO_NEURON_XML_TAG = "neuron";

	/**
	 * XML tag for NEAT ID mapping, innovation ID
	 */
	public final static String ID_XML_TAG = "id";

	/**
	 * XML tag for NEAT ID mapping, connection ID
	 */
	public final static String CONNECTION_ID_XML_TAG = "connection_id";

	/**
	 * XML tag for NEAT ID mapping, connection
	 */
	public final static String CONNECTION_TO_CONNECTION_XML_TAG = "connection";

	/**
	 * XML tag for NEAT ID mapping, source neuron ID
	 */
	public final static String SRC_NEURON_ID_XML_TAG = "src_neuron_id";

	/**
	 * XML tag for NEAT ID mapping, destination neuron ID
	 */
	public final static String DEST_NEURON_ID_XML_TAG = "dest_neuron_id";

	private Map connectionToNeuronId = new HashMap();

	private Map connectionToConnectionId = new HashMap();

	private String neatIdMapFileName = null;

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param newProps
	 * @see NeatIdMap#init(Properties)
	 */
	public NeatIdMap(Properties newProps) {
		super();
		init(newProps);
	}

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param props configuration parameters; newProps[SURVIVAL_RATE_KEY] should be < 0.50f
	 */
	private void init(Properties props) {
		neatIdMapFileName = props.getProperty(NEAT_ID_MAP_FILE_KEY, null);
	}

	/**
	 * @param connectionId
	 * @return return id of previous neuron, if any, that mutated on connection <code>connectionId</code>
	 */
	public Long findNeuronId(Long connectionId) {
		return (Long) connectionToNeuronId.get(connectionId);
	}

	/**
	 * @param srcNeuronId
	 * @param destNeuronId
	 * @return return id of previous connection, if any, that mutated from neuron <code>srcNeuronId</code> to neuron
	 *         <code>destNeuronId</code>
	 */
	public Long findConnectionId(Long srcNeuronId, Long destNeuronId) {
		return (Long) connectionToConnectionId.get(buildList(srcNeuronId, destNeuronId));
	}

	/**
	 * store mapping between connection <code>connectionId</code> and the neuron that replaced it via NEAT add neuron
	 * mutation, <code>newNeuronId</code>
	 * 
	 * @param connectionId
	 * @param newNeuronId
	 */
	protected void putNeuronId(Long connectionId, Long newNeuronId) {
		connectionToNeuronId.put(connectionId, newNeuronId);
	}

	/**
	 * store mapping between neurons <code>srcNeuronId</code> and <code>destNeuronId</code> and the connection that
	 * mutated between them via NEAT add connection mutation, <code>newConnectionId</code>
	 * 
	 * @param srcNeuronId
	 * @param destNeuronId
	 * @param newConnectionId
	 */
	protected void putConnectionId(Long srcNeuronId, Long destNeuronId, Long newConnectionId) {
		connectionToConnectionId.put(buildList(srcNeuronId, destNeuronId), newConnectionId);
	}

	/**
	 * creates list used us key between src/dest neuron pairs and connection
	 * 
	 * @param srcNeuronId
	 * @param destNeuronId
	 * @return List contains Long objects
	 */
	protected List buildList(Long srcNeuronId, Long destNeuronId) {
		List result = new ArrayList();
		result.add(srcNeuronId);
		result.add(destNeuronId);
		return result;
	}

	/**
	 * Load ID factories and maps.
	 * 
	 * @throws IOException
	 */
	public void load() throws IOException {
		if (neatIdMapFileName != null) {
			FileInputStream in = null;
			try {
				in = new FileInputStream(neatIdMapFileName);
				neatIdMapFromXml(in);
			} catch (Exception e) {
				// logger.info( "couldn't load NEAT ids from file, starting with new mapping" );

			} finally {
				if (in != null) {
					in.close();
				}
			}
		}
	}

	/**
	 * Persist ID factories and maps.
	 * 
	 * @return true if file is stored
	 * @throws IOException
	 */
	public boolean store() throws IOException {
		System.out.println("here");
		if (neatIdMapFileName != null) {
			FileWriter out = null;
			try {
				out = new FileWriter(neatIdMapFileName);
				out.write(toXml());
				out.flush();
				return true;
			} finally {
				if (out != null)
					out.close();
			}
		}
		return false;
	}

	/**
	 * Convert NEAT ID mappings (enables re-use of innovation IDs) to XML string.
	 * 
	 * @return XML string
	 */
	public String toXml() {
		StringBuffer result = new StringBuffer();
		result.append("<").append(NEAT_ID_MAP_XML_TAG).append(">\n");

		result.append("<").append(NEURON_ID_MAP_XML_TAG).append(">\n");
		Iterator iter = connectionToNeuronId.keySet().iterator();
		while (iter.hasNext()) {
			Long connId = (Long) iter.next();
			Long neuronId = (Long) connectionToNeuronId.get(connId);
			result.append("<").append(CONNECTION_TO_NEURON_XML_TAG).append(" ");
			result.append(ID_XML_TAG).append("=\"").append(neuronId).append("\" ");
			result.append(CONNECTION_ID_XML_TAG).append("=\"").append(connId).append("\" />");
		}
		result.append("</").append(NEURON_ID_MAP_XML_TAG).append(">\n");

		result.append("<").append(CONNECTION_ID_MAP_XML_TAG).append(">\n");
		iter = connectionToConnectionId.keySet().iterator();
		while (iter.hasNext()) {
			List key = (List) iter.next();
			Long srcNeuronId = (Long) key.get(0);
			Long destNeuronId = (Long) key.get(1);
			Long connId = (Long) connectionToConnectionId.get(key);
			result.append("<").append(CONNECTION_TO_CONNECTION_XML_TAG).append(" ");
			result.append(ID_XML_TAG).append("=\"").append(connId).append("\" ");
			result.append(SRC_NEURON_ID_XML_TAG).append("=\"").append(srcNeuronId).append("\" ");
			result.append(DEST_NEURON_ID_XML_TAG).append("=\"").append(destNeuronId).append("\" />");
		}
		result.append("</").append(CONNECTION_ID_MAP_XML_TAG).append(">\n");

		result.append("</").append(NEAT_ID_MAP_XML_TAG).append(">\n");
		return result.toString();
	}

	/**
	 * Load innovation ID map from <code>in</code> XML
	 * 
	 * @param in
	 * @throws Exception
	 */
	private void neatIdMapFromXml(InputStream in) throws Exception {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc = builder.parse(in);

		Node node = doc.getFirstChild();
		if (NEAT_ID_MAP_XML_TAG.equals(node.getNodeName()) == false)
			throw new IllegalArgumentException("tag != " + NEAT_ID_MAP_XML_TAG);

		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			Node child = children.item(i);
			if (NEURON_ID_MAP_XML_TAG.equals(child.getNodeName()))
				neuronIdMapFromXml(child);
			else if (CONNECTION_ID_MAP_XML_TAG.equals(child.getNodeName()))
				connectionIdMapFromXml(child);
		}
	}

	/**
	 * Load neuron innovation ID mapping from <code>xml</code> node.
	 * 
	 * @param xml
	 */
	private void neuronIdMapFromXml(Node xml) {
		NodeList children = xml.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			Node child = children.item(i);
			if (CONNECTION_TO_NEURON_XML_TAG.equals(child.getNodeName())) {
				NamedNodeMap attrs = child.getAttributes();
				if (attrs == null)
					throw new IllegalArgumentException("missing attributes");
				Node neuronIdAttr = attrs.getNamedItem(ID_XML_TAG);
				if (neuronIdAttr == null)
					throw new IllegalArgumentException("missing neuron id");
				Node connIdAttr = attrs.getNamedItem(CONNECTION_ID_XML_TAG);
				if (connIdAttr == null)
					throw new IllegalArgumentException("missing connection id");
				putNeuronId(Long.valueOf(connIdAttr.getNodeValue()), Long.valueOf(neuronIdAttr.getNodeValue()));
			}
		}
	}

	/**
	 * Load connection innovation ID mapping from <code>xml</code> node.
	 * 
	 * @param xml
	 */
	private void connectionIdMapFromXml(Node xml) {
		NodeList children = xml.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			Node child = children.item(i);
			if (CONNECTION_TO_CONNECTION_XML_TAG.equals(child.getNodeName())) {
				NamedNodeMap attrs = child.getAttributes();
				if (attrs == null)
					throw new IllegalArgumentException("missing attributes");
				Node connIdAttr = attrs.getNamedItem(ID_XML_TAG);
				if (connIdAttr == null)
					throw new IllegalArgumentException("missing connection id");
				Node srcNeuronIdAttr = attrs.getNamedItem(SRC_NEURON_ID_XML_TAG);
				if (srcNeuronIdAttr == null)
					throw new IllegalArgumentException("missing src neuron id");
				Node destNeuronIdAttr = attrs.getNamedItem(DEST_NEURON_ID_XML_TAG);
				if (destNeuronIdAttr == null)
					throw new IllegalArgumentException("missing dest neuron id");
				putConnectionId(Long.valueOf(srcNeuronIdAttr.getNodeValue()), Long.valueOf(destNeuronIdAttr.getNodeValue()), Long.valueOf(connIdAttr.getNodeValue()));
			}
		}
	}

	/**
	 * log stats for id maps
	 * 
	 * @param aLogger
	 * @param pri
	 */
	public void log(Logger aLogger, Priority pri) {
		// aLogger.log( pri, "connection->neuron id map size == " + connectionToNeuronId.size() );
		// aLogger.log( pri, "neurons->connection id map size == " + connectionToConnectionId.size() );
	}
}
