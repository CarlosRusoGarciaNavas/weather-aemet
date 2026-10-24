package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.MunicipalityNotFoundException;
import com.carlosruso.weather.domain.model.Municipality;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ResilientMunicipalityCatalogTest {

    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    private static final Duration STALE_TTL = Duration.ofHours(6);

    /** Counts calls and answers with a new list each time, or with the failure the test sets. */
    static class FakeCatalog implements MunicipalityCatalog {
        final AtomicInteger calls = new AtomicInteger();
        volatile RuntimeException failure;

        @Override
        public List<Municipality> findAll() {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return List.of(new Municipality("03002", "Agost"));
        }
    }

    private final FakeTime time = new FakeTime();
    private final FakeCatalog delegate = new FakeCatalog();
    private final ProviderGuard guard = new ProviderGuard(time.clock(), COOLDOWN);
    private final ResilientMunicipalityCatalog catalog = new ResilientMunicipalityCatalog(
            delegate, guard, CacheConfig.staleCache(STALE_TTL, 1, time.ticker()));

    @Test
    void aSuccessIsReturnedAndKeptAsTheLastGoodAnswer(CapturedOutput output) {
        var fresh = catalog.findAll();

        delegate.failure = new ProviderUnavailableException("down");
        var stale = catalog.findAll();

        assertSame(fresh, stale);
        assertEquals(2, delegate.calls.get());
        assertTrue(output.getOut().lines().anyMatch(line -> line.contains("WARN")
                && line.contains("serving the last good municipality list")), output::getOut);
    }

    @Test
    void aFailureWithoutALastGoodAnswerIsRethrown() {
        var failure = new ProviderRateLimitedException("too many requests");
        delegate.failure = failure;

        assertSame(failure, assertThrows(ProviderRateLimitedException.class, catalog::findAll));
    }

    @Test
    void whileOpenTheDelegateIsNotCalled() {
        var fresh = catalog.findAll();
        delegate.failure = new ProviderUnavailableException("down");
        catalog.findAll(); // opens the circuit

        assertSame(fresh, catalog.findAll());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void aFailureReportedByTheForecastsAlsoStopsTheCatalog() {
        guard.recordFailure(new ProviderUnavailableException("forecast failed"));

        assertThrows(ProviderUnavailableException.class, catalog::findAll);
        assertEquals(0, delegate.calls.get());

        time.advance(COOLDOWN);
        catalog.findAll();
        assertEquals(1, delegate.calls.get());
    }

    @Test
    void aBusinessNotFoundDoesNotOpenTheCircuit() {
        delegate.failure = new MunicipalityNotFoundException("unknown code");

        assertThrows(MunicipalityNotFoundException.class, catalog::findAll);
        assertThrows(MunicipalityNotFoundException.class, catalog::findAll);

        assertEquals(2, delegate.calls.get());
        assertTrue(guard.rejection().isEmpty());
    }

    @Test
    void theLastGoodAnswerExpiresAfterTheStaleTtl() {
        catalog.findAll();
        delegate.failure = new ProviderUnavailableException("down");

        time.advance(STALE_TTL);

        assertThrows(ProviderUnavailableException.class, catalog::findAll);
    }
}
