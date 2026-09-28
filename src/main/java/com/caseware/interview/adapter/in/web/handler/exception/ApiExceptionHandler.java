package com.caseware.interview.adapter.in.web.handler.exception;

import java.time.Instant;
import java.util.Map;

import com.caseware.interview.application.exception.PublicationConflictException;
import com.caseware.interview.application.exception.PublicationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PublicationConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(PublicationConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PublicationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(PublicationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", message));
    }
}
