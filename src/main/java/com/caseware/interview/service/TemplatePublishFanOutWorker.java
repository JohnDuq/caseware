package com.caseware.interview.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.caseware.interview.config.WorkerProperties;
import com.caseware.interview.repository.EngagementFileRepository;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.PublicationRepository;
import com.caseware.interview.repository.entity.EngagementFileEntity;
import com.caseware.interview.repository.entity.FanOutTaskEntity;
import com.caseware.interview.repository.entity.TemplatePublicationEntity;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
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
            TemplatePublicationEntity publication = publications.findNextClaimable(now).orElse(null);
            if (publication == null) {
                return false;
            }

            String leaseToken = UUID.randomUUID().toString();
            publication.claim(leaseToken, now.plus(properties.leaseDuration()), now);

            List<EngagementFileEntity> affectedFiles = files.findAffectedFiles(
                    publication.getTemplateId(),
                    publication.getMarket(),
                    publication.getTargetVersion(),
                    publication.getScanCursor() == null ? "" : publication.getScanCursor(),
                    PageRequest.of(0, properties.fanOutPageSize()));
            List<String> fileIds = affectedFiles.stream()
                    .map(EngagementFileEntity::getFileId)
                    .toList();
            Set<String> existingIds = fileIds.isEmpty()
                    ? Set.of()
                    : new HashSet<>(tasks.findExistingFileIds(publication.getPublicationId(), fileIds));
            List<FanOutTaskEntity> newTasks = affectedFiles.stream()
                    .filter(file -> !existingIds.contains(file.getFileId()))
                    .map(file -> new FanOutTaskEntity(publication, file, now))
                    .toList();
            tasks.saveAll(newTasks);
            int inserted = newTasks.size();
            boolean complete = affectedFiles.size() < properties.fanOutPageSize();
            String cursor = affectedFiles.isEmpty()
                    ? publication.getScanCursor()
                    : affectedFiles.get(affectedFiles.size() - 1).getFileId();
            publication.completePage(cursor, inserted, complete, now);
            metrics.counter("caseware.fanout.tasks.created").increment(inserted);
            if (complete) {
                metrics.counter("caseware.fanout.publications.completed").increment();
            }
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }
}
