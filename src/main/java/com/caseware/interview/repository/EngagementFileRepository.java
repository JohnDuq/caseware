package com.caseware.interview.repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.caseware.interview.domain.EngagementFile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EngagementFileRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public EngagementFileRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void save(EngagementFile file) {
        Instant updatedAt = file.updatedAt() == null ? clock.instant() : file.updatedAt();
        jdbc.sql("""
                MERGE INTO engagement_file_catalog
                    (file_id, firm_id, template_id, template_version, market, region, updated_at)
                KEY(file_id)
                VALUES (:fileId, :firmId, :templateId, :templateVersion, :market, :region, :updatedAt)
                """)
                .param("fileId", file.fileId())
                .param("firmId", file.firmId())
                .param("templateId", file.templateId())
                .param("templateVersion", file.templateVersion())
                .param("market", file.market())
                .param("region", file.region())
                .param("updatedAt", Timestamp.from(updatedAt))
                .update();
    }

    public List<String> findAffectedFileIds(
            String templateId,
            String market,
            String targetVersion,
            String afterFileId,
            int limit) {
        String cursor = afterFileId == null ? "" : afterFileId;
        return jdbc.sql("""
                SELECT file_id
                FROM engagement_file_catalog
                WHERE template_id = :templateId
                  AND market = :market
                  AND template_version <> :targetVersion
                  AND file_id > :cursor
                ORDER BY file_id
                FETCH FIRST :limit ROWS ONLY
                """)
                .param("templateId", templateId)
                .param("market", market)
                .param("targetVersion", targetVersion)
                .param("cursor", cursor)
                .param("limit", limit)
                .query(String.class)
                .list();
    }
}
