package com.caseware.interview.api;

import java.time.Instant;

import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.domain.TemplatePublication;

public record PublicationStatusResponse(
        String publicationId,
        String templateId,
        String targetVersion,
        String market,
        Instant publishedAt,
        PublicationStatus fanOutStatus,
        long discoveredFiles,
        TaskCounts tasks) {

    public static PublicationStatusResponse from(TemplatePublication publication, TaskCounts tasks) {
        return new PublicationStatusResponse(
                publication.publicationId(),
                publication.templateId(),
                publication.targetVersion(),
                publication.market(),
                publication.publishedAt(),
                publication.status(),
                publication.totalTasks(),
                tasks);
    }
}
