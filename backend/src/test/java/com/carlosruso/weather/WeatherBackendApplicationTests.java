package com.carlosruso.weather;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "aemet.api-key=test-key")
class WeatherBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
