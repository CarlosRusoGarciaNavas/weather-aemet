package com.carlosruso.weather.domain.exception;

public class ForecastNotAvailableException extends RuntimeException {

    public ForecastNotAvailableException(String message) {
        super(message);
    }

    public ForecastNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
