package com.carlosruso.weather.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Forecast for a single day. Temperatures are always in Celsius.
 * {@code maxCelsius} and {@code minCelsius} may be null when AEMET does not send them.
 */
public record DailyForecast(
        LocalDate date,
        List<Double> temperatureReadings,
        Double maxCelsius,
        Double minCelsius,
        List<PrecipitationProbability> precipitationProbabilities) {

    public DailyForecast {
        Objects.requireNonNull(date, "date");
        temperatureReadings = temperatureReadings == null ? List.of() : List.copyOf(temperatureReadings);
        precipitationProbabilities = precipitationProbabilities == null
                ? List.of()
                : List.copyOf(precipitationProbabilities);
        if (temperatureReadings.isEmpty() && (maxCelsius == null || minCelsius == null)) {
            throw new IllegalArgumentException(
                    "A daily forecast needs temperature readings or both max and min: " + date);
        }
    }

    /** Mean of the readings; if there are none, (max + min) / 2. Not rounded. */
    public double averageCelsius() {
        if (!temperatureReadings.isEmpty()) {
            return temperatureReadings.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        }
        return (maxCelsius + minCelsius) / 2;
    }
}
