package com.carlosruso.weather.application.service;

import com.carlosruso.weather.domain.model.PrecipitationProbability;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import java.util.List;

/** Tomorrow's forecast ready to present. The average is in {@code unit} and not rounded. */
public record TomorrowForecast(
        double averageTemperature,
        TemperatureUnit unit,
        List<PrecipitationProbability> precipitationProbabilities) {
}
