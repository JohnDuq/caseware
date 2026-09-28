package com.caseware.interview.repository.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "engagement_file_catalog",
        indexes = @Index(
                name = "idx_file_template_market",
                columnList = "template_id,market,file_id"))
public class EngagementFileEntity {

    @Id
    @Column(name = "file_id", length = 100, nullable = false)
    private String fileId;

    @Column(name = "firm_id", length = 100, nullable = false)
    private String firmId;

    @Column(name = "template_id", length = 100, nullable = false)
    private String templateId;

    @Column(name = "template_version", length = 100, nullable = false)
    private String templateVersion;

    @Column(length = 40, nullable = false)
    private String market;

    @Column(length = 40, nullable = false)
    private String region;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EngagementFileEntity() {
    }

    public EngagementFileEntity(
            String fileId,
            String firmId,
            String templateId,
            String templateVersion,
            String market,
            String region,
            Instant updatedAt) {
        this.fileId = fileId;
        this.firmId = firmId;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.market = market;
        this.region = region;
        this.updatedAt = updatedAt;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFirmId() {
        return firmId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getTemplateVersion() {
        return templateVersion;
    }

    public String getMarket() {
        return market;
    }

    public String getRegion() {
        return region;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
