package com.caseware.interview.api;

import java.time.Instant;
import java.util.Map;

import com.caseware.interview.repository.PublicationConflictException;
import com.caseware.interview.service.PublicationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PublicationConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(PublicationConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PublicationNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(PublicationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", message));
    }
}
