package com.caseware.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.caseware.interview.api.TemplatePublicationRequest;
import com.caseware.interview.client.EngagementUpdateClient;
import com.caseware.interview.domain.EngagementFile;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.repository.EngagementFileRepository;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.PublicationConflictException;
import com.caseware.interview.repository.PublicationRepository;
import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import com.caseware.interview.service.DownstreamTaskDispatcher;
import com.caseware.interview.service.PublicationService;
import com.caseware.interview.service.TemplatePublishFanOutWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TemplatePublishWorkerIntegrationTest.FakeClientConfiguration.class)
class TemplatePublishWorkerIntegrationTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-28T12:00:00Z");

    private final EngagementFileRepository files;
    private final PublicationService publications;
    private final TemplatePublishFanOutWorker fanOutWorker;
    private final DownstreamTaskDispatcher dispatcher;
    private final FanOutTaskRepository tasks;
    private final PublicationRepository publicationRepository;
    private final JdbcClient jdbc;
    private final RecordingClient client;
    private final Clock clock;

    @Autowired
    TemplatePublishWorkerIntegrationTest(
            EngagementFileRepository files,
            PublicationService publications,
            TemplatePublishFanOutWorker fanOutWorker,
            DownstreamTaskDispatcher dispatcher,
            FanOutTaskRepository tasks,
            PublicationRepository publicationRepository,
            JdbcClient jdbc,
            RecordingClient client,
            Clock clock) {
        this.files = files;
        this.publications = publications;
        this.fanOutWorker = fanOutWorker;
        this.dispatcher = dispatcher;
        this.tasks = tasks;
        this.publicationRepository = publicationRepository;
        this.jdbc = jdbc;
        this.client = client;
        this.clock = clock;
    }

    @BeforeEach
    void resetState() {
        jdbc.sql("DELETE FROM fan_out_task").update();
        jdbc.sql("DELETE FROM template_publication").update();
        jdbc.sql("DELETE FROM engagement_file_catalog").update();
        client.reset();
    }

    @Test
    void fansOutInPagesAndIgnoresDuplicateDelivery() {
        saveFile("file-1", "v1", "CA");
        saveFile("file-2", "v2", "CA");
        saveFile("file-3", "v3", "CA");
        saveFile("already-current", "v4", "CA");
        saveFile("other-market", "v1", "EU");

        TemplatePublicationRequest event = event("publication-1");
        assertThat(publications.register(event)).isEqualTo(RegistrationResult.CREATED);
        assertThat(publications.register(event)).isEqualTo(RegistrationResult.DUPLICATE);

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

        TemplatePublicationRequest conflicting = new TemplatePublicationRequest(
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
        long taskId = tasks.findClaimableTaskId(now).orElseThrow();

        var firstLease = tasks.claim(taskId, "lease-1", now, now.plusSeconds(5)).orElseThrow();
        assertThat(tasks.claim(taskId, "lease-unavailable", now, now.plusSeconds(5))).isEmpty();
        jdbc.sql("UPDATE fan_out_task SET lease_expires_at = :expired WHERE id = :id")
                .param("expired", Timestamp.from(now.minusSeconds(1)))
                .param("id", taskId)
                .update();

        assertThat(tasks.findClaimableTaskId(now)).contains(taskId);
        var recoveredLease = tasks.claim(taskId, "lease-2", now, now.plusSeconds(5)).orElseThrow();

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
        long taskId = tasks.findClaimableTaskId(now).orElseThrow();
        var lease = tasks.claim(taskId, "lease-null-error", now, now.plusSeconds(5)).orElseThrow();

        assertThat(tasks.fail(lease, null, now, true, now)).isTrue();
        assertThat(tasks.fail(lease, "stale", now, true, now)).isFalse();
        assertThat(jdbc.sql("SELECT last_error FROM fan_out_task WHERE id = :id")
                .param("id", taskId)
                .query(String.class)
                .single()).isEqualTo("Unknown downstream failure");
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
    void usesTheClockWhenCatalogMetadataHasNoTimestampAndProtectsPublicationLeases() {
        files.save(new EngagementFile(
                "file-clock", "firm-1", "template-a", "v1", "CA", "CANADA", null));
        assertThat(jdbc.sql("SELECT updated_at FROM engagement_file_catalog WHERE file_id = 'file-clock'")
                .query(Timestamp.class)
                .single()).isNotNull();

        publications.register(event("publication-guard"));
        Instant now = clock.instant();
        assertThat(publicationRepository.claim(
                "publication-guard", "owner", now, now.plusSeconds(5))).isTrue();
        assertThat(publicationRepository.claim(
                "publication-guard", "other", now, now.plusSeconds(5))).isFalse();
        assertThatThrownBy(() -> publicationRepository.pageCompleted(
                "publication-guard", "wrong-owner", null, 0, true, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Publication lease was lost for publication-guard");
    }

    private boolean noTaskIsProcessing(String publicationId) {
        return tasks.countsForPublication(publicationId).processing() == 0;
    }

    private void saveFile(String fileId, String version, String market) {
        files.save(new EngagementFile(
                fileId, "firm-1", "template-a", version, market, "CANADA", clock.instant()));
    }

    private static TemplatePublicationRequest event(String publicationId) {
        return new TemplatePublicationRequest(
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

    static class RecordingClient implements EngagementUpdateClient {

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
