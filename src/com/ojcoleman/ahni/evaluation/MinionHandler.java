package com.ojcoleman.ahni.evaluation;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jgapcustomised.Chromosome;

import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.util.Exec;

/**
 * Handles communication with a minion worker in a cluster. See {@link com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT}.
 */
class MinionHandler extends Thread {
	static Logger logger = Logger.getLogger(MinionHandler.class);
	
	protected final BulkFitnessFunctionMT ff;

	// The number of times we attempt to connect to the minion after launching it.
	protected static final int RETRY_COUNT = 3;
	
	protected int autoLaunchAttempts;
	protected String host, user;
	protected InetAddress address;
	protected int port;
	protected Socket socket;
	protected ObjectInputStream in;
	protected ObjectOutputStream out;
	protected List<Chromosome> chromsToEval;
	protected boolean lastEvalFailed = false;
	protected int failCount = 0;
	protected int averageMinionEvalTimePerChrom = 0;
	
	protected volatile boolean finish = false;
	protected volatile boolean connected = false;
	
	protected MinionHandler(BulkFitnessFunctionMT bulkFitnessFunctionMT) {
		ff = bulkFitnessFunctionMT;
		assert !ff.isMinionInstance;
	}
	
	/**
	 * Create a MinionHandler that handles a Minion launched on a specified host.
	 */
	public MinionHandler(BulkFitnessFunctionMT bulkFitnessFunctionMT, String hostDef, int defaultPort, boolean autoLaunch) throws UnknownHostException, URISyntaxException {
		ff = bulkFitnessFunctionMT;
		assert !ff.isMinionInstance;
		
		// URI class wants a protocol before it will parse a URI, so just prepend ssh://
		URI uri = new URI("ssh://" + hostDef);
		this.host = uri.getHost();
		this.port = uri.getPort() == -1 ? defaultPort : uri.getPort();
		this.user = uri.getUserInfo();
		address = InetAddress.getByName(host);
		autoLaunchAttempts = autoLaunch ? 5 : 0;
		
		if (autoLaunch)
			launch();
		else
			connect();
		this.start();
	}
	
	/**
	 * Internal use only
	 */
	public void run() {
		while (!finish && failCount < RETRY_COUNT) {
			try {
				sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			if (finish) {
				return;
			}
			
			if (!isConnected()) connect();
			
			if (failCount == RETRY_COUNT && autoLaunchAttempts > 0) {
				autoLaunchAttempts--;
				launch();
			}
		}
		if (failCount == RETRY_COUNT) {
			logger.error("Permanently gave up on " + this + ", retry limit exceeded.");
		}
	}
	
	/**
	 * Start a Minion instance on a remote machine. Assumes shared file system.
	 */
	public synchronized void launch() {
		try {
			File launchScript = new File(System.getProperty("user.dir") + "/ahni-start-minion.sh");
			if (!launchScript.exists()) {
				String classpath = System.getProperty("java.class.path");
				boolean assertsEnabled = false;
				assert assertsEnabled = true;
				DataOutputStream dos = new DataOutputStream(new FileOutputStream(launchScript));
				dos.writeBytes("#!/bin/bash\n");
				dos.writeBytes("ssh $1 \"nohup java " + (assertsEnabled ? "-ea" : "") + " -cp \\\"" + classpath + "\\\" com.ojcoleman.ahni.evaluation.Minion --port $2 --log $3 > $4 &\"\n");
				dos.close();
				launchScript.setExecutable(true);
			}

			String minionLog = ff.props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + ff.props.getProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, "") + "minion." + host + ".log";
			String minionLaunchLog = ff.props.getProperty(HyperNEATConfiguration.OUTPUT_DIR_KEY) + ff.props.getProperty(HyperNEATConfiguration.OUTPUT_PREFIX_KEY, "") + "minion." + host + ".launch.log";
			
			String command = System.getProperty("user.dir") + "/ahni-start-minion.sh " + (user != null ? user + "@" : "") + host + " " + port + " " + minionLog +" " + minionLaunchLog;
			logger.info(command);
			//Exec exec = new Exec("/bin/bash", command);
			Exec exec = new Exec(System.getProperty("user.dir") + "/ahni-start-minion.sh", (user != null ? user + "@" : "") + host, ""+port, minionLog, minionLaunchLog);
			
			if (exec.getExitStatus() == 0 && !exec.exceptionOccurred()) {
				failCount = 0; // reset
			}
			else {
				logger.error("Error starting " + this + ". Exit status: " + exec.getExitStatus() + ". " + (exec.exceptionOccurred() ? "Exception: " + exec.getException().getMessage() : ""));
			}
		} catch (Exception e) {
			logger.error("Error starting " + this + ": ", e);
			e.printStackTrace();
		}
	}
	
	protected synchronized boolean connect() {
		connected = false;
		try {
			// Attempt to close old connection.
			if (socket != null) socket.close();
			// Connect to the instance.
			socket = new Socket(address, port);
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());
			socket.setSoTimeout(Minion.DEFAULT_READ_TIMEOUT);
			// Configure the instance.
			StringWriter sw = new StringWriter();
			ff.props.store(sw, "");
			String propsStr = sw.toString();
			propsStr += "\n" + Minion.MINION_INSTANCE + "=true";
			out.writeObject(new Minion.Request(Minion.Request.Type.CONFIGURE, propsStr));
			if (((Boolean) readFromMinion()).booleanValue()) {
				logger.info("Connected to " + this);
				connected = true;
				return true;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		failCount++;
		logger.error("Unable to connect to or configure " + this);
		return false;
	}
	
	public boolean isConnected() {
		return connected;
	}
	
	private Object readFromMinion() throws Exception {
		Object response = in.readObject();
		if (response instanceof Exception) {
			connected = false;
			logger.error(this + " threw an exception: " + ((Exception) response).getMessage());
			throw (Exception) response;
		}
		return response;
	}
	
	public synchronized boolean initialiseEvaluation() {
		try {
			out.writeObject(new Minion.Request(Minion.Request.Type.INITIALISE_EVALUATION, ff.props.getEvolver().getGeneration()));
			return ((Boolean) readFromMinion()).booleanValue();
		} catch (Exception e) {
			e.printStackTrace();
		}
		connected = false;
		logger.error("Initialise evaluation failed for " + this);
		return false;
	}
	
	public synchronized void setChromosomesToEvaluate(List<Chromosome> chroms) {
		chromsToEval = chroms;
	}
	public List<Chromosome> getChromosomesToEvaluate() {
		return chromsToEval;
	}
	
	synchronized boolean evaluateChroms() {
		try {
			List<Chromosome> dummies = new ArrayList<Chromosome>();
			// Create dummy chromosomes that don't reference a Species to avoid 
			// serialisation of the Species and all the Chromosomes, etc that they contain.
			for (Chromosome c : chromsToEval) {
				Chromosome dummy = (Chromosome) c.clone();
				dummy.resetSpecie();
				dummies.add(dummy);
			}
			out.writeObject(new Minion.Request(Minion.Request.Type.EVALUATE, dummies));
			
			// Can take a while for evaluations to complete.
			// Wait twice as long as the longest average time for this minion, or 10 minutes if this is first time.
			int avgTimePerChrom = averageMinionEvalTimePerChrom == 0 ? 10 * 60 * 1000 : averageMinionEvalTimePerChrom;
			socket.setSoTimeout(avgTimePerChrom * chromsToEval.size() * 2);
			try {
				long evalStart = System.currentTimeMillis();
				Object response = readFromMinion();
				long evalEnd = System.currentTimeMillis();
				socket.setSoTimeout(Minion.DEFAULT_READ_TIMEOUT);
				
				Iterator<Chromosome> chromsEvaluated = ((List<Chromosome>) response).iterator();
				for (Chromosome chrom : chromsToEval) {
					Chromosome evaluated = chromsEvaluated.next();
					assert ((long) chrom.getId() == (long) evaluated.getId()) : chrom.getId() + "==" + evaluated.getId(); 
					chrom.setFitnessValue(evaluated.getFitnessValue());
					chrom.setFitnessValues(evaluated.getFitnessValues());
					chrom.setPerformanceValue(evaluated.getPerformanceValue());
					chrom.setPerformanceValues(evaluated.getAllPerformanceValues());
					chrom.behaviours = evaluated.behaviours;
				}
				lastEvalFailed = false;
				updateAverageMinionEvalTimePerChrom((int) (evalEnd - evalStart) / chromsToEval.size());
				return true;
			} catch (SocketTimeoutException e) {
				lastEvalFailed = true;
				socket.setSoTimeout(Minion.DEFAULT_READ_TIMEOUT);
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		lastEvalFailed = true;
		connected = false;
		failCount++;
		logger.error("Evaluation failed on " + this);
		return false;
	}
	
	public String toString() {
		return "Minion " + host + ":" + port + " (IP " + address.getHostAddress() + ")";
	}
	
	public synchronized boolean lastEvalFailed() {
		return lastEvalFailed;
	}
	
	public synchronized void dispose() {
		try {
			if (connected) {
				socket.setSoTimeout(500);
				out.writeObject(new Minion.Request(Minion.Request.Type.TERMINATE, null));
				socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finish = true;
		notifyAll();
	}
	
	synchronized int getAverageEvalTimePerChrom() {
		return averageMinionEvalTimePerChrom;
	}
	
	private synchronized void updateAverageMinionEvalTimePerChrom(int time) {
		if (averageMinionEvalTimePerChrom == 0) {
			averageMinionEvalTimePerChrom = time;
		} else {
			averageMinionEvalTimePerChrom = (int) Math.round(averageMinionEvalTimePerChrom * 0.95 + time * 0.05);
		}
	}
	
	public synchronized void increaseAverageMinionEvalTime() {
		averageMinionEvalTimePerChrom *= 10;
	}
}