package com.carlosruso.weather.infrastructure.web.dto;

import com.carlosruso.weather.application.service.TomorrowForecast;
import com.carlosruso.weather.domain.model.PrecipitationProbability;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Field names are fixed by the API contract, hence in Spanish. */
public record ForecastResponse(
        BigDecimal mediaTemperatura,
        String unidadTemperatura,
        List<Precipitation> probPrecipitacion) {

    public record Precipitation(int probabilidad, String periodo) {

        static Precipitation from(PrecipitationProbability probability) {
            return new Precipitation(probability.percentage(), probability.period().label());
        }
    }

    /** Rounding to one decimal is presentation, so it happens here and not in the domain. */
    public static ForecastResponse from(TomorrowForecast forecast) {
        return new ForecastResponse(
                round(forecast.averageTemperature()),
                forecast.unit().name(),
                forecast.precipitationProbabilities().stream().map(Precipitation::from).toList());
    }

    /** valueOf goes through the shortest decimal text of the double: 0.15 rounds to 0.2, not 0.1. */
    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
