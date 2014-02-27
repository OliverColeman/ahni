/*
 * $Header: /home/cvs/jakarta-commons/primitives/src/java/org/apache/commons/collections/primitives/ArrayDoubleList.java,v 1.3 2003/10/16 20:49:36 scolebourne Exp $
 * ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowledgement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowledgements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package com.ojcoleman.ahni.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

import org.apache.commons.collections.primitives.DoubleCollection;
import org.apache.commons.collections.primitives.DoubleList;
import org.apache.commons.collections.primitives.RandomAccessDoubleList;

/**
 * A modified version of {@link org.apache.commons.collections.primitives.ArrayDoubleList} that provides additional functionality. 
 */
public class DoubleVector extends RandomAccessDoubleList implements DoubleList, Serializable {

    // constructors
    //-------------------------------------------------------------------------

    /** 
     * Construct an empty list with the default
     * initial capacity.
     */
    public DoubleVector() {
        this(8);
    }    

    /**
     * Construct an empty list with the given
     * initial capacity.
     * @throws IllegalArgumentException when <i>initialCapacity</i> is negative
     */
    public DoubleVector(int initialCapacity) {
        if(initialCapacity < 0) {
            throw new IllegalArgumentException("capacity " + initialCapacity);
        }
        _data = new double[initialCapacity];
        _size = 0;
    }    

    /** 
     * Constructs a list containing the elements of the given collection, 
     * in the order they are returned by that collection's iterator.
     * 
     * @see DoubleVector#addAll(org.apache.commons.collections.primitives.DoubleCollection)
     * @param that the non-<code>null</code> collection of <code>double</code>s 
     *        to add
     * @throws NullPointerException if <i>that</i> is <code>null</code>
     */
    public DoubleVector(DoubleCollection that) { 
        this(that.size());
        addAll(that);
    }    

    // DoubleList methods
    //-------------------------------------------------------------------------

    public double get(int index) {
        checkRange(index);
        return _data[index];
    }
    
    public int size() {
        return _size;
    }
    
    /** 
     * Removes the element at the specified position in 
     * (optional operation).  Any subsequent elements 
     * are shifted to the left, subtracting one from their 
     * indices.  Returns the element that was removed.
     * 
     * @param index the index of the element to remove
     * @return the value of the element that was removed
     * 
     * @throws UnsupportedOperationException when this operation is not 
     *         supported
     * @throws IndexOutOfBoundsException if the specified index is out of range
     */
    public double removeElementAt(int index) {
        checkRange(index);
        incrModCount();
        double oldval = _data[index];
        int numtomove = _size - index - 1;
        if(numtomove > 0) {
            System.arraycopy(_data,index+1,_data,index,numtomove);
        }
        _size--;
        return oldval;
    }
    
    /** 
     * Replaces the element at the specified 
     * position in me with the specified element
     * (optional operation). 
     * 
     * @param index the index of the element to change
     * @param element the value to be stored at the specified position
     * @return the value previously stored at the specified position
     * 
     * @throws UnsupportedOperationException when this operation is not 
     *         supported
     * @throws IndexOutOfBoundsException if the specified index is out of range
     */
    public double set(int index, double element) {
        checkRange(index);
        incrModCount();
        double oldval = _data[index];
        _data[index] = element;
        return oldval;
    }
        
    /** 
     * Inserts the specified element at the specified position 
     * (optional operation). Shifts the element currently 
     * at that position (if any) and any subsequent elements to the 
     * right, increasing their indices.
     * 
     * @param index the index at which to insert the element
     * @param element the value to insert
     * 
     * @throws UnsupportedOperationException when this operation is not 
     *         supported
     * @throws IllegalArgumentException if some aspect of the specified element 
     *         prevents it from being added to me
     * @throws IndexOutOfBoundsException if the specified index is out of range
     */
    public void add(int index, double element) {
        checkRangeIncludingEndpoint(index);
        incrModCount();
        ensureCapacity(_size+1);
        int numtomove = _size-index;
        System.arraycopy(_data,index,_data,index+1,numtomove);
        _data[index] = element;
        _size++;
    }

    // capacity methods
    //-------------------------------------------------------------------------

    /** 
     * Increases my capacity, if necessary, to ensure that I can hold at 
     * least the number of elements specified by the minimum capacity 
     * argument without growing.
     */
    public void ensureCapacity(int mincap) {
        incrModCount();
        if(mincap > _data.length) {
            int newcap = (_data.length * 3)/2 + 1;
            double[] olddata = _data;
            _data = new double[newcap < mincap ? mincap : newcap];
            System.arraycopy(olddata,0,_data,0,_size);
        }
    }

    /** 
     * Reduce my capacity, if necessary, to match my
     * current {@link #size size}.
     */
    public void trimToSize() {
        incrModCount();
        if(_size < _data.length) {
            double[] olddata = _data;
            _data = new double[_size];
            System.arraycopy(olddata,0,_data,0,_size);
        }
    }
    
    /**
     * Sorts the elements in this vector into ascending numerical order.
     */
    public void sort() {
    	Arrays.sort(_data, 0, _size);
    }
    
    /** 
     * Searches the vector for the specified value using the binary search algorithm. 
     * The array must be sorted (as by the sort() method) prior to making this call. 
     * If it is not sorted, the results are undefined. 
     * If the vector contains multiple elements with the specified value, there is no guarantee which one will be found. 
     * This method considers all NaN values to be equivalent and equal.
     * @param key - the value to be searched for
     * @return index of the search key, if it is contained in the vector; otherwise, (-(insertion point) - 1). 
     *   The insertion point is defined as the point at which the key would be inserted into the vector: 
     *   the index of the first element greater than the key, or size() if all elements in the vector are less than the specified key. 
     *   Note that this guarantees that the return value will be >= 0 if and only if the key is found.
     */
    public int binarySearch(double key) {
    	return Arrays.binarySearch(_data, 0, _size, key);
    }
    
    /** 
     * Searches the vector for the specified value or the insertion point of the specified value using the binary search algorithm.
     * The array must be sorted (as by the sort() method) prior to making this call. 
     * If it is not sorted, the results are undefined. 
     * If the vector contains multiple elements with the specified value, there is no guarantee which one will be found. 
     * This method considers all NaN values to be equivalent and equal.
     * @param key - the value to be searched for
     * @return index of the search key or the index at which the key would be inserted into the vector: 
     *   the index of the first element greater than the key, or size() if all elements in the vector are less than the specified key. 
     */
    public int binarySearchIP(double key) {
    	int ip = Arrays.binarySearch(_data, 0, _size, key);
    	if (ip >= 0) return ip;
    	return -ip-1;
    }

    
    // private methods
    //-------------------------------------------------------------------------
    
    private void writeObject(ObjectOutputStream out) throws IOException{
        out.defaultWriteObject();
        out.writeInt(_data.length);
        for(int i=0;i<_size;i++) {
            out.writeDouble(_data[i]);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        _data = new double[in.readInt()];
        for(int i=0;i<_size;i++) {
            _data[i] = in.readDouble();
        }
    }
    
    private final void checkRange(int index) {
        if(index < 0 || index >= _size) {
            throw new IndexOutOfBoundsException("Should be at least 0 and less than " + _size + ", found " + index);
        }
    }

    private final void checkRangeIncludingEndpoint(int index) {
        if(index < 0 || index > _size) {
            throw new IndexOutOfBoundsException("Should be at least 0 and at most " + _size + ", found " + index);
        }
    }

    // attributes
    //-------------------------------------------------------------------------
    
    private transient double[] _data = null;
    private int _size = 0;

}
