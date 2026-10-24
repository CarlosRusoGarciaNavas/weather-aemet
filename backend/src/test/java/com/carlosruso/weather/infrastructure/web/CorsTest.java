package com.carlosruso.weather.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlosruso.weather.application.service.MunicipalitySearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Default configuration: only the Angular dev server, only GET. */
@WebMvcTest(MunicipalityController.class)
class CorsTest {

    private static final String ANGULAR = "http://localhost:4200";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MunicipalitySearchService searchService;

    @Test
    void thePreflightAllowsGetFromTheAngularDevServer() throws Exception {
        mvc.perform(options("/api/municipios")
                        .header(HttpHeaders.ORIGIN, ANGULAR)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ANGULAR))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET"));
    }

    @Test
    void aGetFromTheAngularDevServerCarriesTheCorsHeader() throws Exception {
        mvc.perform(get("/api/municipios").param("nombre", "Ag").header(HttpHeaders.ORIGIN, ANGULAR))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ANGULAR));
    }

    @Test
    void otherMethodsAreRejected() throws Exception {
        mvc.perform(options("/api/municipios")
                        .header(HttpHeaders.ORIGIN, ANGULAR)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        mvc.perform(get("/api/municipios").param("nombre", "Ag").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
