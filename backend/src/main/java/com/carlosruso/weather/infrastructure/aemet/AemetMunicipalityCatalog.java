package com.carlosruso.weather.infrastructure.aemet;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.Municipality;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class AemetMunicipalityCatalog implements MunicipalityCatalog {

    private static final String ID_PREFIX = "id";
    private static final JsonMapper JSON = JsonMapper.shared();

    private final AemetHttpClient client;

    public AemetMunicipalityCatalog(AemetHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<Municipality> findAll() {
        return toMunicipalities(client.fetchMunicipalities());
    }

    /** Entries without id or name are skipped. An empty catalog means AEMET answered badly. */
    static List<Municipality> toMunicipalities(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException e) {
            throw new ProviderUnavailableException("AEMET sent a municipality list that is not valid JSON", e);
        }
        if (!root.isArray()) {
            throw new ProviderUnavailableException("AEMET sent a municipality list that is not a JSON array");
        }
        var municipalities = new ArrayList<Municipality>();
        for (var entry : root) {
            var code = codeOf(entry.path("id"));
            var name = text(entry.path("nombre"));
            if (!code.isEmpty() && !name.isEmpty()) {
                municipalities.add(new Municipality(code, name));
            }
        }
        if (municipalities.isEmpty()) {
            throw new ProviderUnavailableException("AEMET sent an empty municipality list");
        }
        return List.copyOf(municipalities);
    }

    /** "id03002" -> "03002". */
    private static String codeOf(JsonNode id) {
        var value = text(id);
        return value.startsWith(ID_PREFIX) ? value.substring(ID_PREFIX.length()) : value;
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString().strip() : "";
    }
}
