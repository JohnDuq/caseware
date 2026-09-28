package com.caseware.interview.api;

import java.time.Clock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.caseware.interview.domain.EngagementFile;
import com.caseware.interview.repository.EngagementFileRepository;
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
public class EngagementFileController {

    private final EngagementFileRepository files;
    private final Clock clock;

    public EngagementFileController(EngagementFileRepository files, Clock clock) {
        this.files = files;
        this.clock = clock;
    }

    @PutMapping("/{fileId}")
    public ResponseEntity<Void> upsert(
            @PathVariable @Size(max = 100) String fileId,
            @Valid @RequestBody EngagementFileRequest request) {
        files.save(new EngagementFile(
                fileId,
                request.firmId(),
                request.templateId(),
                request.templateVersion(),
                request.market(),
                request.region(),
                clock.instant()));
        return ResponseEntity.noContent().build();
    }
}
