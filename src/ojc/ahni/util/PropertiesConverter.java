package ojc.ahni.util;

import java.io.IOException;

import ojc.ahni.hyperneat.Properties;

import com.beust.jcommander.IStringConverter;

public class PropertiesConverter implements IStringConverter<Properties> {
	@Override
	public Properties convert(String value) {
		try {
			Properties p = new Properties(value);
			return p;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}