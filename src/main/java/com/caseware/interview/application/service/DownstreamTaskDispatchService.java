package com.caseware.interview.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.caseware.interview.application.exception.DownstreamUpdateException;
import com.caseware.interview.application.port.in.TaskDispatchUseCase;
import com.caseware.interview.application.port.out.EngagementUpdatePort;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.application.port.out.TransactionPort;
import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.domain.TaskLease;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DownstreamTaskDispatchService implements TaskDispatchUseCase {

    private final FanOutTaskStorePort tasks;
    private final EngagementUpdatePort client;
    private final WorkerProperties properties;
    private final Clock clock;
    private final TransactionPort transactions;
    private final ExecutorService downstreamExecutor;
    private final ScheduledExecutorService leaseRenewalExecutor;
    private final Semaphore downstreamCapacity;
    private final MeterRegistry metrics;

    @Override
    public int dispatchAvailable() {
        int dispatched = 0;
        while (downstreamCapacity.tryAcquire()) {
            Optional<TaskLease> lease;
            try {
                lease = claimNextTask();
            } catch (RuntimeException failure) {
                downstreamCapacity.release();
                throw failure;
            }
            if (lease.isEmpty()) {
                downstreamCapacity.release();
                break;
            }
            try {
                downstreamExecutor.submit(() -> process(lease.get()));
                dispatched++;
            } catch (RejectedExecutionException rejected) {
                releaseRejectedTask(lease.get(), rejected);
                downstreamCapacity.release();
                break;
            }
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
        ScheduledFuture<?> renewal = null;
        try {
            renewal = startLeaseRenewal(lease);
            client.evaluatePendingUpdate(
                    lease.fileId(), lease.targetVersion(), lease.idempotencyKey());
            if (tasks.complete(lease, clock.instant())) {
                metrics.counter("caseware.downstream.tasks.succeeded").increment();
            }
        } catch (DownstreamUpdateException failure) {
            Instant now = clock.instant();
            boolean terminal = !failure.isRetryable()
                    || lease.attemptNumber() >= properties.maxAttempts();
            Duration retryDelay = exponentialDelay(lease.attemptNumber());
            if (tasks.fail(lease, failure.getMessage(), now.plus(retryDelay), terminal, now)) {
                metrics.counter(terminal
                        ? "caseware.downstream.tasks.dead_letter"
                        : "caseware.downstream.tasks.retry").increment();
            }
        } catch (RuntimeException unexpected) {
            log.error("Unexpected task processing failure for task {}", lease.taskId(), unexpected);
            throw unexpected;
        } finally {
            if (renewal != null) {
                renewal.cancel(false);
            }
            downstreamCapacity.release();
        }
    }

    private ScheduledFuture<?> startLeaseRenewal(TaskLease lease) {
        long intervalMillis = Math.max(1, properties.leaseDuration().toMillis() / 3);
        return leaseRenewalExecutor.scheduleAtFixedRate(
                () -> renewLeaseSafely(lease), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void renewLeaseSafely(TaskLease lease) {
        try {
            Instant now = clock.instant();
            if (!tasks.renewLease(lease, now, now.plus(properties.leaseDuration()))) {
                log.warn("Lease renewal was rejected for task {}", lease.taskId());
            }
        } catch (RuntimeException failure) {
            log.error("Lease renewal failed for task {}", lease.taskId(), failure);
        }
    }

    private void releaseRejectedTask(TaskLease lease, RejectedExecutionException rejected) {
        Instant now = clock.instant();
        try {
            tasks.fail(lease, rejected.getMessage(), now, false, now);
        } catch (RuntimeException failure) {
            log.error("Could not release rejected task {}", lease.taskId(), failure);
        }
    }

    private Duration exponentialDelay(int attemptNumber) {
        int exponent = Math.min(Math.max(attemptNumber - 1, 0), 20);
        return properties.retryInitialDelay().multipliedBy(1L << exponent);
    }
}
