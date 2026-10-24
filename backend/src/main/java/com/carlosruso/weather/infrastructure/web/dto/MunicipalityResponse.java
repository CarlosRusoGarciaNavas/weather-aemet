package com.carlosruso.weather.infrastructure.web.dto;

import com.carlosruso.weather.domain.model.Municipality;

/** Field names are fixed by the API contract, hence in Spanish. */
public record MunicipalityResponse(String codigo, String nombre) {

    public static MunicipalityResponse from(Municipality municipality) {
        return new MunicipalityResponse(municipality.code(), municipality.name());
    }
}
