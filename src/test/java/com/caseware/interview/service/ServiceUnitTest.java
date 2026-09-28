package com.caseware.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import com.caseware.interview.api.TemplatePublicationRequest;
import com.caseware.interview.client.LoggingEngagementUpdateClient;
import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.repository.EngagementFileRepository;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.PublicationRepository;
import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import com.caseware.interview.repository.entity.TemplatePublicationEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

class ServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Test
    void publicationServiceRegistersTheDomainEvent() {
        PublicationRepository repository = mock(PublicationRepository.class);
        FanOutTaskRepository tasks = mock(FanOutTaskRepository.class);
        PublicationService service = new PublicationService(
                repository, tasks, Clock.fixed(NOW, ZoneOffset.UTC));
        TemplatePublicationRequest request = new TemplatePublicationRequest(
                "publication-1", "template-a", "v4", "CA", NOW);

        assertThat(service.register(request)).isEqualTo(RegistrationResult.CREATED);
        ArgumentCaptor<TemplatePublicationEntity> captor =
                ArgumentCaptor.forClass(TemplatePublicationEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPublicationId()).isEqualTo("publication-1");
        assertThat(captor.getValue().getTemplateId()).isEqualTo("template-a");
        assertThat(captor.getValue().getTargetVersion()).isEqualTo("v4");
        assertThat(captor.getValue().getMarket()).isEqualTo("CA");
        assertThat(captor.getValue().getPublishedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getStatus()).isEqualTo(PublicationStatus.PENDING);
    }

    @Test
    void publicationServiceBuildsStatusAndRejectsUnknownIds() {
        PublicationRepository repository = mock(PublicationRepository.class);
        FanOutTaskRepository tasks = mock(FanOutTaskRepository.class);
        PublicationService service = new PublicationService(
                repository, tasks, Clock.fixed(NOW, ZoneOffset.UTC));
        TemplatePublicationEntity publication = new TemplatePublicationEntity(
                "publication-1", "template-a", "v4", "CA", NOW, NOW);
        publication.claim("lease", NOW.plusSeconds(5), NOW);
        publication.completePage("file-2", 2, true, NOW);
        TaskCounts counts = new TaskCounts(0, 0, 0, 2, 0);
        when(repository.findById("publication-1")).thenReturn(Optional.of(publication));
        when(tasks.countsForPublication("publication-1")).thenReturn(counts);
        when(repository.findById("missing")).thenReturn(Optional.empty());

        var response = service.status("publication-1");

        assertThat(response.publicationId()).isEqualTo("publication-1");
        assertThat(response.tasks()).isSameAs(counts);
        assertThatThrownBy(() -> service.status("missing"))
                .isInstanceOf(PublicationNotFoundException.class)
                .hasMessage("Publication not found: missing");
    }

    @Test
    void loggingClientImplementsTheDownstreamContract() {
        new LoggingEngagementUpdateClient()
                .evaluatePendingUpdate("file-1", "v4", "publication-1:file-1");
    }

    @Test
    void scheduledFanOutHonorsTheSchedulingFlag() {
        TemplatePublishFanOutWorker disabled = fanOutWorker(false);
        disabled.runScheduled();

        TemplatePublishFanOutWorker enabled = org.mockito.Mockito.spy(fanOutWorker(true));
        doReturn(4).when(enabled).drainAvailablePages();
        enabled.runScheduled();

        verify(enabled).drainAvailablePages();
    }

    @Test
    void scheduledDispatcherHonorsTheSchedulingFlag() {
        DownstreamTaskDispatcher disabled = dispatcher(false);
        disabled.runScheduled();

        DownstreamTaskDispatcher enabled = org.mockito.Mockito.spy(dispatcher(true));
        doReturn(2).when(enabled).dispatchAvailable();
        enabled.runScheduled();

        verify(enabled).dispatchAvailable();
    }

    @Test
    void fanOutCompletesAnEmptyJpaPage() {
        PublicationRepository publications = mock(PublicationRepository.class);
        EngagementFileRepository files = mock(EngagementFileRepository.class);
        FanOutTaskRepository tasks = mock(FanOutTaskRepository.class);
        TransactionTemplate transaction = immediateTransaction();
        TemplatePublicationEntity publication = new TemplatePublicationEntity(
                "publication-empty", "template-a", "v4", "CA", NOW, NOW);
        when(publications.findNextClaimable(NOW)).thenReturn(Optional.of(publication));
        when(files.findAffectedFiles(
                "template-a", "CA", "v4", "", PageRequest.of(0, 2)))
                .thenReturn(List.of());
        TemplatePublishFanOutWorker worker = new TemplatePublishFanOutWorker(
                publications, files, tasks, properties(true), Clock.fixed(NOW, java.time.ZoneOffset.UTC),
                transaction, new SimpleMeterRegistry());

        assertThat(worker.processOnePage()).isTrue();
        assertThat(publication.getStatus()).isEqualTo(PublicationStatus.FAN_OUT_COMPLETE);
        verify(tasks).saveAll(List.of());
    }

    @Test
    void workersTreatANullTransactionResultAsNoAvailableWork() {
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        when(transaction.execute(any())).thenReturn(null);
        TemplatePublishFanOutWorker fanOut = new TemplatePublishFanOutWorker(
                mock(PublicationRepository.class), mock(EngagementFileRepository.class),
                mock(FanOutTaskRepository.class), properties(true), Clock.systemUTC(), transaction,
                new SimpleMeterRegistry());
        DownstreamTaskDispatcher downstream = new DownstreamTaskDispatcher(
                mock(FanOutTaskRepository.class),
                mock(com.caseware.interview.client.EngagementUpdateClient.class),
                properties(true), Clock.systemUTC(), transaction, mock(ExecutorService.class),
                new SimpleMeterRegistry());

        assertThat(fanOut.processOnePage()).isFalse();
        assertThat(downstream.dispatchAvailable()).isZero();
    }

    private static TemplatePublishFanOutWorker fanOutWorker(boolean schedulingEnabled) {
        return new TemplatePublishFanOutWorker(
                mock(PublicationRepository.class),
                mock(EngagementFileRepository.class),
                mock(FanOutTaskRepository.class),
                properties(schedulingEnabled),
                Clock.systemUTC(),
                mock(TransactionTemplate.class),
                new SimpleMeterRegistry());
    }

    private static DownstreamTaskDispatcher dispatcher(boolean schedulingEnabled) {
        return new DownstreamTaskDispatcher(
                mock(FanOutTaskRepository.class),
                mock(com.caseware.interview.client.EngagementUpdateClient.class),
                properties(schedulingEnabled),
                Clock.systemUTC(),
                mock(TransactionTemplate.class),
                mock(ExecutorService.class),
                new SimpleMeterRegistry());
    }

    private static WorkerProperties properties(boolean schedulingEnabled) {
        return new WorkerProperties(
                2, 10, 2, Duration.ofMillis(10), Duration.ofSeconds(5),
                3, Duration.ofMillis(1), schedulingEnabled);
    }

    @SuppressWarnings("unchecked")
    private static TransactionTemplate immediateTransaction() {
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        when(transaction.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transaction;
    }
}
