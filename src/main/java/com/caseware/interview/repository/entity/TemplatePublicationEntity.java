package com.caseware.interview.repository.entity;

import java.time.Instant;

import com.caseware.interview.domain.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "template_publication",
        indexes = @Index(
                name = "idx_publication_work",
                columnList = "status,lease_expires_at,published_at"))
public class TemplatePublicationEntity {

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

    protected TemplatePublicationEntity() {
    }

    public TemplatePublicationEntity(
            String publicationId,
            String templateId,
            String targetVersion,
            String market,
            Instant publishedAt,
            Instant now) {
        this.publicationId = publicationId;
        this.templateId = templateId;
        this.targetVersion = targetVersion;
        this.market = market;
        this.publishedAt = publishedAt;
        this.status = PublicationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

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

    public String getPublicationId() {
        return publicationId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public String getMarket() {
        return market;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public String getScanCursor() {
        return scanCursor;
    }

    public long getTotalTasks() {
        return totalTasks;
    }
}
