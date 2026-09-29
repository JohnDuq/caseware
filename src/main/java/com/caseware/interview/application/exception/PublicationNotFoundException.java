package com.caseware.interview.application.exception;

public class PublicationNotFoundException extends RuntimeException {

    public PublicationNotFoundException(String publicationId) {
        super("Publication not found: " + publicationId);
    }
}
