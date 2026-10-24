package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.Municipality;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the cached decorator: a cache hit never reaches AEMET, so only real calls can fail here.
 * While the circuit is open AEMET is not called. When AEMET fails, the last good list is served if there is one.
 * Any other exception (MunicipalityNotFoundException included) goes through and leaves the circuit alone.
 */
public class ResilientMunicipalityCatalog implements MunicipalityCatalog {

    private static final Logger log = LoggerFactory.getLogger(ResilientMunicipalityCatalog.class);

    /** The whole list is a single entry, as in the cached decorator. */
    static final String KEY = "all";

    private final MunicipalityCatalog delegate;
    private final ProviderGuard guard;
    private final Cache<String, List<Municipality>> lastGood;

    public ResilientMunicipalityCatalog(
            MunicipalityCatalog delegate, ProviderGuard guard, Cache<String, List<Municipality>> lastGood) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.lastGood = Objects.requireNonNull(lastGood, "lastGood");
    }

    @Override
    public List<Municipality> findAll() {
        var rejection = guard.rejection();
        if (rejection.isPresent()) {
            var stale = lastGood.getIfPresent(KEY);
            if (stale == null) {
                throw rejection.get();
            }
            log.debug("Circuit open: serving the last good municipality list");
            return stale;
        }

        try {
            var municipalities = delegate.findAll();
            guard.recordSuccess();
            lastGood.put(KEY, municipalities);
            return municipalities;
        } catch (ProviderUnavailableException | ProviderRateLimitedException e) {
            guard.recordFailure(e);
            var stale = lastGood.getIfPresent(KEY);
            if (stale == null) {
                throw e;
            }
            log.warn("AEMET failed ({}): serving the last good municipality list", e.getMessage());
            return stale;
        }
    }
}
