package com.ojcoleman.ahni.experiments.doublepolebalancing;

import java.nio.BufferUnderflowException;

/**
 * This is a circular buffer of double precision floating point numbers, customised for use in the double pole
 * experiments.
 * 
 * This buffer maintains a total of all of the values it contains, adjusting for values that are overwritten when the
 * buffer overwrites old values.
 * 
 * This code was adapted from SharpNEAT by Colin Green (http://sharpneat.sourceforge.net/).
 */
public class JiggleBuffer {
	double[] _buffer;
	// The buffer.
	double _jiggleTotal = 0.0;
	// The index of the previously enqueued item. -1 if buffer is empty.
	int _headIdx;
	// The index of the next item to be dequeued. -1 if buffer is empty.
	int _tailIdx;

	/**
	 * Construct buffer with the specified capacity.
	 */
	public JiggleBuffer(int size) {
		_buffer = new double[size];
		_headIdx = _tailIdx = -1;
	}

	/**
	 * The length of the buffer.
	 */
	public int getLength() {
		if (_headIdx == -1)
			return 0;

		if (_headIdx > _tailIdx)
			return (_headIdx - _tailIdx) + 1;

		if (_tailIdx > _headIdx)
			return (_buffer.length - _tailIdx) + _headIdx + 1;

		return 1;
	}

	/**
	 * The sum of all values on in the buffer.
	 */
	public double getTotal() {
		return _jiggleTotal;
	}

	/**
	 * Clear the buffer.
	 */
	public void clear() {
		_headIdx = _tailIdx = -1;
		_jiggleTotal = 0.0;
	}

	/**
	 * Add an item to the front of the buffer.
	 */
	public void enqueue(double item) {
		if (_headIdx == -1) {
			// buffer is currently empty.
			_headIdx = _tailIdx = 0;
			_buffer[0] = item;
			_jiggleTotal += item;
			return;
		}

		// Determine the index to write to.
		if (++_headIdx == _buffer.length) {
			// Wrap around.
			_headIdx = 0;
		}

		if (_headIdx == _tailIdx) {
			// Buffer overflow. Increment tailIdx.
			_jiggleTotal -= _buffer[_headIdx];
			if (++_tailIdx == _buffer.length) {
				// Wrap around.
				_tailIdx = 0;
			}

			_buffer[_headIdx] = item;
			_jiggleTotal += item;
			return;
		}

		_jiggleTotal += item;
		_buffer[_headIdx] = item;
		return;
	}

	/**
	 * Remove an item from the back of the queue.
	 */
	public double dequeue() {
		if (_tailIdx == -1) {
			throw new BufferUnderflowException();
		}

		// buffer is currently empty.
		double o = _buffer[_tailIdx];
		_jiggleTotal -= o;
		if (_tailIdx == _headIdx) {
			// The buffer is now empty.
			_headIdx = _tailIdx = -1;
			return o;
		}

		if (++_tailIdx == _buffer.length) {
			// Wrap around.
			_tailIdx = 0;
		}

		return o;
	}

	/**
	 * Pop an item from the head/top of the queue.
	 */
	public double pop() {
		if (_tailIdx == -1) {
			throw new BufferUnderflowException();
		}

		// buffer is currently empty.
		double o = _buffer[_headIdx];
		_jiggleTotal -= o;
		if (_tailIdx == _headIdx) {
			// The buffer is now empty.
			_headIdx = _tailIdx = -1;
			return o;
		}

		if (--_headIdx == -1) {
			// Wrap around.
			_headIdx = _buffer.length - 1;
		}

		return o;
	}

}
