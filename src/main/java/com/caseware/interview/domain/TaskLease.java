package com.caseware.interview.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record TaskLease(
        long taskId,
        String publicationId,
        String fileId,
        String targetVersion,
        String leaseToken,
        int attemptNumber) {

    public String idempotencyKey() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "v1."
                + encoder.encodeToString(publicationId.getBytes(StandardCharsets.UTF_8))
                + "."
                + encoder.encodeToString(fileId.getBytes(StandardCharsets.UTF_8));
    }
}
