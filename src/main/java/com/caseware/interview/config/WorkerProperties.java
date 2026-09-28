package com.caseware.interview.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("worker")
public record WorkerProperties(
        int fanOutPageSize,
        int maxFanOutPagesPerRun,
        int maxConcurrency,
        Duration pollDelay,
        Duration leaseDuration,
        int maxAttempts,
        Duration retryInitialDelay,
        boolean schedulingEnabled) {

    public WorkerProperties {
        if (fanOutPageSize < 1 || maxFanOutPagesPerRun < 1 || maxConcurrency < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("Worker limits must be positive");
        }
        if (pollDelay.isNegative() || leaseDuration.isNegative() || leaseDuration.isZero()
                || retryInitialDelay.isNegative()) {
            throw new IllegalArgumentException("Worker durations must be non-negative and the lease must be positive");
        }
    }
}
