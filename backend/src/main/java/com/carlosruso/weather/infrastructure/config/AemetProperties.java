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
        @DefaultValue Cache cache) {

    public record Cache(
            @DefaultValue("24h") Duration municipiosTtl,
            @DefaultValue("30m") Duration forecastTtl,
            @DefaultValue("10000") long forecastMaxEntries,
            @DefaultValue("true") boolean warmupOnStartup) {
    }
}