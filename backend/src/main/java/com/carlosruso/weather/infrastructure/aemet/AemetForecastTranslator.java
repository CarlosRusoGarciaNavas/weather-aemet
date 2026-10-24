package com.carlosruso.weather.infrastructure.aemet;

import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import com.carlosruso.weather.domain.model.DailyForecast;
import com.carlosruso.weather.domain.model.DayPeriod;
import com.carlosruso.weather.domain.model.PrecipitationProbability;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Translates AEMET's daily forecast JSON into a {@link DailyForecast}. Pure function, no I/O. */
public final class AemetForecastTranslator {

    private static final JsonMapper JSON = JsonMapper.shared();

    private AemetForecastTranslator() {
    }

    /**
     * @throws ForecastNotAvailableException if the day is missing or has no usable temperatures
     * @throws ProviderUnavailableException if the text is not valid JSON
     */
    public static DailyForecast toDailyForecast(String json, LocalDate date) {
        var day = findDay(parse(json), date)
                .orElseThrow(() -> new ForecastNotAvailableException("AEMET has no forecast for " + date));

        var temperature = day.path("temperatura");
        var readings = new ArrayList<Double>();
        for (var reading : temperature.path("dato")) {
            number(reading.path("value")).ifPresent(readings::add);
        }
        var max = number(temperature.path("maxima")).orElse(null);
        var min = number(temperature.path("minima")).orElse(null);
        if (readings.isEmpty() && (max == null || min == null)) {
            throw new ForecastNotAvailableException("AEMET has no temperatures for " + date);
        }
        return new DailyForecast(date, readings, max, min, precipitation(day.path("probPrecipitacion")));
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (JacksonException e) {
            throw new ProviderUnavailableException("AEMET sent a forecast that is not valid JSON", e);
        }
    }

    /** The answer is an array with one element; days are located by {@code fecha}, not by position. */
    private static Optional<JsonNode> findDay(JsonNode root, LocalDate date) {
        for (var day : root.path(0).path("prediccion").path("dia")) {
            if (date.equals(dateOf(day.path("fecha")))) {
                return Optional.of(day);
            }
        }
        return Optional.empty();
    }

    /** {@code fecha} looks like "2026-10-20T00:00:00": only the date part matters. */
    private static LocalDate dateOf(JsonNode fecha) {
        if (!fecha.isString() || fecha.asString().length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(fecha.asString().substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Only the four 6-hour periods. Missing or invalid values are skipped, never turned into 0. */
    private static List<PrecipitationProbability> precipitation(JsonNode entries) {
        var byPeriod = new EnumMap<DayPeriod, PrecipitationProbability>(DayPeriod.class);
        for (var entry : entries) {
            var period = periodOf(entry.path("periodo"));
            var value = number(entry.path("value"));
            if (period.isEmpty() || value.isEmpty()) {
                continue;
            }
            var percentage = (int) Math.round(value.get());
            if (percentage >= 0 && percentage <= 100) {
                byPeriod.putIfAbsent(period.get(), new PrecipitationProbability(percentage, period.get()));
            }
        }
        return List.copyOf(byPeriod.values());
    }

    private static Optional<DayPeriod> periodOf(JsonNode periodo) {
        if (!periodo.isString()) {
            return Optional.empty();
        }
        var label = periodo.asString().strip();
        for (var period : DayPeriod.values()) {
            if (period.label().equals(label)) {
                return Optional.of(period);
            }
        }
        return Optional.empty();
    }

    /** AEMET sends numbers either as JSON numbers or as text; empty text and null mean "no value". */
    private static Optional<Double> number(JsonNode node) {
        if (node.isNumber()) {
            return Optional.of(node.doubleValue());
        }
        if (node.isString() && !node.asString().isBlank()) {
            try {
                var value = Double.parseDouble(node.asString().strip());
                return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
