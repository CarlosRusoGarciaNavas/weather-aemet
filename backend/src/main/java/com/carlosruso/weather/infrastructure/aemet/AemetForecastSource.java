package com.carlosruso.weather.infrastructure.aemet;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.domain.model.DailyForecast;
import java.time.LocalDate;
import java.util.Objects;

public class AemetForecastSource implements ForecastSource {

    private final AemetHttpClient client;

    public AemetForecastSource(AemetHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public DailyForecast forecastFor(String municipalityCode, LocalDate date) {
        return AemetForecastTranslator.toDailyForecast(client.fetchDailyForecast(municipalityCode), date);
    }
}
