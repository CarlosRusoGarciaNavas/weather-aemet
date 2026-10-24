package com.carlosruso.weather.infrastructure.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlosruso.weather.application.service.TomorrowForecast;
import com.carlosruso.weather.application.service.TomorrowForecastService;
import com.carlosruso.weather.domain.model.DayPeriod;
import com.carlosruso.weather.domain.model.PrecipitationProbability;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ForecastController.class)
class ForecastControllerTest {

    private static final String AGOST = "03002";
    private static final String URL = "/api/municipios/{codigo}/prediccion/dia-siguiente";
    /** The example from the assignment. */
    private static final List<PrecipitationProbability> PRECIPITATION = List.of(
            new PrecipitationProbability(5, DayPeriod.H00_06),
            new PrecipitationProbability(10, DayPeriod.H06_12),
            new PrecipitationProbability(10, DayPeriod.H12_18),
            new PrecipitationProbability(5, DayPeriod.H18_24));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TomorrowForecastService forecastService;

    @Test
    void theJsonHasExactlyTheContractFormat() throws Exception {
        when(forecastService.forecastFor(AGOST, TemperatureUnit.G_FAH))
                .thenReturn(new TomorrowForecast(66.66, TemperatureUnit.G_FAH, PRECIPITATION));

        mvc.perform(get(URL, AGOST).param("unidad", "G_FAH"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"mediaTemperatura": 66.7,
                         "unidadTemperatura": "G_FAH",
                         "probPrecipitacion": [
                           {"probabilidad": 5, "periodo": "00-06"},
                           {"probabilidad": 10, "periodo": "06-12"},
                           {"probabilidad": 10, "periodo": "12-18"},
                           {"probabilidad": 5, "periodo": "18-24"}]}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void withoutUnitTheServiceIsAskedForCelsius() throws Exception {
        when(forecastService.forecastFor(AGOST, TemperatureUnit.G_CEL))
                .thenReturn(new TomorrowForecast(19.25, TemperatureUnit.G_CEL, List.of()));

        mvc.perform(get(URL, AGOST))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"mediaTemperatura": 19.3, "unidadTemperatura": "G_CEL", "probPrecipitacion": []}
                        """, JsonCompareMode.STRICT));

        verify(forecastService).forecastFor(AGOST, TemperatureUnit.G_CEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"KELVIN", "g_fah", "CELSIUS"})
    void anInvalidUnitIs400(String unit) throws Exception {
        mvc.perform(get(URL, AGOST).param("unidad", unit))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(forecastService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3002", "030021", "0300a", "id03002", "03 02"})
    void anInvalidCodeIs400(String code) throws Exception {
        mvc.perform(get(URL, code))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(forecastService);
    }
}
