package com.caseware.interview.client;

public interface EngagementUpdateClient {

    void evaluatePendingUpdate(String fileId, String targetVersion, String idempotencyKey);
}
