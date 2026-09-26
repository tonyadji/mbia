package com.lehnade.mbia.genealogy.application.archiverelationship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * PR-24: removing a relationship (person-relationships-collaboration.md §8, data-model.md §20):
 * ADMIN or CONTRIBUTOR, from the current version, archived and audited; already removed → nothing
 * written (OQ-020).
 */
class ArchiveRelationshipUseCaseTest {

    private static final Instant CREATED = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final RelationshipRepository relationships = mock(RelationshipRepository.class);
    private final AuditLog auditLog = mock(AuditLog.class);
    private ArchiveRelationshipUseCase useCase;

    private final FamilyRelationship active = FamilyRelationship.restore(RelationshipId.newId(), FAMILY,
            RelationshipType.PARENT_OF, PersonId.newId(), PersonId.newId(), RelationshipStatus.ACTIVE, CALLER, CALLER,
            CREATED, CREATED, null, 4);

    @BeforeEach
    void setUp() {
        CurrentUserAccessor currentUser = () -> new CurrentUser(CALLER, "caller@example.com", null, "fr");
        useCase = new ArchiveRelationshipUseCase(currentUser, familyAccess, relationships, auditLog,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(relationships.findInFamily(FAMILY, active.id())).thenReturn(Optional.of(active));
        when(relationships.update(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void archivesAndAuditsTheRelationship() {
        useCase.archive(command(active.id(), 4));

        ArgumentCaptor<FamilyRelationship> stored = ArgumentCaptor.forClass(FamilyRelationship.class);
        verify(relationships).update(stored.capture());
        assertThat(stored.getValue().status()).isEqualTo(RelationshipStatus.ARCHIVED);
        assertThat(stored.getValue().archivedAt()).isEqualTo(NOW);
        assertThat(stored.getValue().updatedBy()).isEqualTo(CALLER);
        assertThat(stored.getValue().version()).as("the version it was built from").isEqualTo(4);
        verify(auditLog).append(new AuditEntry(FAMILY, CALLER, "RELATIONSHIP_ARCHIVED", "RELATIONSHIP",
                active.id().value(), Map.of("status", "ACTIVE"), Map.of("status", "ARCHIVED"), NOW));
    }

    @Test
    void adminOrContributorIsRequiredBeforeTheRelationshipIsLoaded() {
        when(familyAccess.requireRole(FAMILY, FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR))
                .thenThrow(new DomainException(ErrorCode.PERMISSION_DENIED, "denied"));

        assertRefused(() -> useCase.archive(command(active.id(), 4)), ErrorCode.PERMISSION_DENIED);
        verifyNoInteractions(relationships);
    }

    @Test
    void anUnknownRelationshipOrOneOfAnotherFamilyIsNotFound() {
        RelationshipId unknown = RelationshipId.newId();
        when(relationships.findInFamily(FAMILY, unknown)).thenReturn(Optional.empty());

        assertRefused(() -> useCase.archive(command(unknown, 0)), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void aStaleVersionIsAConflict() {
        assertRefused(() -> useCase.archive(command(active.id(), 3)), ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void anAlreadyRemovedRelationshipIsLeftAsItIs() {
        FamilyRelationship archived = active.archive(CALLER, CREATED);
        when(relationships.findInFamily(FAMILY, active.id())).thenReturn(Optional.of(archived));

        useCase.archive(command(active.id(), 4));

        verify(relationships, never()).update(any());
        verifyNoInteractions(auditLog);
    }

    private void assertRefused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
        verify(relationships, never()).update(any());
        verifyNoInteractions(auditLog);
    }

    private static ArchiveRelationshipCommand command(RelationshipId id, long version) {
        return new ArchiveRelationshipCommand(FAMILY, id.value(), version);
    }
}
