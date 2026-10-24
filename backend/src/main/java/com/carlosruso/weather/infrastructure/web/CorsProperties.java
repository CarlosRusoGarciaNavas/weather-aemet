package com.carlosruso.weather.infrastructure.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Origins allowed to call the API from a browser. Defaults to the Angular dev server. */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
}
