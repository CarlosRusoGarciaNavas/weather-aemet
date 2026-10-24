package com.carlosruso.weather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WeatherBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeatherBackendApplication.class, args);
	}
}