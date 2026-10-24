package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlosruso.weather.application.service.MunicipalitySearchService;
import com.carlosruso.weather.infrastructure.aemet.AemetHttpClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Full context with the warm-up on. The client is stubbed in its factory, before the context starts,
 * because the warm-up runs as soon as the application is ready: earlier than any @BeforeEach.
 */
@SpringBootTest(properties = {"aemet.api-key=test-key", "aemet.cache.warmup-on-startup=true"})
class CatalogWarmupIntegrationTest {

    @TestBean
    private AemetHttpClient client;

    static AemetHttpClient client() {
        var client = mock(AemetHttpClient.class);
        when(client.fetchMunicipalities()).thenReturn(readFixture("/aemet/municipios-extracto.json"));
        return client;
    }

    @Autowired
    private MunicipalitySearchService searchService;

    private static String readFixture(String path) {
        try (var in = CatalogWarmupIntegrationTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theWarmUpFillsTheCacheOfTheDecorator() {
        verify(client, timeout(5_000).times(1)).fetchMunicipalities();

        // If the warm-up had used the raw adapter, this search would go to AEMET again.
        assertFalse(searchService.search("Ag").isEmpty());
        verify(client, times(1)).fetchMunicipalities();
    }
}
