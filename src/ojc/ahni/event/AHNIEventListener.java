package ojc.ahni.event;

/**
 * An interface for objects that listen to events that occur during an evolutionary run in AHNI.
 * An object will typically register as a listener via the {@link ojc.ahni.hyperneat.Properties} object passed 
 * to the {@link ojc.ahni.hyperneat.Configurable#init(ojc.ahni.hyperneat.Properties)} method.
 */ 
public interface AHNIEventListener {
	public void ahniEventOccurred(AHNIEvent event);
}
