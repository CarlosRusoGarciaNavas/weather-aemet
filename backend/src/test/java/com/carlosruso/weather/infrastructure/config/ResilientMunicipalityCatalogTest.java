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

    /**
     * The real TTLs on one clock: the 24 h cache in front of AEMET (as @Cacheable with sync = true would do)
     * and the catalog's 7 day last good list. A gap longer than 6 h used to leave nothing to serve.
     */
    @Test
    @SuppressWarnings("unchecked")
    void afterAQuietGapTheCacheHasExpiredButTheLastGoodListIsStillServed() {
        var primary = CacheConfig.municipalitiesCache(Duration.ofHours(24), time.ticker());
        MunicipalityCatalog cached = () -> (List<Municipality>) primary.get("all", key -> delegate.findAll());
        var chain = new ResilientMunicipalityCatalog(
                cached, guard, CacheConfig.staleCache(Duration.ofDays(7), 1, time.ticker()));
        var fresh = chain.findAll();

        time.advance(Duration.ofDays(3)); // no requests: longer than 6 h, shorter than 7 days
        delegate.failure = new ProviderUnavailableException("down");

        assertSame(fresh, chain.findAll());
        assertEquals(2, delegate.calls.get()); // the cache had expired, so AEMET was called and failed
    }
}
