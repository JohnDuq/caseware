package com.caseware.interview.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.caseware.interview.adapter.out.persistence.entity.TemplatePublicationJpaEntity;
import com.caseware.interview.adapter.out.persistence.repository.PublicationJpaRepository;
import com.caseware.interview.application.port.out.PublicationStorePort;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TemplatePublication;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaPublicationAdapter implements PublicationStorePort {

    private final PublicationJpaRepository repository;

    @Override
    public CreatePublicationResult create(TemplatePublication publication, Instant now) {
        Optional<TemplatePublicationJpaEntity> existing = repository.findById(publication.publicationId());
        if (existing.isPresent()) {
            return new CreatePublicationResult(false, toDomain(existing.get()));
        }
        try {
            TemplatePublicationJpaEntity saved = repository.saveAndFlush(toEntity(publication, now));
            return new CreatePublicationResult(true, toDomain(saved));
        } catch (DataIntegrityViolationException race) {
            TemplatePublication concurrent = repository.findById(publication.publicationId())
                    .map(JpaPublicationAdapter::toDomain)
                    .orElseThrow(() -> race);
            return new CreatePublicationResult(false, concurrent);
        }
    }

    @Override
    public Optional<TemplatePublication> findById(String publicationId) {
        return repository.findById(publicationId).map(JpaPublicationAdapter::toDomain);
    }

    @Override
    public Optional<TemplatePublication> findNextClaimable(Instant now) {
        return repository.findClaimable(
                        List.of(PublicationStatus.PENDING, PublicationStatus.FANNING_OUT),
                        now,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(JpaPublicationAdapter::toDomain);
    }

    @Override
    public void claim(String publicationId, String leaseToken, Instant leaseExpiry, Instant now) {
        repository.findById(publicationId)
                .orElseThrow(() -> new IllegalStateException("Publication disappeared: " + publicationId))
                .claim(leaseToken, leaseExpiry, now);
    }

    @Override
    public void completePage(
            String publicationId,
            String cursor,
            int insertedTasks,
            boolean complete,
            Instant now) {
        repository.findById(publicationId)
                .orElseThrow(() -> new IllegalStateException("Publication disappeared: " + publicationId))
                .completePage(cursor, insertedTasks, complete, now);
    }

    private static TemplatePublicationJpaEntity toEntity(
            TemplatePublication publication,
            Instant now) {
        return TemplatePublicationJpaEntity.builder()
                .publicationId(publication.publicationId())
                .templateId(publication.templateId())
                .targetVersion(publication.targetVersion())
                .market(publication.market())
                .publishedAt(publication.publishedAt())
                .status(publication.status())
                .scanCursor(publication.scanCursor())
                .totalTasks(publication.totalTasks())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static TemplatePublication toDomain(TemplatePublicationJpaEntity entity) {
        return new TemplatePublication(
                entity.getPublicationId(),
                entity.getTemplateId(),
                entity.getTargetVersion(),
                entity.getMarket(),
                entity.getPublishedAt(),
                entity.getStatus(),
                entity.getScanCursor(),
                entity.getTotalTasks());
    }
}
