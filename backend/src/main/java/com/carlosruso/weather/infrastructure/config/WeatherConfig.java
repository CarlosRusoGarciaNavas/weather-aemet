package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.ForecastSource;
import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.service.MunicipalitySearchService;
import com.carlosruso.weather.application.service.TomorrowForecastService;
import com.carlosruso.weather.infrastructure.aemet.AemetForecastSource;
import com.carlosruso.weather.infrastructure.aemet.AemetHttpClient;
import com.carlosruso.weather.infrastructure.aemet.AemetMunicipalityCatalog;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the AEMET adapters to the application services, which know nothing about Spring. */
@Configuration
class WeatherConfig {

    @Bean
    AemetHttpClient aemetHttpClient(AemetProperties properties) {
        return new AemetHttpClient(properties);
    }

    @Bean
    MunicipalityCatalog municipalityCatalog(AemetHttpClient client) {
        return new AemetMunicipalityCatalog(client);
    }

    @Bean
    ForecastSource forecastSource(AemetHttpClient client) {
        return new AemetForecastSource(client);
    }

    @Bean
    MunicipalitySearchService municipalitySearchService(MunicipalityCatalog catalog) {
        return new MunicipalitySearchService(catalog);
    }

    @Bean
    TomorrowForecastService tomorrowForecastService(ForecastSource forecastSource, Clock clock) {
        return new TomorrowForecastService(forecastSource, clock);
    }
}
