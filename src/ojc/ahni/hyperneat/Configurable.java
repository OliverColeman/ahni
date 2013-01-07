package ojc.ahni.hyperneat;

/**
 * All classes implementing this interface must have a public no-argument constructor. All initialization must be done
 * in {@link #init(Properties)} . This is a replacement for {@link com.anji.util.Configurable} that allows using {@link ojc.ahni.hyperneat.Properties}.
 */
public interface Configurable {
	/**
	 * @param props Configuration parameters.
	 * @throws Exception
	 */
	public void init(Properties props) throws Exception;
}
