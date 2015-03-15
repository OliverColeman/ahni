package com.ojcoleman.ahni.evaluation;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.PropertyConfigurator;
import org.jgapcustomised.BulkFitnessFunction;
import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.Transcriber;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * Transcription and evaluation may be performed by a cluster of computers using Minions. The fitness function must be
 * based on {@link com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT}, which contains the relevant configuration
 * property key definitions and usage information.
 */
public class Minion {
	public static final int DEFAULT_READ_TIMEOUT = 60*60*1000; // 60 minutes.
	
	/**
	 * Properties key indicating that this is a Minion instance, for internal use.
	 */
	public static final String MINION_INSTANCE = "minion.instance";

	@Parameter(names = { "--port", "-p" }, arity = 1, description = "Port for controlling instance to connect to.")
	public int port = 0;

	@Parameter(names = { "--log", "-l" }, description = "Location of log file to redirect standard out and error streams to. If not given then no redirection occurs.")
	public String logFile = null;
	
	Socket socket;
	ObjectOutputStream out;
	ObjectInputStream in;
	Properties properties;
	BulkFitnessFunctionMT fitnessFunc;
	int currentGeneration;
	
	public static void main(String[] args) {
		try {
			Minion minion = new Minion();
			new JCommander(minion, args);
			minion.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void run() {
		ServerSocket serverSocket = null;
		RequestProcessor requestProcessor = null;
		try {
			if (logFile != null && !logFile.trim().isEmpty()) {
				PrintStream console = new PrintStream(new FileOutputStream(logFile.trim()));
				System.setOut(console);
				System.setErr(console);
			}
			
			System.out.println("Starting on port " + port);
			
			serverSocket = new ServerSocket(port);
			//serverSocket.setSoTimeout(30*60*1000); // 30 minutes.
			serverSocket.setSoTimeout(0); // No timeout.
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		
		while (true) {
			try {
				if (requestProcessor != null) {
					requestProcessor.terminate();
				}
				if (socket != null) {
					socket.close();
				}
				requestProcessor = new RequestProcessor();
				requestProcessor.start();
				
				try {
					// Wait until a connection is made.
					System.out.println("Opening socket at " + InetAddress.getLocalHost().getHostAddress() + ":" + port);
					//System.out.flush();
					socket = serverSocket.accept();
				} catch(SocketTimeoutException e) {
					System.out.println("Minion exiting, no connections made in a timely manner.");
					serverSocket.close();
					System.exit(0);
				}
				System.out.println("Connection from controller with IP: " + socket.getInetAddress().getHostAddress());
				
				socket.setSoTimeout(Minion.DEFAULT_READ_TIMEOUT);
				out = new ObjectOutputStream(socket.getOutputStream());
				in = new ObjectInputStream(socket.getInputStream());
				
				// While the connection is active, process requests from it.
				System.out.println("Ready.");
				while (!socket.isOutputShutdown() && !socket.isInputShutdown() && !socket.isClosed()) {
					// TODO check load average.
					
					// Wait for a request.
					Request request = (Request) in.readObject();
					// Always process terminate requests immediately.
					System.out.println("Received request " + request.type);
					if (request.type == Request.Type.TERMINATE) {
						System.out.println("Received terminate signal.");
						try {
							socket.close();
							serverSocket.close();
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						System.exit(0);
					}
					else {
						requestProcessor.process(request);
					}
					
				}
				socket.close();
			} catch(Exception e) {
				e.printStackTrace();
				if (socket != null)
					try {
						socket.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
			}
			socket = null;
		}
	}

	public static class Request implements Serializable {
		public enum Type {CONFIGURE, INITIALISE_EVALUATION, EVALUATE, TERMINATE};
		
		final Type type;
		final Object data;
		
		public Request(Type type, Object data) {
			this.type = type;
			this.data = data;
		}
	}
	
	private class RequestProcessor extends Thread {
		volatile boolean active = true;
		Request request;
		
		public synchronized void process(Request request) throws InterruptedException {
			this.request = request;
			notify();
		}
		
		public void terminate() {
			active = false;
			interrupt();
		}
		
		@Override
		public void run() {
			while (active) {
				try {
					synchronized(this) {
						while (request == null) {
							wait();
						}
						switch (request.type) {
						case CONFIGURE:
							System.out.println("Receiving configuration from " + socket.getInetAddress().toString());
							
							properties = new Properties();
							String propsStr = (String) request.data;
							properties.loadFromReader(new StringReader(propsStr), null);
							properties.put(MINION_INSTANCE, "true");
							properties.remove(BulkFitnessFunctionMT.MINION_HOSTS); // We don't want minions starting minions...
							// Disable output to files.
							properties.remove(HyperNEATConfiguration.OUTPUT_DIR_KEY);
							// Disable all Log4J file logs
							properties.configureLog4JSettings(null, true);
							
							java.util.Properties log4jProps = new java.util.Properties();
							log4jProps.putAll(properties);
							PropertyConfigurator.configure(log4jProps);
							properties.configureLogger();
		
							// The transcriber initialisation is sometimes necessary for setting up some config stuff.
							properties.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
							fitnessFunc = (BulkFitnessFunctionMT) properties.getFitnessFunction();
							currentGeneration = -1;
							
							if (active) {
								System.out.println("  Configured.");
								out.writeObject(Boolean.TRUE);
							}
							break;
							
						case INITIALISE_EVALUATION:
							int newGeneration = (Integer) request.data;
							// Make sure to call initialiseEvaluationOnAll() only once per generation.
							// We use currentGeneration initialised to -1 rather than call properties.getEvolver().getGeneration() 
							// in order to correctly handle the case where the current generation number is 0.
							if (newGeneration != currentGeneration) {
								currentGeneration = newGeneration;
								properties.getEvolver().setGeneration(newGeneration);
								fitnessFunc.initialiseEvaluationOnAll();
							}
							if (active) {
								System.out.println("  Initialised.");
								out.writeObject(Boolean.TRUE);
							}
							break;
							
						case EVALUATE:
							if (properties == null) {
								if (active) {
									System.err.println("Evaluate request sent when minion not configured");
									out.writeObject(new IllegalStateException("Evaluate request sent when minion not configured"));
								}
							} else {
								List<Chromosome> chroms = (List<Chromosome>) request.data;
								fitnessFunc.evaluateFitnessMT(chroms);
								
								if (active) {
									System.out.println("  Finished evaluation.");
									out.writeObject(chroms);
								}
							}
							break;
							
						default:
							if (active) {
								System.err.println("Unknown request type.");
								out.writeObject(new IllegalStateException("Unknown request type."));
							}
							break;
						}
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					try {
						if (active) {
							out.writeObject(e);
						}
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				request = null;
			}
		}
	}
}
