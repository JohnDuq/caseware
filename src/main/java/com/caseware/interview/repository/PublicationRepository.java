package com.caseware.interview.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.repository.entity.TemplatePublicationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationRepository extends JpaRepository<TemplatePublicationEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT publication
            FROM TemplatePublicationEntity publication
            WHERE publication.status IN :statuses
              AND (publication.leaseExpiresAt IS NULL OR publication.leaseExpiresAt < :now)
            ORDER BY publication.publishedAt, publication.publicationId
            """)
    List<TemplatePublicationEntity> findClaimable(
            @Param("statuses") List<PublicationStatus> statuses,
            @Param("now") Instant now,
            Pageable page);

    default Optional<TemplatePublicationEntity> findNextClaimable(Instant now) {
        return findClaimable(
                List.of(PublicationStatus.PENDING, PublicationStatus.FANNING_OUT),
                now,
                PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    enum RegistrationResult {
        CREATED,
        DUPLICATE
    }
}
