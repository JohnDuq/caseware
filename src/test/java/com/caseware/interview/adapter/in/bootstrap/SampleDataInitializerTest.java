package com.caseware.interview.adapter.in.bootstrap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Instant;

import com.caseware.interview.application.port.in.command.EngagementFileCommand;
import com.caseware.interview.application.port.in.EngagementFileUseCase;
import com.caseware.interview.application.port.in.command.TemplatePublicationCommand;
import com.caseware.interview.application.port.in.TemplatePublicationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class SampleDataInitializerTest {

    @Test
    void loadsARepeatableWorkingDatasetThroughTheInputPorts() {
        EngagementFileUseCase files = mock(EngagementFileUseCase.class);
        TemplatePublicationUseCase publications = mock(TemplatePublicationUseCase.class);
        SampleDataInitializer initializer = new SampleDataInitializer(files, publications);

        initializer.run(mock(ApplicationArguments.class));

        verify(files).upsert(file("sample-file-001", "firm-north", "v4", "CA", "CANADA"));
        verify(files).upsert(file("sample-file-002", "firm-north", "v5", "CA", "CANADA"));
        verify(files).upsert(file("sample-file-003", "firm-west", "v6", "CA", "CANADA"));
        verify(files).upsert(file("sample-file-current", "firm-west", "v7", "CA", "CANADA"));
        verify(files).upsert(file("sample-file-eu", "firm-eu", "v3", "EU", "EUROPE"));
        verifyNoMoreInteractions(files);
        verify(publications).register(new TemplatePublicationCommand(
                "sample-publication-audit-ca-v7",
                "audit-ca",
                "v7",
                "CA",
                Instant.parse("2026-09-28T12:00:00Z")));
        verifyNoMoreInteractions(publications);
    }

    private static EngagementFileCommand file(
            String fileId,
            String firmId,
            String version,
            String market,
            String region) {
        return new EngagementFileCommand(
                fileId, firmId, "audit-ca", version, market, region);
    }
}
