package com.carlosruso.weather.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.Municipality;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MunicipalitySearchServiceTest {

    private static final Municipality AGOST = new Municipality("03002", "Agost");
    private static final Municipality AGUILAS = new Municipality("30003", "Águilas");
    private static final Municipality ALBACETE = new Municipality("02003", "Albacete");
    private static final Municipality AVILA = new Municipality("05019", "Ávila");

    private final MunicipalitySearchService service =
            new MunicipalitySearchService(() -> List.of(AVILA, ALBACETE, AGUILAS, AGOST));

    @Test
    void findsByPrefixIgnoringCase() {
        assertEquals(List.of(AGOST), service.search("AGO"));
    }

    @Test
    void prefixWithoutAccentMatchesAccentedName() {
        assertEquals(List.of(AGUILAS), service.search("agu"));
    }

    @Test
    void accentedUppercasePrefixMatchesNameWithoutAccent() {
        assertEquals(List.of(AGOST), service.search("ÁGO"));
    }

    @Test
    void matchesOnlyAtTheStartOfTheName() {
        assertEquals(List.of(), service.search("gost"));
    }

    @Test
    void returnsEmptyListWhenNothingMatches() {
        assertEquals(List.of(), service.search("Zamora"));
    }

    @Test
    void resultsAreSortedByNameIgnoringAccents() {
        assertEquals(List.of(AGOST, AGUILAS, ALBACETE, AVILA), service.search("a"));
    }

    @Test
    void surroundingSpacesAreIgnored() {
        assertEquals(List.of(AGOST), service.search("  ago "));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void blankPrefixReturnsNoResults(String prefix) {
        assertEquals(List.of(), service.search(prefix));
    }

    @Test
    void propagatesCatalogErrors() {
        var error = new ProviderUnavailableException("AEMET down");
        var failing = new MunicipalitySearchService(() -> {
            throw error;
        });

        assertSame(error, assertThrows(ProviderUnavailableException.class, () -> failing.search("ag")));
    }
}
