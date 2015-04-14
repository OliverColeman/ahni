package com.ojcoleman.ahni.evaluation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;
import com.ojcoleman.ahni.util.Exec;

/**
 * Handles communication with a minion worker in a cluster that is managed by HTCondor. 
 * See {@link com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT}.
 */
public class MinionHandlerCondor extends MinionHandler {
	static Logger logger = Logger.getLogger(MinionHandlerCondor.class);
	
	static int idCounter = 0;
	
	int id;
	String condorClusterID;
	String condorOutDir;
	
	/**
	 * Create a MinionHandler that handles a Minion launched on a specified host.
	 * @throws IOException 
	 */
	public MinionHandlerCondor(BulkFitnessFunctionMT bulkFitnessFunctionMT, int defaultPort) throws IOException {
		super(bulkFitnessFunctionMT);
		
		this.id = idCounter++;
		this.port = defaultPort;
		autoLaunchAttempts = 5;
		
		condorOutDir = "minion-" + id;
		File condorOutDirFile = new File(condorOutDir);
		FileUtils.deleteQuietly(condorOutDirFile);
		String condorOutDirSep = condorOutDir + File.separator;
		condorOutDirFile.mkdir();
		
		// Generate condor submit file.
		String mainJAR = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		mainJAR = URLDecoder.decode(mainJAR, "UTF-8");
		
		String jarFiles = "";
		URLClassLoader classLoader = (URLClassLoader) this.getClass().getClassLoader();
		for (URL url : classLoader.getURLs()) {
			jarFiles += url.getPath() + " ";
		}
		
		Map<String, String> condorSubmit = new HashMap<String, String>();
		condorSubmit.put("universe", "java");
		condorSubmit.put("executable", mainJAR);
		condorSubmit.put("jar_files", jarFiles);
		condorSubmit.put("arguments", "com.ojcoleman.ahni.evaluation.Minion --port " + defaultPort + " --log minion-$(Process).log");
		condorSubmit.put("universe", "java");
		condorSubmit.put("output", "out-$(Process).txt");
		condorSubmit.put("error", "err-$(Process).txt");
		condorSubmit.put("log", "condorlog-$(Process).txt");
		condorSubmit.put("when_to_transfer_output", "ON_EXIT_OR_EVICT");
		condorSubmit.put("should_transfer_files", "YES");
		condorSubmit.put("Rank", "kflops");
		condorSubmit.put("+RequiresWholeMachine", "True");
		condorSubmit.put("notification", "Never");
		
		BufferedWriter fileWriter = new BufferedWriter(new FileWriter(new File(condorOutDirSep + "submit.txt")));
		for (Map.Entry<String, String> entry : condorSubmit.entrySet()) {
			fileWriter.write(entry.getKey() + "=" + entry.getValue() + "\n");
		}
		fileWriter.write("queue 1\n");
		fileWriter.close();
		
		launch();
		this.start();
	}
	
	/**
	 * Internal use only
	 */
	public void run() {
		long startTime = System.currentTimeMillis();
		while (!finish && failCount < RETRY_COUNT) {
			try {
				sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			if (finish) {
				return;
			}
			
			// Check the status of the Condor job.
			//H = on hold, R = running, I = idle (waiting for a machine to execute on), C = completed, X = removed, 
			// < = transferring input (or queued to do so), and > = transferring output (or queued to do so)
			String status = null;
			try {
				status = getHTCondorStatus();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			System.err.println(this + " status = " + status);
			
			// If it's held, then maybe it did something naughty.
			if ("H".equals(status)) {
				connected = false;
				logger.error("Permanently gave up on " + this + ", HTCondor job was put on hold! It hasn't been removed so that you can find out the reason.");
				return;
			}
			// If it's running, then try to connect to it if we're not already connected.
			else if ("R".equals(status)) {
				if (!isConnected()) {
					if (determineHost()) {
						System.err.println(this + " CONNECTING");
						connect();
					}
					else {
						connected = false;
						System.err.println(this + " CONNECT FAILED");
						logger.error("Permanently gave up on " + this + ", HTCondor says it's running, but I couldn't determine the host. It hasn't been removed so that you can find out the reason.");
						return;
					}
				}
			}
			// Otherwise if it's anything other than idle or transferring input files (including if we couldn't 
			// determine the status, which probably means the job has disappeared completely from Condor)
			// then try to launch it (if auto-launch is enabled).
			else if (!"I".equals(status) && !"<".equals(status) && autoLaunchAttempts > 0) {
				connected = false;
				autoLaunchAttempts--;
				launch();
			}
			
			// Note that the above logic won't re-launch the minion if Condor says it's running but we can't connect to it.
		}
		if (failCount == RETRY_COUNT) {
			logger.error("Permanently gave up on " + this + ", retry limit exceeded.");
		}
	}
	
	/**
	 * Start a Minion instance via HTCondor.
	 */
	public synchronized void launch() {
		try {
			// Submit jobs to condor.
			List<String> output = Exec.executeShellCommand(new String[]{"condor_submit", "submit.txt"}, new File(condorOutDir));
			
			Pattern p = Pattern.compile("\\d+ job\\(s\\) submitted to cluster (\\d+)\\.");
			condorClusterID = null;
			String allOutput = "";
			for (String line : output) {
				System.err.println(line);
				allOutput += "  " + line + "\n";
				Matcher m = p.matcher(line);
				if (m.find()) {
					condorClusterID = m.group(1);
					logger.info("Started " + this);
				}
			}
			
			if (condorClusterID == null) {
				throw new Exception("Unable to determine cluster ID from condor_submit output:\n " + allOutput);
			}
			
			failCount = 0; // reset
		}
		catch (Exception e) {
			logger.error("Error starting " + this + ": ", e);
			e.printStackTrace();
		}
	}
	
	public String toString() {
		return "Minion " + id + " (HTCondor cluster: " + condorClusterID + ")";
	}
	
	protected String getHTCondorStatus() throws Exception {
		// The lines from "condor_q" look like this: 
		// ID      OWNER            SUBMITTED     RUN_TIME ST PRI SIZE CMD
		// 393.0   username        4/2  12:13   0+00:00:01 H  0   0.0  Command
		List<String> output = Exec.executeShellCommand(new String[]{"condor_q", condorClusterID});
		String status = null;
		int stPos = -1;
		for (String line : output) {
			System.err.println(line);
			if (line.trim().startsWith("ID")){
				stPos = line.indexOf("ST");
			}
			else if (stPos != -1 && line.trim().startsWith(condorClusterID)) {
				status = line.substring(stPos, stPos+1);
				break;
			}
		}
		
		return status;
	}
	
	protected boolean determineHost() {
		try {
			// The lines from "condor_q -run" look like this: 
			// 394.0 username 4/10 11:31 0+00:00:07 slot1@my.host.name
			List<String> output = Exec.executeShellCommand(new String[]{"condor_q", "-run", condorClusterID});
			host = null;
			for (String line : output) {
				if (line.trim().startsWith(condorClusterID)) {
					String[] parts = line.split("\\s+");
					parts = parts[parts.length - 1].split("@");
					host = parts[1];
					break;
				}
			}
			
			System.err.println(this + " host = " + host);
			
			if (host == null) {
				return false;
			}
			
			address = InetAddress.getByName(host);
			return true;
		}
		catch (Exception e) {
			logger.error("Error determining host for " + this + ": ", e);
			e.printStackTrace();
		}
		return false;
	}
	

	public synchronized void dispose() {
		try {
			if (isConnected()) {
				socket.setSoTimeout(500);
				out.writeObject(new Minion.Request(Minion.Request.Type.TERMINATE, null));
				socket.close();
			}
			
			if (condorClusterID != null) {
				Exec.executeShellCommand(new String[]{"condor_rm", condorClusterID});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finish = true;
		notifyAll();
	}
}
