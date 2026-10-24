package com.carlosruso.weather.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlosruso.weather.application.service.MunicipalitySearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The property replaces the default origin instead of adding to it. */
@WebMvcTest(controllers = MunicipalityController.class,
        properties = "cors.allowed-origins=https://weather.example.com,https://www.weather.example.com")
class CustomCorsOriginTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MunicipalitySearchService searchService;

    @Test
    void theConfiguredOriginsAreAllowed() throws Exception {
        for (var origin : new String[] {"https://weather.example.com", "https://www.weather.example.com"}) {
            mvc.perform(get("/api/municipios").param("nombre", "Ag").header(HttpHeaders.ORIGIN, origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
    }

    @Test
    void theDefaultOriginIsNoLongerAllowed() throws Exception {
        mvc.perform(get("/api/municipios").param("nombre", "Ag").header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isForbidden());
    }
}
