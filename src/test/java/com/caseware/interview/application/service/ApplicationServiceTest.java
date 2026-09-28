package com.caseware.interview.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import com.caseware.interview.adapter.in.scheduler.DownstreamTaskScheduler;
import com.caseware.interview.adapter.in.scheduler.TemplatePublishScheduler;
import com.caseware.interview.adapter.out.downstream.LoggingEngagementUpdateAdapter;
import com.caseware.interview.application.exception.PublicationConflictException;
import com.caseware.interview.application.exception.PublicationNotFoundException;
import com.caseware.interview.application.port.in.EngagementFileCommand;
import com.caseware.interview.application.port.in.FanOutUseCase;
import com.caseware.interview.application.port.in.PublicationRegistration;
import com.caseware.interview.application.port.in.TaskDispatchUseCase;
import com.caseware.interview.application.port.in.TemplatePublicationCommand;
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
                new Semaphore(2), new SimpleMeterRegistry());

        assertThat(service.dispatchAvailable()).isZero();
        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void loggingAdapterImplementsTheDownstreamPort() {
        new LoggingEngagementUpdateAdapter()
                .evaluatePendingUpdate("file-1", "v4", "publication-1:file-1");
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
