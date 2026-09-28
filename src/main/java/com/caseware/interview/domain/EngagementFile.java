package com.caseware.interview.domain;

import java.time.Instant;

public record EngagementFile(
        String fileId,
        String firmId,
        String templateId,
        String templateVersion,
        String market,
        String region,
        Instant updatedAt) {
}
