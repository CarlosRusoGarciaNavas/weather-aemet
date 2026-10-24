package com.carlosruso.weather.infrastructure.config;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/** One time source for the guard (Clock) and Caffeine (Ticker), so a test moves both with a single advance. */
final class FakeTime {

    private static final Instant START = Instant.parse("2026-10-23T10:00:00Z");

    private final AtomicLong nanos = new AtomicLong();

    void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    Ticker ticker() {
        return nanos::get;
    }

    Clock clock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instant instant() {
                return START.plusNanos(nanos.get());
            }
        };
    }
}
