package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.service.MunicipalitySearchService;
import com.carlosruso.weather.application.service.TomorrowForecastService;
import com.carlosruso.weather.domain.model.DailyForecast;
import com.carlosruso.weather.domain.model.Municipality;
import com.carlosruso.weather.infrastructure.aemet.AemetForecastSource;
import com.carlosruso.weather.infrastructure.aemet.AemetHttpClient;
import com.carlosruso.weather.infrastructure.aemet.AemetMunicipalityCatalog;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the AEMET adapters to the application services, which know nothing about Spring.
 * Each port is a chain: resilient decorator -> cached decorator (Spring proxy) -> AEMET adapter.
 * The resilient one is outside, so a cache hit is never affected by the circuit and only real calls can fail.
 */
@Configuration
class WeatherConfig {

    @Bean
    AemetHttpClient aemetHttpClient(AemetProperties properties) {
        return new AemetHttpClient(properties);
    }

    /** One guard for both ports: the catalog and the forecasts go to the same provider. */
    @Bean
    ProviderGuard providerGuard(AemetProperties properties, Clock clock) {
        return new ProviderGuard(clock, properties.resilience().cooldown());
    }

    /**
     * A bean so that Spring proxies it and applies @Cacheable, but not injectable by type:
     * only the resilient decorator uses it. The AEMET adapter is not a bean.
     */
    @Bean(autowireCandidate = false)
    CachedMunicipalityCatalog cachedMunicipalityCatalog(AemetHttpClient client) {
        return new CachedMunicipalityCatalog(new AemetMunicipalityCatalog(client));
    }

    /** Same as the catalog: proxied by Spring, but not injectable by type. */
    @Bean(autowireCandidate = false)
    CachedForecastSource cachedForecastSource(AemetHttpClient client) {
        return new CachedForecastSource(new AemetForecastSource(client));
    }

    /**
     * The only injectable MunicipalityCatalog. Calling cachedMunicipalityCatalog(...) inside a @Configuration class
     * returns the container's bean, that is the proxy with the cache, not a new instance.
     */
    @Bean
    MunicipalityCatalog municipalityCatalog(
            AemetHttpClient client, ProviderGuard guard, AemetProperties properties,
            ObjectProvider<MeterRegistry> meterRegistry) {
        Cache<String, List<Municipality>> lastGood =
                CacheConfig.staleCache(properties.resilience().staleTtlMunicipios(), 1, Ticker.systemTicker());
        meterRegistry.ifAvailable(registry -> CaffeineCacheMetrics.monitor(registry, lastGood, "stale-municipalities"));
        return new ResilientMunicipalityCatalog(cachedMunicipalityCatalog(client), guard, lastGood);
    }

    /** The only injectable ForecastSource; see municipalityCatalog. */
    @Bean
    ForecastSource forecastSource(
            AemetHttpClient client, ProviderGuard guard, AemetProperties properties,
            ObjectProvider<MeterRegistry> meterRegistry) {
        var resilience = properties.resilience();
        Cache<ResilientForecastSource.Key, DailyForecast> lastGood =
                CacheConfig.staleCache(resilience.staleTtl(), resilience.staleMaxEntries(), Ticker.systemTicker());
        meterRegistry.ifAvailable(registry -> CaffeineCacheMetrics.monitor(registry, lastGood, "stale-forecasts"));
        return new ResilientForecastSource(cachedForecastSource(client), guard, lastGood);
    }

    @Bean
    MunicipalitySearchService municipalitySearchService(MunicipalityCatalog catalog) {
        return new MunicipalitySearchService(catalog);
    }

    @Bean
    TomorrowForecastService tomorrowForecastService(
            MunicipalityCatalog catalog, ForecastSource forecastSource, Clock clock) {
        return new TomorrowForecastService(catalog, forecastSource, clock);
    }
}
