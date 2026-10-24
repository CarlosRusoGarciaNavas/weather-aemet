package com.carlosruso.weather.infrastructure.web.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.carlosruso.weather.application.service.TomorrowForecast;
import com.carlosruso.weather.domain.model.DayPeriod;
import com.carlosruso.weather.domain.model.PrecipitationProbability;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ForecastResponseTest {

    private static ForecastResponse withAverage(double average) {
        return ForecastResponse.from(new TomorrowForecast(average, TemperatureUnit.G_CEL, List.of()));
    }

    @ParameterizedTest
    @CsvSource({
            "66.66, 66.7",
            "66.64, 66.6",
            "66.65, 66.7",  // HALF_UP on the tie
            "0.15, 0.2",    // new BigDecimal(0.15) would be 0.1499... and give 0.1
            "-2.25, -2.3",  // HALF_UP rounds ties away from zero
            "-0.04, 0.0",   // no "-0.0"
            "20, 20.0"      // always one decimal
    })
    void roundsToOneDecimalHalfUp(double average, String expected) {
        assertEquals(expected, withAverage(average).mediaTemperatura().toPlainString());
    }

    @Test
    void mapsUnitAndPeriodsKeepingTheirOrder() {
        var forecast = new TomorrowForecast(66.66, TemperatureUnit.G_FAH, List.of(
                new PrecipitationProbability(0, DayPeriod.H00_06),
                new PrecipitationProbability(15, DayPeriod.H12_18)));

        var response = ForecastResponse.from(forecast);

        assertEquals("G_FAH", response.unidadTemperatura());
        assertEquals(List.of(
                new ForecastResponse.Precipitation(0, "00-06"),
                new ForecastResponse.Precipitation(15, "12-18")), response.probPrecipitacion());
    }

    @Test
    void noPeriodsGiveAnEmptyList() {
        assertEquals(List.of(), withAverage(20).probPrecipitacion());
    }
}
