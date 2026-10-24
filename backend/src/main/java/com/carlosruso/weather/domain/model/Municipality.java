package com.carlosruso.weather.domain.model;

import java.util.Objects;

public record Municipality(String code, String name) {

    public Municipality {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
