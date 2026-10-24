package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderGuardTest {

    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private final FakeTime time = new FakeTime();
    private final ProviderGuard guard = new ProviderGuard(time.clock(), COOLDOWN);

    @Test
    void itStartsClosed() {
        assertTrue(guard.rejection().isEmpty());
    }

    @Test
    void anUnavailableProviderOpensItWithTheSameType() {
        var failure = new ProviderUnavailableException("AEMET answered HTTP 500");
        guard.recordFailure(failure);

        var rejection = guard.rejection().orElseThrow();

        assertInstanceOf(ProviderUnavailableException.class, rejection);
        assertSame(failure, rejection.getCause());
    }

    @Test
    void aRateLimitOpensItWithTheSameType() {
        guard.recordFailure(new ProviderRateLimitedException("too many requests"));

        assertInstanceOf(ProviderRateLimitedException.class, guard.rejection().orElseThrow());
    }

    @Test
    void itStaysOpenDuringTheCooldownAndClosesWhenItEnds() {
        guard.recordFailure(new ProviderUnavailableException("down"));

        time.advance(COOLDOWN.minusMillis(1));
        assertTrue(guard.rejection().isPresent());

        time.advance(Duration.ofMillis(1));
        assertTrue(guard.rejection().isEmpty());
    }

    @Test
    void aSuccessClosesItBeforeTheCooldown() {
        guard.recordFailure(new ProviderUnavailableException("down"));

        guard.recordSuccess();

        assertTrue(guard.rejection().isEmpty());
    }

    @Test
    void aNewFailureRestartsTheCooldown() {
        guard.recordFailure(new ProviderUnavailableException("down"));
        time.advance(Duration.ofSeconds(20));
        guard.recordFailure(new ProviderRateLimitedException("too many requests"));

        time.advance(Duration.ofSeconds(20));

        // 40 s after the first failure, 20 s after the second: still open, and with the latest type.
        assertInstanceOf(ProviderRateLimitedException.class, guard.rejection().orElseThrow());
    }

    @Test
    void onlyProviderFailuresAreAccepted() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.recordFailure(new ForecastNotAvailableException("no forecast")));
        assertTrue(guard.rejection().isEmpty());
    }
}
