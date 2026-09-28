package com.caseware.interview.application.port.in;

public record EngagementFileCommand(
        String fileId,
        String firmId,
        String templateId,
        String templateVersion,
        String market,
        String region) {
}
