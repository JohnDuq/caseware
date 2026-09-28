package com.caseware.interview.adapter.out.persistence;

import java.util.List;

import com.caseware.interview.adapter.out.persistence.entity.EngagementFileJpaEntity;
import com.caseware.interview.adapter.out.persistence.repository.EngagementFileJpaRepository;
import com.caseware.interview.application.port.out.EngagementFileCatalogPort;
import com.caseware.interview.domain.EngagementFile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaEngagementFileAdapter implements EngagementFileCatalogPort {

    private final EngagementFileJpaRepository repository;

    @Override
    public void save(EngagementFile file) {
        repository.save(toEntity(file));
    }

    @Override
    public List<EngagementFile> findAffected(
            String templateId,
            String market,
            String targetVersion,
            String cursor,
            int limit) {
        return repository.findAffectedFiles(
                        templateId,
                        market,
                        targetVersion,
                        cursor == null ? "" : cursor,
                        PageRequest.of(0, limit))
                .stream()
                .map(JpaEngagementFileAdapter::toDomain)
                .toList();
    }

    private static EngagementFileJpaEntity toEntity(EngagementFile file) {
        return EngagementFileJpaEntity.builder()
                .fileId(file.fileId())
                .firmId(file.firmId())
                .templateId(file.templateId())
                .templateVersion(file.templateVersion())
                .market(file.market())
                .region(file.region())
                .updatedAt(file.updatedAt())
                .build();
    }

    private static EngagementFile toDomain(EngagementFileJpaEntity entity) {
        return new EngagementFile(
                entity.getFileId(),
                entity.getFirmId(),
                entity.getTemplateId(),
                entity.getTemplateVersion(),
                entity.getMarket(),
                entity.getRegion(),
                entity.getUpdatedAt());
    }
}
