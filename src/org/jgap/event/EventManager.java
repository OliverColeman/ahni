/*
 * Copyright 2001-2003 Neil Rotstan
 *
 * This file is part of JGAP.
 *
 * JGAP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * JGAP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with JGAP; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jgap.event;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Manages event notification in the system. Observers that desire to be
 * notified of genetic events should subscribe to this class via the
 * addEventListener() method. To unsubscribe, use the removeEventListener()
 * method. To generate a genetic event, use the fireGeneticEvent() method,
 * which will take care of notifying the appropriate subscribers.
 */
public class EventManager
{
    /**
     * References a Map of subscribed event listeners. Each key is an event
     * name, and each value is a List of listeners subscribed to that event.
     */
    private Map m_listeners = new HashMap();


    /**
     * Adds a new listener that will be notified when the event represented
     * by the given name is fired.
     *
     * @param a_eventName the name of the event to which the given listener
     *                    should be subscribed. Standard events are
     *                    represented by constants in the GeneticEvent class.
     * @param a_eventListenerToAdd the genetic listener to subscribe to
     *                             notifications of the given event.
     */
    public synchronized void addEventListener(
                                 String a_eventName,
                                 GeneticEventListener a_eventListenerToAdd )
    {
        List eventListeners = (List) m_listeners.get( a_eventName );

        if( eventListeners == null )
        {
            eventListeners = new LinkedList();
            m_listeners.put( a_eventName, eventListeners );
        }

        eventListeners.add( a_eventListenerToAdd );
    }


    /**
     * Removes the given listener from subscription of the indicated event.
     * The listener will no longer be notified when the given event occurs.
     *
     * @param a_eventName the name of the event to which the given listener
     *                    should be removed. Standard events are
     *                    represented by constants in the GeneticEvent class.
     * @param a_eventListenerToRemove the genetic listener to unsubscribe from
     *                                notifications of the given event.
     */
    public synchronized void removeEventListener(
                                 String a_eventName,
                                 GeneticEventListener a_eventListenerToRemove )
    {
        List eventListeners = (List) m_listeners.get( a_eventName );

        if( eventListeners != null )
        {
            eventListeners.remove( a_eventListenerToRemove );
        }
    }


    /**
     * Fires a genetic event. All subscribers of that particular event type
     * (as determined by the name of the event) will be notified of it
     * having been fired.
     *
     * @param a_eventToFire The representation of the GeneticEvent to fire.
     */
    public synchronized void fireGeneticEvent( GeneticEvent a_eventToFire )
    {
        List eventListeners =
            (List) m_listeners.get( a_eventToFire.getEventName() );

        if ( eventListeners != null )
        {
            // Iterate over the listeners and notify each one of the event.
            // ------------------------------------------------------------
            Iterator listenerIterator = eventListeners.iterator();
            while( listenerIterator.hasNext() )
            {
                ( (GeneticEventListener) listenerIterator.next() ).
                    geneticEventFired( a_eventToFire );
            }
        }
    }
}

