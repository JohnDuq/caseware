package com.caseware.interview.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;

import com.caseware.interview.adapter.out.persistence.entity.FanOutTaskJpaEntity;
import com.caseware.interview.domain.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface FanOutTaskJpaRepository extends JpaRepository<FanOutTaskJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT task
            FROM FanOutTaskJpaEntity task
            JOIN FETCH task.publication
            JOIN FETCH task.file
            WHERE ((task.status IN :readyStatuses AND task.nextAttemptAt <= :now)
                OR (task.status = :processingStatus AND task.leaseExpiresAt < :now))
            ORDER BY task.nextAttemptAt, task.id
            """)
    List<FanOutTaskJpaEntity> findClaimable(
            @Param("readyStatuses") List<TaskStatus> readyStatuses,
            @Param("processingStatus") TaskStatus processingStatus,
            @Param("now") Instant now,
            Pageable page);

    @Query("""
            SELECT task.file.fileId
            FROM FanOutTaskJpaEntity task
            WHERE task.publication.publicationId = :publicationId
              AND task.file.fileId IN :fileIds
            """)
    List<String> findExistingFileIds(
            @Param("publicationId") String publicationId,
            @Param("fileIds") List<String> fileIds);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE FanOutTaskJpaEntity task
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

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE FanOutTaskJpaEntity task
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

    @Query("""
            SELECT task.status AS status, COUNT(task) AS total
            FROM FanOutTaskJpaEntity task
            WHERE task.publication.publicationId = :publicationId
            GROUP BY task.status
            """)
    List<TaskStatusCountProjection> countByStatus(@Param("publicationId") String publicationId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE FanOutTaskJpaEntity task
            SET task.leaseExpiresAt = :expiry,
                task.updatedAt = :now
            WHERE task.id = :taskId
              AND task.status = :processing
              AND task.leaseToken = :leaseToken
            """)
    int renewLease(
            @Param("taskId") long taskId,
            @Param("leaseToken") String leaseToken,
            @Param("expiry") Instant expiry,
            @Param("now") Instant now,
            @Param("processing") TaskStatus processing);
}
