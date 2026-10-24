package com.carlosruso.weather.domain.model;

import java.util.Objects;

public record PrecipitationProbability(int percentage, DayPeriod period) {

    public PrecipitationProbability {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("percentage must be between 0 and 100: " + percentage);
        }
        Objects.requireNonNull(period, "period");
    }
}
