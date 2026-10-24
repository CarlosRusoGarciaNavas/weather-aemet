package com.carlosruso.weather.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import com.carlosruso.weather.domain.model.DailyForecast;
import com.carlosruso.weather.domain.model.DayPeriod;
import com.carlosruso.weather.domain.model.PrecipitationProbability;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class TomorrowForecastServiceTest {

    private static final double DELTA = 1e-9;
    private static final String AGOST = "03002";
    private static final Clock MORNING = clockAt("2026-10-22T10:00:00Z");
    private static final List<PrecipitationProbability> PRECIPITATION = List.of(
            new PrecipitationProbability(0, DayPeriod.H00_06),
            new PrecipitationProbability(15, DayPeriod.H12_18));

    /** Fake that records the request and answers with a fixed forecast for the requested date. */
    private static class RecordingForecastSource implements ForecastSource {
        private final List<Double> readings;
        private String requestedCode;
        private LocalDate requestedDate;

        RecordingForecastSource(List<Double> readings) {
            this.readings = readings;
        }

        @Override
        public DailyForecast forecastFor(String municipalityCode, LocalDate date) {
            requestedCode = municipalityCode;
            requestedDate = date;
            return new DailyForecast(date, readings, null, null, PRECIPITATION);
        }
    }

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @ParameterizedTest
    @CsvSource({
            // Summer time (UTC+2): midday, last second of the day in Madrid and just after midnight
            "2026-10-22T10:00:00Z, 2026-10-23",
            "2026-10-22T21:59:59Z, 2026-10-23",
            "2026-10-22T22:30:00Z, 2026-10-24",
            // Winter time (UTC+1), crossing the new year
            "2026-12-31T22:59:59Z, 2027-01-01",
            "2026-12-31T23:30:00Z, 2027-01-02"
    })
    void asksForTomorrowInMadrid(String now, LocalDate expectedTomorrow) {
        var source = new RecordingForecastSource(List.of(10.0));
        var service = new TomorrowForecastService(source, clockAt(now));

        service.forecastFor(AGOST, TemperatureUnit.G_CEL);

        assertEquals(expectedTomorrow, source.requestedDate);
        assertEquals(AGOST, source.requestedCode);
    }

    @Test
    void nullUnitDefaultsToCelsius() {
        var service = new TomorrowForecastService(
                new RecordingForecastSource(List.of(14.0, 24.0, 20.0, 16.0)), MORNING);

        var result = service.forecastFor(AGOST, null);

        assertEquals(TemperatureUnit.G_CEL, result.unit());
        assertEquals(18.5, result.averageTemperature(), DELTA);
    }

    @Test
    void convertsTheAverageToFahrenheit() {
        var service = new TomorrowForecastService(
                new RecordingForecastSource(List.of(14.0, 24.0, 20.0, 16.0)), MORNING);

        var result = service.forecastFor(AGOST, TemperatureUnit.G_FAH);

        assertEquals(TemperatureUnit.G_FAH, result.unit());
        assertEquals(65.3, result.averageTemperature(), DELTA);
    }

    @Test
    void averageIsNotRounded() {
        var service = new TomorrowForecastService(new RecordingForecastSource(List.of(10.0, 10.0, 11.0)), MORNING);

        var result = service.forecastFor(AGOST, TemperatureUnit.G_CEL);

        assertEquals(31.0 / 3, result.averageTemperature(), DELTA);
    }

    @Test
    void keepsThePrecipitationProbabilities() {
        var service = new TomorrowForecastService(new RecordingForecastSource(List.of(10.0)), MORNING);

        assertEquals(PRECIPITATION, service.forecastFor(AGOST, TemperatureUnit.G_CEL).precipitationProbabilities());
    }

    static Stream<Arguments> sourceErrors() {
        return Stream.of(
                Arguments.of(new ProviderUnavailableException("AEMET down")),
                Arguments.of(new ProviderRateLimitedException("Too many requests")),
                Arguments.of(new ForecastNotAvailableException("No forecast for tomorrow")));
    }

    @ParameterizedTest
    @MethodSource("sourceErrors")
    void propagatesSourceErrors(RuntimeException error) {
        ForecastSource failing = (code, date) -> {
            throw error;
        };
        var service = new TomorrowForecastService(failing, MORNING);

        assertSame(error, assertThrows(RuntimeException.class, () -> service.forecastFor(AGOST, null)));
    }
}
