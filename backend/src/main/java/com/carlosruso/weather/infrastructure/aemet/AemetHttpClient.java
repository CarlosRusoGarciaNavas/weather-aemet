package com.carlosruso.weather.infrastructure.aemet;

import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import com.carlosruso.weather.infrastructure.config.AemetProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AEMET OpenData client. Every endpoint answers in two steps: an envelope
 * {descripcion, estado, datos} and then the real payload at the {@code datos} URL.
 * Returns the payload as text; translating it is up to the callers.
 */
public class AemetHttpClient {

    private static final String API_KEY_HEADER = "api_key";
    private static final JsonMapper JSON = JsonMapper.shared();

    private final RestClient restClient;
    private final String allowedHost;

    public AemetHttpClient(AemetProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory(properties)), properties);
    }

    /** Receives the builder so tests can bind a mock server to it. */
    AemetHttpClient(RestClient.Builder builder, AemetProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .build();
        this.allowedHost = URI.create(properties.baseUrl()).getHost();
    }

    private static JdkClientHttpRequestFactory requestFactory(AemetProperties properties) {
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    public String fetchMunicipalities() {
        var envelope = get(restClient.get().uri("/maestro/municipios"));
        return get(restClient.get().uri(dataUrl(envelope, ProviderUnavailableException::new)));
    }

    /** @throws ForecastNotAvailableException if AEMET has no forecast for that code */
    public String fetchDailyForecast(String municipalityCode) {
        var envelope = get(restClient.get().uri("/prediccion/especifica/municipio/diaria/{code}", municipalityCode));
        return get(restClient.get().uri(dataUrl(envelope, ForecastNotAvailableException::new)));
    }

    /** Reads the envelope and returns the {@code datos} URL, checking it before following it. */
    private URI dataUrl(String envelopeText, Function<String, RuntimeException> notFound) {
        JsonNode envelope;
        try {
            envelope = JSON.readTree(envelopeText);
        } catch (JacksonException e) {
            throw new ProviderUnavailableException("AEMET sent an envelope that is not valid JSON", e);
        }
        var status = statusOf(envelope.path("estado"));
        if (status == 429) {
            throw new ProviderRateLimitedException("AEMET rejected the request: too many requests");
        }
        if (status == 404) {
            throw notFound.apply("AEMET has no data for this request");
        }
        if (status != 200) {
            throw new ProviderUnavailableException("AEMET answered with estado " + status);
        }
        var datos = envelope.path("datos");
        if (!datos.isString() || datos.asString().isBlank()) {
            throw new ProviderUnavailableException("AEMET envelope has no datos URL");
        }
        return checkedDataUrl(datos.asString());
    }

    /** The api_key header goes with every request, so we only follow https URLs on AEMET's own host. */
    private URI checkedDataUrl(String text) {
        URI uri;
        try {
            uri = URI.create(text.strip());
        } catch (IllegalArgumentException e) {
            throw new ProviderUnavailableException("AEMET sent an invalid datos URL", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost.equalsIgnoreCase(uri.getHost())) {
            throw new ProviderUnavailableException("AEMET datos URL is not https on " + allowedHost);
        }
        return uri;
    }

    /** {@code estado} may come as a number or as text. Anything else counts as an unknown status (-1). */
    private static int statusOf(JsonNode node) {
        if (node.isNumber()) {
            return node.intValue();
        }
        if (node.isString()) {
            try {
                return Integer.parseInt(node.asString().strip());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static String get(RestClient.RequestHeadersSpec<?> request) {
        byte[] body;
        try {
            body = request.exchange((req, response) -> {
                var status = response.getStatusCode().value();
                if (status == 429) {
                    throw new ProviderRateLimitedException("AEMET rejected the request: too many requests");
                }
                if (status < 200 || status > 299) {
                    throw new ProviderUnavailableException("AEMET answered HTTP " + status);
                }
                return response.getBody().readAllBytes();
            });
        } catch (RestClientException e) {
            // Timeouts and network errors. The message only has the URL; the key travels in a header.
            throw new ProviderUnavailableException("AEMET could not be reached", e);
        }
        if (body == null || body.length == 0) {
            throw new ProviderUnavailableException("AEMET answered with an empty body");
        }
        return decode(body);
    }

    /** AEMET mixes encodings: strict UTF-8 first, ISO-8859-1 if the bytes are not valid UTF-8. */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
