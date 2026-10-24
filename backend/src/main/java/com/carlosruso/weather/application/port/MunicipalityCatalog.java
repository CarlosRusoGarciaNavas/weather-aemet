package com.carlosruso.weather.application.port;

import com.carlosruso.weather.domain.model.Municipality;
import java.util.List;

public interface MunicipalityCatalog {

    /**
     * @throws ProviderUnavailableException if the provider fails or answers with an error
     * @throws ProviderRateLimitedException if the provider rejects the request for exceeding its limits
     */
    List<Municipality> findAll();
}
