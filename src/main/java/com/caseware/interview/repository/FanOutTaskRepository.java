package com.caseware.interview.repository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.domain.TaskStatus;
import com.caseware.interview.repository.entity.FanOutTaskEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface FanOutTaskRepository extends JpaRepository<FanOutTaskEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT task
            FROM FanOutTaskEntity task
            JOIN FETCH task.publication
            JOIN FETCH task.file
            WHERE ((task.status IN :readyStatuses AND task.nextAttemptAt <= :now)
                OR (task.status = :processingStatus AND task.leaseExpiresAt < :now))
            ORDER BY task.nextAttemptAt, task.id
            """)
    List<FanOutTaskEntity> findClaimable(
            @Param("readyStatuses") List<TaskStatus> readyStatuses,
            @Param("processingStatus") TaskStatus processingStatus,
            @Param("now") Instant now,
            Pageable page);

    default Optional<FanOutTaskEntity> findNextClaimable(Instant now) {
        return findClaimable(
                List.of(TaskStatus.PENDING, TaskStatus.RETRY),
                TaskStatus.PROCESSING,
                now,
                PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    @Query("""
            SELECT task.file.fileId
            FROM FanOutTaskEntity task
            WHERE task.publication.publicationId = :publicationId
              AND task.file.fileId IN :fileIds
            """)
    List<String> findExistingFileIds(
            @Param("publicationId") String publicationId,
            @Param("fileIds") List<String> fileIds);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE FanOutTaskEntity task
            SET task.status = :succeeded,
                task.leaseToken = NULL,
                task.leaseExpiresAt = NULL,
                task.lastError = NULL,
                task.updatedAt = :now
            WHERE task.id = :taskId
              AND task.status = :processing
              AND task.leaseToken = :leaseToken
            """)
    int completeLease(
            @Param("taskId") long taskId,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now,
            @Param("processing") TaskStatus processing,
            @Param("succeeded") TaskStatus succeeded);

    default boolean complete(TaskLease lease, Instant now) {
        return completeLease(
                lease.taskId(), lease.leaseToken(), now,
                TaskStatus.PROCESSING, TaskStatus.SUCCEEDED) == 1;
    }

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE FanOutTaskEntity task
            SET task.status = :newStatus,
                task.nextAttemptAt = :retryAt,
                task.leaseToken = NULL,
                task.leaseExpiresAt = NULL,
                task.lastError = :error,
                task.updatedAt = :now
            WHERE task.id = :taskId
              AND task.status = :processing
              AND task.leaseToken = :leaseToken
            """)
    int failLease(
            @Param("taskId") long taskId,
            @Param("leaseToken") String leaseToken,
            @Param("error") String error,
            @Param("retryAt") Instant retryAt,
            @Param("newStatus") TaskStatus newStatus,
            @Param("processing") TaskStatus processing,
            @Param("now") Instant now);

    default boolean fail(TaskLease lease, String error, Instant retryAt, boolean terminal, Instant now) {
        String safeError = error == null
                ? "Unknown downstream failure"
                : error.substring(0, Math.min(1000, error.length()));
        return failLease(
                lease.taskId(), lease.leaseToken(), safeError, retryAt,
                terminal ? TaskStatus.DEAD_LETTER : TaskStatus.RETRY,
                TaskStatus.PROCESSING, now) == 1;
    }

    @Query("""
            SELECT task.status AS status, COUNT(task) AS total
            FROM FanOutTaskEntity task
            WHERE task.publication.publicationId = :publicationId
            GROUP BY task.status
            """)
    List<TaskStatusCount> countByStatus(@Param("publicationId") String publicationId);

    default TaskCounts countsForPublication(String publicationId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        countByStatus(publicationId)
                .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return new TaskCounts(
                counts.getOrDefault(TaskStatus.PENDING, 0L),
                counts.getOrDefault(TaskStatus.PROCESSING, 0L),
                counts.getOrDefault(TaskStatus.RETRY, 0L),
                counts.getOrDefault(TaskStatus.SUCCEEDED, 0L),
                counts.getOrDefault(TaskStatus.DEAD_LETTER, 0L));
    }

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FanOutTaskEntity task SET task.leaseExpiresAt = :expiry WHERE task.id = :taskId")
    int updateLeaseExpiry(@Param("taskId") long taskId, @Param("expiry") Instant expiry);
}
