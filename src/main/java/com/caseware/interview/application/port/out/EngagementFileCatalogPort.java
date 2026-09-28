package com.caseware.interview.application.port.out;

import java.util.List;

import com.caseware.interview.domain.EngagementFile;

public interface EngagementFileCatalogPort {

    void save(EngagementFile file);

    List<EngagementFile> findAffected(
            String templateId,
            String market,
            String targetVersion,
            String cursor,
            int limit);
}
