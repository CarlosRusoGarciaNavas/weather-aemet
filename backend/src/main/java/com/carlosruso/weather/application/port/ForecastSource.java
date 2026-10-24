package com.carlosruso.weather.application.port;

import com.carlosruso.weather.domain.model.DailyForecast;
import java.time.LocalDate;

public interface ForecastSource {

    /**
     * @throws com.carlosruso.weather.domain.exception.ForecastNotAvailableException if the
     *         provider has no forecast for that date
     * @throws ProviderUnavailableException if the provider fails or answers with an error
     * @throws ProviderRateLimitedException if the provider rejects the request for exceeding its limits
     */
    DailyForecast forecastFor(String municipalityCode, LocalDate date);
}
