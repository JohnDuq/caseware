package com.caseware.interview.adapter.in.web.handler.exception;

import java.time.Clock;
import java.time.Instant;

import com.caseware.interview.adapter.in.web.data.response.ApiErrorResponse;
import com.caseware.interview.application.exception.PublicationConflictException;
import com.caseware.interview.application.exception.PublicationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.RequiredArgsConstructor;

@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(PublicationConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(PublicationConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PublicationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(PublicationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(clock), status.value(), message));
    }
}
