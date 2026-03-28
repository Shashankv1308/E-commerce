package com.spring.ecommerce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Graceful cache error handler — logs Redis errors and swallows them
 * so the application falls back to the database instead of returning 500.
 *
 * Without this, Spring's default SimpleCacheErrorHandler re-throws all
 * cache exceptions, which propagate to the controller as unhandled errors.
 */
public class LoggingCacheErrorHandler implements CacheErrorHandler
{
    private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key)
    {
        log.warn("Cache GET failed [cache={}, key={}]: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value)
    {
        log.warn("Cache PUT failed [cache={}, key={}]: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key)
    {
        log.warn("Cache EVICT failed [cache={}, key={}]: {}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache)
    {
        log.warn("Cache CLEAR failed [cache={}]: {}",
                cache.getName(), exception.getMessage());
    }
}
