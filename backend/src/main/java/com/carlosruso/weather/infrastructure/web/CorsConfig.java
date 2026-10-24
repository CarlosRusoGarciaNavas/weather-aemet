package com.carlosruso.weather.infrastructure.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The API is read-only, so browsers may only send GET (plus the preflight, which Spring answers). */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties properties;

    CorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET");
    }
}
