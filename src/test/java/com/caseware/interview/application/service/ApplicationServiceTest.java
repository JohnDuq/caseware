package com.caseware.interview.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.caseware.interview.adapter.in.scheduler.DownstreamTaskScheduler;
import com.caseware.interview.adapter.in.scheduler.TemplatePublishScheduler;
import com.caseware.interview.adapter.out.downstream.LoggingEngagementUpdateAdapter;
import com.caseware.interview.application.exception.DownstreamUpdateException;
import com.caseware.interview.application.exception.PublicationConflictException;
import com.caseware.interview.application.exception.PublicationNotFoundException;
import com.caseware.interview.application.port.in.command.EngagementFileCommand;
import com.caseware.interview.application.port.in.FanOutUseCase;
import com.caseware.interview.application.port.in.PublicationRegistration;
import com.caseware.interview.application.port.in.TaskDispatchUseCase;
import com.caseware.interview.application.port.in.command.TemplatePublicationCommand;
import com.caseware.interview.application.port.out.EngagementFileCatalogPort;
import com.caseware.interview.application.port.out.EngagementUpdatePort;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.application.port.out.PublicationStorePort;
import com.caseware.interview.application.port.out.PublicationStorePort.CreatePublicationResult;
import com.caseware.interview.application.port.out.TransactionPort;
import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.domain.EngagementFile;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.domain.TemplatePublication;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void engagementFileServiceAddsTheApplicationTimestamp() {
        EngagementFileCatalogPort catalog = mock(EngagementFileCatalogPort.class);
        EngagementFileApplicationService service = new EngagementFileApplicationService(catalog, CLOCK);

        service.upsert(new EngagementFileCommand(
                "file-1", "firm-1", "template-a", "v3", "CA", "CANADA"));

        verify(catalog).save(new EngagementFile(
                "file-1", "firm-1", "template-a", "v3", "CA", "CANADA", NOW));
    }

    @Test
    void applicationCommandsAndDomainObjectsEnforceTheirInvariants() {
        assertThatThrownBy(() -> new EngagementFileCommand(
                " ", "firm", "template", "v1", "CA", "CANADA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileId");
        assertThatThrownBy(() -> new TemplatePublicationCommand(
                "publication", "template", "v1", "CA", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("publishedAt is required");
        assertThatThrownBy(() -> new TemplatePublication(
                "publication", "template", "v1", "CA", NOW,
                PublicationStatus.PENDING, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalTasks must not be negative");

        TaskLease first = new TaskLease(1, "a:b", "c", "v1", "lease", 1);
        TaskLease second = new TaskLease(2, "a", "b:c", "v1", "lease", 1);
        assertThat(first.idempotencyKey()).isNotEqualTo(second.idempotencyKey());
    }

    @Test
    void publicationServiceRegistersANewEventAndReturnsItsStatus() {
        PublicationStorePort publications = mock(PublicationStorePort.class);
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        PublicationApplicationService service = new PublicationApplicationService(publications, tasks, CLOCK);
        TemplatePublicationCommand command = command("publication-1");
        TemplatePublication stored = publication("publication-1", PublicationStatus.PENDING, null, 0);
        when(publications.create(any(), any())).thenReturn(new CreatePublicationResult(true, stored));
        when(publications.findById("publication-1")).thenReturn(Optional.of(stored));
        TaskCounts counts = new TaskCounts(1, 0, 0, 0, 0);
        when(tasks.countsForPublication("publication-1")).thenReturn(counts);

        assertThat(service.register(command)).isEqualTo(PublicationRegistration.CREATED);
        var status = service.status("publication-1");

        ArgumentCaptor<TemplatePublication> captor = ArgumentCaptor.forClass(TemplatePublication.class);
        verify(publications).create(captor.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(captor.getValue().status()).isEqualTo(PublicationStatus.PENDING);
        assertThat(status.publicationId()).isEqualTo("publication-1");
        assertThat(status.tasks()).isSameAs(counts);
    }

    @Test
    void publicationServiceAcceptsEquivalentDuplicatesAndRejectsConflicts() {
        PublicationStorePort publications = mock(PublicationStorePort.class);
        PublicationApplicationService service = new PublicationApplicationService(
                publications, mock(FanOutTaskStorePort.class), CLOCK);
        TemplatePublication existing = publication("publication-1", PublicationStatus.PENDING, null, 0);
        when(publications.create(any(), any()))
                .thenReturn(new CreatePublicationResult(false, existing));

        assertThat(service.register(command("publication-1")))
                .isEqualTo(PublicationRegistration.DUPLICATE);
        assertThatThrownBy(() -> service.register(new TemplatePublicationCommand(
                "publication-1", "different", "v4", "CA", NOW)))
                .isInstanceOf(PublicationConflictException.class);
    }

    @Test
    void publicationServiceRejectsUnknownIds() {
        PublicationStorePort publications = mock(PublicationStorePort.class);
        when(publications.findById("missing")).thenReturn(Optional.empty());
        PublicationApplicationService service = new PublicationApplicationService(
                publications, mock(FanOutTaskStorePort.class), CLOCK);

        assertThatThrownBy(() -> service.status("missing"))
                .isInstanceOf(PublicationNotFoundException.class)
                .hasMessage("Publication not found: missing");
    }

    @Test
    void fanOutProcessesAFullPageAndThenStopsAtTheConfiguredLimit() {
        PublicationStorePort publications = mock(PublicationStorePort.class);
        EngagementFileCatalogPort files = mock(EngagementFileCatalogPort.class);
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        TemplatePublication publication = publication("publication-1", PublicationStatus.PENDING, null, 0);
        when(publications.findNextClaimable(NOW))
                .thenReturn(Optional.of(publication), Optional.of(publication));
        List<EngagementFile> page = List.of(
                file("file-1"), file("file-2"));
        when(files.findAffected("template-a", "CA", "v4", null, 2)).thenReturn(page);
        when(tasks.createPendingTasks("publication-1", page, NOW)).thenReturn(2);
        TemplatePublishFanOutService service = new TemplatePublishFanOutService(
                publications, files, tasks, properties(true), CLOCK,
                immediateTransactions(), new SimpleMeterRegistry());

        assertThat(service.drainAvailablePages()).isEqualTo(2);
        verify(publications, org.mockito.Mockito.times(2))
                .completePage("publication-1", "file-2", 2, false, NOW);
    }

    @Test
    void fanOutCompletesEmptyPagesAndHandlesNullTransactionResults() {
        PublicationStorePort publications = mock(PublicationStorePort.class);
        EngagementFileCatalogPort files = mock(EngagementFileCatalogPort.class);
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        TemplatePublication publication = publication("publication-empty", PublicationStatus.PENDING, "cursor", 0);
        when(publications.findNextClaimable(NOW)).thenReturn(Optional.of(publication));
        when(files.findAffected("template-a", "CA", "v4", "cursor", 2)).thenReturn(List.of());
        TemplatePublishFanOutService service = new TemplatePublishFanOutService(
                publications, files, tasks, properties(true), CLOCK,
                immediateTransactions(), new SimpleMeterRegistry());

        assertThat(service.processOnePage()).isTrue();
        verify(publications).completePage("publication-empty", "cursor", 0, true, NOW);

        TemplatePublishFanOutService nullResult = new TemplatePublishFanOutService(
                publications, files, tasks, properties(true), CLOCK,
                nullTransactions(), new SimpleMeterRegistry());
        assertThat(nullResult.processOnePage()).isFalse();
    }

    @Test
    void fanOutReturnsFalseWhenNoPublicationCanBeClaimed() {
        PublicationStorePort publications = mock(PublicationStorePort.class);
        when(publications.findNextClaimable(NOW)).thenReturn(Optional.empty());
        TemplatePublishFanOutService service = new TemplatePublishFanOutService(
                publications, mock(EngagementFileCatalogPort.class), mock(FanOutTaskStorePort.class),
                properties(true), CLOCK, immediateTransactions(), new SimpleMeterRegistry());

        assertThat(service.processOnePage()).isFalse();
    }

    @Test
    void fanOutPublishesMetricsOnlyAfterTheTransactionCommits() {
        PublicationStorePort publications = mock(PublicationStorePort.class);
        EngagementFileCatalogPort files = mock(EngagementFileCatalogPort.class);
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        TemplatePublication publication = publication("publication-1", PublicationStatus.PENDING, null, 0);
        when(publications.findNextClaimable(NOW)).thenReturn(Optional.of(publication));
        when(files.findAffected("template-a", "CA", "v4", null, 2))
                .thenReturn(List.of(file("file-1")));
        when(tasks.createPendingTasks(any(), any(), eq(NOW))).thenReturn(1);
        var metrics = new SimpleMeterRegistry();
        TransactionPort failedCommit = new TransactionPort() {
            @Override
            public <T> T required(Supplier<T> work) {
                work.get();
                throw new IllegalStateException("commit failed");
            }
        };
        TemplatePublishFanOutService service = new TemplatePublishFanOutService(
                publications, files, tasks, properties(true), CLOCK, failedCommit, metrics);

        assertThatThrownBy(service::processOnePage)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("commit failed");
        assertThat(metrics.find("caseware.fanout.tasks.created").counter()).isNull();
        assertThat(metrics.find("caseware.fanout.publications.completed").counter()).isNull();
    }

    @Test
    void schedulersHonorTheConfigurationFlag() {
        FanOutUseCase fanOut = mock(FanOutUseCase.class);
        TaskDispatchUseCase dispatcher = mock(TaskDispatchUseCase.class);
        new TemplatePublishScheduler(fanOut, properties(false)).run();
        new DownstreamTaskScheduler(dispatcher, properties(false)).run();
        verify(fanOut, never()).drainAvailablePages();
        verify(dispatcher, never()).dispatchAvailable();

        new TemplatePublishScheduler(fanOut, properties(true)).run();
        new DownstreamTaskScheduler(dispatcher, properties(true)).run();
        verify(fanOut).drainAvailablePages();
        verify(dispatcher).dispatchAvailable();
    }

    @Test
    void downstreamDispatcherTreatsNullTransactionResultAsNoWork() {
        ExecutorService executor = mock(ExecutorService.class);
        DownstreamTaskDispatchService service = new DownstreamTaskDispatchService(
                mock(FanOutTaskStorePort.class), mock(EngagementUpdatePort.class),
                properties(true), CLOCK, nullTransactions(), executor,
                mock(ScheduledExecutorService.class),
                new Semaphore(2), new SimpleMeterRegistry());

        assertThat(service.dispatchAvailable()).isZero();
        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void downstreamDispatcherCompletesWorkAndRenewsOnlyTheOwnedLease() {
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        EngagementUpdatePort client = mock(EngagementUpdatePort.class);
        ExecutorService executor = directExecutor();
        ScheduledExecutorService renewals = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> renewal = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        TaskLease lease = lease(1);
        when(tasks.claimNext(any(), eq(NOW), eq(NOW.plusSeconds(5))))
                .thenReturn(Optional.of(lease), Optional.empty());
        when(tasks.complete(lease, NOW)).thenReturn(true);
        when(renewals.scheduleAtFixedRate(
                heartbeat.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(ignored -> renewal);
        when(tasks.renewLease(lease, NOW, NOW.plusSeconds(5)))
                .thenReturn(true, false)
                .thenThrow(new IllegalStateException("database unavailable"));
        Semaphore capacity = new Semaphore(2);
        var metrics = new SimpleMeterRegistry();
        DownstreamTaskDispatchService service = new DownstreamTaskDispatchService(
                tasks, client, properties(true), CLOCK, immediateTransactions(), executor,
                renewals, capacity, metrics);

        assertThat(service.dispatchAvailable()).isEqualTo(1);
        heartbeat.getValue().run();
        heartbeat.getValue().run();
        heartbeat.getValue().run();

        verify(client).evaluatePendingUpdate(
                "file-1", "v4", "v1.cHVibGljYXRpb24tMQ.ZmlsZS0x");
        verify(renewal).cancel(false);
        assertThat(capacity.availablePermits()).isEqualTo(2);
        assertThat(metrics.counter("caseware.downstream.tasks.succeeded").count()).isEqualTo(1);
    }

    @Test
    void downstreamDispatcherClassifiesRetryableAndPermanentFailures() {
        assertDownstreamFailure(new DownstreamUpdateException("temporary", true), lease(1), false);
        assertDownstreamFailure(new DownstreamUpdateException("permanent", false), lease(1), true);
        assertDownstreamFailure(new DownstreamUpdateException("exhausted", true), lease(3), true);
        DownstreamUpdateException caused = new DownstreamUpdateException(
                "wrapped", new IllegalStateException("cause"), true);
        assertThat(caused.getCause()).hasMessage("cause");
        assertThat(caused.isRetryable()).isTrue();
    }

    @Test
    void downstreamDispatcherReleasesCapacityAndWorkWhenSubmissionIsRejected() {
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        ExecutorService executor = mock(ExecutorService.class);
        TaskLease lease = lease(1);
        when(tasks.claimNext(any(), eq(NOW), eq(NOW.plusSeconds(5))))
                .thenReturn(Optional.of(lease));
        when(executor.submit(any(Runnable.class)))
                .thenThrow(new RejectedExecutionException("executor stopped"));
        Semaphore capacity = new Semaphore(1);
        DownstreamTaskDispatchService service = new DownstreamTaskDispatchService(
                tasks, mock(EngagementUpdatePort.class), properties(true), CLOCK,
                immediateTransactions(), executor, mock(ScheduledExecutorService.class),
                capacity, new SimpleMeterRegistry());

        assertThat(service.dispatchAvailable()).isZero();
        verify(tasks).fail(lease, "executor stopped", NOW, false, NOW);
        assertThat(capacity.availablePermits()).isEqualTo(1);

        doThrow(new IllegalStateException("database unavailable"))
                .when(tasks).fail(lease, "executor stopped", NOW, false, NOW);
        when(tasks.claimNext(any(), eq(NOW), eq(NOW.plusSeconds(5))))
                .thenReturn(Optional.of(lease));
        assertThat(service.dispatchAvailable()).isZero();
        assertThat(capacity.availablePermits()).isEqualTo(1);
    }

    @Test
    void downstreamDispatcherReleasesCapacityWhenClaimingOrStartingRenewalFails() {
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        Semaphore claimCapacity = new Semaphore(1);
        when(tasks.claimNext(any(), eq(NOW), eq(NOW.plusSeconds(5))))
                .thenThrow(new IllegalStateException("claim failed"));
        DownstreamTaskDispatchService claimFailure = new DownstreamTaskDispatchService(
                tasks, mock(EngagementUpdatePort.class), properties(true), CLOCK,
                immediateTransactions(), mock(ExecutorService.class), scheduledExecutor(),
                claimCapacity, new SimpleMeterRegistry());

        assertThatThrownBy(claimFailure::dispatchAvailable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("claim failed");
        assertThat(claimCapacity.availablePermits()).isEqualTo(1);

        TaskLease lease = lease(1);
        FanOutTaskStorePort renewalTasks = mock(FanOutTaskStorePort.class);
        when(renewalTasks.claimNext(any(), eq(NOW), eq(NOW.plusSeconds(5))))
                .thenReturn(Optional.of(lease));
        ScheduledExecutorService rejectedRenewal = mock(ScheduledExecutorService.class);
        when(rejectedRenewal.scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new RejectedExecutionException("renewal stopped"));
        ExecutorService queuedExecutor = mock(ExecutorService.class);
        ArgumentCaptor<Runnable> queuedTask = ArgumentCaptor.forClass(Runnable.class);
        when(queuedExecutor.submit(queuedTask.capture())).thenReturn(mock(Future.class));
        Semaphore renewalCapacity = new Semaphore(1);
        DownstreamTaskDispatchService renewalFailure = new DownstreamTaskDispatchService(
                renewalTasks, mock(EngagementUpdatePort.class), properties(true), CLOCK,
                immediateTransactions(), queuedExecutor, rejectedRenewal,
                renewalCapacity, new SimpleMeterRegistry());

        assertThat(renewalFailure.dispatchAvailable()).isEqualTo(1);
        assertThatThrownBy(() -> queuedTask.getValue().run())
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessage("renewal stopped");
        assertThat(renewalCapacity.availablePermits()).isEqualTo(1);
    }

    @Test
    void downstreamDispatcherDoesNotMisclassifyProgrammingErrorsAsDownstreamFailures() {
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        EngagementUpdatePort client = mock(EngagementUpdatePort.class);
        TaskLease lease = lease(1);
        when(tasks.claimNext(any(), eq(NOW), eq(NOW.plusSeconds(5))))
                .thenReturn(Optional.of(lease));
        doThrow(new IllegalStateException("bug"))
                .when(client).evaluatePendingUpdate(any(), any(), any());
        DownstreamTaskDispatchService service = new DownstreamTaskDispatchService(
                tasks, client, properties(true), CLOCK, immediateTransactions(), directExecutor(),
                scheduledExecutor(), new Semaphore(1), new SimpleMeterRegistry());

        assertThatThrownBy(service::dispatchAvailable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug");
        verify(tasks, never()).fail(any(), any(), any(), eq(false), any());
    }

    @Test
    void loggingAdapterImplementsTheDownstreamPort() {
        new LoggingEngagementUpdateAdapter()
                .evaluatePendingUpdate("file-1", "v4", "v1.cHVibGljYXRpb24tMQ.ZmlsZS0x");
    }

    private static TransactionPort immediateTransactions() {
        return new TransactionPort() {
            @Override
            public <T> T required(Supplier<T> work) {
                return work.get();
            }
        };
    }

    private static TransactionPort nullTransactions() {
        return new TransactionPort() {
            @Override
            public <T> T required(Supplier<T> work) {
                return null;
            }
        };
    }

    private static void assertDownstreamFailure(
            DownstreamUpdateException failure, TaskLease lease, boolean terminal) {
        FanOutTaskStorePort tasks = mock(FanOutTaskStorePort.class);
        EngagementUpdatePort client = mock(EngagementUpdatePort.class);
        when(tasks.claimNext(any(), eq(NOW), eq(NOW.plusSeconds(5))))
                .thenReturn(Optional.of(lease), Optional.empty());
        when(tasks.fail(eq(lease), eq(failure.getMessage()), any(), eq(terminal), eq(NOW)))
                .thenReturn(true);
        doThrow(failure).when(client).evaluatePendingUpdate(any(), any(), any());
        var metrics = new SimpleMeterRegistry();
        DownstreamTaskDispatchService service = new DownstreamTaskDispatchService(
                tasks, client, properties(true), CLOCK, immediateTransactions(), directExecutor(),
                scheduledExecutor(), new Semaphore(1), metrics);

        assertThat(service.dispatchAvailable()).isEqualTo(1);
        String counter = terminal
                ? "caseware.downstream.tasks.dead_letter"
                : "caseware.downstream.tasks.retry";
        assertThat(metrics.counter(counter).count()).isEqualTo(1);
    }

    private static ExecutorService directExecutor() {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return mock(Future.class);
        });
        return executor;
    }

    private static ScheduledExecutorService scheduledExecutor() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(mock(ScheduledFuture.class));
        return executor;
    }

    private static TaskLease lease(int attempt) {
        return new TaskLease(7L, "publication-1", "file-1", "v4", "lease", attempt);
    }

    private static WorkerProperties properties(boolean schedulingEnabled) {
        return new WorkerProperties(
                2, 2, 2, Duration.ofMillis(10), Duration.ofSeconds(5),
                3, Duration.ofMillis(1), schedulingEnabled);
    }

    private static TemplatePublicationCommand command(String id) {
        return new TemplatePublicationCommand(id, "template-a", "v4", "CA", NOW);
    }

    private static TemplatePublication publication(
            String id, PublicationStatus status, String cursor, long tasks) {
        return new TemplatePublication(
                id, "template-a", "v4", "CA", NOW, status, cursor, tasks);
    }

    private static EngagementFile file(String id) {
        return new EngagementFile(id, "firm-1", "template-a", "v1", "CA", "CANADA", NOW);
    }
}
