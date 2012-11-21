package ojc.ahni.integration;

/**
 * An interface for objects that listen to events that occur during an evolutionary run in AHNI.
 * An object will typically register as a listener via the {@link AHNIRunProperties} object passed 
 * to the {@link com.anji.util.Configurable#init(com.anji.util.Properties)} method.
 */ 
public interface AHNIEventListener {
	public void ahniEventOccurred(AHNIEvent event);
}
