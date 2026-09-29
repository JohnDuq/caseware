package com.caseware.interview.application.port.out;

public interface EngagementUpdatePort {

    /**
     * Evaluates a file update. Adapters translate expected downstream failures into
     * {@code DownstreamUpdateException}; unexpected programming failures remain visible.
     */
    void evaluatePendingUpdate(String fileId, String targetVersion, String idempotencyKey);
}
