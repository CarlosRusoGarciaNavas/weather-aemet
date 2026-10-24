package com.carlosruso.weather.infrastructure.aemet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import com.carlosruso.weather.domain.model.DayPeriod;
import com.carlosruso.weather.domain.model.PrecipitationProbability;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Uses a real AEMET answer for Agost made on 2026-10-19. Dates are chosen from its content:
 * 19th and 20th have hourly readings, 21st and 22nd only 12/24 h periods, 23rd to 25th no periods.
 */
class AemetForecastTranslatorTest {

    private static final double DELTA = 1e-9;
    private static final String AGOST = readFixture("/aemet/prediccion-agost.json");

    private static String readFixture(String path) {
        try (var in = AemetForecastTranslatorTest.class.getResourceAsStream(path)) {
            // The fixture is stored as AEMET sent it: ISO-8859-1.
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void readsTheRequestedDayByDate() {
        var forecast = AemetForecastTranslator.toDailyForecast(AGOST, LocalDate.of(2026, 10, 20));

        assertEquals(LocalDate.of(2026, 10, 20), forecast.date());
        assertEquals(List.of(14.0, 24.0, 20.0, 16.0), forecast.temperatureReadings());
        assertEquals(26.0, forecast.maxCelsius(), DELTA);
        assertEquals(14.0, forecast.minCelsius(), DELTA);
        assertEquals(18.5, forecast.averageCelsius(), DELTA);
    }

    @Test
    void keepsOnlyTheSixHourPeriodsAndRealZeros() {
        var forecast = AemetForecastTranslator.toDailyForecast(AGOST, LocalDate.of(2026, 10, 20));

        assertEquals(List.of(
                new PrecipitationProbability(25, DayPeriod.H00_06),
                new PrecipitationProbability(0, DayPeriod.H06_12),
                new PrecipitationProbability(10, DayPeriod.H12_18),
                new PrecipitationProbability(0, DayPeriod.H18_24)), forecast.precipitationProbabilities());
    }

    @Test
    void keepsAZeroReading() {
        var forecast = AemetForecastTranslator.toDailyForecast(AGOST, LocalDate.of(2026, 10, 19));

        assertEquals(List.of(0.0, 22.0, 17.0, 16.0), forecast.temperatureReadings());
    }

    @Test
    void dayWithoutReadingsKeepsMaxAndMin() {
        var forecast = AemetForecastTranslator.toDailyForecast(AGOST, LocalDate.of(2026, 10, 21));

        assertEquals(List.of(), forecast.temperatureReadings());
        assertEquals(19.0, forecast.averageCelsius(), DELTA);
    }

    @Test
    void dayWithOnlyTwelveHourPeriodsHasNoPrecipitation() {
        var forecast = AemetForecastTranslator.toDailyForecast(AGOST, LocalDate.of(2026, 10, 22));

        assertEquals(List.of(), forecast.precipitationProbabilities());
    }

    @Test
    void dayWithPeriodlessPrecipitationHasNoPrecipitation() {
        var forecast = AemetForecastTranslator.toDailyForecast(AGOST, LocalDate.of(2026, 10, 23));

        assertEquals(List.of(), forecast.precipitationProbabilities());
    }

    @Test
    void dayOutsideTheForecastIsNotAvailable() {
        assertThrows(ForecastNotAvailableException.class,
                () -> AemetForecastTranslator.toDailyForecast(AGOST, LocalDate.of(2026, 10, 26)));
    }

    @Test
    void acceptsNumbersAsTextAndSkipsEmptyAndNullValues() {
        var json = """
                [{"prediccion": {"dia": [{
                  "fecha": "2026-10-23T00:00:00",
                  "temperatura": {"maxima": "20", "minima": "10",
                    "dato": [{"value": "12", "hora": 6}, {"value": "", "hora": 12},
                             {"value": null, "hora": 18}, {"value": 16.5, "hora": 24}]},
                  "probPrecipitacion": [{"value": "", "periodo": "00-06"}, {"periodo": "06-12"},
                                        {"value": "30", "periodo": "12-18"}, {"value": 5, "periodo": "18-24"}]
                }]}}]
                """;

        var forecast = AemetForecastTranslator.toDailyForecast(json, LocalDate.of(2026, 10, 23));

        assertEquals(List.of(12.0, 16.5), forecast.temperatureReadings());
        assertEquals(20.0, forecast.maxCelsius(), DELTA);
        assertEquals(List.of(
                new PrecipitationProbability(30, DayPeriod.H12_18),
                new PrecipitationProbability(5, DayPeriod.H18_24)), forecast.precipitationProbabilities());
    }

    @Test
    void locatesTheDayByDateEvenWhenDaysAreOutOfOrder() {
        var json = """
                [{"prediccion": {"dia": [
                  {"fecha": "2026-10-24T00:00:00", "temperatura": {"maxima": 30, "minima": 20, "dato": []}},
                  {"fecha": "2026-10-23T00:00:00", "temperatura": {"maxima": 10, "minima": 0, "dato": []}}
                ]}}]
                """;

        var forecast = AemetForecastTranslator.toDailyForecast(json, LocalDate.of(2026, 10, 23));

        assertEquals(5.0, forecast.averageCelsius(), DELTA);
    }

    @Test
    void ignoresUnknownFields() {
        var json = """
                [{"nuevo": true, "prediccion": {"extra": 1, "dia": [{
                  "fecha": "2026-10-23T00:00:00", "otro": "x",
                  "temperatura": {"maxima": 20, "minima": 10, "dato": [], "sensacion": 3}
                }]}}]
                """;

        assertEquals(15.0,
                AemetForecastTranslator.toDailyForecast(json, LocalDate.of(2026, 10, 23)).averageCelsius(), DELTA);
    }

    @Test
    void dayWithoutReadingsAndWithoutMinimumIsNotAvailable() {
        var json = """
                [{"prediccion": {"dia": [{"fecha": "2026-10-23T00:00:00",
                  "temperatura": {"maxima": 20, "minima": "", "dato": []}}]}}]
                """;

        assertThrows(ForecastNotAvailableException.class,
                () -> AemetForecastTranslator.toDailyForecast(json, LocalDate.of(2026, 10, 23)));
    }

    @Test
    void dayWithoutTemperatureBlockIsNotAvailable() {
        var json = """
                [{"prediccion": {"dia": [{"fecha": "2026-10-23T00:00:00"}]}}]
                """;

        assertThrows(ForecastNotAvailableException.class,
                () -> AemetForecastTranslator.toDailyForecast(json, LocalDate.of(2026, 10, 23)));
    }

    @Test
    void skipsPercentagesOutOfRange() {
        var json = """
                [{"prediccion": {"dia": [{"fecha": "2026-10-23T00:00:00",
                  "temperatura": {"maxima": 20, "minima": 10},
                  "probPrecipitacion": [{"value": 120, "periodo": "00-06"}, {"value": 40, "periodo": "06-12"}]}]}}]
                """;

        assertEquals(List.of(new PrecipitationProbability(40, DayPeriod.H06_12)),
                AemetForecastTranslator.toDailyForecast(json, LocalDate.of(2026, 10, 23))
                        .precipitationProbabilities());
    }

    @Test
    void unexpectedStructureIsNotAvailable() {
        assertThrows(ForecastNotAvailableException.class,
                () -> AemetForecastTranslator.toDailyForecast("{\"estado\": 200}", LocalDate.of(2026, 10, 23)));
    }

    @Test
    void invalidJsonMeansTheProviderFailed() {
        assertThrows(ProviderUnavailableException.class,
                () -> AemetForecastTranslator.toDailyForecast("<html>", LocalDate.of(2026, 10, 23)));
    }
}
