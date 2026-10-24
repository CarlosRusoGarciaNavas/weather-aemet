package com.carlosruso.weather.infrastructure.config;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads the municipality list once the application is ready, so the first user does not wait ~4 s for AEMET.
 * The catalog is injected by the port type: that is the cached decorator, so the list stays in the cache.
 * It runs on a virtual thread (always a daemon): startup is not blocked and shutdown does not wait for it.
 * A failure only logs a warning; the first real request will try again.
 */
@Component
class CatalogWarmup {

    private static final Logger log = LoggerFactory.getLogger(CatalogWarmup.class);

    private final MunicipalityCatalog catalog;
    private final boolean enabled;

    CatalogWarmup(MunicipalityCatalog catalog, AemetProperties properties) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.enabled = properties.cache().warmupOnStartup();
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        start();
    }

    /** The started thread, so tests can wait for it; empty if the warm-up is disabled. */
    Optional<Thread> start() {
        if (!enabled) {
            return Optional.empty();
        }
        return Optional.of(Thread.ofVirtual().name("catalog-warmup").start(this::warmUp));
    }

    private void warmUp() {
        try {
            var municipalities = catalog.findAll();
            log.info("Municipality catalog warmed up with {} municipalities", municipalities.size());
        } catch (RuntimeException e) {
            log.warn("Municipality catalog warm-up failed: {}", e.getMessage());
        }
    }
}
