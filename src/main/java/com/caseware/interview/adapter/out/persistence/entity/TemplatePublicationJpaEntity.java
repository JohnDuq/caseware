package com.caseware.interview.adapter.out.persistence.entity;

import java.time.Instant;

import com.caseware.interview.domain.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
        name = "template_publication",
        indexes = @Index(
                name = "idx_publication_work",
                columnList = "status,lease_expires_at,published_at"))
public class TemplatePublicationJpaEntity {

    @Id
    @Column(name = "publication_id", length = 100, nullable = false)
    private String publicationId;

    @Column(name = "template_id", length = 100, nullable = false)
    private String templateId;

    @Column(name = "target_version", length = 100, nullable = false)
    private String targetVersion;

    @Column(length = 40, nullable = false)
    private String market;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private PublicationStatus status;

    @Column(name = "scan_cursor", length = 100)
    private String scanCursor;

    @Column(name = "lease_token", length = 100)
    private String leaseToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "total_tasks", nullable = false)
    private long totalTasks;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void claim(String token, Instant leaseExpiry, Instant now) {
        status = PublicationStatus.FANNING_OUT;
        leaseToken = token;
        leaseExpiresAt = leaseExpiry;
        updatedAt = now;
    }

    public void completePage(String cursor, int insertedTasks, boolean complete, Instant now) {
        status = complete ? PublicationStatus.FAN_OUT_COMPLETE : PublicationStatus.FANNING_OUT;
        scanCursor = cursor;
        totalTasks += insertedTasks;
        leaseToken = null;
        leaseExpiresAt = null;
        updatedAt = now;
    }
}
