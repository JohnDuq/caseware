package com.caseware.interview.application.port.in;

public interface EngagementFileUseCase {

    void upsert(EngagementFileCommand command);
}
