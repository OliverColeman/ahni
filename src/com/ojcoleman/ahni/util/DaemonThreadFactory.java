package com.ojcoleman.ahni.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFactory to create daemon threads. Uses the factory given by {@link Executors#defaultThreadFactory()} to create the threads, then makes them daemons.
 */
class DaemonThreadFactory implements ThreadFactory {
	final String name;
	final ThreadGroup group;
	final AtomicInteger threadNumber = new AtomicInteger(1);
	
	public DaemonThreadFactory() {
		this("DaemonThreadFactory");
	}
	public DaemonThreadFactory(String name) {
		this(name, new ThreadGroup(name));
	}
	public DaemonThreadFactory(String name, ThreadGroup group) {
		this.name = name;
		this.group = group;
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(group, r, name + "-" + threadNumber.getAndIncrement());
		t.setDaemon(true);
		return t;
	}
}