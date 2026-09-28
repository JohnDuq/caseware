package com.caseware.interview.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import com.caseware.interview.application.port.in.TaskDispatchUseCase;
import com.caseware.interview.application.port.out.EngagementUpdatePort;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.application.port.out.TransactionPort;
import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.domain.TaskLease;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DownstreamTaskDispatchService implements TaskDispatchUseCase {

    private final FanOutTaskStorePort tasks;
    private final EngagementUpdatePort client;
    private final WorkerProperties properties;
    private final Clock clock;
    private final TransactionPort transactions;
    private final ExecutorService downstreamExecutor;
    private final Semaphore downstreamCapacity;
    private final MeterRegistry metrics;

    @Override
    public int dispatchAvailable() {
        int dispatched = 0;
        while (downstreamCapacity.tryAcquire()) {
            Optional<TaskLease> lease = claimNextTask();
            if (lease.isEmpty()) {
                downstreamCapacity.release();
                break;
            }
            dispatched++;
            downstreamExecutor.submit(() -> process(lease.get()));
        }
        return dispatched;
    }

    private Optional<TaskLease> claimNextTask() {
        Optional<TaskLease> result = transactions.required(() -> {
            Instant now = clock.instant();
            return tasks.claimNext(
                    UUID.randomUUID().toString(),
                    now,
                    now.plus(properties.leaseDuration()));
        });
        return result == null ? Optional.empty() : result;
    }

    private void process(TaskLease lease) {
        try {
            client.evaluatePendingUpdate(
                    lease.fileId(), lease.targetVersion(), lease.idempotencyKey());
            if (tasks.complete(lease, clock.instant())) {
                metrics.counter("caseware.downstream.tasks.succeeded").increment();
            }
        } catch (Exception failure) {
            Instant now = clock.instant();
            boolean terminal = lease.attemptNumber() >= properties.maxAttempts();
            Duration retryDelay = exponentialDelay(lease.attemptNumber());
            if (tasks.fail(lease, failure.getMessage(), now.plus(retryDelay), terminal, now)) {
                metrics.counter(terminal
                        ? "caseware.downstream.tasks.dead_letter"
                        : "caseware.downstream.tasks.retry").increment();
            }
        } finally {
            downstreamCapacity.release();
        }
    }

    private Duration exponentialDelay(int attemptNumber) {
        int exponent = Math.min(Math.max(attemptNumber - 1, 0), 20);
        return properties.retryInitialDelay().multipliedBy(1L << exponent);
    }
}
