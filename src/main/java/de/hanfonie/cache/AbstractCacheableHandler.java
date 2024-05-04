package de.hanfonie.cache;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.simpleyaml.configuration.file.YamlFile;
import org.simpleyaml.configuration.serialization.ConfigurationSerializable;

public abstract class AbstractCacheableHandler<T extends ICacheable<T>, U extends ICacheDescriptor<T>, V extends Map<?, ?>> {

	private static final String DEFAULT_DATA_FILE = "data.yml";

	protected Cache cache;

	protected final V cacheMap;
	protected final long timeoutSeconds;
	private long now;
	private final Method deserialize;

	public AbstractCacheableHandler(V v, long timeoutSeconds) {
		this.cacheMap = v;
		this.timeoutSeconds = timeoutSeconds;
		try {
			deserialize = getType().getDeclaredMethod("deserialize", Map.class);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("deserialize method of " + getType() + " not found");
		}
	}

	protected ReentrantLock getLock() {
		return cache.getLockFor(this);
	}

	public T get(U descriptor) {
		if (!exists(descriptor))
			return null;
		return getOrCreate(descriptor);
	}

	public abstract T getOrCreate(U descriptor);

	@SuppressWarnings("unchecked")
	protected T getCacheOnly(Object... path) {
		int pos = 0;

		Map<Object, Object> map = (Map<Object, Object>) cacheMap;

		for (; pos + 1 != path.length; pos++)
			map = (Map<Object, Object>) map.computeIfAbsent(path[pos], k -> new HashMap<>());

		if (map.containsKey(pos))
			return (T) map.get(path[pos]);
		return null;
	}
	
	@SuppressWarnings("unchecked")
	protected void put(T t, Object...path) {
		int pos = 0;

		Map<Object, Object> map = (Map<Object, Object>) cacheMap;

		for (; pos + 1 != path.length; pos++)
			map = (Map<Object, Object>) map.computeIfAbsent(path[pos], k -> new HashMap<>());
		map.put(path[pos], t);
	}

	@SuppressWarnings("unchecked")
	protected T getOrCreate0(U u, Object... path) {
		int pos = 0;

		Map<Object, Object> map = (Map<Object, Object>) cacheMap;

		for (; pos + 1 != path.length; pos++)
			map = (Map<Object, Object>) map.computeIfAbsent(path[pos], k -> new HashMap<>());

		if (map.containsKey(path[pos])) {
			T t = (T) map.get(path[pos]);
			t.updateLastAccess();
			return t;
		}

		File saveDir = getDirectory(u);
		if (!saveDir.exists())
			saveDir.mkdirs();

		YamlFile dataFile = new YamlFile(getDataFile(u));
		try {
			if (!dataFile.exists())
				save(u);
			dataFile.load();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		T t = null;
		try {
			t = (T) deserialize.invoke(null, dataFile.getMapValues(false));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new IllegalStateException("could not deserialize " + getType() + ": " + dataFile, ex);
		}
		map.put(path[pos], t);
		return t;
	}

	public boolean exists(U descriptor) {
		return getDataFile(descriptor).exists();
	}

	protected void saveAll() {
		saveMap(cacheMap);
		cacheMap.clear();
	}

	@SuppressWarnings("unchecked")
	private void saveMap(Map<?, ?> map) {
		Iterator<?> iterator = map.entrySet().iterator();
		while (iterator.hasNext()) {
			Object o = iterator.next();
			if (o instanceof Map)
				saveMap((Map<?, ?>) o);
			else
				save((T) o);
		}
	}

	protected void checkAll() {
		now = System.currentTimeMillis();
		checkMap(cacheMap);
	}

	@SuppressWarnings("unchecked")
	private void checkMap(Map<?, ?> map) {
		Iterator<?> iterator = map.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<?, ?> o = (Entry<?, ?>) iterator.next();
			if (o.getValue() instanceof Map) {
				Map<?, ?> val = (Map<?, ?>) o;
				checkMap(val);
				if (val.isEmpty())
					iterator.remove();
			} else {
				T t = (T) o.getValue();
				if (t.getLastAccess() + TimeUnit.SECONDS.toMillis(timeoutSeconds) < now) {
					save(t);
					iterator.remove();
				}
			}

		}
	}

	protected File getDataFile(T t) {
		return new File(getDirectory(t), DEFAULT_DATA_FILE);
	}

	protected File getDataFile(U u) {
		return new File(getDirectory(u), DEFAULT_DATA_FILE);
	}

	protected abstract File getDirectory(U desc);

	protected abstract File getDirectory(T t);

	protected void save(T t) {
		save(getDataFile(t), t);
	}

	protected void save(U u) {
		save(getDataFile(u), u);
	}

	protected void save(File f, ConfigurationSerializable cs) {
		YamlFile yaml = new YamlFile(f);
		try {
			yaml.createNewFile();
			yaml.load();
			Map<String, Object> ser = cs.serialize();
			for (Entry<String, Object> e : ser.entrySet())
				yaml.set(e.getKey(), e.getValue());
			yaml.save();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public abstract Class<T> getType();
}
