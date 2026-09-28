package com.caseware.interview.repository;

public class PublicationConflictException extends RuntimeException {

    public PublicationConflictException(String publicationId) {
        super("Publication id already exists with a different payload: " + publicationId);
    }
}
