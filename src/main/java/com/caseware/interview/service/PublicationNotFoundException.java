package com.caseware.interview.service;

public class PublicationNotFoundException extends RuntimeException {

    public PublicationNotFoundException(String publicationId) {
        super("Publication not found: " + publicationId);
    }
}
