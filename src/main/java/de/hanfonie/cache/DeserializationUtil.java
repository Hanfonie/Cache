package de.hanfonie.cache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.ancash.misc.yaml.YamlAnnotationProcessor;

public class DeserializationUtil {

	DeserializationUtil() {
	}

	@SuppressWarnings("unchecked")
	public static <T extends ICacheable<T>> T deserialize(Class<T> clazz, Map<String, Object> map) {
		try {
			if (!newInstanceMethods.containsKey(clazz)) {
				try {
					newInstanceMethods.put(clazz, clazz.getDeclaredMethod("newInstance"));
				} catch (NoSuchMethodException | SecurityException e) {
					throw new IllegalStateException(e);
				}
			}
			T t = (T) newInstanceMethods.get(clazz).invoke(null);
			return YamlAnnotationProcessor.loadInto(map, t);
		} catch (Throwable th) {
			throw new IllegalStateException(th);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends ICacheable<T>, U extends ICacheDescriptor<T>> U deserializeDescriptor(Class<U> clazz, Map<String, Object> map) {
		if (!cacheDescriptorDeserialize.containsKey(clazz)) {
			try {
				cacheDescriptorDeserialize.put(clazz, clazz.getDeclaredMethod("deserialize", Map.class));
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IllegalStateException(e);
			}
		}
		Method m = cacheDescriptorDeserialize.get(clazz);
		try {
			return (U) m.invoke(null, map);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final Map<String, Class<?>> clazzMap = new ConcurrentHashMap<String, Class<?>>();
	private static final Map<Class<? extends ICacheDescriptor<?>>, Method> cacheDescriptorDeserialize = new ConcurrentHashMap<>();

	public static Class<?> getClazz(String s) {
		if (clazzMap.containsKey(s))
			return clazzMap.get(s);
		try {
			Class<?> clazz = Class.forName(s);
			clazzMap.put(s, clazz);
			return clazz;
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final Map<Class<?>, Method> newInstanceMethods = new HashMap<Class<?>, Method>();

}
