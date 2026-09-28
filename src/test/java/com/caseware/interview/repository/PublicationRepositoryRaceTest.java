package com.caseware.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.caseware.interview.domain.PublicationStatus;
import com.caseware.interview.domain.TemplatePublication;
import com.caseware.interview.repository.PublicationRepository.RegistrationResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

class PublicationRepositoryRaceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Test
    void treatsAConcurrentEquivalentInsertAsDuplicate() {
        TemplatePublication event = event();
        DuplicateKeyException collision = new DuplicateKeyException("concurrent insert");
        PublicationRepository repository = repositoryWhoseInsertThrows(collision);
        doReturn(Optional.empty(), Optional.of(event))
                .when(repository).findById(event.publicationId());

        assertThat(repository.register(event)).isEqualTo(RegistrationResult.DUPLICATE);
    }

    @Test
    void preservesTheDatabaseErrorWhenTheConcurrentRecordCannotBeRead() {
        TemplatePublication event = event();
        DuplicateKeyException collision = new DuplicateKeyException("concurrent insert");
        PublicationRepository repository = repositoryWhoseInsertThrows(collision);
        doReturn(Optional.empty(), Optional.empty())
                .when(repository).findById(event.publicationId());

        assertThatThrownBy(() -> repository.register(event)).isSameAs(collision);
    }

    private static PublicationRepository repositoryWhoseInsertThrows(DuplicateKeyException collision) {
        JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any(Object.class))).thenReturn(statement);
        when(statement.update()).thenThrow(collision);
        return spy(new PublicationRepository(jdbc, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static TemplatePublication event() {
        return new TemplatePublication(
                "publication-race", "template-a", "v4", "CA", NOW,
                PublicationStatus.PENDING, null, 0);
    }
}
