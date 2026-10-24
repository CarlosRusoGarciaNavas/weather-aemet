package com.carlosruso.weather.application.service;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public class TomorrowForecastService {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ForecastSource forecastSource;
    private final Clock clock;

    public TomorrowForecastService(ForecastSource forecastSource, Clock clock) {
        this.forecastSource = Objects.requireNonNull(forecastSource, "forecastSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Forecast for tomorrow in Europe/Madrid. A null unit means Celsius. */
    public TomorrowForecast forecastFor(String municipalityCode, TemperatureUnit unit) {
        Objects.requireNonNull(municipalityCode, "municipalityCode");
        var requestedUnit = unit == null ? TemperatureUnit.G_CEL : unit;
        var tomorrow = LocalDate.ofInstant(clock.instant(), MADRID).plusDays(1);

        var forecast = forecastSource.forecastFor(municipalityCode, tomorrow);

        return new TomorrowForecast(
                requestedUnit.fromCelsius(forecast.averageCelsius()),
                requestedUnit,
                forecast.precipitationProbabilities());
    }
}
