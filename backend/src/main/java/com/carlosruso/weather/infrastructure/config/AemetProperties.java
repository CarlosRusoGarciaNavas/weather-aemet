package com.carlosruso.weather.infrastructure.config;

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

    public record Cache(
            @DefaultValue("24h") Duration municipiosTtl,
            @DefaultValue("30m") Duration forecastTtl,
            @DefaultValue("10000") long forecastMaxEntries,
            @DefaultValue("true") boolean warmupOnStartup) {
    }

    /**
     * cooldown: how long AEMET is left alone after a failure. staleTtl: how long the last good answer
     * may be served when AEMET fails. staleMaxEntries: bound for the last good forecasts.
     */
    public record Resilience(
            @DefaultValue("30s") Duration cooldown,
            @DefaultValue("6h") Duration staleTtl,
            @DefaultValue("10000") long staleMaxEntries) {
    }
}