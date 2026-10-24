package com.carlosruso.weather.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carlosruso.weather.application.port.MunicipalityCatalog;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.model.Municipality;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CatalogWarmupTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private final AtomicInteger calls = new AtomicInteger();

    private final MunicipalityCatalog counting = () -> {
        calls.incrementAndGet();
        return List.of(new Municipality("03002", "Agost"));
    };

    private static AemetProperties properties(boolean warmupOnStartup) {
        return new AemetProperties("test-key", "http://localhost", Duration.ofSeconds(1), Duration.ofSeconds(1),
                new AemetProperties.Cache(Duration.ofHours(24), Duration.ofMinutes(30), 100, warmupOnStartup), null);
    }

    @Test
    void whenEnabledItCallsTheCatalogOnceOnAVirtualThread() throws Exception {
        var thread = new CatalogWarmup(counting, properties(true)).start().orElseThrow();

        assertTrue(thread.join(WAIT));
        assertEquals(1, calls.get());
        assertTrue(thread.isVirtual());
        assertTrue(thread.isDaemon(), "must not keep the JVM alive on shutdown");
    }

    @Test
    void whenDisabledItNeverCallsTheCatalog() {
        var thread = new CatalogWarmup(counting, properties(false)).start();

        assertTrue(thread.isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void itDoesNotWaitForTheCatalog() throws Exception {
        var release = new CountDownLatch(1);
        MunicipalityCatalog slow = () -> {
            awaitUninterruptibly(release);
            return List.of();
        };

        var thread = new CatalogWarmup(slow, properties(true)).start().orElseThrow();

        // start() has returned while the catalog is still blocked: startup goes on.
        assertTrue(thread.isAlive());
        release.countDown();
        assertTrue(thread.join(WAIT));
    }

    @Test
    void aFailureOnlyLogsAWarning(CapturedOutput output) throws Exception {
        MunicipalityCatalog failing = () -> {
            throw new ProviderUnavailableException("AEMET could not be reached");
        };

        var thread = new CatalogWarmup(failing, properties(true)).start().orElseThrow();

        assertTrue(thread.join(WAIT));
        assertTrue(output.getOut().lines().anyMatch(line -> line.contains("WARN")
                && line.contains("Municipality catalog warm-up failed: AEMET could not be reached")), output::getOut);
        assertFalse(output.getOut().contains("Exception in thread"), "the failure must not escape the thread");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
