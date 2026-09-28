package com.caseware.interview.application.port.out;

public interface EngagementUpdatePort {

    void evaluatePendingUpdate(String fileId, String targetVersion, String idempotencyKey);
}
