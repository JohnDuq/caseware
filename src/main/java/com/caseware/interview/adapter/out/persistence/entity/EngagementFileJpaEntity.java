package com.caseware.interview.adapter.out.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "engagement_file_catalog",
        indexes = @Index(
                name = "idx_file_template_market",
                columnList = "template_id,market,file_id"))
public class EngagementFileJpaEntity {

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
}
