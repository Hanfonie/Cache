package de.hanfonie.cache;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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

	private static final Map<Class<?>, Method> newInstanceMethods = new HashMap<Class<?>, Method>();

}
