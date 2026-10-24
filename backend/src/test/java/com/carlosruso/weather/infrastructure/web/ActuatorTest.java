package com.carlosruso.weather.infrastructure.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The container healthcheck relies on /actuator/health; nothing else of the actuator may be reachable. */
@SpringBootTest(properties = {"aemet.api-key=test-key", "aemet.cache.warmup-on-startup=false"})
@AutoConfigureMockMvc
class ActuatorTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsUpWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void theDiscoveryPageOnlyListsHealth() throws Exception {
        mvc.perform(get("/actuator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links", aMapWithSize(3)))
                .andExpect(jsonPath("$._links", hasKey("self")))
                .andExpect(jsonPath("$._links", hasKey("health")))
                .andExpect(jsonPath("$._links", hasKey("health-path")));
    }

    @Test
    void otherEndpointsAreNotExposed() throws Exception {
        for (String endpoint : new String[] {"env", "beans", "configprops", "info", "metrics", "caches"}) {
            mvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
        }
    }
}
