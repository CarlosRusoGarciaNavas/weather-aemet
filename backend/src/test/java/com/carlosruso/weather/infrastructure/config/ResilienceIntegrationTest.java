package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.application.service.TomorrowForecastService;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import com.carlosruso.weather.infrastructure.aemet.AemetHttpClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Full context with the HTTP client mocked: the whole chain resilient -> cached -> AEMET adapter.
 * The clock is fixed, so the cooldown never ends during a test. Clearing the CacheManager plays the part
 * of the cache TTL running out; the last good answers live elsewhere and are not cleared.
 */
@SpringBootTest(properties = {"aemet.api-key=test-key", "aemet.cache.warmup-on-startup=false"})
class ResilienceIntegrationTest {

    private static final String MUNICIPALITIES = readFixture("/aemet/municipios-extracto.json");
    private static final String AGOST_FORECAST = readFixture("/aemet/prediccion-agost.json");
    private static final String AGOST = "03002";

    @MockitoBean
    private AemetHttpClient client;

    /** The fixture covers 2026-10-19 to 2026-10-25, so "tomorrow" must fall inside it. */
    @TestBean
    private Clock clock;

    static Clock clock() {
        return Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    }

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ProviderGuard guard;

    @Autowired
    private TomorrowForecastService forecastService;

    private static String readFixture(String path) {
        try (var in = ResilienceIntegrationTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The guard is a singleton of a context that may be reused: every test starts and ends with it closed. */
    @BeforeEach
    @AfterEach
    void reset() {
        expireTheCache();
        guard.recordSuccess();
    }

    private void expireTheCache() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void theCacheStillAvoidsCallsToAemet() {
        when(client.fetchMunicipalities()).thenReturn(MUNICIPALITIES);
        when(client.fetchDailyForecast(AGOST)).thenReturn(AGOST_FORECAST);

        var first = forecastService.forecastFor(AGOST, TemperatureUnit.G_CEL);
        var second = forecastService.forecastFor(AGOST, TemperatureUnit.G_CEL);

        assertEquals(first, second);
        verify(client, times(1)).fetchMunicipalities();
        verify(client, times(1)).fetchDailyForecast(AGOST);
    }

    @Test
    void whenAemetFailsTheLastGoodAnswerIsServedAndAemetIsLeftAlone() {
        when(client.fetchMunicipalities()).thenReturn(MUNICIPALITIES);
        when(client.fetchDailyForecast(AGOST)).thenReturn(AGOST_FORECAST);
        var fresh = forecastService.forecastFor(AGOST, TemperatureUnit.G_CEL);

        // The cache expires and AEMET is down: the forecast call fails and opens the circuit.
        expireTheCache();
        when(client.fetchDailyForecast(AGOST)).thenThrow(new ProviderUnavailableException("AEMET answered HTTP 500"));
        assertEquals(fresh, forecastService.forecastFor(AGOST, TemperatureUnit.G_CEL));
        verify(client, times(2)).fetchMunicipalities();
        verify(client, times(2)).fetchDailyForecast(AGOST);

        // Circuit open: neither the catalog nor the forecast reaches AEMET, both come from the last good answers.
        expireTheCache();
        assertEquals(fresh, forecastService.forecastFor(AGOST, TemperatureUnit.G_CEL));
        verify(client, times(2)).fetchMunicipalities();
        verify(client, times(2)).fetchDailyForecast(AGOST);
    }
}
