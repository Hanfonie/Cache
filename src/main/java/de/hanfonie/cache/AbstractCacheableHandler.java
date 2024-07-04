package de.hanfonie.cache;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.simpleyaml.configuration.file.YamlFile;
import org.simpleyaml.configuration.serialization.ConfigurationSerializable;

public abstract class AbstractCacheableHandler<T extends ICacheable<T>, U extends ICacheDescriptor<T>, V extends Map<?, ?>> {

	protected Cache cache;

	protected final V cacheMap;
	protected final long timeoutSeconds;
	private long now;
	private final Method deserialize;
	protected final File dir;

	public AbstractCacheableHandler(File dir, V v, long timeoutSeconds) {
		this.cacheMap = v;
		this.dir = dir;
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

	protected abstract Object[] toPath(U u);

	protected abstract Object[] toPath(T t);

	public T get(U descriptor) {
		if (!exists(descriptor))
			return null;
		return getOrCreate(descriptor);
	}

	public T getOrCreate(U descriptor) {
		return getOrCreate0(descriptor, toPath(descriptor));
	}

	@SuppressWarnings("unchecked")
	protected void put(T t, boolean save, Object... path) {
		int pos = 0;

		Map<Object, Object> map = (Map<Object, Object>) cacheMap;

		for (; pos + 1 != path.length; pos++)
			map = (Map<Object, Object>) map.computeIfAbsent(path[pos], k -> new HashMap<>());
		if (t != null) {
			map.put(path[pos], t);
			if(save)
				save(t);
		} else {
			map.remove(path[pos]);
			getDataFile(path).delete();
//			System.out.println("deleted " + Arrays.asList(path));
			onDelete(t, path);
			return;
		}
//		System.out.println("set " + Arrays.asList(path) + " to " + (t != null ? t.serialize() : "null"));
	}

	protected void onDelete(T t, Object...path) {
		
	}
	
	@SuppressWarnings("unchecked")
	protected T getOrCreate0(U u, Object... path) {
		int pos = 0;
		Map<Object, Object> map = (Map<Object, Object>) cacheMap;

		for (; pos + 1 != path.length; pos++)
			map = (Map<Object, Object>) map.computeIfAbsent(path[pos], k -> new HashMap<>());

		if (map.containsKey(path[pos])) {
			T t = (T) map.get(path[pos]);
//			System.out.println("cached already " + u.serialize() + ": " + t);
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
		T t = loadFromFile(dataFile.getConfigurationFile());
		put(t, false, path);
		return t;
	}

	@SuppressWarnings("unchecked")
	protected T loadFromFile(File file) {
		long now = System.nanoTime();
		YamlFile dataFile = new YamlFile(file);
		try {
			if (!dataFile.exists())
				return null;
			dataFile.load();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		T t = null;
		try {
			t = (T) deserialize.invoke(null, dataFile.getMapValues(false));
			System.out.println("loaded " + dataFile.getConfigurationFile() + " " + (System.nanoTime() - now) / 1000 + " micros");
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new IllegalStateException("could not deserialize " + getType() + ": " + dataFile, ex);
		}
		return t;
	}

	public boolean exists(U descriptor) {
//		System.out.println("exists: " + descriptor.serialize() + ": " + getDataFile(descriptor).exists());
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
				Map<?, ?> val = (Map<?, ?>) o.getValue();
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
		return getDataFile(toPath(t));
	}

	protected File getDataFile(U u) {
		return getDataFile(toPath(u));
	}
	
	protected File getDataFile(Object[] path) {
		return new File(pathToDirectory(path), path[path.length - 1].toString() + ".yml");
	}

	protected File getDirectory(U desc) {
		return pathToDirectory(toPath(desc));
	}

	protected File pathToDirectory(Object... path) {
		File f = dir;
		for (int i = 0; i < path.length - 1; i++)
			f = new File(f, path[i].toString());
		return f;
	}

	protected File getDirectory(T t) {
		return pathToDirectory(toPath(t));
	}

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
//			System.out.println("saved " + f);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public List<T> getCopyOfAll() {
		List<T> t = new ArrayList<>();
		getCopyOfAll(dir, t);
		return t;
	}

	private void getCopyOfAll(File f, List<T> list) {
		if (f.isDirectory()) {
			if (f.listFiles().length > 0)
				for (File s : f.listFiles())
					getCopyOfAll(s, list);
		} else if (isValid(f))
			list.add(loadFromFile(f));

	}

	public abstract boolean isValid(File f);

	public abstract Class<T> getType();
}
