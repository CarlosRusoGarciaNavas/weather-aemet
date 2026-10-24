package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.domain.model.DailyForecast;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.cache.annotation.Cacheable;

/**
 * Caches one forecast per municipality and date. Not final: Spring proxies it to apply the cache.
 * sync = true: concurrent misses wait for a single call to the delegate. Exceptions are not cached.
 */
public class CachedForecastSource implements ForecastSource {

    private final ForecastSource delegate;

    public CachedForecastSource(ForecastSource delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.FORECASTS, key = "#municipalityCode + ':' + #date", sync = true)
    public DailyForecast forecastFor(String municipalityCode, LocalDate date) {
        return delegate.forecastFor(municipalityCode, date);
    }
}
