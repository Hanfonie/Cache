package de.hanfonie.cache;

import org.simpleyaml.configuration.serialization.ConfigurationSerializable;

public interface ICacheable<T extends ICacheable<T>> extends ConfigurationSerializable {

	public long getLastAccess();

	public void updateLastAccess();
}
