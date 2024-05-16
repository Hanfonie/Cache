package de.hanfonie.cache;

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
	public <T extends ICacheable<T>, U extends ICacheDescriptor<T>> boolean exists(Class<T> clazz, U descriptor) {
		ReentrantLock handlerLock = getHandlerLock(clazz);
		boolean b;
		try {
			handlerLock.lock();
			b = ((AbstractCacheableHandler<T, U, ?>) handlerMap.get(clazz)).exists(descriptor);
		} finally {
			handlerLock.unlock();
		}
		return b;
	}

	@SuppressWarnings("unchecked")
	public <T extends ICacheable<T>, U extends ICacheDescriptor<T>> void set(Class<T> clazz, T value, U descriptor) {
		ReentrantLock handlerLock = getHandlerLock(clazz);
		try {
			handlerLock.lock();
			AbstractCacheableHandler<T, U, ?> handler = ((AbstractCacheableHandler<T, U, ?>) handlerMap.get(clazz));
			if (!handler.exists(descriptor) && value == null)
				return;
			else
				handler.getOrCreate(descriptor);

			handler.put(value, handler.toPath(descriptor));
		} finally {
			handlerLock.unlock();
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends ICacheable<T>, U extends ICacheDescriptor<T>> void delete(Class<T> clazz, U descriptor) {
		ReentrantLock handlerLock = getHandlerLock(clazz);
		try {
			handlerLock.lock();
			AbstractCacheableHandler<T, U, ?> handler = ((AbstractCacheableHandler<T, U, ?>) handlerMap.get(clazz));
			if (handler.get(descriptor) != null) {
				handler.put(null, handler.toPath(descriptor));
				handler.getDataFile(descriptor).delete();
			}
		} finally {
			handlerLock.unlock();
		}
	}

	private ReentrantLock getHandlerLock(Class<?> clazz) {
		ReentrantLock handlerLock = null;
		try {
			lockLock.lock();
			if (!running)
				throw new IllegalStateException("cache not runnning");
			handlerLock = lockMap.get(clazz);
			if (handlerLock == null)
				throw new IllegalStateException("no handler registered for " + clazz.getCanonicalName());
		} finally {
			lockLock.unlock();
		}
		return handlerLock;
	}

	@SuppressWarnings("unchecked")
	public <T extends ICacheable<T>, U extends ICacheDescriptor<T>> T get(Class<T> clazz, U descriptor) {
		ReentrantLock handlerLock = getHandlerLock(clazz);
		T t;
		try {
			handlerLock.lock();
			t = ((AbstractCacheableHandler<T, U, ?>) handlerMap.get(clazz)).get(descriptor);
		} finally {
			handlerLock.unlock();
		}
		return t;
	}

	@SuppressWarnings("unchecked")
	public <T extends ICacheable<T>, U extends ICacheDescriptor<T>> T getOrCreate(Class<T> clazz, U descriptor) {
		ReentrantLock handlerLock = getHandlerLock(clazz);
		T t;
		try {
			handlerLock.lock();
			t = ((AbstractCacheableHandler<T, U, ?>) handlerMap.get(clazz)).getOrCreate(descriptor);
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
						th.printStackTrace();
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
