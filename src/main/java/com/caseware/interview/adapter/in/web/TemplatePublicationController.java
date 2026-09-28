package com.caseware.interview.adapter.in.web;

import java.net.URI;

import com.caseware.interview.adapter.in.web.data.request.TemplatePublicationRequest;
import com.caseware.interview.adapter.in.web.data.response.PublicationStatusResponse;
import com.caseware.interview.application.port.in.command.TemplatePublicationCommand;
import com.caseware.interview.application.port.in.TemplatePublicationUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/template-publications")
@RequiredArgsConstructor
public class TemplatePublicationController {

    private final TemplatePublicationUseCase useCase;

    @PostMapping
    public ResponseEntity<Void> publish(@Valid @RequestBody TemplatePublicationRequest request) {
        var result = useCase.register(new TemplatePublicationCommand(
                request.publicationId(),
                request.templateId(),
                request.targetVersion(),
                request.market(),
                request.publishedAt()));
        URI location = URI.create("/api/v1/template-publications/" + request.publicationId());
        return ResponseEntity.accepted()
                .location(location)
                .header("X-Idempotency-Result", result.name())
                .build();
    }

    @GetMapping("/{publicationId}")
    public PublicationStatusResponse status(@PathVariable String publicationId) {
        return PublicationStatusResponse.from(useCase.status(publicationId));
    }
}
