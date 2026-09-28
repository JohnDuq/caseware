package com.caseware.interview.service;

import java.time.Clock;

import com.caseware.interview.api.PublicationStatusResponse;
import com.caseware.interview.api.TemplatePublicationRequest;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.PublicationConflictException;
import com.caseware.interview.repository.PublicationRepository;
import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import com.caseware.interview.repository.entity.TemplatePublicationEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PublicationService {

    private final PublicationRepository publications;
    private final FanOutTaskRepository tasks;
    private final Clock clock;

    public PublicationService(
            PublicationRepository publications,
            FanOutTaskRepository tasks,
            Clock clock) {
        this.publications = publications;
        this.tasks = tasks;
        this.clock = clock;
    }

    public RegistrationResult register(TemplatePublicationRequest request) {
        var existing = publications.findById(request.publicationId());
        if (existing.isPresent()) {
            assertSameEvent(existing.get(), request);
            return RegistrationResult.DUPLICATE;
        }

        TemplatePublicationEntity publication = new TemplatePublicationEntity(
                request.publicationId(),
                request.templateId(),
                request.targetVersion(),
                request.market(),
                request.publishedAt(),
                clock.instant());
        try {
            publications.saveAndFlush(publication);
            return RegistrationResult.CREATED;
        } catch (DataIntegrityViolationException race) {
            TemplatePublicationEntity concurrent = publications.findById(request.publicationId())
                    .orElseThrow(() -> race);
            assertSameEvent(concurrent, request);
            return RegistrationResult.DUPLICATE;
        }
    }

    public PublicationStatusResponse status(String publicationId) {
        TemplatePublicationEntity publication = publications.findById(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException(publicationId));
        return new PublicationStatusResponse(
                publication.getPublicationId(),
                publication.getTemplateId(),
                publication.getTargetVersion(),
                publication.getMarket(),
                publication.getPublishedAt(),
                publication.getStatus(),
                publication.getTotalTasks(),
                tasks.countsForPublication(publicationId));
    }

    private static void assertSameEvent(
            TemplatePublicationEntity existing,
            TemplatePublicationRequest incoming) {
        boolean same = existing.getTemplateId().equals(incoming.templateId())
                && existing.getTargetVersion().equals(incoming.targetVersion())
                && existing.getMarket().equals(incoming.market())
                && existing.getPublishedAt().equals(incoming.publishedAt());
        if (!same) {
            throw new PublicationConflictException(incoming.publicationId());
        }
    }
}
