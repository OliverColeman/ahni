package com.ojcoleman.ahni.util;

import java.io.IOException;


import com.beust.jcommander.IStringConverter;
import com.ojcoleman.ahni.hyperneat.Properties;

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