package com.caseware.interview.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.caseware.interview.adapter.out.persistence.entity.EngagementFileJpaEntity;
import com.caseware.interview.adapter.out.persistence.entity.FanOutTaskJpaEntity;
import com.caseware.interview.adapter.out.persistence.entity.TemplatePublicationJpaEntity;
import com.caseware.interview.adapter.out.persistence.repository.EngagementFileJpaRepository;
import com.caseware.interview.adapter.out.persistence.repository.FanOutTaskJpaRepository;
import com.caseware.interview.adapter.out.persistence.repository.PublicationJpaRepository;
import com.caseware.interview.adapter.out.persistence.repository.TaskStatusCountProjection;
import com.caseware.interview.domain.EngagementFile;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.domain.TaskStatus;
import com.caseware.interview.domain.TemplatePublication;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class PersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Test
    void publicationAdapterCreatesAndReadsDomainObjects() {
        PublicationJpaRepository repository = mock(PublicationJpaRepository.class);
        JpaPublicationAdapter adapter = new JpaPublicationAdapter(repository);
        TemplatePublication domain = publication();
        when(repository.findById("publication-1"))
                .thenReturn(Optional.empty(), Optional.of(publicationEntity()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = adapter.create(domain, NOW);

        assertThat(created.created()).isTrue();
        assertThat(created.stored()).isEqualTo(domain);
        assertThat(adapter.findById("publication-1")).contains(domain);
    }

    @Test
    void publicationAdapterReturnsExistingRecordsWithoutInserting() {
        PublicationJpaRepository repository = mock(PublicationJpaRepository.class);
        when(repository.findById("publication-1")).thenReturn(Optional.of(publicationEntity()));

        var result = new JpaPublicationAdapter(repository).create(publication(), NOW);

        assertThat(result.created()).isFalse();
        assertThat(result.stored()).isEqualTo(publication());
    }

    @Test
    void publicationAdapterRecoversEquivalentConcurrentInsert() {
        PublicationJpaRepository repository = mock(PublicationJpaRepository.class);
        DataIntegrityViolationException collision = new DataIntegrityViolationException("race");
        when(repository.findById("publication-1"))
                .thenReturn(Optional.empty(), Optional.of(publicationEntity()));
        when(repository.saveAndFlush(any())).thenThrow(collision);

        var result = new JpaPublicationAdapter(repository).create(publication(), NOW);

        assertThat(result.created()).isFalse();
        assertThat(result.stored()).isEqualTo(publication());
    }

    @Test
    void publicationAdapterPreservesConcurrentInsertFailureWhenRecordIsUnavailable() {
        PublicationJpaRepository repository = mock(PublicationJpaRepository.class);
        DataIntegrityViolationException collision = new DataIntegrityViolationException("race");
        when(repository.findById("publication-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(collision);

        assertThatThrownBy(() -> new JpaPublicationAdapter(repository).create(publication(), NOW))
                .isSameAs(collision);
    }

    @Test
    void publicationAdapterClaimsAndCompletesPages() {
        PublicationJpaRepository repository = mock(PublicationJpaRepository.class);
        TemplatePublicationJpaEntity entity = publicationEntity();
        when(repository.findClaimable(anyList(), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(entity));
        when(repository.findById("publication-1")).thenReturn(Optional.of(entity));
        JpaPublicationAdapter adapter = new JpaPublicationAdapter(repository);

        assertThat(adapter.findNextClaimable(NOW)).contains(publication());
        adapter.claim("publication-1", "lease", NOW.plusSeconds(5), NOW);
        adapter.completePage("publication-1", "file-2", 2, true, NOW);

        assertThat(entity.getStatus()).isEqualTo(PublicationStatus.FAN_OUT_COMPLETE);
        assertThat(entity.getScanCursor()).isEqualTo("file-2");
        assertThat(entity.getTotalTasks()).isEqualTo(2);
    }

    @Test
    void publicationAdapterReportsDisappearingClaimedRecords() {
        PublicationJpaRepository repository = mock(PublicationJpaRepository.class);
        when(repository.findById("missing")).thenReturn(Optional.empty());
        JpaPublicationAdapter adapter = new JpaPublicationAdapter(repository);

        assertThatThrownBy(() -> adapter.claim("missing", "lease", NOW, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.completePage("missing", null, 0, true, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void engagementFileAdapterMapsBothDirectionsAndNormalizesNullCursor() {
        EngagementFileJpaRepository repository = mock(EngagementFileJpaRepository.class);
        EngagementFileJpaEntity entity = fileEntity("file-1");
        when(repository.findAffectedFiles(
                eq("template-a"), eq("CA"), eq("v4"), eq(""), any(Pageable.class)))
                .thenReturn(List.of(entity));
        JpaEngagementFileAdapter adapter = new JpaEngagementFileAdapter(repository);
        EngagementFile file = file("file-1");

        adapter.save(file);
        assertThat(adapter.findAffected("template-a", "CA", "v4", null, 2))
                .containsExactly(file);

        ArgumentCaptor<EngagementFileJpaEntity> captor =
                ArgumentCaptor.forClass(EngagementFileJpaEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFileId()).isEqualTo("file-1");
    }

    @Test
    void fanOutTaskAdapterCreatesOnlyMissingTasksAndClaimsWork() {
        FanOutTaskJpaRepository tasks = mock(FanOutTaskJpaRepository.class);
        PublicationJpaRepository publications = mock(PublicationJpaRepository.class);
        EngagementFileJpaRepository files = mock(EngagementFileJpaRepository.class);
        TemplatePublicationJpaEntity publication = publicationEntity();
        EngagementFileJpaEntity file1 = fileEntity("file-1");
        EngagementFileJpaEntity file2 = fileEntity("file-2");
        when(tasks.findExistingFileIds("publication-1", List.of("file-1", "file-2")))
                .thenReturn(List.of("file-1"));
        when(publications.getReferenceById("publication-1")).thenReturn(publication);
        when(files.getReferenceById("file-2")).thenReturn(file2);
        FanOutTaskJpaEntity claimable = taskEntity(7L, publication, file1);
        when(tasks.findClaimable(anyList(), eq(TaskStatus.PROCESSING), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(claimable));
        JpaFanOutTaskAdapter adapter = new JpaFanOutTaskAdapter(tasks, publications, files);

        assertThat(adapter.createPendingTasks(
                "publication-1", List.of(file("file-1"), file("file-2")), NOW)).isEqualTo(1);
        TaskLease lease = adapter.claimNext("lease", NOW, NOW.plusSeconds(5)).orElseThrow();

        assertThat(lease.taskId()).isEqualTo(7L);
        assertThat(lease.idempotencyKey()).isEqualTo("v1.cHVibGljYXRpb24tMQ.ZmlsZS0x");
        assertThat(claimable.getStatus()).isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void fanOutTaskAdapterHandlesEmptyPagesAndNoClaimableWork() {
        FanOutTaskJpaRepository tasks = mock(FanOutTaskJpaRepository.class);
        PublicationJpaRepository publications = mock(PublicationJpaRepository.class);
        when(publications.getReferenceById("publication-1")).thenReturn(publicationEntity());
        when(tasks.findClaimable(anyList(), eq(TaskStatus.PROCESSING), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of());
        JpaFanOutTaskAdapter adapter = new JpaFanOutTaskAdapter(
                tasks, publications, mock(EngagementFileJpaRepository.class));

        assertThat(adapter.createPendingTasks("publication-1", List.of(), NOW)).isZero();
        assertThat(adapter.claimNext("lease", NOW, NOW)).isEmpty();
    }

    @Test
    void fanOutTaskAdapterCompletesFailsAndAggregatesCounts() {
        FanOutTaskJpaRepository tasks = mock(FanOutTaskJpaRepository.class);
        JpaFanOutTaskAdapter adapter = new JpaFanOutTaskAdapter(
                tasks, mock(PublicationJpaRepository.class), mock(EngagementFileJpaRepository.class));
        TaskLease lease = new TaskLease(7L, "publication-1", "file-1", "v4", "lease", 2);
        when(tasks.completeLease(7L, "lease", NOW, TaskStatus.PROCESSING, TaskStatus.SUCCEEDED))
                .thenReturn(1);
        when(tasks.renewLease(7L, "lease", NOW.plusSeconds(5), NOW, TaskStatus.PROCESSING))
                .thenReturn(1);
        when(tasks.failLease(eq(7L), eq("lease"), any(), eq(NOW),
                eq(TaskStatus.DEAD_LETTER), eq(TaskStatus.PROCESSING), eq(NOW))).thenReturn(1);
        TaskStatusCountProjection pending = count(TaskStatus.PENDING, 2L);
        TaskStatusCountProjection retry = count(TaskStatus.RETRY, 3L);
        when(tasks.countByStatus("publication-1")).thenReturn(List.of(pending, retry));

        assertThat(adapter.complete(lease, NOW)).isTrue();
        assertThat(adapter.renewLease(lease, NOW, NOW.plusSeconds(5))).isTrue();
        assertThat(adapter.fail(lease, null, NOW, true, NOW)).isTrue();
        assertThat(adapter.countsForPublication("publication-1").pending()).isEqualTo(2);
        assertThat(adapter.countsForPublication("publication-1").retrying()).isEqualTo(3);
    }

    @Test
    void fanOutTaskAdapterTruncatesErrorsAndReturnsFalseForStaleLeases() {
        FanOutTaskJpaRepository tasks = mock(FanOutTaskJpaRepository.class);
        JpaFanOutTaskAdapter adapter = new JpaFanOutTaskAdapter(
                tasks, mock(PublicationJpaRepository.class), mock(EngagementFileJpaRepository.class));
        TaskLease lease = new TaskLease(7L, "publication-1", "file-1", "v4", "lease", 1);
        String longError = "x".repeat(1001);

        assertThat(adapter.complete(lease, NOW)).isFalse();
        assertThat(adapter.fail(lease, longError, NOW, false, NOW)).isFalse();
        verify(tasks).failLease(7L, "lease", "x".repeat(1000), NOW,
                TaskStatus.RETRY, TaskStatus.PROCESSING, NOW);
    }

    @Test
    @SuppressWarnings("unchecked")
    void springTransactionAdapterDelegatesTheUnitOfWork() {
        TransactionOperations operations = mock(TransactionOperations.class);
        when(operations.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<String> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        assertThat(new SpringTransactionAdapter(operations).required(() -> "done"))
                .isEqualTo("done");
    }

    private static TaskStatusCountProjection count(TaskStatus status, long total) {
        TaskStatusCountProjection row = mock(TaskStatusCountProjection.class);
        when(row.getStatus()).thenReturn(status);
        when(row.getTotal()).thenReturn(total);
        return row;
    }

    private static TemplatePublication publication() {
        return new TemplatePublication(
                "publication-1", "template-a", "v4", "CA", NOW,
                PublicationStatus.PENDING, null, 0);
    }

    private static TemplatePublicationJpaEntity publicationEntity() {
        return TemplatePublicationJpaEntity.builder()
                .publicationId("publication-1")
                .templateId("template-a")
                .targetVersion("v4")
                .market("CA")
                .publishedAt(NOW)
                .status(PublicationStatus.PENDING)
                .totalTasks(0)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static EngagementFile file(String id) {
        return new EngagementFile(id, "firm-1", "template-a", "v1", "CA", "CANADA", NOW);
    }

    private static EngagementFileJpaEntity fileEntity(String id) {
        return EngagementFileJpaEntity.builder()
                .fileId(id)
                .firmId("firm-1")
                .templateId("template-a")
                .templateVersion("v1")
                .market("CA")
                .region("CANADA")
                .updatedAt(NOW)
                .build();
    }

    private static FanOutTaskJpaEntity taskEntity(
            long id,
            TemplatePublicationJpaEntity publication,
            EngagementFileJpaEntity file) {
        return FanOutTaskJpaEntity.builder()
                .id(id)
                .publication(publication)
                .file(file)
                .status(TaskStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
