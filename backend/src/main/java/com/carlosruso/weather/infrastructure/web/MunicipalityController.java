package com.carlosruso.weather.infrastructure.web;

import com.carlosruso.weather.application.service.MunicipalitySearchService;
import com.carlosruso.weather.infrastructure.web.dto.MunicipalityResponse;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/municipios")
class MunicipalityController {

    private final MunicipalitySearchService searchService;

    MunicipalityController(MunicipalitySearchService searchService) {
        this.searchService = searchService;
    }

    /** "nombre" is required; a blank value is valid and returns an empty list. */
    @GetMapping
    List<MunicipalityResponse> search(@RequestParam("nombre") @Size(max = 100) String name) {
        return searchService.search(name).stream().map(MunicipalityResponse::from).toList();
    }
}
