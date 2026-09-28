package com.caseware.interview.application.service;

import java.time.Clock;

import com.caseware.interview.application.port.in.command.EngagementFileCommand;
import com.caseware.interview.application.port.in.EngagementFileUseCase;
import com.caseware.interview.application.port.out.EngagementFileCatalogPort;
import com.caseware.interview.domain.EngagementFile;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EngagementFileApplicationService implements EngagementFileUseCase {

    private final EngagementFileCatalogPort catalog;
    private final Clock clock;

    @Override
    public void upsert(EngagementFileCommand command) {
        catalog.save(new EngagementFile(
                command.fileId(),
                command.firmId(),
                command.templateId(),
                command.templateVersion(),
                command.market(),
                command.region(),
                clock.instant()));
    }
}
