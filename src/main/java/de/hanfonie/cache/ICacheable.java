package de.hanfonie.cache;

import java.util.Map;

import org.simpleyaml.configuration.serialization.ConfigurationSerializable;

import de.ancash.misc.yaml.YamlAnnotationProcessor;

public interface ICacheable<T extends ICacheable<T>> extends ConfigurationSerializable {

	public long getLastAccess();

	public void updateLastAccess();

	@Override
	public default Map<String, Object> serialize() {
		return YamlAnnotationProcessor.serialize(this);
	}
}
