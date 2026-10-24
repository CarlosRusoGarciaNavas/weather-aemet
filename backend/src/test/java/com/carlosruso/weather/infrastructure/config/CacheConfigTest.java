package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Builds the caches with a fake ticker, so expiration is tested without waiting. */
class CacheConfigTest {

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    void forecastEntryLivesUntilItsTtl() {
        var cache = CacheConfig.forecastsCache(Duration.ofMinutes(30), 10, ticker);
        cache.put("03002", "forecast");

        advance(Duration.ofMinutes(29));
        assertEquals("forecast", cache.getIfPresent("03002"));

        advance(Duration.ofMinutes(1));
        assertNull(cache.getIfPresent("03002"));
    }

    @Test
    void readingDoesNotExtendTheTtl() {
        var cache = CacheConfig.forecastsCache(Duration.ofMinutes(30), 10, ticker);
        cache.put("03002", "forecast");

        advance(Duration.ofMinutes(20));
        cache.getIfPresent("03002");
        advance(Duration.ofMinutes(10));

        assertNull(cache.getIfPresent("03002"));
    }

    @Test
    void municipalitiesEntryLivesUntilItsTtl() {
        var cache = CacheConfig.municipalitiesCache(Duration.ofHours(24), ticker);
        cache.put("all", "list");

        advance(Duration.ofHours(23));
        assertEquals("list", cache.getIfPresent("all"));

        advance(Duration.ofHours(1));
        assertNull(cache.getIfPresent("all"));
    }

    @Test
    void forecastsAreBoundedInSize() {
        var cache = CacheConfig.forecastsCache(Duration.ofMinutes(30), 2, ticker);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        cache.cleanUp(); // eviction is asynchronous; this runs it now
        assertEquals(2, cache.estimatedSize());
    }

    @Test
    void managerUsesTheConfiguredTtls() {
        var settings = new AemetProperties.Cache(Duration.ofHours(24), Duration.ofMinutes(30), 10);
        var manager = CacheConfig.cacheManager(settings, ticker);
        var forecasts = manager.getCache(CacheConfig.FORECASTS);
        var municipalities = manager.getCache(CacheConfig.MUNICIPALITIES);
        forecasts.put("03002", "forecast");
        municipalities.put("all", "list");

        advance(Duration.ofMinutes(30));

        assertNull(forecasts.get("03002"));
        assertNotNull(municipalities.get("all"));
    }

    @Test
    void managerOnlyKnowsTheTwoCaches() {
        var settings = new AemetProperties.Cache(Duration.ofHours(24), Duration.ofMinutes(30), 10);
        var manager = CacheConfig.cacheManager(settings, ticker);

        assertEquals(Set.of(CacheConfig.MUNICIPALITIES, CacheConfig.FORECASTS), Set.copyOf(manager.getCacheNames()));
        assertNull(manager.getCache("typo"));
    }

    @Test
    void nullValuesAreRejected() {
        var settings = new AemetProperties.Cache(Duration.ofHours(24), Duration.ofMinutes(30), 10);
        var forecasts = CacheConfig.cacheManager(settings, ticker).getCache(CacheConfig.FORECASTS);

        assertThrows(IllegalArgumentException.class, () -> forecasts.put("03002", null));
        assertNull(forecasts.get("03002"));
    }
}
