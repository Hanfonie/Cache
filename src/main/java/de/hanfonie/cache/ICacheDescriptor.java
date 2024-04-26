package de.hanfonie.cache;

import org.simpleyaml.configuration.serialization.ConfigurationSerializable;

public interface ICacheDescriptor<T extends ICacheable<T>> extends ConfigurationSerializable {

}
