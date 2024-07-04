package de.hanfonie.cache;

import java.util.Map;

import org.simpleyaml.configuration.serialization.ConfigurationSerializable;

import de.ancash.misc.yaml.YamlAnnotationProcessor;

public interface ICacheDescriptor<T extends ICacheable<T>> extends ConfigurationSerializable {

	@Override
	public default Map<String, Object> serialize() {
		return YamlAnnotationProcessor.serialize(this);
	}
	
}
