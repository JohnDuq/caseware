package com.caseware.interview.service;

import com.caseware.interview.api.PublicationStatusResponse;
import com.caseware.interview.api.TemplatePublicationRequest;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TemplatePublication;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.PublicationRepository;
import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import org.springframework.stereotype.Service;

@Service
public class PublicationService {

    private final PublicationRepository publications;
    private final FanOutTaskRepository tasks;

    public PublicationService(PublicationRepository publications, FanOutTaskRepository tasks) {
        this.publications = publications;
        this.tasks = tasks;
    }

    public RegistrationResult register(TemplatePublicationRequest request) {
        return publications.register(new TemplatePublication(
                request.publicationId(),
                request.templateId(),
                request.targetVersion(),
                request.market(),
                request.publishedAt(),
                PublicationStatus.PENDING,
                null,
                0));
    }

    public PublicationStatusResponse status(String publicationId) {
        TemplatePublication publication = publications.findById(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException(publicationId));
        return PublicationStatusResponse.from(
                publication,
                tasks.countsForPublication(publicationId));
    }
}
