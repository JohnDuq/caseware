package com.caseware.interview.domain;

import java.time.Instant;
import java.util.Objects;

public record TemplatePublication(
        String publicationId,
        String templateId,
        String targetVersion,
        String market,
        Instant publishedAt,
        PublicationStatus status,
        String scanCursor,
        long totalTasks) {

    public TemplatePublication {
        publicationId = DomainValidation.requireText(publicationId, "publicationId", 100);
        templateId = DomainValidation.requireText(templateId, "templateId", 100);
        targetVersion = DomainValidation.requireText(targetVersion, "targetVersion", 100);
        market = DomainValidation.requireText(market, "market", 40);
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt is required");
        status = Objects.requireNonNull(status, "status is required");
        if (totalTasks < 0) {
            throw new IllegalArgumentException("totalTasks must not be negative");
        }
    }
}
