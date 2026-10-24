package com.carlosruso.weather.domain.exception;

public class MunicipalityNotFoundException extends RuntimeException {

    public MunicipalityNotFoundException(String message) {
        super(message);
    }
}
