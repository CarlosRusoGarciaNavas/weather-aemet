package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.Municipality;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** @Cacheable only works through Spring's proxy, so the decorator is tested inside a minimal context. */
@SpringJUnitConfig
class CachedMunicipalityCatalogTest {

    private static final Duration TTL = Duration.ofHours(24);

    @Configuration
    @EnableCaching(proxyTargetClass = true) // like Spring Boot: a subclass proxy, so the decorator cannot be final
    static class Config {

        @Bean
        AtomicLong nanos() {
            return new AtomicLong();
        }

        @Bean
        CacheManager cacheManager(AtomicLong nanos) {
            return CacheConfig.cacheManager(new AemetProperties.Cache(TTL, Duration.ofMinutes(30), 100), nanos::get);
        }

        @Bean
        CountingCatalog delegate() {
            return new CountingCatalog();
        }

        @Bean
        CachedMunicipalityCatalog cachedMunicipalityCatalog(CountingCatalog delegate) {
            return new CachedMunicipalityCatalog(delegate);
        }
    }

    /** Counts calls; can fail once or answer slowly. */
    static class CountingCatalog implements MunicipalityCatalog {
        final AtomicInteger calls = new AtomicInteger();
        volatile RuntimeException nextFailure;
        volatile Duration delay = Duration.ZERO;

        @Override
        public List<Municipality> findAll() {
            calls.incrementAndGet();
            sleep(delay);
            var failure = nextFailure;
            if (failure != null) {
                nextFailure = null;
                throw failure;
            }
            return List.of(new Municipality("03002", "Agost"));
        }
    }

    @Autowired
    private CachedMunicipalityCatalog cached;

    @Autowired
    private CountingCatalog delegate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AtomicLong nanos;

    @BeforeEach
    void reset() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        delegate.calls.set(0);
        delegate.nextFailure = null;
        delegate.delay = Duration.ZERO;
    }

    @Test
    void twoCallsReachTheDelegateOnce() {
        var first = cached.findAll();
        var second = cached.findAll();

        assertSame(first, second);
        assertEquals(1, delegate.calls.get());
    }

    @Test
    void anExceptionIsNotCached() {
        delegate.nextFailure = new ProviderUnavailableException("AEMET is down");

        assertThrows(ProviderUnavailableException.class, () -> cached.findAll());
        cached.findAll();
        cached.findAll();

        assertEquals(2, delegate.calls.get());
    }

    @Test
    void theEntryExpiresWhenTheTickerPassesTheTtl() {
        cached.findAll();

        nanos.addAndGet(TTL.minusSeconds(1).toNanos());
        cached.findAll();
        assertEquals(1, delegate.calls.get());

        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        cached.findAll();
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void concurrentMissesCallTheDelegateOnce() throws Exception {
        // The slow delegate keeps the first load open while the other threads arrive.
        delegate.delay = Duration.ofMillis(200);
        var threads = 8;
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(threads)) {
            var futures = IntStream.range(0, threads)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return cached.findAll();
                    }))
                    .toList();
            start.countDown();

            var first = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (var future : futures) {
                assertSame(first, future.get(5, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, delegate.calls.get());
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
