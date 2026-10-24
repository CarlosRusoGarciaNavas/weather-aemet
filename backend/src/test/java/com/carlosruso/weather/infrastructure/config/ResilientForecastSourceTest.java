package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import com.carlosruso.weather.domain.model.DailyForecast;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ResilientForecastSourceTest {

    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    private static final Duration STALE_TTL = Duration.ofHours(6);
    private static final String AGOST = "03002";
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 24);

    /** Counts calls and answers with whatever the test sets: a new forecast each time, or a failure. */
    static class FakeForecastSource implements ForecastSource {
        final AtomicInteger calls = new AtomicInteger();
        volatile RuntimeException failure;

        @Override
        public DailyForecast forecastFor(String municipalityCode, LocalDate date) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return new DailyForecast(date, List.of(20.0), null, null, List.of());
        }
    }

    private final FakeTime time = new FakeTime();
    private final FakeForecastSource delegate = new FakeForecastSource();
    private final ProviderGuard guard = new ProviderGuard(time.clock(), COOLDOWN);
    private final ResilientForecastSource source = new ResilientForecastSource(
            delegate, guard, CacheConfig.staleCache(STALE_TTL, 100, time.ticker()));

    @Test
    void aSuccessIsReturnedAndKeptAsTheLastGoodAnswer() {
        var fresh = source.forecastFor(AGOST, TOMORROW);

        delegate.failure = new ProviderUnavailableException("down");
        var stale = source.forecastFor(AGOST, TOMORROW);

        assertSame(fresh, stale);
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void aFailureWithoutALastGoodAnswerIsRethrown() {
        var failure = new ProviderUnavailableException("down");
        delegate.failure = failure;

        var thrown = assertThrows(ProviderUnavailableException.class, () -> source.forecastFor(AGOST, TOMORROW));

        assertSame(failure, thrown);
    }

    @Test
    void aFailureWithALastGoodAnswerServesItAndLogsAWarning(CapturedOutput output) {
        var fresh = source.forecastFor(AGOST, TOMORROW);
        delegate.failure = new ProviderRateLimitedException("too many requests");

        assertSame(fresh, source.forecastFor(AGOST, TOMORROW));
        assertTrue(output.getOut().lines().anyMatch(line -> line.contains("WARN")
                && line.contains("serving the last good forecast for 03002 on 2026-10-24")), output::getOut);
    }

    @Test
    void theLastGoodAnswerIsPerMunicipalityAndDate() {
        source.forecastFor(AGOST, TOMORROW);
        delegate.failure = new ProviderUnavailableException("down");

        assertThrows(ProviderUnavailableException.class, () -> source.forecastFor(AGOST, TOMORROW.plusDays(1)));
        assertThrows(ProviderUnavailableException.class, () -> source.forecastFor("03014", TOMORROW));
    }

    @Test
    void whileOpenTheDelegateIsNotCalledAndTheLastGoodAnswerIsServed() {
        var fresh = source.forecastFor(AGOST, TOMORROW);
        delegate.failure = new ProviderUnavailableException("down");
        source.forecastFor(AGOST, TOMORROW); // opens the circuit
        var callsWhenOpened = delegate.calls.get();

        assertSame(fresh, source.forecastFor(AGOST, TOMORROW));
        assertSame(fresh, source.forecastFor(AGOST, TOMORROW));
        assertEquals(callsWhenOpened, delegate.calls.get());
    }

    @Test
    void whileOpenWithoutALastGoodAnswerItThrowsTheTypeThatOpenedIt() {
        delegate.failure = new ProviderRateLimitedException("too many requests");
        assertThrows(ProviderRateLimitedException.class, () -> source.forecastFor(AGOST, TOMORROW));

        var thrown = assertThrows(RuntimeException.class, () -> source.forecastFor(AGOST, TOMORROW));

        assertInstanceOf(ProviderRateLimitedException.class, thrown);
        assertEquals(1, delegate.calls.get());
    }

    @Test
    void afterTheCooldownOneTrialCallReachesTheDelegate() {
        delegate.failure = new ProviderUnavailableException("down");
        assertThrows(ProviderUnavailableException.class, () -> source.forecastFor(AGOST, TOMORROW));

        time.advance(COOLDOWN);
        assertThrows(ProviderUnavailableException.class, () -> source.forecastFor(AGOST, TOMORROW));
        assertEquals(2, delegate.calls.get());

        // The trial failed, so the circuit opened again: the next call does not reach the delegate.
        assertThrows(ProviderUnavailableException.class, () -> source.forecastFor(AGOST, TOMORROW));
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void aSuccessfulTrialClosesTheCircuit() {
        delegate.failure = new ProviderUnavailableException("down");
        assertThrows(ProviderUnavailableException.class, () -> source.forecastFor(AGOST, TOMORROW));

        time.advance(COOLDOWN);
        delegate.failure = null;
        source.forecastFor(AGOST, TOMORROW);
        source.forecastFor(AGOST, TOMORROW);

        assertEquals(3, delegate.calls.get());
        assertTrue(guard.rejection().isEmpty());
    }

    @Test
    void aMissingForecastDoesNotOpenTheCircuit() {
        delegate.failure = new ForecastNotAvailableException("no forecast for that date");

        assertThrows(ForecastNotAvailableException.class, () -> source.forecastFor(AGOST, TOMORROW));
        assertThrows(ForecastNotAvailableException.class, () -> source.forecastFor(AGOST, TOMORROW));

        assertEquals(2, delegate.calls.get());
        assertTrue(guard.rejection().isEmpty());
    }

    @Test
    void theLastGoodAnswerExpiresAfterTheStaleTtl() {
        var fresh = source.forecastFor(AGOST, TOMORROW);
        delegate.failure = new ProviderUnavailableException("down");

        time.advance(STALE_TTL.minusSeconds(1));
        assertSame(fresh, source.forecastFor(AGOST, TOMORROW));

        time.advance(Duration.ofSeconds(1));
        assertThrows(ProviderUnavailableException.class, () -> source.forecastFor(AGOST, TOMORROW));
    }
}
