package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.DailyForecast;
import java.time.Duration;
import java.time.LocalDate;
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
class CachedForecastSourceTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String AGOST = "03002";
    private static final String ALICANTE = "03014";
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 23);

    @Configuration
    @EnableCaching(proxyTargetClass = true) // like Spring Boot: a subclass proxy, so the decorator cannot be final
    static class Config {

        @Bean
        AtomicLong nanos() {
            return new AtomicLong();
        }

        @Bean
        CacheManager cacheManager(AtomicLong nanos) {
            return CacheConfig.cacheManager(new AemetProperties.Cache(Duration.ofHours(24), TTL, 100), nanos::get);
        }

        @Bean
        CountingForecastSource delegate() {
            return new CountingForecastSource();
        }

        @Bean
        CachedForecastSource cachedForecastSource(CountingForecastSource delegate) {
            return new CachedForecastSource(delegate);
        }
    }

    /** Counts calls; can fail once or answer slowly. */
    static class CountingForecastSource implements ForecastSource {
        final AtomicInteger calls = new AtomicInteger();
        volatile RuntimeException nextFailure;
        volatile Duration delay = Duration.ZERO;

        @Override
        public DailyForecast forecastFor(String municipalityCode, LocalDate date) {
            calls.incrementAndGet();
            sleep(delay);
            var failure = nextFailure;
            if (failure != null) {
                nextFailure = null;
                throw failure;
            }
            return new DailyForecast(date, List.of(20.0), null, null, List.of());
        }
    }

    @Autowired
    private CachedForecastSource cached;

    @Autowired
    private CountingForecastSource delegate;

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
    void twoEqualCallsReachTheDelegateOnce() {
        var first = cached.forecastFor(AGOST, TOMORROW);
        var second = cached.forecastFor(AGOST, TOMORROW);

        assertSame(first, second);
        assertEquals(1, delegate.calls.get());
    }

    @Test
    void differentCodesDoNotShareAnEntry() {
        cached.forecastFor(AGOST, TOMORROW);
        cached.forecastFor(ALICANTE, TOMORROW);

        assertEquals(2, delegate.calls.get());
    }

    @Test
    void differentDatesDoNotShareAnEntry() {
        var tomorrow = cached.forecastFor(AGOST, TOMORROW);
        var dayAfter = cached.forecastFor(AGOST, TOMORROW.plusDays(1));

        assertEquals(2, delegate.calls.get());
        assertEquals(TOMORROW, tomorrow.date());
        assertEquals(TOMORROW.plusDays(1), dayAfter.date());
    }

    @Test
    void anExceptionIsNotCached() {
        delegate.nextFailure = new ProviderUnavailableException("AEMET is down");

        assertThrows(ProviderUnavailableException.class, () -> cached.forecastFor(AGOST, TOMORROW));
        cached.forecastFor(AGOST, TOMORROW);
        cached.forecastFor(AGOST, TOMORROW);

        assertEquals(2, delegate.calls.get());
    }

    @Test
    void theEntryExpiresWhenTheTickerPassesTheTtl() {
        cached.forecastFor(AGOST, TOMORROW);

        nanos.addAndGet(TTL.minusSeconds(1).toNanos());
        cached.forecastFor(AGOST, TOMORROW);
        assertEquals(1, delegate.calls.get());

        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        cached.forecastFor(AGOST, TOMORROW);
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void concurrentMissesForTheSameKeyCallTheDelegateOnce() throws Exception {
        // The slow delegate keeps the first load open while the other threads arrive.
        delegate.delay = Duration.ofMillis(200);
        var threads = 8;
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(threads)) {
            var futures = IntStream.range(0, threads)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return cached.forecastFor(AGOST, TOMORROW);
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
