package com.caseware.interview.application.exception;

public class DownstreamUpdateException extends RuntimeException {

    private final boolean retryable;

    public DownstreamUpdateException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public DownstreamUpdateException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
