package com.carlosruso.weather.application.service;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.domain.model.Municipality;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class MunicipalitySearchService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Comparator<Municipality> BY_NAME =
            Comparator.comparing((Municipality m) -> normalize(m.name())).thenComparing(Municipality::name);

    private final MunicipalityCatalog catalog;

    public MunicipalitySearchService(MunicipalityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** Municipalities whose name starts with the prefix, ignoring case and accents. Blank prefix: no results. */
    public List<Municipality> search(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        var normalizedPrefix = normalize(prefix.strip());
        return catalog.findAll().stream()
                .filter(m -> normalize(m.name()).startsWith(normalizedPrefix))
                .sorted(BY_NAME)
                .toList();
    }

    private static String normalize(String text) {
        var decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
