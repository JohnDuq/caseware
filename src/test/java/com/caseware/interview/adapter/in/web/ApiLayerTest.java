package com.caseware.interview.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;

import com.caseware.interview.adapter.in.web.data.request.EngagementFileRequest;
import com.caseware.interview.adapter.in.web.data.request.TemplatePublicationRequest;
import com.caseware.interview.adapter.in.web.data.response.PublicationStatusResponse;
import com.caseware.interview.adapter.in.web.handler.exception.ApiExceptionHandler;
import com.caseware.interview.application.exception.PublicationConflictException;
import com.caseware.interview.application.exception.PublicationNotFoundException;
import com.caseware.interview.application.port.in.command.EngagementFileCommand;
import com.caseware.interview.application.port.in.EngagementFileUseCase;
import com.caseware.interview.application.port.in.PublicationRegistration;
import com.caseware.interview.application.port.in.PublicationStatusView;
import com.caseware.interview.application.port.in.command.TemplatePublicationCommand;
import com.caseware.interview.application.port.in.TemplatePublicationUseCase;
import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TaskCounts;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ApiLayerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Mock
    private TemplatePublicationUseCase publications;

    @Mock
    private EngagementFileUseCase files;

    @Test
    void publicationControllerMapsNewAndDuplicateEventsToTheInputPort() {
        TemplatePublicationController controller = new TemplatePublicationController(publications);
        TemplatePublicationRequest request = validPublicationRequest();
        TemplatePublicationCommand command = new TemplatePublicationCommand(
                "publication-1", "template-a", "v4", "CA", NOW);
        when(publications.register(command))
                .thenReturn(PublicationRegistration.CREATED, PublicationRegistration.DUPLICATE);

        var created = controller.publish(request);
        var duplicate = controller.publish(request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getHeaders().getLocation().toString())
                .isEqualTo("/api/v1/template-publications/publication-1");
        assertThat(created.getHeaders().getFirst("X-Idempotency-Result")).isEqualTo("CREATED");
        assertThat(duplicate.getHeaders().getFirst("X-Idempotency-Result")).isEqualTo("DUPLICATE");
    }

    @Test
    void publicationControllerMapsTheUseCaseStatusToItsApiResponse() {
        TemplatePublicationController controller = new TemplatePublicationController(publications);
        PublicationStatusView view = new PublicationStatusView(
                "publication-1", "template-a", "v4", "CA", NOW,
                PublicationStatus.FAN_OUT_COMPLETE, 2,
                new TaskCounts(0, 0, 0, 2, 0));
        when(publications.status("publication-1")).thenReturn(view);

        PublicationStatusResponse response = controller.status("publication-1");

        assertThat(response.publicationId()).isEqualTo("publication-1");
        assertThat(response.fanOutStatus()).isEqualTo(PublicationStatus.FAN_OUT_COMPLETE);
        assertThat(response.tasks()).isEqualTo(view.tasks());
    }

    @Test
    void engagementFileControllerMapsTheRequestToTheInputPort() {
        EngagementFileController controller = new EngagementFileController(files);
        EngagementFileRequest request = new EngagementFileRequest(
                "firm-1", "template-a", "v3", "CA", "CANADA");

        assertThat(controller.upsert("file-1", request).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(files).upsert(new EngagementFileCommand(
                "file-1", "firm-1", "template-a", "v3", "CA", "CANADA"));
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
