package com.caseware.interview.application.port.in;

public interface TemplatePublicationUseCase {

    PublicationRegistration register(TemplatePublicationCommand command);

    PublicationStatusView status(String publicationId);
}
