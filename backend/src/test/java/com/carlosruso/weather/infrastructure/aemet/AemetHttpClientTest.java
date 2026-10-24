package com.carlosruso.weather.infrastructure.aemet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import com.carlosruso.weather.infrastructure.config.AemetProperties;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AemetHttpClientTest {

    private static final String API_KEY = "test-key";
    private static final String BASE_URL = "https://opendata.aemet.es/opendata/api";
    private static final String MUNICIPIOS_URL = BASE_URL + "/maestro/municipios";
    private static final String FORECAST_URL = BASE_URL + "/prediccion/especifica/municipio/diaria/03002";
    private static final String DATA_URL = "https://opendata.aemet.es/opendata/sh/66818001";

    private MockRestServiceServer server;
    private AemetHttpClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new AemetProperties(
                API_KEY, BASE_URL, Duration.ofSeconds(1), Duration.ofSeconds(1), null);
        client = new AemetHttpClient(builder, properties);
    }

    private static String envelope(int estado, String datos) {
        return """
                {"descripcion": "exito", "estado": %d, "datos": "%s", "metadatos": "%s"}
                """.formatted(estado, datos, DATA_URL);
    }

    /** AEMET sends JSON as text/plain, so the tests do too. */
    private void expectEnvelope(String url, String body) {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("api_key", API_KEY))
                .andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
    }

    @Test
    void followsTheDatosUrlAndReturnsItsBody() {
        expectEnvelope(MUNICIPIOS_URL, envelope(200, DATA_URL));
        server.expect(requestTo(DATA_URL))
                .andRespond(withSuccess("[{\"id\": \"id03002\", \"nombre\": \"Agost\"}]", MediaType.TEXT_PLAIN));

        assertEquals("[{\"id\": \"id03002\", \"nombre\": \"Agost\"}]", client.fetchMunicipalities());
        server.verify();
    }

    @Test
    void forecastUsesTheMunicipalityCodeInThePath() {
        expectEnvelope(FORECAST_URL, envelope(200, DATA_URL));
        server.expect(requestTo(DATA_URL)).andRespond(withSuccess("[]", MediaType.TEXT_PLAIN));

        assertEquals("[]", client.fetchDailyForecast("03002"));
        server.verify();
    }

    @Test
    void decodesUtf8() {
        expectEnvelope(MUNICIPIOS_URL, envelope(200, DATA_URL));
        server.expect(requestTo(DATA_URL)).andRespond(
                withSuccess("Águilas".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));

        assertEquals("Águilas", client.fetchMunicipalities());
    }

    @Test
    void fallsBackToLatin1WhenTheBytesAreNotUtf8() {
        expectEnvelope(MUNICIPIOS_URL, envelope(200, DATA_URL));
        server.expect(requestTo(DATA_URL)).andRespond(
                withSuccess("Águilas, Coruña".getBytes(StandardCharsets.ISO_8859_1), MediaType.TEXT_PLAIN));

        assertEquals("Águilas, Coruña", client.fetchMunicipalities());
    }

    @Test
    void http429IsRateLimited() {
        server.expect(requestTo(MUNICIPIOS_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(ProviderRateLimitedException.class, client::fetchMunicipalities);
    }

    @Test
    void estado429IsRateLimited() {
        expectEnvelope(MUNICIPIOS_URL, envelope(429, ""));

        assertThrows(ProviderRateLimitedException.class, client::fetchMunicipalities);
    }

    @Test
    void http429OnTheDatosUrlIsRateLimited() {
        expectEnvelope(MUNICIPIOS_URL, envelope(200, DATA_URL));
        server.expect(requestTo(DATA_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(ProviderRateLimitedException.class, client::fetchMunicipalities);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 502, 503})
    void httpErrorsMeanTheProviderIsUnavailable(int status) {
        server.expect(requestTo(MUNICIPIOS_URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("{\"estado\": " + status + "}"));

        assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);
    }

    @Test
    void errorMessagesNeverContainTheApiKey() {
        server.expect(requestTo(MUNICIPIOS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var error = assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);

        assertFalse(error.getMessage().contains(API_KEY));
    }

    @Test
    void timeoutMeansTheProviderIsUnavailable() {
        server.expect(requestTo(MUNICIPIOS_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        var error = assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);

        assertFalse(error.getMessage().contains(API_KEY));
    }

    @Test
    void emptyEnvelopeMeansTheProviderIsUnavailable() {
        // What AEMET does when the request has no key: HTTP 200 and nothing in the body.
        server.expect(requestTo(MUNICIPIOS_URL)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);
    }

    @Test
    void emptyDatosBodyMeansTheProviderIsUnavailable() {
        expectEnvelope(MUNICIPIOS_URL, envelope(200, DATA_URL));
        server.expect(requestTo(DATA_URL)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);
    }

    @Test
    void estado404OnTheForecastMeansNoForecast() {
        expectEnvelope(FORECAST_URL, """
                {"descripcion": "Error al obtener los datos", "estado": 404}
                """);

        assertThrows(ForecastNotAvailableException.class, () -> client.fetchDailyForecast("03002"));
    }

    @Test
    void estado404OnTheMunicipalitiesMeansTheProviderIsUnavailable() {
        expectEnvelope(MUNICIPIOS_URL, """
                {"descripcion": "Error al obtener los datos", "estado": 404}
                """);

        assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"estado\": 500, \"datos\": \"" + DATA_URL + "\"}",
            "{\"estado\": \"401\"}",
            "{\"descripcion\": \"sin estado\"}",
            "{\"estado\": 200}",
            "{\"estado\": 200, \"datos\": \"  \"}",
            "not json"})
    void badEnvelopesMeanTheProviderIsUnavailable(String body) {
        expectEnvelope(MUNICIPIOS_URL, body);

        assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example.com/opendata/sh/66818001",
            "http://opendata.aemet.es/opendata/sh/66818001",
            "https://opendata.aemet.es.evil.example.com/sh/1",
            "file:///etc/passwd"})
    void neverFollowsDatosUrlsOutsideTheAllowedHost(String datos) {
        expectEnvelope(MUNICIPIOS_URL, envelope(200, datos));

        assertThrows(ProviderUnavailableException.class, client::fetchMunicipalities);
        // Only the envelope was requested: the key never went to the other URL.
        server.verify();
    }
}
