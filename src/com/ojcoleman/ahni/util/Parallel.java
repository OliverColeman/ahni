package com.ojcoleman.ahni.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;

import com.ojcoleman.ahni.hyperneat.HyperNEATConfiguration;

/**
 * Utility class for performing parallel iteration over generic Collections or anything that implements the Iterable
 * interface. Code adapted from http://stackoverflow.com/questions/4010185/parallel-for-for-java#4010275
 */
public class Parallel {
	private static final Logger logger = Logger.getLogger(Parallel.class);
	private static HashMap<Integer, ExecutorService> forPoolMap = new HashMap<Integer, ExecutorService>();
	private static int defaultThreads = Runtime.getRuntime().availableProcessors();
	private static boolean reportedDefaultThreads = false;
	
	/**
	 * Sets the default number of threads that will be used. Calls to the forEach(...) methods made prior to calling this method will not be affected.
	 * The default number of threads is initially set to Runtime.getRuntime().availableProcessors().
	 */
	public static void setDefaultThreads(int mt) {
		reportedDefaultThreads = false;
		defaultThreads = mt;
	}
	
	/**
	 * Perform the given {@link Parallel.Operation} on the given elements. Returns when all elements have been processed.
	 * @param elements The Collection of elements to apply the operation to.
	 * @param threads The number of threads to use. If set to 0 then the default number will be used.
	 * @param operation The operation to apply to each element.
	 */
	public static <T> void foreach(final Collection<T> elements, int threads, final Operation<T> operation) {
		submitAndWait(elements, operation, threads, elements.size());
	}
	
	/**
	 * Perform the given {@link Parallel.Operation} on the given elements. Returns when all elements have been processed.
	 * @param elements An Iterator over elements to apply the operation to.
	 * @param threads The number of threads to use. If set to 0 then the default number will be used.
	 * @param operation The operation to apply to each element.
	 */
	public static <T> void foreach(final Iterable<T> elements, int threads, final Operation<T> operation) {
		submitAndWait(elements, operation, threads, 8);
	}
	
	private static <T> void submitAndWait(final Iterable<T> elements, final Operation<T> operation, int threads, int size) {
		if (!reportedDefaultThreads) {
			logger.info("Parallel: default thread count set to " + defaultThreads);
			reportedDefaultThreads = true;
		}

		try {
			ExecutorService forPool = null;
			
			if (threads == 0) threads = defaultThreads;
			synchronized(forPoolMap) {
				forPool = forPoolMap.get(threads);
				if (forPool == null) {
					forPool = Executors.newFixedThreadPool(threads, new DaemonThreadFactory(Parallel.class.getName()));
					forPoolMap.put(threads, forPool);
				}
			}
	
			List<Future<Void>> futures = forPool.invokeAll(createCallables(elements, operation, size));
			assert (futures.size() == size) : futures.size() + " != " + size;

			// Wait for all elements to be processed.
			for (Future<?> f : futures) {
				f.get();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
			System.err.println("CAUSE:");
			e.getCause().printStackTrace();
		}
	}

	private static <T> Collection<Callable<Void>> createCallables(final Iterable<T> elements, final Operation<T> operation, int size) {
		List<Callable<Void>> callables = new ArrayList<Callable<Void>>(size);
		for (final T elem : elements) {
			callables.add(new Callable<Void>() {
				@Override
				public Void call() {
					operation.perform(elem);
					return null;
				}
			});
		}

		return callables;
	}

	/**
	 * An operation to be performed on a single element. The perform method will be invoked for each element in the
	 * given collection, with the element passed as the parameter.
	 */
	public static interface Operation<T> {
		public void perform(T pParameter);
	}
}
