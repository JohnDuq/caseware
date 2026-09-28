package com.caseware.interview.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;
import com.caseware.interview.repository.EngagementFileRepository;
import com.caseware.interview.repository.entity.EngagementFileEntity;
import com.caseware.interview.repository.PublicationConflictException;
import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import com.caseware.interview.service.PublicationNotFoundException;
import com.caseware.interview.service.PublicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ApiLayerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Mock
    private PublicationService publicationService;

    @Mock
    private EngagementFileRepository fileRepository;

    @Test
    void publicationControllerAcceptsNewAndDuplicateEvents() {
        TemplatePublicationController controller = new TemplatePublicationController(publicationService);
        TemplatePublicationRequest request = validPublicationRequest();
        when(publicationService.register(request))
                .thenReturn(RegistrationResult.CREATED, RegistrationResult.DUPLICATE);

        var created = controller.publish(request);
        var duplicate = controller.publish(request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getHeaders().getLocation().toString())
                .isEqualTo("/api/v1/template-publications/publication-1");
        assertThat(created.getHeaders().getFirst("X-Idempotency-Result")).isEqualTo("CREATED");
        assertThat(duplicate.getHeaders().getFirst("X-Idempotency-Result")).isEqualTo("DUPLICATE");
    }

    @Test
    void publicationControllerReturnsServiceStatus() {
        TemplatePublicationController controller = new TemplatePublicationController(publicationService);
        PublicationStatusResponse expected = new PublicationStatusResponse(
                "publication-1", "template-a", "v4", "CA", NOW,
                PublicationStatus.FAN_OUT_COMPLETE, 2,
                new TaskCounts(0, 0, 0, 2, 0));
        when(publicationService.status("publication-1")).thenReturn(expected);

        assertThat(controller.status("publication-1")).isSameAs(expected);
    }

    @Test
    void engagementFileControllerMapsTheRequestToCatalogMetadata() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        EngagementFileController controller = new EngagementFileController(fileRepository, clock);
        EngagementFileRequest request = new EngagementFileRequest(
                "firm-1", "template-a", "v3", "CA", "CANADA");

        assertThat(controller.upsert("file-1", request).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ArgumentCaptor<EngagementFileEntity> captor = ArgumentCaptor.forClass(EngagementFileEntity.class);
        verify(fileRepository).save(captor.capture());
        EngagementFileEntity saved = captor.getValue();
        assertThat(saved.getFileId()).isEqualTo("file-1");
        assertThat(saved.getFirmId()).isEqualTo("firm-1");
        assertThat(saved.getTemplateId()).isEqualTo("template-a");
        assertThat(saved.getTemplateVersion()).isEqualTo("v3");
        assertThat(saved.getMarket()).isEqualTo("CA");
        assertThat(saved.getRegion()).isEqualTo("CANADA");
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void exceptionHandlerMapsConflictAndNotFoundResponses() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var conflict = handler.conflict(new PublicationConflictException("publication-1"));
        var missing = handler.notFound(new PublicationNotFoundException("missing"));

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody())
                .containsEntry("status", 409)
                .containsEntry("error", "Publication id already exists with a different payload: publication-1")
                .containsKey("timestamp");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody())
                .containsEntry("status", 404)
                .containsEntry("error", "Publication not found: missing")
                .containsKey("timestamp");
    }

    @Test
    void requestDtosRejectBlankOversizedAndMissingFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            TemplatePublicationRequest invalidPublication = new TemplatePublicationRequest(
                    "", "x".repeat(101), "", "x".repeat(41), null);
            EngagementFileRequest invalidFile = new EngagementFileRequest(
                    "", "x".repeat(101), "", "x".repeat(41), "");

            Set<ConstraintViolation<TemplatePublicationRequest>> publicationViolations =
                    validator.validate(invalidPublication);
            Set<ConstraintViolation<EngagementFileRequest>> fileViolations =
                    validator.validate(invalidFile);

            assertThat(publicationViolations).hasSize(5);
            assertThat(fileViolations).hasSize(5);
            assertThat(validator.validate(validPublicationRequest())).isEmpty();
            assertThat(validator.validate(new EngagementFileRequest(
                    "firm-1", "template-a", "v1", "CA", "CANADA"))).isEmpty();
        }
    }

    private static TemplatePublicationRequest validPublicationRequest() {
        return new TemplatePublicationRequest(
                "publication-1", "template-a", "v4", "CA", NOW);
    }
}
