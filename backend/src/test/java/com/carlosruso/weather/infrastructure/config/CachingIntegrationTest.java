package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.service.MunicipalitySearchService;
import com.carlosruso.weather.application.service.TomorrowForecastService;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import com.carlosruso.weather.infrastructure.aemet.AemetHttpClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/** Full context with the HTTP client mocked: no network. The fixtures are real AEMET answers. */
@SpringBootTest(properties = "aemet.api-key=test-key")
class CachingIntegrationTest {

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
    private ApplicationContext context;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MunicipalitySearchService searchService;

    @Autowired
    private TomorrowForecastService forecastService;

    private static String readFixture(String path) {
        try (var in = CachingIntegrationTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void eachPortHasASingleBeanAndItIsTheProxiedDecorator() {
        var catalogs = context.getBeansOfType(MunicipalityCatalog.class).values();
        var sources = context.getBeansOfType(ForecastSource.class).values();

        assertEquals(1, catalogs.size());
        assertEquals(1, sources.size());
        assertInstanceOf(CachedMunicipalityCatalog.class, catalogs.iterator().next());
        assertInstanceOf(CachedForecastSource.class, sources.iterator().next());
        assertTrue(AopUtils.isAopProxy(catalogs.iterator().next()));
        assertTrue(AopUtils.isAopProxy(sources.iterator().next()));
    }

    @Test
    void theServicesReceiveTheDecorators() {
        assertSame(context.getBean(MunicipalityCatalog.class), ReflectionTestUtils.getField(searchService, "catalog"));
        assertSame(context.getBean(ForecastSource.class), ReflectionTestUtils.getField(forecastService, "forecastSource"));
    }

    @Test
    void twoSearchesFetchTheListFromAemetOnce() {
        when(client.fetchMunicipalities()).thenReturn(MUNICIPALITIES);

        var first = searchService.search("Ag");
        var second = searchService.search("ág");

        verify(client, times(1)).fetchMunicipalities();
        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    void twoForecastRequestsFetchFromAemetOnce() {
        when(client.fetchDailyForecast(AGOST)).thenReturn(AGOST_FORECAST);

        var celsius = forecastService.forecastFor(AGOST, TemperatureUnit.G_CEL);
        var fahrenheit = forecastService.forecastFor(AGOST, TemperatureUnit.G_FAH);

        verify(client, times(1)).fetchDailyForecast(AGOST);
        assertEquals(celsius.precipitationProbabilities(), fahrenheit.precipitationProbabilities());
    }
}
