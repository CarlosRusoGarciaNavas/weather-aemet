package com.carlosruso.weather.application.service;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.domain.exception.MunicipalityNotFoundException;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public class TomorrowForecastService {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final MunicipalityCatalog catalog;
    private final ForecastSource forecastSource;
    private final Clock clock;

    public TomorrowForecastService(MunicipalityCatalog catalog, ForecastSource forecastSource, Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.forecastSource = Objects.requireNonNull(forecastSource, "forecastSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Forecast for tomorrow in Europe/Madrid. A null unit means Celsius. The code is checked against the
     * catalog first, so an unknown code never reaches AEMET (which answers it with HTTP 200 and estado 404).
     */
    public TomorrowForecast forecastFor(String municipalityCode, TemperatureUnit unit) {
        Objects.requireNonNull(municipalityCode, "municipalityCode");
        if (!exists(municipalityCode)) {
            throw new MunicipalityNotFoundException("Unknown municipality code " + municipalityCode);
        }
        var requestedUnit = unit == null ? TemperatureUnit.G_CEL : unit;
        var tomorrow = LocalDate.ofInstant(clock.instant(), MADRID).plusDays(1);

        var forecast = forecastSource.forecastFor(municipalityCode, tomorrow);

        return new TomorrowForecast(
                requestedUnit.fromCelsius(forecast.averageCelsius()),
                requestedUnit,
                forecast.precipitationProbabilities());
    }

    /**
     * Called from outside the catalog on purpose: a default method on the port calling this.findAll() would
     * run inside the cached decorator and skip its @Cacheable proxy. Linear scan of ~8,000 entries.
     */
    private boolean exists(String municipalityCode) {
        return catalog.findAll().stream().anyMatch(municipality -> municipality.code().equals(municipalityCode));
    }
}
