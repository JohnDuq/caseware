package com.caseware.interview.domain;

public enum DomainValidation {
    ;

    public static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must contain text and have at most " + maxLength + " characters");
        }
        return value;
    }
}
