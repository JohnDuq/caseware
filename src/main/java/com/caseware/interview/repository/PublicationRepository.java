package com.caseware.interview.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TemplatePublication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PublicationRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public PublicationRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public RegistrationResult register(TemplatePublication publication) {
        Optional<TemplatePublication> existing = findById(publication.publicationId());
        if (existing.isPresent()) {
            assertSameEvent(existing.get(), publication);
            return RegistrationResult.DUPLICATE;
        }

        Instant now = clock.instant();
        try {
            jdbc.sql("""
                    INSERT INTO template_publication
                        (publication_id, template_id, target_version, market, published_at, status,
                         total_tasks, created_at, updated_at)
                    VALUES (:publicationId, :templateId, :targetVersion, :market, :publishedAt,
                            'PENDING', 0, :now, :now)
                    """)
                    .param("publicationId", publication.publicationId())
                    .param("templateId", publication.templateId())
                    .param("targetVersion", publication.targetVersion())
                    .param("market", publication.market())
                    .param("publishedAt", Timestamp.from(publication.publishedAt()))
                    .param("now", Timestamp.from(now))
                    .update();
            return RegistrationResult.CREATED;
        } catch (DuplicateKeyException race) {
            TemplatePublication concurrent = findById(publication.publicationId()).orElseThrow(() -> race);
            assertSameEvent(concurrent, publication);
            return RegistrationResult.DUPLICATE;
        }
    }

    public Optional<TemplatePublication> findById(String publicationId) {
        return jdbc.sql("""
                SELECT publication_id, template_id, target_version, market, published_at,
                       status, scan_cursor, total_tasks
                FROM template_publication
                WHERE publication_id = :publicationId
                """)
                .param("publicationId", publicationId)
                .query(PublicationRepository::mapPublication)
                .optional();
    }

    public Optional<TemplatePublication> findClaimable(Instant now) {
        return jdbc.sql("""
                SELECT publication_id, template_id, target_version, market, published_at,
                       status, scan_cursor, total_tasks
                FROM template_publication
                WHERE status IN ('PENDING', 'FANNING_OUT')
                  AND (lease_expires_at IS NULL OR lease_expires_at < :now)
                ORDER BY published_at, publication_id
                FETCH FIRST 1 ROWS ONLY
                """)
                .param("now", Timestamp.from(now))
                .query(PublicationRepository::mapPublication)
                .optional();
    }

    public boolean claim(String publicationId, String leaseToken, Instant now, Instant leaseExpiresAt) {
        return jdbc.sql("""
                UPDATE template_publication
                SET status = 'FANNING_OUT', lease_token = :leaseToken,
                    lease_expires_at = :leaseExpiresAt, updated_at = :now
                WHERE publication_id = :publicationId
                  AND status IN ('PENDING', 'FANNING_OUT')
                  AND (lease_expires_at IS NULL OR lease_expires_at < :now)
                """)
                .param("leaseToken", leaseToken)
                .param("leaseExpiresAt", Timestamp.from(leaseExpiresAt))
                .param("now", Timestamp.from(now))
                .param("publicationId", publicationId)
                .update() == 1;
    }

    public void pageCompleted(
            String publicationId,
            String leaseToken,
            String newCursor,
            int insertedTasks,
            boolean fanOutComplete,
            Instant now) {
        int updated = jdbc.sql("""
                UPDATE template_publication
                SET status = :status, scan_cursor = :cursor, total_tasks = total_tasks + :insertedTasks,
                    lease_token = NULL, lease_expires_at = NULL, updated_at = :now
                WHERE publication_id = :publicationId AND lease_token = :leaseToken
                """)
                .param("status", fanOutComplete ? "FAN_OUT_COMPLETE" : "FANNING_OUT")
                .param("cursor", newCursor)
                .param("insertedTasks", insertedTasks)
                .param("now", Timestamp.from(now))
                .param("publicationId", publicationId)
                .param("leaseToken", leaseToken)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Publication lease was lost for " + publicationId);
        }
    }

    private static TemplatePublication mapPublication(ResultSet rs, int rowNum) throws SQLException {
        return new TemplatePublication(
                rs.getString("publication_id"),
                rs.getString("template_id"),
                rs.getString("target_version"),
                rs.getString("market"),
                rs.getTimestamp("published_at").toInstant(),
                PublicationStatus.valueOf(rs.getString("status")),
                rs.getString("scan_cursor"),
                rs.getLong("total_tasks"));
    }

    private static void assertSameEvent(TemplatePublication existing, TemplatePublication incoming) {
        boolean same = existing.templateId().equals(incoming.templateId())
                && existing.targetVersion().equals(incoming.targetVersion())
                && existing.market().equals(incoming.market())
                && existing.publishedAt().equals(incoming.publishedAt());
        if (!same) {
            throw new PublicationConflictException(incoming.publicationId());
        }
    }

    public enum RegistrationResult {
        CREATED,
        DUPLICATE
    }
}
