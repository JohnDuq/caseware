package com.caseware.interview.application.service;

import java.time.Clock;

import com.caseware.interview.application.exception.PublicationConflictException;
import com.caseware.interview.application.exception.PublicationNotFoundException;
import com.caseware.interview.application.port.in.PublicationRegistration;
import com.caseware.interview.application.port.in.PublicationStatusView;
import com.caseware.interview.application.port.in.TemplatePublicationCommand;
import com.caseware.interview.application.port.in.TemplatePublicationUseCase;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.application.port.out.PublicationStorePort;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TemplatePublication;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PublicationApplicationService implements TemplatePublicationUseCase {

    private final PublicationStorePort publications;
    private final FanOutTaskStorePort tasks;
    private final Clock clock;

    @Override
    public PublicationRegistration register(TemplatePublicationCommand command) {
        TemplatePublication publication = new TemplatePublication(
                command.publicationId(),
                command.templateId(),
                command.targetVersion(),
                command.market(),
                command.publishedAt(),
                PublicationStatus.PENDING,
                null,
                0);
        var result = publications.create(publication, clock.instant());
        if (!result.created()) {
            assertSameEvent(result.stored(), command);
            return PublicationRegistration.DUPLICATE;
        }
        return PublicationRegistration.CREATED;
    }

    @Override
    public PublicationStatusView status(String publicationId) {
        TemplatePublication publication = publications.findById(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException(publicationId));
        return new PublicationStatusView(
                publication.publicationId(),
                publication.templateId(),
                publication.targetVersion(),
                publication.market(),
                publication.publishedAt(),
                publication.status(),
                publication.totalTasks(),
                tasks.countsForPublication(publicationId));
    }

    private static void assertSameEvent(
            TemplatePublication existing,
            TemplatePublicationCommand incoming) {
        boolean same = existing.templateId().equals(incoming.templateId())
                && existing.targetVersion().equals(incoming.targetVersion())
                && existing.market().equals(incoming.market())
                && existing.publishedAt().equals(incoming.publishedAt());
        if (!same) {
            throw new PublicationConflictException(incoming.publicationId());
        }
    }
}
