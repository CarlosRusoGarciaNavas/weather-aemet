package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.domain.model.Municipality;
import java.util.List;
import java.util.Objects;
import org.springframework.cache.annotation.Cacheable;

/**
 * Caches the whole list as a single entry. Not final: Spring proxies it to apply the cache.
 * sync = true: concurrent misses wait for a single call to the delegate. Exceptions are not cached.
 */
public class CachedMunicipalityCatalog implements MunicipalityCatalog {

    private final MunicipalityCatalog delegate;

    public CachedMunicipalityCatalog(MunicipalityCatalog delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.MUNICIPALITIES, sync = true)
    public List<Municipality> findAll() {
        return delegate.findAll();
    }
}
