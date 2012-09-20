/*
 * 
 * Copyright 2001-2003 Neil Rotstan
 * 
 * 
 * 
 * This file is part of JGAP.
 * 
 * 
 * 
 * JGAP is free software; you can redistribute it and/or modify
 * 
 * it under the terms of the GNU Lesser Public License as published by
 * 
 * the Free Software Foundation; either version 2.1 of the License, or
 * 
 * (at your option) any later version.
 * 
 * 
 * 
 * JGAP is distributed in the hope that it will be useful,
 * 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * 
 * GNU Lesser Public License for more details.
 * 
 * 
 * 
 * You should have received a copy of the GNU Lesser Public License
 * 
 * along with JGAP; if not, write to the Free Software Foundation, Inc.,
 * 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *  
 */

package org.jgap.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 
 * A simple, generic pool class that can be used to pool any kind of object.
 * 
 * Objects can be released to this pool, either individually or as a
 * 
 * Collection, and then later acquired again. It is not necessary for an
 * 
 * object to have been originally acquired from the pool in order for it to
 * 
 * be released to the pool. If there are no objects present in the pool,
 * 
 * an attempt to acquire one will return null. The number of objects
 * 
 * available in the pool can be determined with the size() method. Finally,
 * 
 * it should be noted that the pool does not attempt to perform any kind
 * 
 * of cleanup or re-initialization on the objects to restore them to some
 * 
 * clean state when they are released to the pool; it's up to the user to
 * 
 * reset any necessary state in the object prior to the release call (or
 * 
 * just after the acquire call).
 *  
 */

public class Pool

{

/**
 * 
 * The List of Objects currently in the pool.
 *  
 */

private List m_pooledObjects;

/**
 * 
 * Constructor.
 *  
 */

public Pool()

{

	m_pooledObjects = new ArrayList();

}

/**
 * 
 * Attempts to acquire an Object instance from the pool. It should
 * 
 * be noted that no cleanup or re-initialization occurs on these
 * 
 * objects, so it's up to the caller to reset the state of the
 * 
 * returned Object if that's desirable.
 * 
 * 
 * 
 * @return An Object instance from the pool or null if no
 * 
 * Object instances are available in the pool.
 *  
 */

public synchronized Object acquirePooledObject()

{

	if ( m_pooledObjects.isEmpty() )

	{

		return null;

	}

	// Remove the last Object in the pool and return it.

	// Note that removing the last Object (as opposed to the first

	// one) is an optimization because it prevents the ArrayList

	// from resizing itself.

	// -----------------------------------------------------------

	return m_pooledObjects.remove( m_pooledObjects.size() - 1 );

}

/**
 * Releases an Object to the pool. It's not required that the Object originated from the
 * pool--any Object can be released to it.
 * @param a_objectToPool The Object instance to be released into the pool.
 */
public synchronized void releaseObject( Object a_objectToPool ) {
	m_pooledObjects.add( a_objectToPool );
}

/**
 * Releases a Collection of objects to the pool. It's not required that the objects in the
 * Collection originated from the pool--any objects can be released to it.
 * @param a_objectsToPool The Collection of objects to release into the pool.
 */
public synchronized void releaseAllObjects( Collection a_objectsToPool )

{

	if ( a_objectsToPool != null )

	{

		m_pooledObjects.addAll( a_objectsToPool );

	}

}

/**
 * 
 * Retrieves the number of objects currently available in this pool.
 * 
 * 
 * 
 * @return the number of objects in this pool.
 *  
 */

public synchronized int size()

{

	return m_pooledObjects.size();

}

/**
 * 
 * Empties out this pool of all objects.
 *  
 */

public synchronized void clear()

{

	m_pooledObjects.clear();

}

}


