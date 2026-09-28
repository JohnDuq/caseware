package com.caseware.interview.api;

import java.time.Instant;

import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;

public record PublicationStatusResponse(
        String publicationId,
        String templateId,
        String targetVersion,
        String market,
        Instant publishedAt,
        PublicationStatus fanOutStatus,
        long discoveredFiles,
        TaskCounts tasks) {
}
