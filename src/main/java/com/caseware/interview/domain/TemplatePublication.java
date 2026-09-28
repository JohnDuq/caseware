package com.caseware.interview.domain;

import java.time.Instant;

public record TemplatePublication(
        String publicationId,
        String templateId,
        String targetVersion,
        String market,
        Instant publishedAt,
        PublicationStatus status,
        String scanCursor,
        long totalTasks) {
}
