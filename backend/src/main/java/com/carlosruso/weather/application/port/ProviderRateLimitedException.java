package com.carlosruso.weather.application.port;

public class ProviderRateLimitedException extends RuntimeException {

    public ProviderRateLimitedException(String message) {
        super(message);
    }
}
