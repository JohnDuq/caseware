package com.caseware.interview.application.port.in;

import com.caseware.interview.application.port.in.command.TemplatePublicationCommand;

public interface TemplatePublicationUseCase {

    PublicationRegistration register(TemplatePublicationCommand command);

    PublicationStatusView status(String publicationId);
}
