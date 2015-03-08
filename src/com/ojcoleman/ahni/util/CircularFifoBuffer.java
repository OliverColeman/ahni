package com.ojcoleman.ahni.util;

import java.io.Serializable;
import java.util.ArrayDeque;

public class CircularFifoBuffer<E> implements Serializable {
	int capacity;
	ArrayDeque<E> storage;

	public CircularFifoBuffer(int capacity) {
		this.capacity = capacity;
		storage = new ArrayDeque<E>(capacity);
	}

	/**
	 * Adds the given element to the buffer. If the buffer is full, the least recently added element is discarded so
	 * that a new element can be inserted.
	 */
	public void add(E e) {
		if (isFull()) {
			remove();
		}
		storage.addLast(e);
	}

	/**
	 * Removes and returns the least recently inserted element from this buffer.
	 */
	public E remove() {
		return storage.removeFirst();
	}

	/**
	 * Returns true iff the buffers remaining capacity is 0.
	 */
	public boolean isFull() {
		return storage.size() == capacity;
	}
	
	/**
	 * Returns true iff the buffers size is 0.
	 */
	public boolean isEmpty() {
		return storage.isEmpty();
	}

	/**
	 * Returns the number of elements in the buffer.
	 */
	public int size() {
		return storage.size();
	}
	
	public Object[] toArray() {
		return storage.toArray();
	}
	
	public<T> T[] toArray(T[] a) {
		return storage.toArray(a);
	}
	
	public ArrayDeque getBackingStore() {
		return storage;
	}
}
