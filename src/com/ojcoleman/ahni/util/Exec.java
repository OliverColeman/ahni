package com.ojcoleman.ahni.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Convenience class to execute external commands via a new Thread and retrieve the output if desired.
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
		InputStream inputStream = null;
		try {
			process = pb.start();
			inputStream = process.getInputStream();
			exitStatus = process.waitFor();
			Writer writer = new StringWriter();
			char[] buffer = new char[1024];
			Reader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
			int n;
			while ((n = reader.read(buffer)) != -1) {
				writer.write(buffer, 0, n);
			}
			output = writer.toString();
		} catch (Exception e) {
			e.printStackTrace();
			exception = e;
		} finally {
			finished = true;
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
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
}
