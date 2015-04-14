package com.ojcoleman.ahni.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience class to either execute an external command asynchronously 
 * via a new Thread and retrieve the output if desired,
 * or run an external command synchronously (.
 */
public class Exec extends Thread {
	String[] command;
	Process process;
	String output;
	int exitStatus;
	Exception exception;
	volatile boolean started = false;
	volatile boolean finished = false;

	/**
	 * Execute the given shell command.
	 * 
	 * @param command The shell command to execute.
	 */
	public Exec(String... command) {
		this.command = command;
		this.start();
		// Wait till thread is started before returning, so exit status and output can be safely requested immediately
		// upon return.
		while (!started) {
			try {
				Thread.currentThread().sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public synchronized void run() {
		started = true;
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		try {
			process = pb.start();
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            while ( (line = br.readLine()) != null ) {
                output += line;
            }
            exitStatus = process.waitFor();
			
		} catch (Exception e) {
			e.printStackTrace();
			exception = e;
		} finally {
			finished = true;
		}
	}

	/**
	 * Returns true iff the command has finished running or the attempt to run it has finished.
	 */
	public boolean finished() {
		return finished;
	}

	/**
	 * Get the exit status of the command. Blocks until the command finishes executing or the attempt to run it has
	 * finished. Most commands return a non-zero value to indicate an error occurred.
	 */
	public synchronized int getExitStatus() throws InterruptedException {
		return exitStatus;
	}

	/**
	 * Get the output of the command, including output written to the error stream. Blocks until the command finishes
	 * executing or the attempt to run it has finished. Null is returned if an error occurred when trying to execute the
	 * command.
	 */
	public synchronized String getOutput() throws InterruptedException {
		return output;
	}

	/**
	 * Returns true iff an exception occurred when attempting to run the command. Blocks until the command finishes
	 * executing or the attempt to run it has finished.
	 */
	public synchronized boolean exceptionOccurred() {
		return exception != null;
	}

	/**
	 * Returns the exception that occurred when attempting to run the command, or null if no exception occurred. Blocks
	 * until the command finishes executing or the attempt to run it has finished.
	 */
	public synchronized Exception getException() {
		return exception;
	}
	
	/**
	 * Executes the given command and returns the output.
	 * @param command The command to run, the first element is the command and the following elements are the arguments to the command.
	 * @return The output of the command, including the error stream, with each line as a separate element.
	 */
	public static List<String> executeShellCommand(String[] command) {
		return executeShellCommand(command, null);
	}
	
	/**
	 * Executes the given command and returns the output.
	 * @param command The command to run, the first element is the command and the following elements are the arguments to the command.
	 * @param workingDir The working directory that the command is to be executed in.
	 * @return The output of the command, including the error stream, with each line as a separate element.
	 */
	public static List<String> executeShellCommand(String[] command, File workingDir) {
		ArrayList<String> output = new ArrayList<String>();
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).directory(workingDir).start();

            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            while ( (line = br.readLine()) != null ) {
                output.add(line);
            }

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }
}
