package com.caseware.interview.api;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TemplatePublicationRequest(
        @NotBlank @Size(max = 100) String publicationId,
        @NotBlank @Size(max = 100) String templateId,
        @NotBlank @Size(max = 100) String targetVersion,
        @NotBlank @Size(max = 40) String market,
        @NotNull Instant publishedAt) {
}
