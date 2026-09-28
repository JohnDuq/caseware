package com.caseware.interview.adapter.in.bootstrap;

import java.time.Instant;
import java.util.List;

import com.caseware.interview.application.port.in.EngagementFileCommand;
import com.caseware.interview.application.port.in.EngagementFileUseCase;
import com.caseware.interview.application.port.in.TemplatePublicationCommand;
import com.caseware.interview.application.port.in.TemplatePublicationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "application.sample-data.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SampleDataInitializer implements ApplicationRunner {

    private static final Instant SAMPLE_PUBLICATION_TIME =
            Instant.parse("2026-09-28T12:00:00Z");

    private final EngagementFileUseCase engagementFiles;
    private final TemplatePublicationUseCase publications;

    @Override
    public void run(ApplicationArguments arguments) {
        sampleFiles().forEach(engagementFiles::upsert);
        publications.register(new TemplatePublicationCommand(
                "sample-publication-audit-ca-v7",
                "audit-ca",
                "v7",
                "CA",
                SAMPLE_PUBLICATION_TIME));
    }

    private static List<EngagementFileCommand> sampleFiles() {
        return List.of(
                file("sample-file-001", "firm-north", "v4", "CA", "CANADA"),
                file("sample-file-002", "firm-north", "v5", "CA", "CANADA"),
                file("sample-file-003", "firm-west", "v6", "CA", "CANADA"),
                file("sample-file-current", "firm-west", "v7", "CA", "CANADA"),
                file("sample-file-eu", "firm-eu", "v3", "EU", "EUROPE"));
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
