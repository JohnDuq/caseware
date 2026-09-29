package com.caseware.interview.adapter.out.persistence.entity;

import java.time.Instant;

import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(
        name = "fan_out_task",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_publication_file",
                columnNames = {"publication_id", "file_id"}),
        indexes = @Index(
                name = "idx_task_dispatch",
                columnList = "status,next_attempt_at,lease_expires_at,id"))
public class FanOutTaskJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private TemplatePublicationJpaEntity publication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private EngagementFileJpaEntity file;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private TaskStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_token", length = 100)
    private String leaseToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TaskLease claim(String token, Instant leaseExpiry, Instant now) {
        status = TaskStatus.PROCESSING;
        attemptCount++;
        leaseToken = token;
        leaseExpiresAt = leaseExpiry;
        updatedAt = now;
        return new TaskLease(
                id,
                publication.getPublicationId(),
                file.getFileId(),
                publication.getTargetVersion(),
                token,
                attemptCount);
    }
}
