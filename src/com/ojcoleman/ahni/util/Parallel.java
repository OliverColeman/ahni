package com.ojcoleman.ahni.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Utility class for performing parallel iteration over generic Collections or anything that implements the Iterable
 * interface. Code adapted from http://stackoverflow.com/questions/4010185/parallel-for-for-java#4010275
 */
public class Parallel {
	private static ExecutorService forPool = null;

	/**
	 * Perform the given {@link Parallel.Operation} on the given elements.
	 */
	public static <T> void foreach(final Collection<T> elements, final Operation<T> operation) {
		submitAndWait(elements, operation, elements.size());
	}

	/**
	 * Perform the given {@link Parallel.Operation} on the given elements.
	 */
	public static <T> void foreach(final Iterable<T> elements, final Operation<T> operation) {
		submitAndWait(elements, operation, 8);
	}

	private static <T> void submitAndWait(final Iterable<T> elements, final Operation<T> operation, int size) {
		if (forPool == null) {
			forPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new DaemonThreadFactory(Parallel.class.getName()));
		}

		try {
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
