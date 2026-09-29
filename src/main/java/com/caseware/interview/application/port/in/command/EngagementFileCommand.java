package com.caseware.interview.application.port.in.command;

import com.caseware.interview.domain.DomainValidation;

public record EngagementFileCommand(
        String fileId,
        String firmId,
        String templateId,
        String templateVersion,
        String market,
        String region) {

    public EngagementFileCommand {
        fileId = DomainValidation.requireText(fileId, "fileId", 100);
        firmId = DomainValidation.requireText(firmId, "firmId", 100);
        templateId = DomainValidation.requireText(templateId, "templateId", 100);
        templateVersion = DomainValidation.requireText(templateVersion, "templateVersion", 100);
        market = DomainValidation.requireText(market, "market", 40);
        region = DomainValidation.requireText(region, "region", 40);
    }
}
