package com.carlosruso.weather.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyForecastTest {

    private static final double DELTA = 1e-9;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);

    @Test
    void averageIsTheMeanOfTheReadings() {
        var forecast = new DailyForecast(DATE, List.of(10.0, 14.0, 20.0, 16.0), 22.0, 8.0, List.of());

        assertEquals(15.0, forecast.averageCelsius(), DELTA);
    }

    @Test
    void realAgostForecastAveragesReadingsAndConvertsToFahrenheit() {
        var forecast = new DailyForecast(
                LocalDate.of(2026, 10, 20), List.of(14.0, 24.0, 20.0, 16.0), 26.0, 14.0, List.of());

        assertEquals(18.5, forecast.averageCelsius(), DELTA);
        assertEquals(65.3, TemperatureUnit.G_FAH.fromCelsius(forecast.averageCelsius()), DELTA);
    }

    @Test
    void readingsTakePrecedenceOverMaxAndMin() {
        var forecast = new DailyForecast(DATE, List.of(10.0, 12.0), 30.0, 0.0, List.of());

        assertEquals(11.0, forecast.averageCelsius(), DELTA);
    }

    @Test
    void averageFallsBackToMaxAndMinWithoutReadings() {
        var forecast = new DailyForecast(DATE, List.of(), 21.0, 8.0, List.of());

        assertEquals(14.5, forecast.averageCelsius(), DELTA);
    }

    @Test
    void averageIsNotRounded() {
        var forecast = new DailyForecast(DATE, List.of(10.0, 10.0, 11.0), null, null, List.of());

        assertEquals(31.0 / 3, forecast.averageCelsius(), DELTA);
    }

    @Test
    void negativeTemperaturesAreAveraged() {
        var forecast = new DailyForecast(DATE, null, 2.0, -5.0, null);

        assertEquals(-1.5, forecast.averageCelsius(), DELTA);
    }

    @Test
    void rejectsForecastWithoutReadingsNorMaxAndMin() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailyForecast(DATE, List.of(), null, null, List.of()));
    }

    @Test
    void rejectsForecastWithoutReadingsAndOnlyMax() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailyForecast(DATE, List.of(), 20.0, null, List.of()));
    }

    @Test
    void rejectsForecastWithoutDate() {
        assertThrows(NullPointerException.class,
                () -> new DailyForecast(null, List.of(10.0), null, null, List.of()));
    }

    @Test
    void rejectsNullInsideTheReadings() {
        assertThrows(NullPointerException.class,
                () -> new DailyForecast(DATE, Arrays.asList(10.0, null), null, null, List.of()));
    }

    @Test
    void nullPrecipitationProbabilitiesBecomeEmptyList() {
        var forecast = new DailyForecast(DATE, List.of(10.0), null, null, null);

        assertEquals(List.of(), forecast.precipitationProbabilities());
    }

    @Test
    void readingsAreCopiedDefensively() {
        var readings = new ArrayList<>(List.of(10.0, 20.0));
        var forecast = new DailyForecast(DATE, readings, null, null, List.of());

        readings.add(90.0);

        assertEquals(15.0, forecast.averageCelsius(), DELTA);
    }
}
