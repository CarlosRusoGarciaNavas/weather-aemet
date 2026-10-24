package com.carlosruso.weather.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

    /** UTC on purpose: services apply their own time zone, so the clock only provides the instant. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
