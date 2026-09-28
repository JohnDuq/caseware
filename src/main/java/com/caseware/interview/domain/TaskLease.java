package com.caseware.interview.domain;

public record TaskLease(
        long taskId,
        String publicationId,
        String fileId,
        String targetVersion,
        String leaseToken,
        int attemptNumber) {

    public String idempotencyKey() {
        return publicationId + ":" + fileId;
    }
}
