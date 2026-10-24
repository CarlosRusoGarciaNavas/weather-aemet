package com.carlosruso.weather.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MunicipalityTest {

    @Test
    void keepsCodeAndName() {
        var municipality = new Municipality("03002", "Agost");

        assertEquals("03002", municipality.code());
        assertEquals("Agost", municipality.name());
    }

    @Test
    void rejectsNullCode() {
        assertThrows(NullPointerException.class, () -> new Municipality(null, "Agost"));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> new Municipality("03002", null));
    }
}
