package com.carlosruso.weather.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.application.service.MunicipalitySearchService;
import com.carlosruso.weather.application.service.TomorrowForecastService;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private static final String FORECAST_URL = "/api/municipios/03002/prediccion/dia-siguiente";
    /** Planted in exception messages: it must reach the log at most, never the body. */
    private static final String SECRET = "secret-api-key-123";
    private static final List<String> INTERNAL_MARKERS =
            List.of(SECRET, "Exception", "com.carlosruso", "java.", "org.springframework", "tools.jackson", "\tat ");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MunicipalitySearchService searchService;

    @MockitoBean
    private TomorrowForecastService forecastService;

    private MvcResult perform(String url, int expectedStatus) throws Exception {
        return mvc.perform(get(url))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn();
    }

    private static void assertNothingInternal(MvcResult result) throws Exception {
        var body = result.getResponse().getContentAsString();
        for (var marker : INTERNAL_MARKERS) {
            assertFalse(body.contains(marker), () -> "body exposes '" + marker + "': " + body);
        }
    }

    static List<String> invalidRequests() {
        return List.of(
                "/api/municipios",                                  // missing parameter
                "/api/municipios?nombre=" + "a".repeat(101),        // @Size
                "/api/municipios/0300a/prediccion/dia-siguiente",   // @Pattern
                FORECAST_URL + "?unidad=KELVIN");                   // type mismatch
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidParametersAre400ProblemDetails(String url) throws Exception {
        assertNothingInternal(perform(url, 400));
    }

    @Test
    void anInvalidUnitNamesTheParameterWithoutEchoingTheValue() throws Exception {
        var result = perform(FORECAST_URL + "?unidad=KELVIN", 400);

        var body = result.getResponse().getContentAsString();
        assertTrue(body.contains("Invalid value for parameter 'unidad'."), body);
        assertFalse(body.contains("KELVIN"), body);
        assertFalse(body.contains("convert"), body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/nada", "/api/municipios/03002/otra-cosa", "/favicon.ico"})
    void anUnknownRouteIs404WithAFixedText(String url) throws Exception {
        var result = perform(url, 404);

        assertNothingInternal(result);
        var detail = JsonPath.read(result.getResponse().getContentAsString(), "$.detail");
        assertEquals("Resource not found.", detail);
        assertFalse(result.getResponse().getContentAsString().contains("static resource"));
    }

    @Test
    void forecastNotAvailableIs404() throws Exception {
        when(forecastService.forecastFor(anyString(), any()))
                .thenThrow(new ForecastNotAvailableException("AEMET has no forecast " + SECRET));

        assertNothingInternal(perform(FORECAST_URL, 404));
    }

    @Test
    void providerUnavailableIs502AndLogsAWarningWithTheCause(CapturedOutput output) throws Exception {
        when(forecastService.forecastFor(anyString(), any())).thenThrow(new ProviderUnavailableException(
                "AEMET could not be reached " + SECRET, new IOException("Connect timed out")));

        assertNothingInternal(perform(FORECAST_URL, 502));

        assertTrue(output.getOut().lines().anyMatch(line -> line.contains("WARN")
                && line.contains("AEMET could not be reached")
                && line.contains("(cause: Connect timed out)")), output::getOut);
    }

    @Test
    void providerRateLimitedIs503WithRetryAfterAndLogsAWarning(CapturedOutput output) throws Exception {
        when(searchService.search(anyString()))
                .thenThrow(new ProviderRateLimitedException("AEMET rejected the request " + SECRET));

        var result = mvc.perform(get("/api/municipios?nombre=Ag"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertNothingInternal(result);
        assertTrue(output.getOut().lines().anyMatch(line -> line.contains("WARN")
                && line.contains("AEMET rejected the request")), output::getOut);
    }

    @Test
    void anUnexpectedErrorIs500AndLogsTheTraceAsError(CapturedOutput output) throws Exception {
        when(forecastService.forecastFor(anyString(), any()))
                .thenThrow(new IllegalStateException("bug with " + SECRET));

        assertNothingInternal(perform(FORECAST_URL, 500));

        var log = output.getOut();
        assertTrue(log.lines().anyMatch(line -> line.contains("ERROR") && line.contains("Unexpected error")), log);
        assertTrue(log.contains("java.lang.IllegalStateException"), log);
        assertTrue(log.contains("\tat "), "the stack trace is logged");
    }
}
