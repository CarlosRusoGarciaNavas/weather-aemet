package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.DailyForecast;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.LocalDate;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the cached decorator: a cache hit never reaches AEMET, so only real calls can fail here.
 * While the circuit is open AEMET is not called. When AEMET fails, the last good forecast is served if there is
 * one. ForecastNotAvailableException is an answer, not a failure: it goes through and leaves the circuit alone.
 */
public class ResilientForecastSource implements ForecastSource {

    private static final Logger log = LoggerFactory.getLogger(ResilientForecastSource.class);

    record Key(String municipalityCode, LocalDate date) {
    }

    private final ForecastSource delegate;
    private final ProviderGuard guard;
    private final Cache<Key, DailyForecast> lastGood;

    public ResilientForecastSource(ForecastSource delegate, ProviderGuard guard, Cache<Key, DailyForecast> lastGood) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.lastGood = Objects.requireNonNull(lastGood, "lastGood");
    }

    @Override
    public DailyForecast forecastFor(String municipalityCode, LocalDate date) {
        var key = new Key(municipalityCode, date);

        var rejection = guard.rejection();
        if (rejection.isPresent()) {
            var stale = lastGood.getIfPresent(key);
            if (stale == null) {
                throw rejection.get();
            }
            log.debug("Circuit open: serving the last good forecast for {} on {}", municipalityCode, date);
            return stale;
        }

        try {
            var forecast = delegate.forecastFor(municipalityCode, date);
            guard.recordSuccess();
            lastGood.put(key, forecast);
            return forecast;
        } catch (ProviderUnavailableException | ProviderRateLimitedException e) {
            guard.recordFailure(e);
            var stale = lastGood.getIfPresent(key);
            if (stale == null) {
                throw e;
            }
            log.warn("AEMET failed ({}): serving the last good forecast for {} on {}",
                    e.getMessage(), municipalityCode, date);
            return stale;
        }
    }
}
