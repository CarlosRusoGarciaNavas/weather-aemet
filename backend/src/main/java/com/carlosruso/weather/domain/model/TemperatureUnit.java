package com.carlosruso.weather.domain.model;

public enum TemperatureUnit {
    G_CEL,
    G_FAH;

    public double fromCelsius(double celsius) {
        return switch (this) {
            case G_CEL -> celsius;
            case G_FAH -> celsius * 9 / 5 + 32;
        };
    }
}
