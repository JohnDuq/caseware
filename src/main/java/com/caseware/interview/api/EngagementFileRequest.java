package com.caseware.interview.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EngagementFileRequest(
        @NotBlank @Size(max = 100) String firmId,
        @NotBlank @Size(max = 100) String templateId,
        @NotBlank @Size(max = 100) String templateVersion,
        @NotBlank @Size(max = 40) String market,
        @NotBlank @Size(max = 40) String region) {
}
