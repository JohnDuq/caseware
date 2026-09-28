package com.caseware.interview.api;

import java.net.URI;

import jakarta.validation.Valid;

import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import com.caseware.interview.service.PublicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/template-publications")
public class TemplatePublicationController {

    private final PublicationService service;

    public TemplatePublicationController(PublicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> publish(@Valid @RequestBody TemplatePublicationRequest request) {
        RegistrationResult result = service.register(request);
        URI location = URI.create("/api/v1/template-publications/" + request.publicationId());
        return ResponseEntity.accepted()
                .location(location)
                .header("X-Idempotency-Result", result.name())
                .build();
    }

    @GetMapping("/{publicationId}")
    public PublicationStatusResponse status(@PathVariable String publicationId) {
        return service.status(publicationId);
    }
}
