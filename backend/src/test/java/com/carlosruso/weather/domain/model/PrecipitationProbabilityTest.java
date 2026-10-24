package com.carlosruso.weather.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrecipitationProbabilityTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void acceptsLimits(int percentage) {
        var probability = new PrecipitationProbability(percentage, DayPeriod.H06_12);

        assertEquals(percentage, probability.percentage());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void rejectsValuesOutsideZeroToHundred(int percentage) {
        assertThrows(IllegalArgumentException.class,
                () -> new PrecipitationProbability(percentage, DayPeriod.H06_12));
    }

    @Test
    void rejectsNullPeriod() {
        assertThrows(NullPointerException.class, () -> new PrecipitationProbability(50, null));
    }
}
