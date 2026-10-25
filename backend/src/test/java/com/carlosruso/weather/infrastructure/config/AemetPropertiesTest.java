package com.carlosruso.weather.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Binding and validation as at startup, without the rest of the application. */
class AemetPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AemetProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("aemet.api-key=test-key");

    @Test
    void theCatalogKeepsItsLastGoodListLongerThanTheForecasts() {
        runner.run(context -> {
            var resilience = context.getBean(AemetProperties.class).resilience();
            assertThat(resilience.staleTtlMunicipios()).isEqualTo(Duration.ofDays(7));
            assertThat(resilience.staleTtl()).isEqualTo(Duration.ofHours(6));
        });
    }

    @Test
    void aCatalogStaleTtlNotLongerThanTheCacheTtlFailsAtStartup() {
        runner.withPropertyValues("aemet.cache.municipios-ttl=24h", "aemet.resilience.stale-ttl-municipios=24h")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("stale-ttl-municipios must be longer than aemet.cache.municipios-ttl"));
    }

    @Test
    void aCatalogStaleTtlLongerThanTheCacheTtlStarts() {
        runner.withPropertyValues("aemet.cache.municipios-ttl=24h", "aemet.resilience.stale-ttl-municipios=25h")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
