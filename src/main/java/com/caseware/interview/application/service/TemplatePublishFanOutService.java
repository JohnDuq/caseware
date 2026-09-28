package com.caseware.interview.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.caseware.interview.application.port.in.FanOutUseCase;
import com.caseware.interview.application.port.out.EngagementFileCatalogPort;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.application.port.out.PublicationStorePort;
import com.caseware.interview.application.port.out.TransactionPort;
import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.domain.EngagementFile;
import com.caseware.interview.domain.TemplatePublication;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TemplatePublishFanOutService implements FanOutUseCase {

    private final PublicationStorePort publications;
    private final EngagementFileCatalogPort files;
    private final FanOutTaskStorePort tasks;
    private final WorkerProperties properties;
    private final Clock clock;
    private final TransactionPort transactions;
    private final MeterRegistry metrics;

    @Override
    public int drainAvailablePages() {
        int processed = 0;
        while (processed < properties.maxFanOutPagesPerRun() && processOnePage()) {
            processed++;
        }
        return processed;
    }

    @Override
    public boolean processOnePage() {
        Boolean processed = transactions.required(() -> {
            Instant now = clock.instant();
            TemplatePublication publication = publications.findNextClaimable(now).orElse(null);
            if (publication == null) {
                return false;
            }

            String leaseToken = UUID.randomUUID().toString();
            publications.claim(
                    publication.publicationId(),
                    leaseToken,
                    now.plus(properties.leaseDuration()),
                    now);
            List<EngagementFile> affectedFiles = files.findAffected(
                    publication.templateId(),
                    publication.market(),
                    publication.targetVersion(),
                    publication.scanCursor(),
                    properties.fanOutPageSize());
            int inserted = tasks.createPendingTasks(publication.publicationId(), affectedFiles, now);
            boolean complete = affectedFiles.size() < properties.fanOutPageSize();
            String cursor = affectedFiles.isEmpty()
                    ? publication.scanCursor()
                    : affectedFiles.get(affectedFiles.size() - 1).fileId();
            publications.completePage(
                    publication.publicationId(), cursor, inserted, complete, now);
            metrics.counter("caseware.fanout.tasks.created").increment(inserted);
            if (complete) {
                metrics.counter("caseware.fanout.publications.completed").increment();
            }
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }
}
