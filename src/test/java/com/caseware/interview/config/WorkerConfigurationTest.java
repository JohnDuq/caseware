package com.caseware.interview.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

class WorkerConfigurationTest {

    private final WorkerConfiguration configuration = new WorkerConfiguration();

    @Test
    void createsUtcClockAndConfiguredExecutor() throws Exception {
        WorkerProperties properties = validProperties(true);

        assertThat(configuration.clock().getZone()).isEqualTo(ZoneOffset.UTC);
        ExecutorService executor = configuration.downstreamExecutor(properties);
        Semaphore capacity = configuration.downstreamCapacity(properties);
        try {
            assertThat(executor.submit(() -> Thread.currentThread().getName()).get())
                    .startsWith("downstream-update-");
            assertThat(capacity.availablePermits()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void acceptsValidLimitsAndDurations() {
        assertThat(validProperties(false).schedulingEnabled()).isFalse();
    }

    @Test
    void rejectsEachNonPositiveLimit() {
        assertInvalid(0, 1, 1, 1, Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
        assertInvalid(1, 0, 1, 1, Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
        assertInvalid(1, 1, 0, 1, Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
        assertInvalid(1, 1, 1, 0, Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
    }

    @Test
    void rejectsInvalidDurations() {
        assertInvalid(1, 1, 1, 1, Duration.ofMillis(-1), Duration.ofSeconds(1), Duration.ZERO);
        assertInvalid(1, 1, 1, 1, Duration.ZERO, Duration.ofMillis(-1), Duration.ZERO);
        assertInvalid(1, 1, 1, 1, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        assertInvalid(1, 1, 1, 1, Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(-1));
    }

    static WorkerProperties validProperties(boolean schedulingEnabled) {
        return new WorkerProperties(
                2, 10, 2, Duration.ofMillis(10), Duration.ofSeconds(5),
                3, Duration.ofMillis(1), schedulingEnabled);
    }

    private static void assertInvalid(
            int pageSize,
            int maxPages,
            int concurrency,
            int attempts,
            Duration poll,
            Duration lease,
            Duration retry) {
        assertThatThrownBy(() -> new WorkerProperties(
                pageSize, maxPages, concurrency, poll, lease, attempts, retry, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
