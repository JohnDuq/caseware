package com.caseware.interview.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.caseware.interview.domain.TemplatePublication;

public interface PublicationStorePort {

    CreatePublicationResult create(TemplatePublication publication, Instant now);

    Optional<TemplatePublication> findById(String publicationId);

    Optional<TemplatePublication> findNextClaimable(Instant now);

    void claim(String publicationId, String leaseToken, Instant leaseExpiry, Instant now);

    void completePage(
            String publicationId,
            String cursor,
            int insertedTasks,
            boolean complete,
            Instant now);

    record CreatePublicationResult(boolean created, TemplatePublication stored) {
    }
}
