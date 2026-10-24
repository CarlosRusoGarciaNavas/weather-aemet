package com.carlosruso.weather.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AEMET answers with Cache-Control no-store, so caching is ours. Only these two caches exist:
 * an unknown cache name fails instead of silently creating an unbounded one.
 * Both record stats so Micrometer can publish hits and misses.
 */
@Configuration
@EnableCaching
class CacheConfig {

    static final String MUNICIPALITIES = "municipalities";
    static final String FORECASTS = "forecasts";

    @Bean
    CacheManager cacheManager(AemetProperties properties) {
        return cacheManager(properties.cache(), Ticker.systemTicker());
    }

    static SimpleCacheManager cacheManager(AemetProperties.Cache settings, Ticker ticker) {
        var manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                new CaffeineCache(MUNICIPALITIES, municipalitiesCache(settings.municipiosTtl(), ticker), false),
                new CaffeineCache(FORECASTS,
                        forecastsCache(settings.forecastTtl(), settings.forecastMaxEntries(), ticker), false)));
        manager.afterPropertiesSet();
        return manager;
    }

    /** The whole list is a single entry: the catalog method takes no arguments. */
    static Cache<Object, Object> municipalitiesCache(Duration ttl, Ticker ticker) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(1)
                .ticker(ticker)
                .recordStats()
                .build();
    }

    static Cache<Object, Object> forecastsCache(Duration ttl, long maxEntries, Ticker ticker) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxEntries)
                .ticker(ticker)
                .recordStats()
                .build();
    }

    /**
     * Last good answers, used by the resilient decorators when AEMET fails. Not part of the CacheManager:
     * they are read directly, not through @Cacheable. Typed, because each decorator owns its own.
     */
    static <K, V> Cache<K, V> staleCache(Duration ttl, long maxEntries, Ticker ticker) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxEntries)
                .ticker(ticker)
                .recordStats()
                .<K, V>build();
    }
}
