package com.carlosruso.weather.infrastructure.web;

import com.carlosruso.weather.application.service.TomorrowForecastService;
import com.carlosruso.weather.domain.model.TemperatureUnit;
import com.carlosruso.weather.infrastructure.web.dto.ForecastResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/municipios")
class ForecastController {

    private final TomorrowForecastService forecastService;

    ForecastController(TomorrowForecastService forecastService) {
        this.forecastService = forecastService;
    }

    /** The code is AEMET's id without the "id" prefix: five digits. Any unit other than G_CEL or G_FAH is a 400. */
    @GetMapping("/{codigo}/prediccion/dia-siguiente")
    ForecastResponse tomorrow(
            @PathVariable("codigo") @Pattern(regexp = "^\\d{5}$") String code,
            @RequestParam(name = "unidad", defaultValue = "G_CEL") TemperatureUnit unit) {
        return ForecastResponse.from(forecastService.forecastFor(code, unit));
    }
}
