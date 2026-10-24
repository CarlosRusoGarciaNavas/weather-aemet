package com.carlosruso.weather.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TemperatureUnitTest {

    private static final double DELTA = 1e-9;

    @Test
    void celsiusIsReturnedUnchanged() {
        assertEquals(21.5, TemperatureUnit.G_CEL.fromCelsius(21.5), DELTA);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 32",
            "100, 212",
            "-40, -40",
            "37, 98.6",
            "12.5, 54.5"
    })
    void convertsCelsiusToFahrenheit(double celsius, double expectedFahrenheit) {
        assertEquals(expectedFahrenheit, TemperatureUnit.G_FAH.fromCelsius(celsius), DELTA);
    }
}
