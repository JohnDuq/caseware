package com.caseware.interview.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.domain.TaskLease;
import com.caseware.interview.domain.TaskStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FanOutTaskRepository {

    private final JdbcClient jdbc;

    public FanOutTaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int createPendingTasks(String publicationId, List<String> fileIds, Instant now) {
        int inserted = 0;
        for (String fileId : fileIds) {
            inserted += jdbc.sql("""
                    INSERT INTO fan_out_task
                        (publication_id, file_id, status, attempt_count, next_attempt_at, created_at, updated_at)
                    SELECT :publicationId, :fileId, 'PENDING', 0, :now, :now, :now
                    WHERE NOT EXISTS (
                        SELECT 1 FROM fan_out_task
                        WHERE publication_id = :publicationId AND file_id = :fileId
                    )
                    """)
                    .param("publicationId", publicationId)
                    .param("fileId", fileId)
                    .param("now", Timestamp.from(now))
                    .update();
        }
        return inserted;
    }

    public Optional<Long> findClaimableTaskId(Instant now) {
        return jdbc.sql("""
                SELECT id
                FROM fan_out_task
                WHERE ((status IN ('PENDING', 'RETRY') AND next_attempt_at <= :now)
                    OR (status = 'PROCESSING' AND lease_expires_at < :now))
                ORDER BY next_attempt_at, id
                FETCH FIRST 1 ROWS ONLY
                """)
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .optional();
    }

    public Optional<TaskLease> claim(long taskId, String leaseToken, Instant now, Instant leaseExpiresAt) {
        int updated = jdbc.sql("""
                UPDATE fan_out_task
                SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                    lease_token = :leaseToken, lease_expires_at = :leaseExpiresAt, updated_at = :now
                WHERE id = :taskId
                  AND ((status IN ('PENDING', 'RETRY') AND next_attempt_at <= :now)
                    OR (status = 'PROCESSING' AND lease_expires_at < :now))
                """)
                .param("leaseToken", leaseToken)
                .param("leaseExpiresAt", Timestamp.from(leaseExpiresAt))
                .param("now", Timestamp.from(now))
                .param("taskId", taskId)
                .update();
        if (updated == 0) {
            return Optional.empty();
        }

        return jdbc.sql("""
                SELECT t.id, t.publication_id, t.file_id, p.target_version,
                       t.lease_token, t.attempt_count
                FROM fan_out_task t
                JOIN template_publication p ON p.publication_id = t.publication_id
                WHERE t.id = :taskId AND t.lease_token = :leaseToken
                """)
                .param("taskId", taskId)
                .param("leaseToken", leaseToken)
                .query(FanOutTaskRepository::mapLease)
                .optional();
    }

    public boolean complete(TaskLease lease, Instant now) {
        return jdbc.sql("""
                UPDATE fan_out_task
                SET status = 'SUCCEEDED', lease_token = NULL, lease_expires_at = NULL,
                    last_error = NULL, updated_at = :now
                WHERE id = :taskId AND status = 'PROCESSING' AND lease_token = :leaseToken
                """)
                .param("now", Timestamp.from(now))
                .param("taskId", lease.taskId())
                .param("leaseToken", lease.leaseToken())
                .update() == 1;
    }

    public boolean fail(TaskLease lease, String error, Instant retryAt, boolean terminal, Instant now) {
        String safeError = error == null ? "Unknown downstream failure" : error.substring(0, Math.min(1000, error.length()));
        return jdbc.sql("""
                UPDATE fan_out_task
                SET status = :status, next_attempt_at = :retryAt,
                    lease_token = NULL, lease_expires_at = NULL,
                    last_error = :error, updated_at = :now
                WHERE id = :taskId AND status = 'PROCESSING' AND lease_token = :leaseToken
                """)
                .param("status", terminal ? "DEAD_LETTER" : "RETRY")
                .param("retryAt", Timestamp.from(retryAt))
                .param("error", safeError)
                .param("now", Timestamp.from(now))
                .param("taskId", lease.taskId())
                .param("leaseToken", lease.leaseToken())
                .update() == 1;
    }

    public TaskCounts countsForPublication(String publicationId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        jdbc.sql("""
                SELECT status, COUNT(*) AS task_count
                FROM fan_out_task
                WHERE publication_id = :publicationId
                GROUP BY status
                """)
                .param("publicationId", publicationId)
                .query((rs, rowNum) -> Map.entry(
                        TaskStatus.valueOf(rs.getString("status")),
                        rs.getLong("task_count")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return new TaskCounts(
                counts.getOrDefault(TaskStatus.PENDING, 0L),
                counts.getOrDefault(TaskStatus.PROCESSING, 0L),
                counts.getOrDefault(TaskStatus.RETRY, 0L),
                counts.getOrDefault(TaskStatus.SUCCEEDED, 0L),
                counts.getOrDefault(TaskStatus.DEAD_LETTER, 0L));
    }

    private static TaskLease mapLease(ResultSet rs, int rowNum) throws SQLException {
        return new TaskLease(
                rs.getLong("id"),
                rs.getString("publication_id"),
                rs.getString("file_id"),
                rs.getString("target_version"),
                rs.getString("lease_token"),
                rs.getInt("attempt_count"));
    }
}
