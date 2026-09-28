package com.caseware.interview.application.port.in;

import java.time.Instant;

public record TemplatePublicationCommand(
        String publicationId,
        String templateId,
        String targetVersion,
        String market,
        Instant publishedAt) {
}
