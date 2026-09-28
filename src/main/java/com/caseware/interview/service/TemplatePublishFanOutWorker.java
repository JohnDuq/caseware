package com.caseware.interview.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.domain.TemplatePublication;
import com.caseware.interview.repository.EngagementFileRepository;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.PublicationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TemplatePublishFanOutWorker {

    private final PublicationRepository publications;
    private final EngagementFileRepository files;
    private final FanOutTaskRepository tasks;
    private final WorkerProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final MeterRegistry metrics;

    public TemplatePublishFanOutWorker(
            PublicationRepository publications,
            EngagementFileRepository files,
            FanOutTaskRepository tasks,
            WorkerProperties properties,
            Clock clock,
            TransactionTemplate transaction,
            MeterRegistry metrics) {
        this.publications = publications;
        this.files = files;
        this.tasks = tasks;
        this.properties = properties;
        this.clock = clock;
        this.transaction = transaction;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay:250ms}")
    public void runScheduled() {
        if (!properties.schedulingEnabled()) {
            return;
        }
        drainAvailablePages();
    }

    public int drainAvailablePages() {
        int processed = 0;
        while (processed < properties.maxFanOutPagesPerRun() && processOnePage()) {
            processed++;
        }
        return processed;
    }

    public boolean processOnePage() {
        Boolean processed = transaction.execute(ignored -> {
            Instant now = clock.instant();
            TemplatePublication publication = publications.findClaimable(now).orElse(null);
            if (publication == null) {
                return false;
            }

            String leaseToken = UUID.randomUUID().toString();
            if (!publications.claim(
                    publication.publicationId(),
                    leaseToken,
                    now,
                    now.plus(properties.leaseDuration()))) {
                return false;
            }

            List<String> fileIds = files.findAffectedFileIds(
                    publication.templateId(),
                    publication.market(),
                    publication.targetVersion(),
                    publication.scanCursor(),
                    properties.fanOutPageSize());
            int inserted = tasks.createPendingTasks(publication.publicationId(), fileIds, now);
            boolean complete = fileIds.size() < properties.fanOutPageSize();
            String cursor = fileIds.isEmpty()
                    ? publication.scanCursor()
                    : fileIds.get(fileIds.size() - 1);
            publications.pageCompleted(
                    publication.publicationId(), leaseToken, cursor, inserted, complete, now);
            metrics.counter("caseware.fanout.tasks.created").increment(inserted);
            if (complete) {
                metrics.counter("caseware.fanout.publications.completed").increment();
            }
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }
}
