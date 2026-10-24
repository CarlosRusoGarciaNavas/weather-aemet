package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker state for AEMET, shared by the catalog and the forecasts: both go to the same provider.
 * It opens when a caller reports a provider failure and closes when the cooldown has passed or a call succeeds.
 * The whole state is one immutable value in an AtomicReference, so it is thread-safe without locks.
 */
public final class ProviderGuard {

    /** When the circuit opened and the failure that opened it. */
    private record Open(Instant since, RuntimeException failure) {
    }

    private final Clock clock;
    private final Duration cooldown;
    private final AtomicReference<Open> open = new AtomicReference<>();

    public ProviderGuard(Clock clock, Duration cooldown) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Empty if AEMET may be called. Otherwise a new exception of the same type as the failure that opened the
     * circuit, so callers answer the same (502 or 503) whether AEMET failed now or a few seconds ago.
     */
    public Optional<RuntimeException> rejection() {
        var current = open.get();
        if (current == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(current.since().plus(cooldown))) {
            open.compareAndSet(current, null); // only if no newer failure has reopened it meanwhile
            return Optional.empty();
        }
        return Optional.of(rejectionFor(current.failure()));
    }

    /** @throws IllegalArgumentException if the failure is not a provider failure */
    public void recordFailure(RuntimeException failure) {
        if (!(failure instanceof ProviderUnavailableException || failure instanceof ProviderRateLimitedException)) {
            throw new IllegalArgumentException("Not a provider failure: " + failure);
        }
        open.set(new Open(clock.instant(), failure));
    }

    public void recordSuccess() {
        open.set(null);
    }

    private static RuntimeException rejectionFor(RuntimeException failure) {
        var message = "AEMET is not called for a while after a failure: " + failure.getMessage();
        return failure instanceof ProviderRateLimitedException
                ? new ProviderRateLimitedException(message)
                : new ProviderUnavailableException(message, failure);
    }
}
