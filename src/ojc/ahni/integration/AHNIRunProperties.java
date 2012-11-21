package ojc.ahni.integration;

import java.io.IOException;
import java.util.List;

import ojc.ahni.hyperneat.HyperNEATEvolver;

import com.anji.util.Properties;

/**
 * The AHNIProperties class extends the {@link com.anji.util.Properties} class to provide event communication functionality via 
 * {@link AHNIEventListener}s. 
 */
public class AHNIRunProperties extends Properties {
	private static final long serialVersionUID = 1L;
	private HyperNEATEvolver evolver;
	
	/**
	 * Creates a new empty AHNIProperties.
	 */
	public AHNIRunProperties() {
		super();
	}

	/**
	 * Creates a new AHNIProperties initialised with the given properties.
	 */
	public AHNIRunProperties(Properties values) {
		super(values);
	}

	/**
	 * Creates a new AHNIProperties initialised with properties read from the given resource.
	 * @throws IOException
	 */
	public AHNIRunProperties(String resource) throws IOException {
		super(resource);
	}
	
	/**
	 * Get the HyperNEATEvolver that is handling this run.
	 */
	public HyperNEATEvolver getEvolver() {
		return evolver;
	}
	
	/**
	 * Set the HyperNEATEvolver that is handling this run.
	 */
	public void setEvolver(HyperNEATEvolver evolver) {
		this.evolver = evolver;
	}
}
