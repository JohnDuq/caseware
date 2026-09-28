package com.caseware.interview.adapter.in.web;

import java.time.Instant;

import com.caseware.interview.application.port.in.PublicationStatusView;
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

    static PublicationStatusResponse from(PublicationStatusView view) {
        return new PublicationStatusResponse(
                view.publicationId(),
                view.templateId(),
                view.targetVersion(),
                view.market(),
                view.publishedAt(),
                view.fanOutStatus(),
                view.discoveredFiles(),
                view.tasks());
    }
}
