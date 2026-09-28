package com.caseware.interview.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.caseware.interview.domain.EngagementFile;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.domain.TaskLease;

public interface FanOutTaskStorePort {

    int createPendingTasks(
            String publicationId,
            List<EngagementFile> files,
            Instant now);

    Optional<TaskLease> claimNext(String leaseToken, Instant now, Instant leaseExpiry);

    boolean complete(TaskLease lease, Instant now);

    boolean fail(TaskLease lease, String error, Instant retryAt, boolean terminal, Instant now);

    TaskCounts countsForPublication(String publicationId);
}
