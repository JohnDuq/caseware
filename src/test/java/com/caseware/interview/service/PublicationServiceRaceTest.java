package com.caseware.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.caseware.interview.api.TemplatePublicationRequest;
import com.caseware.interview.repository.FanOutTaskRepository;
import com.caseware.interview.repository.PublicationRepository;
import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import com.caseware.interview.repository.entity.TemplatePublicationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PublicationServiceRaceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Test
    void treatsAConcurrentEquivalentInsertAsDuplicate() {
        PublicationRepository repository = mock(PublicationRepository.class);
        DataIntegrityViolationException collision = new DataIntegrityViolationException("concurrent insert");
        TemplatePublicationRequest request = request();
        when(repository.findById(request.publicationId()))
                .thenReturn(Optional.empty(), Optional.of(entity()));
        when(repository.saveAndFlush(any())).thenThrow(collision);
        PublicationService service = service(repository);

        assertThat(service.register(request)).isEqualTo(RegistrationResult.DUPLICATE);
    }

    @Test
    void preservesTheDatabaseErrorWhenTheConcurrentRecordCannotBeRead() {
        PublicationRepository repository = mock(PublicationRepository.class);
        DataIntegrityViolationException collision = new DataIntegrityViolationException("concurrent insert");
        TemplatePublicationRequest request = request();
        when(repository.findById(request.publicationId())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(collision);
        PublicationService service = service(repository);

        assertThatThrownBy(() -> service.register(request)).isSameAs(collision);
    }

    private static PublicationService service(PublicationRepository repository) {
        return new PublicationService(
                repository,
                mock(FanOutTaskRepository.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TemplatePublicationRequest request() {
        return new TemplatePublicationRequest(
                "publication-race", "template-a", "v4", "CA", NOW);
    }

    private static TemplatePublicationEntity entity() {
        return new TemplatePublicationEntity(
                "publication-race", "template-a", "v4", "CA", NOW, NOW);
    }
}
