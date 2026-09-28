package com.caseware.interview.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;

import com.caseware.interview.adapter.out.persistence.entity.TemplatePublicationJpaEntity;
import com.caseware.interview.domain.PublicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationJpaRepository
        extends JpaRepository<TemplatePublicationJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT publication
            FROM TemplatePublicationJpaEntity publication
            WHERE publication.status IN :statuses
              AND (publication.leaseExpiresAt IS NULL OR publication.leaseExpiresAt < :now)
            ORDER BY publication.publishedAt, publication.publicationId
            """)
    List<TemplatePublicationJpaEntity> findClaimable(
            @Param("statuses") List<PublicationStatus> statuses,
            @Param("now") Instant now,
            Pageable page);
}
