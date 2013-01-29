package com.ojcoleman.ahni.event;

/**
 * An interface for objects that listen to events that occur during an evolutionary run in AHNI.
 * An object will typically register as a listener via the {@link com.ojcoleman.ahni.hyperneat.Properties} object passed 
 * to the {@link com.ojcoleman.ahni.hyperneat.Configurable#init(com.ojcoleman.ahni.hyperneat.Properties)} method.
 */ 
public interface AHNIEventListener {
	public void ahniEventOccurred(AHNIEvent event);
}
