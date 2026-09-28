package com.caseware.interview.adapter.in.web;

import com.caseware.interview.application.port.in.EngagementFileCommand;
import com.caseware.interview.application.port.in.EngagementFileUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/engagement-files")
@RequiredArgsConstructor
public class EngagementFileController {

    private final EngagementFileUseCase useCase;

    @PutMapping("/{fileId}")
    public ResponseEntity<Void> upsert(
            @PathVariable @Size(max = 100) String fileId,
            @Valid @RequestBody EngagementFileRequest request) {
        useCase.upsert(new EngagementFileCommand(
                fileId,
                request.firmId(),
                request.templateId(),
                request.templateVersion(),
                request.market(),
                request.region()));
        return ResponseEntity.noContent().build();
    }
}
