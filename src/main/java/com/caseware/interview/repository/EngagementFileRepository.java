package com.caseware.interview.repository;

import java.util.List;

import com.caseware.interview.repository.entity.EngagementFileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EngagementFileRepository extends JpaRepository<EngagementFileEntity, String> {

    @Query("""
            SELECT file
            FROM EngagementFileEntity file
            WHERE file.templateId = :templateId
              AND file.market = :market
              AND file.templateVersion <> :targetVersion
              AND file.fileId > :cursor
            ORDER BY file.fileId
            """)
    List<EngagementFileEntity> findAffectedFiles(
            @Param("templateId") String templateId,
            @Param("market") String market,
            @Param("targetVersion") String targetVersion,
            @Param("cursor") String cursor,
            Pageable page);
}
