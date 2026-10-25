package com.carlosruso.weather.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aemet")
public record AemetProperties(
        @NotBlank String apiKey,
        @NotBlank @DefaultValue("https://opendata.aemet.es/opendata/api") String baseUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue Cache cache,
        @DefaultValue Resilience resilience) {

    /**
     * Every successful request renews the last good list, so it is still there when the cache expires only if
     * it outlives the cache: otherwise a quiet gap leaves nothing to serve if AEMET fails right then.
     * Strictly longer: with equal TTLs both entries, written together, expire together.
     */
    @AssertTrue(message = "aemet.resilience.stale-ttl-municipios must be longer than aemet.cache.municipios-ttl")
    public boolean isStaleTtlMunicipiosLongerThanMunicipiosTtl() {
        if (cache == null || resilience == null) {
            return true; // only when built by hand in tests; binding always applies the defaults
        }
        return resilience.staleTtlMunicipios().compareTo(cache.municipiosTtl()) > 0;
    }

    public record Cache(
            @DefaultValue("24h") Duration municipiosTtl,
            @DefaultValue("30m") Duration forecastTtl,
            @DefaultValue("10000") long forecastMaxEntries,
            @DefaultValue("true") boolean warmupOnStartup) {
    }

    /**
     * cooldown: how long AEMET is left alone after a failure. staleTtl: how long the last good forecast
     * may be served when AEMET fails. staleTtlMunicipios: the same for the municipality list, longer than its
     * cache TTL. staleMaxEntries: bound for the last good forecasts.
     */
    public record Resilience(
            @DefaultValue("30s") Duration cooldown,
            @DefaultValue("6h") Duration staleTtl,
            @DefaultValue("7d") Duration staleTtlMunicipios,
            @DefaultValue("10000") long staleMaxEntries) {
    }
}