package de.hanfonie.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Cache implements Runnable {

	private final ConcurrentHashMap<Class<?>, AbstractCacheableHandler<?, ?, ?>> handlerMap = new ConcurrentHashMap<Class<?>, AbstractCacheableHandler<?, ?, ?>>();
	private final ConcurrentHashMap<Class<?>, ReentrantLock> lockMap = new ConcurrentHashMap<Class<?>, ReentrantLock>();
	private final ReentrantLock lockLock = new ReentrantLock();
	private boolean running = false;
	private final Logger logger;

	public Cache(Logger logger) {
		this.logger = logger;
	}

	public void registerHandler(AbstractCacheableHandler<?, ?, ?> handler) {
		handlerMap.put(handler.getType(), handler);
		lockMap.put(handler.getType(), new ReentrantLock());
		handler.cache = this;
	}

	@SuppressWarnings("unlikely-arg-type")
	protected ReentrantLock getLockFor(AbstractCacheableHandler<?, ?, ?> handler) {
		return lockMap.get(handler);
	}

	@SuppressWarnings("unchecked")
	public <T extends ICacheable<T>, U extends ICacheDescriptor<T>, V extends Map<?, ?>> T get(Class<T> clazz, U descriptor) {
		ReentrantLock handlerLock = null;
		try {
			lockLock.lock();
			if (!running) {
				logger.severe("cache not running");
				return null;
			}
			handlerLock = lockMap.get(clazz);
			if (handlerLock == null) {
				logger.severe("no handler registered for " + clazz.getCanonicalName());
				return null;
			}
		} finally {
		}
		T t;
		try {
			handlerLock.lock();
			t = ((AbstractCacheableHandler<T, U, V>) handlerMap.get(clazz)).get(descriptor);
		} finally {
			handlerLock.unlock();
		}
		return t;
	}

	@Override
	public void run() {
		running = true;
		while (running && !Thread.currentThread().isInterrupted()) {
			try {
				Thread.sleep(TimeUnit.SECONDS.toMillis(1));
			} catch (InterruptedException e) {
				logger.warning("cache interrupted");
				break;
			}

			try {
				lockLock.lock();
				for (AbstractCacheableHandler<?, ?, ?> handler : handlerMap.values())
					try {
						handler.checkAll();
					} catch (Throwable th) {
						logger.log(Level.SEVERE, "failed checking cache lifetime for " + handler.getType().getCanonicalName(), th);
					}
			} finally {
				lockLock.unlock();
			}
		}
		saveAll();
	}

	public void saveAll() {
		try {
			lockLock.lock();
			for (AbstractCacheableHandler<?, ?, ?> handler : handlerMap.values()) {
				handler.saveAll();
				logger.info("saved all cached " + handler.getType().getCanonicalName());
			}
		} finally {
			lockLock.unlock();
		}
	}

	public void stop() {
		try {
			lockLock.lock();
			if (!running)
				throw new IllegalStateException("cache not running");
			running = false;
		} finally {
			lockLock.unlock();
		}
	}
}
