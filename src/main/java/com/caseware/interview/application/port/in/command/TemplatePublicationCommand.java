package com.caseware.interview.application.port.in.command;

import java.time.Instant;
import java.util.Objects;

import com.caseware.interview.domain.DomainValidation;

public record TemplatePublicationCommand(
        String publicationId,
        String templateId,
        String targetVersion,
        String market,
        Instant publishedAt) {

    public TemplatePublicationCommand {
        publicationId = DomainValidation.requireText(publicationId, "publicationId", 100);
        templateId = DomainValidation.requireText(templateId, "templateId", 100);
        targetVersion = DomainValidation.requireText(targetVersion, "targetVersion", 100);
        market = DomainValidation.requireText(market, "market", 40);
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt is required");
    }
}
