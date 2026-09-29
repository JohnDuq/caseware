package com.caseware.interview.adapter.out.persistence;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.caseware.interview.adapter.out.persistence.entity.FanOutTaskJpaEntity;
import com.caseware.interview.adapter.out.persistence.repository.EngagementFileJpaRepository;
import com.caseware.interview.adapter.out.persistence.repository.FanOutTaskJpaRepository;
import com.caseware.interview.adapter.out.persistence.repository.PublicationJpaRepository;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.domain.EngagementFile;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.domain.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaFanOutTaskAdapter implements FanOutTaskStorePort {

    private final FanOutTaskJpaRepository tasks;
    private final PublicationJpaRepository publications;
    private final EngagementFileJpaRepository files;

    @Override
    public int createPendingTasks(
            String publicationId,
            List<EngagementFile> affectedFiles,
            Instant now) {
        List<String> fileIds = affectedFiles.stream().map(EngagementFile::fileId).toList();
        Set<String> existingIds = fileIds.isEmpty()
                ? Set.of()
                : new HashSet<>(tasks.findExistingFileIds(publicationId, fileIds));
        var publication = publications.getReferenceById(publicationId);
        List<FanOutTaskJpaEntity> newTasks = affectedFiles.stream()
                .filter(file -> !existingIds.contains(file.fileId()))
                .map(file -> FanOutTaskJpaEntity.builder()
                        .publication(publication)
                        .file(files.getReferenceById(file.fileId()))
                        .status(TaskStatus.PENDING)
                        .attemptCount(0)
                        .nextAttemptAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        tasks.saveAll(newTasks);
        return newTasks.size();
    }

    @Override
    public Optional<TaskLease> claimNext(String leaseToken, Instant now, Instant leaseExpiry) {
        return tasks.findClaimable(
                        List.of(TaskStatus.PENDING, TaskStatus.RETRY),
                        TaskStatus.PROCESSING,
                        now,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(task -> task.claim(leaseToken, leaseExpiry, now));
    }

    @Override
    public boolean complete(TaskLease lease, Instant now) {
        return tasks.completeLease(
                lease.taskId(), lease.leaseToken(), now,
                TaskStatus.PROCESSING, TaskStatus.SUCCEEDED) == 1;
    }

    @Override
    public boolean renewLease(TaskLease lease, Instant now, Instant leaseExpiry) {
        return tasks.renewLease(
                lease.taskId(), lease.leaseToken(), leaseExpiry, now, TaskStatus.PROCESSING) == 1;
    }

    @Override
    public boolean fail(TaskLease lease, String error, Instant retryAt, boolean terminal, Instant now) {
        String safeError = error == null
                ? "Unknown downstream failure"
                : error.substring(0, Math.min(1000, error.length()));
        return tasks.failLease(
                lease.taskId(), lease.leaseToken(), safeError, retryAt,
                terminal ? TaskStatus.DEAD_LETTER : TaskStatus.RETRY,
                TaskStatus.PROCESSING, now) == 1;
    }

    @Override
    public TaskCounts countsForPublication(String publicationId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        tasks.countByStatus(publicationId)
                .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return new TaskCounts(
                counts.getOrDefault(TaskStatus.PENDING, 0L),
                counts.getOrDefault(TaskStatus.PROCESSING, 0L),
                counts.getOrDefault(TaskStatus.RETRY, 0L),
                counts.getOrDefault(TaskStatus.SUCCEEDED, 0L),
                counts.getOrDefault(TaskStatus.DEAD_LETTER, 0L));
    }
}
