package com.caseware.interview.application.port.in;

import com.caseware.interview.application.port.in.command.EngagementFileCommand;

public interface EngagementFileUseCase {

    void upsert(EngagementFileCommand command);
}
