package com.caseware.interview.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import com.caseware.interview.client.EngagementUpdateClient;
import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.entity.FanOutTaskEntity;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DownstreamTaskDispatcher {

    private final FanOutTaskRepository tasks;
    private final EngagementUpdateClient client;
    private final WorkerProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ExecutorService executor;
    private final Semaphore capacity;
    private final MeterRegistry metrics;

    public DownstreamTaskDispatcher(
            FanOutTaskRepository tasks,
            EngagementUpdateClient client,
            WorkerProperties properties,
            Clock clock,
            TransactionTemplate transaction,
            ExecutorService downstreamExecutor,
            MeterRegistry metrics) {
        this.tasks = tasks;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.transaction = transaction;
        this.executor = downstreamExecutor;
        this.capacity = new Semaphore(properties.maxConcurrency());
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay:250ms}")
    public void runScheduled() {
        if (!properties.schedulingEnabled()) {
            return;
        }
        dispatchAvailable();
    }

    public int dispatchAvailable() {
        int dispatched = 0;
        while (capacity.tryAcquire()) {
            Optional<TaskLease> lease = claimNextTask();
            if (lease.isEmpty()) {
                capacity.release();
                break;
            }
            dispatched++;
            executor.submit(() -> process(lease.get()));
        }
        return dispatched;
    }

    private Optional<TaskLease> claimNextTask() {
        Optional<TaskLease> result = transaction.execute(ignored -> {
            Instant now = clock.instant();
            Optional<FanOutTaskEntity> task = tasks.findNextClaimable(now);
            if (task.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(task.get().claim(
                    UUID.randomUUID().toString(),
                    now.plus(properties.leaseDuration()),
                    now));
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
            capacity.release();
        }
    }

    private Duration exponentialDelay(int attemptNumber) {
        int exponent = Math.min(Math.max(attemptNumber - 1, 0), 20);
        return properties.retryInitialDelay().multipliedBy(1L << exponent);
    }
}
