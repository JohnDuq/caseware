package com.caseware.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.caseware.interview.adapter.out.persistence.repository.EngagementFileJpaRepository;
import com.caseware.interview.adapter.out.persistence.repository.FanOutTaskJpaRepository;
import com.caseware.interview.adapter.out.persistence.repository.PublicationJpaRepository;
import com.caseware.interview.application.exception.PublicationConflictException;
import com.caseware.interview.application.port.in.command.EngagementFileCommand;
import com.caseware.interview.application.port.in.EngagementFileUseCase;
import com.caseware.interview.application.port.in.FanOutUseCase;
import com.caseware.interview.application.port.in.PublicationRegistration;
import com.caseware.interview.application.port.in.TaskDispatchUseCase;
import com.caseware.interview.application.port.in.command.TemplatePublicationCommand;
import com.caseware.interview.application.port.in.TemplatePublicationUseCase;
import com.caseware.interview.application.port.out.EngagementUpdatePort;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.application.port.out.TransactionPort;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.domain.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest
@Import(TemplatePublishWorkerIntegrationTest.FakeClientConfiguration.class)
class TemplatePublishWorkerIntegrationTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-28T12:00:00Z");

    private final EngagementFileUseCase files;
    private final TemplatePublicationUseCase publications;
    private final FanOutUseCase fanOutWorker;
    private final TaskDispatchUseCase dispatcher;
    private final FanOutTaskStorePort tasks;
    private final EngagementFileJpaRepository fileJpaRepository;
    private final PublicationJpaRepository publicationJpaRepository;
    private final FanOutTaskJpaRepository taskJpaRepository;
    private final TransactionPort transaction;
    private final RecordingClient client;
    private final Clock clock;

    @Autowired
    TemplatePublishWorkerIntegrationTest(
            EngagementFileUseCase files,
            TemplatePublicationUseCase publications,
            FanOutUseCase fanOutWorker,
            TaskDispatchUseCase dispatcher,
            FanOutTaskStorePort tasks,
            EngagementFileJpaRepository fileJpaRepository,
            PublicationJpaRepository publicationJpaRepository,
            FanOutTaskJpaRepository taskJpaRepository,
            TransactionPort transaction,
            RecordingClient client,
            Clock clock) {
        this.files = files;
        this.publications = publications;
        this.fanOutWorker = fanOutWorker;
        this.dispatcher = dispatcher;
        this.tasks = tasks;
        this.fileJpaRepository = fileJpaRepository;
        this.publicationJpaRepository = publicationJpaRepository;
        this.taskJpaRepository = taskJpaRepository;
        this.transaction = transaction;
        this.client = client;
        this.clock = clock;
    }

    @BeforeEach
    void resetState() {
        taskJpaRepository.deleteAllInBatch();
        publicationJpaRepository.deleteAllInBatch();
        fileJpaRepository.deleteAllInBatch();
        client.reset();
    }

    @Test
    void fansOutInPagesAndIgnoresDuplicateDelivery() {
        saveFile("file-1", "v1", "CA");
        saveFile("file-2", "v2", "CA");
        saveFile("file-3", "v3", "CA");
        saveFile("already-current", "v4", "CA");
        saveFile("other-market", "v1", "EU");

        TemplatePublicationCommand event = event("publication-1");
        assertThat(publications.register(event)).isEqualTo(PublicationRegistration.CREATED);
        assertThat(publications.register(event)).isEqualTo(PublicationRegistration.DUPLICATE);

        assertThat(fanOutWorker.drainAvailablePages()).isEqualTo(2);
        assertThat(fanOutWorker.drainAvailablePages()).isZero();

        var status = publications.status("publication-1");
        assertThat(status.fanOutStatus()).isEqualTo(PublicationStatus.FAN_OUT_COMPLETE);
        assertThat(status.discoveredFiles()).isEqualTo(3);
        assertThat(status.tasks().pending()).isEqualTo(3);
        assertThat(status.tasks().total()).isEqualTo(3);
    }

    @Test
    void rejectsReuseOfAnIdempotencyKeyForDifferentPayload() {
        publications.register(event("publication-conflict"));

        TemplatePublicationCommand conflicting = new TemplatePublicationCommand(
                "publication-conflict", "template-9", "v99", "EU", PUBLISHED_AT);

        assertThatThrownBy(() -> publications.register(conflicting))
                .isInstanceOf(PublicationConflictException.class);
    }

    @Test
    void neverExceedsConfiguredDownstreamCapacity() throws Exception {
        saveFile("file-1", "v1", "CA");
        saveFile("file-2", "v1", "CA");
        saveFile("file-3", "v1", "CA");
        publications.register(event("publication-capacity"));
        fanOutWorker.drainAvailablePages();
        client.blockCalls();

        assertThat(dispatcher.dispatchAvailable()).isEqualTo(2);
        await(() -> client.activeCalls() == 2);
        assertThat(client.maxConcurrentCalls()).isEqualTo(2);
        assertThat(dispatcher.dispatchAvailable()).isZero();

        client.releaseCalls();
        await(() -> tasks.countsForPublication("publication-capacity").succeeded() == 2);
        assertThat(dispatcher.dispatchAvailable()).isEqualTo(1);
        await(() -> tasks.countsForPublication("publication-capacity").succeeded() == 3);
    }

    @Test
    void retriesTransientFailuresWithTheSameIdempotencyKey() throws Exception {
        saveFile("file-retry", "v1", "CA");
        publications.register(event("publication-retry"));
        fanOutWorker.drainAvailablePages();
        client.failFirstCallFor("file-retry");

        assertThat(dispatcher.dispatchAvailable()).isEqualTo(1);
        await(() -> tasks.countsForPublication("publication-retry").retrying() == 1);
        Thread.sleep(5);
        assertThat(dispatcher.dispatchAvailable()).isEqualTo(1);
        await(() -> tasks.countsForPublication("publication-retry").succeeded() == 1);

        assertThat(client.attemptsFor("file-retry")).isEqualTo(2);
        assertThat(client.idempotencyKeysFor("file-retry"))
                .containsExactly("publication-retry:file-retry");
        TaskCounts counts = tasks.countsForPublication("publication-retry");
        assertThat(counts.deadLetter()).isZero();
    }

    @Test
    void movesPermanentlyFailingCallsToDeadLetterAfterTheAttemptLimit() throws Exception {
        saveFile("file-dead", "v1", "CA");
        publications.register(event("publication-dead"));
        fanOutWorker.drainAvailablePages();
        client.failEveryCallFor("file-dead");

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(dispatcher.dispatchAvailable()).isEqualTo(1);
            int expectedAttempts = attempt;
            await(() -> client.attemptsFor("file-dead") == expectedAttempts
                    && noTaskIsProcessing("publication-dead"));
            Thread.sleep(10);
        }

        TaskCounts counts = tasks.countsForPublication("publication-dead");
        assertThat(counts.deadLetter()).isEqualTo(1);
        assertThat(counts.retrying()).isZero();
    }

    @Test
    void reclaimsAnExpiredTaskLeaseAndRejectsStaleCompletion() {
        saveFile("file-lease", "v1", "CA");
        publications.register(event("publication-lease"));
        fanOutWorker.drainAvailablePages();
        Instant now = clock.instant();
        TaskLease firstLease = claimNext("lease-1", now);
        long taskId = firstLease.taskId();
        var persistedTask = taskJpaRepository.findById(taskId).orElseThrow();
        assertThat(persistedTask.getId()).isEqualTo(taskId);
        assertThat(persistedTask.getStatus()).isEqualTo(TaskStatus.PROCESSING);
        assertThat(persistedTask.getAttemptCount()).isEqualTo(1);

        assertThat(findAndClaim("lease-unavailable", now)).isEmpty();
        assertThat(taskJpaRepository.updateLeaseExpiry(taskId, now.minusSeconds(1))).isEqualTo(1);

        TaskLease recoveredLease = claimNext("lease-2", now);

        assertThat(recoveredLease.attemptNumber()).isEqualTo(2);
        assertThat(tasks.complete(firstLease, now)).isFalse();
        assertThat(tasks.complete(recoveredLease, now)).isTrue();
    }

    @Test
    void recordsAStableErrorWhenTheDownstreamFailureHasNoMessage() {
        saveFile("file-null-error", "v1", "CA");
        publications.register(event("publication-null-error"));
        fanOutWorker.drainAvailablePages();
        Instant now = clock.instant();
        TaskLease lease = claimNext("lease-null-error", now);
        long taskId = lease.taskId();

        assertThat(tasks.fail(lease, null, now, true, now)).isTrue();
        assertThat(tasks.fail(lease, "stale", now, true, now)).isFalse();
        assertThat(taskJpaRepository.findById(taskId).orElseThrow().getLastError())
                .isEqualTo("Unknown downstream failure");
    }

    @Test
    void completesFanOutWhenNoFilesAreAffected() {
        publications.register(event("publication-empty"));

        assertThat(fanOutWorker.processOnePage()).isTrue();
        assertThat(fanOutWorker.processOnePage()).isFalse();
        assertThat(publications.status("publication-empty").fanOutStatus())
                .isEqualTo(PublicationStatus.FAN_OUT_COMPLETE);
        assertThat(publications.status("publication-empty").tasks().total()).isZero();
    }

    @Test
    void persistsAndUpdatesCatalogMetadataThroughJpa() {
        saveFile("file-clock", "v1", "CA");
        saveFile("file-clock", "v2", "CA");

        var saved = fileJpaRepository.findById("file-clock").orElseThrow();
        assertThat(saved.getTemplateVersion()).isEqualTo("v2");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private boolean noTaskIsProcessing(String publicationId) {
        return tasks.countsForPublication(publicationId).processing() == 0;
    }

    private void saveFile(String fileId, String version, String market) {
        files.upsert(new EngagementFileCommand(
                fileId, "firm-1", "template-a", version, market, "CANADA"));
    }

    private TaskLease claimNext(String token, Instant now) {
        return findAndClaim(token, now).orElseThrow();
    }

    private java.util.Optional<TaskLease> findAndClaim(String token, Instant now) {
        return transaction.required(() -> tasks.claimNext(token, now, now.plusSeconds(5)));
    }

    private static TemplatePublicationCommand event(String publicationId) {
        return new TemplatePublicationCommand(
                publicationId, "template-a", "v4", "CA", PUBLISHED_AT);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @TestConfiguration
    static class FakeClientConfiguration {

        @Bean
        @Primary
        RecordingClient recordingClient() {
            return new RecordingClient();
        }
    }

    static class RecordingClient implements EngagementUpdatePort {

        private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Set<String>> idempotencyKeys = new ConcurrentHashMap<>();
        private final Set<String> failFirst = ConcurrentHashMap.newKeySet();
        private final Set<String> failAlways = ConcurrentHashMap.newKeySet();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maximumActive = new AtomicInteger();
        private volatile CountDownLatch gate = new CountDownLatch(0);

        @Override
        public void evaluatePendingUpdate(String fileId, String targetVersion, String idempotencyKey) {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            int attempt = attempts.computeIfAbsent(fileId, ignored -> new AtomicInteger()).incrementAndGet();
            idempotencyKeys.computeIfAbsent(fileId, ignored -> ConcurrentHashMap.newKeySet()).add(idempotencyKey);
            try {
                gate.await(3, TimeUnit.SECONDS);
                if (failAlways.contains(fileId) || (attempt == 1 && failFirst.contains(fileId))) {
                    throw new IllegalStateException("temporary downstream failure");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", interrupted);
            } finally {
                active.decrementAndGet();
            }
        }

        void blockCalls() {
            gate = new CountDownLatch(1);
        }

        void releaseCalls() {
            gate.countDown();
        }

        void failFirstCallFor(String fileId) {
            failFirst.add(fileId);
        }

        void failEveryCallFor(String fileId) {
            failAlways.add(fileId);
        }

        int activeCalls() {
            return active.get();
        }

        int maxConcurrentCalls() {
            return maximumActive.get();
        }

        int attemptsFor(String fileId) {
            AtomicInteger value = attempts.get(fileId);
            return value == null ? 0 : value.get();
        }

        Set<String> idempotencyKeysFor(String fileId) {
            return idempotencyKeys.get(fileId);
        }

        void reset() {
            gate.countDown();
            gate = new CountDownLatch(0);
            attempts.clear();
            idempotencyKeys.clear();
            failFirst.clear();
            failAlways.clear();
            active.set(0);
            maximumActive.set(0);
        }
    }
}
