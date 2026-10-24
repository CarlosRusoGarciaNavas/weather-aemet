package com.carlosruso.weather.domain.model;

public enum DayPeriod {
    H00_06("00-06"),
    H06_12("06-12"),
    H12_18("12-18"),
    H18_24("18-24");

    private final String label;

    DayPeriod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
