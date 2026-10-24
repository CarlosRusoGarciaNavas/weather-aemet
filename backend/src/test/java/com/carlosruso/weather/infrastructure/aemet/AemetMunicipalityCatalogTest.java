package com.carlosruso.weather.infrastructure.aemet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.Municipality;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The extract has seven real entries from AEMET's list, stored in ISO-8859-1 as AEMET sends it. */
class AemetMunicipalityCatalogTest {

    private static final String EXTRACT = readFixture("/aemet/municipios-extracto.json");

    private static String readFixture(String path) {
        try (var in = AemetMunicipalityCatalogTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void readsTheRealExtractWithoutTheIdPrefix() {
        var municipalities = AemetMunicipalityCatalog.toMunicipalities(EXTRACT);

        assertEquals(7, municipalities.size());
        assertTrue(municipalities.contains(new Municipality("03002", "Agost")));
    }

    @Test
    void keepsAccentedNames() {
        var municipalities = AemetMunicipalityCatalog.toMunicipalities(EXTRACT);

        assertTrue(municipalities.contains(new Municipality("30003", "Águilas")));
        assertTrue(municipalities.contains(new Municipality("05019", "Ávila")));
        assertTrue(municipalities.contains(new Municipality("15030", "Coruña, A")));
    }

    @Test
    void skipsEntriesWithoutIdOrName() {
        var json = """
                [{"id": "id03002", "nombre": "Agost"},
                 {"nombre": "Sin id"},
                 {"id": "", "nombre": "Id vacío"},
                 {"id": "id99999"},
                 {"id": "id99998", "nombre": "  "},
                 {"id": "id99997", "nombre": null}]
                """;

        assertEquals(List.of(new Municipality("03002", "Agost")), AemetMunicipalityCatalog.toMunicipalities(json));
    }

    @Test
    void anIdThatIsOnlyThePrefixIsSkipped() {
        var json = """
                [{"id": "id", "nombre": "Raro"}, {"id": "id03002", "nombre": "Agost"}]
                """;

        assertEquals(List.of(new Municipality("03002", "Agost")), AemetMunicipalityCatalog.toMunicipalities(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"nombre\": \"Sin id\"}]", "{\"estado\": 200}", "not json"})
    void emptyOrInvalidListMeansTheProviderFailed(String json) {
        assertThrows(ProviderUnavailableException.class, () -> AemetMunicipalityCatalog.toMunicipalities(json));
    }
}
