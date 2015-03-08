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
 * Created on Apr 12, 2004 by Philip Tucker
 */
package com.anji.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.anji.Copyright;
import com.anji.neat.NeatConfiguration;
import com.anji.neat.NeatIdMap;
import com.anji.persistence.Persistence;

/**
 * Utility class to clear all persisted data, including chromosomes and runs in DB, innovation ID data, and log.
 * 
 * @author Philip Tucker
 */
public class Reset {

	private Properties props;

	private boolean userInteraction = true;

	/**
	 * @param propFilePath
	 * @throws IOException
	 * @see Reset#Reset(Properties)
	 */
	public Reset(String propFilePath) throws IOException {
		super();
		props = new Properties();
		props.loadFromFileWithoutLogging(propFilePath);
	}

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param someProps
	 */
	public Reset(Properties someProps) {
		super();
		props = someProps;
	}

	/**
	 * Interface to execute via command line.
	 * 
	 * @param args args[0] is properties file path
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		System.out.println(Copyright.STRING);
		if (args.length < 1) {
			System.err.println("usage: <cmd> <properties-file>");
			System.exit(-1);
		}
		Reset reset = new Reset(args[0]);
		reset.reset();
	}

	private boolean userResponse(String question) throws IOException {
		if (!userInteraction)
			return true;

		System.out.print(question + " ");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String response = reader.readLine();
		return response.toLowerCase().startsWith("y");
	}

	/**
	 * Clear all persisted data.
	 * 
	 * @throws IOException
	 */
	public void reset() throws IOException {
		boolean deleteAll = userResponse("Delete all?");

		// database, including runs and chromosomes
		if (deleteAll || userResponse("Delete database (including runs and chromosomes)?")) {
			Persistence db = (Persistence) props.singletonObjectProperty(Persistence.PERSISTENCE_CLASS_KEY);
			db.reset();
		}

		List fileNamesToDelete = new ArrayList();

		// id factory
		if (deleteAll || userResponse("Delete ID Factory?")) {
			String fileName = props.getProperty(NeatConfiguration.ID_FACTORY_KEY, null);
			if (fileName != null && fileName.length() > 0)
				fileNamesToDelete.add(fileName);
		}

		// Neat id map
		if (deleteAll || userResponse("Delete ID map?")) {
			String fileName = props.getProperty(NeatIdMap.NEAT_ID_MAP_FILE_KEY, null);
			if (fileName != null && fileName.length() > 0)
				fileNamesToDelete.add(fileName);
		}

		// log file
		if (deleteAll || userResponse("Delete log files?")) {
			// Set fileNames = props.getPropertiesForPattern( "log4j\\..*\\.File" );
			Set keys = props.getKeysForPattern("log4j\\.appender\\.*");
			Iterator it = keys.iterator();
			while (it.hasNext()) {
				String key = (String) it.next();
				String suffix = key.substring("log4j.appender.".length());

				// appender property
				if (suffix.indexOf('.') == -1) {
					String val = props.getProperty(key);
					if ("org.apache.log4j.FileAppender".equals(val)) {
						String fileName = props.getProperty(key + ".File");
						fileNamesToDelete.add(fileName);
					} else if ("org.apache.log4j.RollingFileAppender".equals(val)) {
						// get file name and its components
						String fullFileName = props.getProperty(key + ".File");
						File base = new File(fullFileName);
						File parent = base.getParentFile();
						final String fileName = base.getName();

						// get files starting with that name and mark them for deletion
						String fileNames[] = parent.list(new FilenameFilter() {

							public boolean accept(File aDir, String aName) {
								return (aName.startsWith(fileName));
							}
						});
						for (int i = 0; i < fileNames.length; ++i)
							fileNamesToDelete.add(parent.getAbsolutePath() + File.separator + fileNames[i]);
					}
				}
			}
		}

		// delete files
		Iterator it = fileNamesToDelete.iterator();
		while (it.hasNext()) {
			String fileName = (String) it.next();
			if (fileName != null && fileName.length() > 0) {
				File f = new File(fileName);
				f.delete();
				// if ( f.delete() )
				// System.out.println( "deleted " + f.getAbsolutePath() );
				// else
				// System.err.println( "error deleting " + f.getAbsolutePath() );
			}
		}

	}

	/**
	 * default is false; set to true if you want it to delete everything without prompting user
	 * 
	 * @param aUserInteraction
	 */
	public void setUserInteraction(boolean aUserInteraction) {
		this.userInteraction = aUserInteraction;
	}
}
