package com.caseware.interview.domain;

import java.time.Instant;
import java.util.Objects;

public record EngagementFile(
        String fileId,
        String firmId,
        String templateId,
        String templateVersion,
        String market,
        String region,
        Instant updatedAt) {

    public EngagementFile {
        fileId = DomainValidation.requireText(fileId, "fileId", 100);
        firmId = DomainValidation.requireText(firmId, "firmId", 100);
        templateId = DomainValidation.requireText(templateId, "templateId", 100);
        templateVersion = DomainValidation.requireText(templateVersion, "templateVersion", 100);
        market = DomainValidation.requireText(market, "market", 40);
        region = DomainValidation.requireText(region, "region", 40);
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
