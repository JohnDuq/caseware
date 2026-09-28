package com.caseware.interview.application.port.in.command;

public record EngagementFileCommand(
        String fileId,
        String firmId,
        String templateId,
        String templateVersion,
        String market,
        String region) {
}
