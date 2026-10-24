package com.carlosruso.weather.infrastructure.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlosruso.weather.application.service.MunicipalitySearchService;
import com.carlosruso.weather.domain.model.Municipality;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MunicipalityController.class)
class MunicipalityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MunicipalitySearchService searchService;

    @Test
    void returnsCodeAndNameExactly() throws Exception {
        when(searchService.search("Ag")).thenReturn(List.of(
                new Municipality("03002", "Agost"),
                new Municipality("30003", "Águilas")));

        mvc.perform(get("/api/municipios").param("nombre", "Ag"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"codigo": "03002", "nombre": "Agost"},
                         {"codigo": "30003", "nombre": "Águilas"}]
                        """, JsonCompareMode.STRICT));
    }

    /** URI-based request so MockMvc does not re-encode the %: the query arrives exactly as a browser sends it. */
    @Test
    void anAccentInTheQueryIsDecodedAsUtf8() throws Exception {
        when(searchService.search("Ág")).thenReturn(List.of(new Municipality("30003", "Águilas")));

        mvc.perform(get(URI.create("/api/municipios?nombre=%C3%81g")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"codigo": "30003", "nombre": "Águilas"}]
                        """, JsonCompareMode.STRICT));

        verify(searchService).search("Ág");
    }

    @Test
    void aBlankNameReturnsAnEmptyArray() throws Exception {
        when(searchService.search("  ")).thenReturn(List.of());

        mvc.perform(get("/api/municipios").param("nombre", "  "))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    void aMissingNameIs400() throws Exception {
        mvc.perform(get("/api/municipios"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchService);
    }

    @Test
    void aNameOf100CharactersIsAccepted() throws Exception {
        var name = "a".repeat(100);

        mvc.perform(get("/api/municipios").param("nombre", name))
                .andExpect(status().isOk());

        verify(searchService).search(name);
    }

    @Test
    void aNameLongerThan100CharactersIs400() throws Exception {
        mvc.perform(get("/api/municipios").param("nombre", "a".repeat(101)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchService);
    }
}
