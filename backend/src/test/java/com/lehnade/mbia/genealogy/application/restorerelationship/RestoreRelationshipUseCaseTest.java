package com.lehnade.mbia.genealogy.application.restorerelationship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.domain.FamilyGraphLock;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.genealogy.domain.RelationshipWarningCode;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * PR-24: restoring a removed relationship (person-relationships-collaboration.md §7, §8; mvp.md
 * §13): ADMIN only, from the current version; every current block is re-run under the Family graph
 * lock; date warnings are returned without blocking (OQ-021); already ACTIVE → unchanged (OQ-020).
 */
class RestoreRelationshipUseCaseTest {

    private static final Instant CREATED = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant ARCHIVED = Instant.parse("2026-09-21T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final RelationshipRepository relationships = mock(RelationshipRepository.class);
    private final ParentalCycleCheck cycleCheck = mock(ParentalCycleCheck.class);
    private final FamilyGraphLock graphLock = mock(FamilyGraphLock.class);
    private final AuditLog auditLog = mock(AuditLog.class);
    private RestoreRelationshipUseCase useCase;

    private final PersonId paul = PersonId.newId();
    private final PersonId marie = PersonId.newId();
    private final FamilyRelationship removed = archived(RelationshipType.PARENT_OF, paul, marie);

    @BeforeEach
    void setUp() {
        CurrentUserAccessor currentUser = () -> new CurrentUser(CALLER, "caller@example.com", null, "fr");
        useCase = new RestoreRelationshipUseCase(currentUser, familyAccess, persons, relationships, cycleCheck,
                graphLock, auditLog, Clock.fixed(NOW, ZoneOffset.UTC));
        given(paul, PersonStatus.ACTIVE, PartialDate.UNKNOWN);
        given(marie, PersonStatus.ACTIVE, PartialDate.UNKNOWN);
        when(relationships.findInFamily(FAMILY, removed.id())).thenReturn(Optional.of(removed));
        when(relationships.update(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void restoresAndAuditsTheRelationship() {
        RestoredRelationship restored = useCase.restore(command(removed, 2));

        ArgumentCaptor<FamilyRelationship> stored = ArgumentCaptor.forClass(FamilyRelationship.class);
        verify(relationships).update(stored.capture());
        assertThat(stored.getValue().status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(stored.getValue().archivedAt()).isNull();
        assertThat(stored.getValue().updatedBy()).isEqualTo(CALLER);
        assertThat(stored.getValue().version()).isEqualTo(2);
        assertThat(restored.relationship()).isEqualTo(stored.getValue());
        assertThat(restored.warnings()).isEmpty();
        verify(auditLog).append(new AuditEntry(FAMILY, CALLER, "RELATIONSHIP_RESTORED", "RELATIONSHIP",
                removed.id().value(), Map.of("status", "ARCHIVED"), Map.of("status", "ACTIVE"), NOW));
    }

    @Test
    void onlyAnAdminRestoresAndTheRoleIsCheckedFirst() {
        when(familyAccess.requireRole(FAMILY, FamilyRole.ADMIN))
                .thenThrow(new DomainException(ErrorCode.PERMISSION_DENIED, "denied"));

        assertRefused(() -> useCase.restore(command(removed, 2)), ErrorCode.PERMISSION_DENIED);
        verifyNoInteractions(relationships, persons, cycleCheck, graphLock);
    }

    @Test
    void anUnknownRelationshipOrOneOfAnotherFamilyIsNotFound() {
        RelationshipId unknown = RelationshipId.newId();
        when(relationships.findInFamily(FAMILY, unknown)).thenReturn(Optional.empty());

        assertRefused(() -> useCase.restore(new RestoreRelationshipCommand(FAMILY, unknown.value(), 0)),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void aStaleVersionIsAConflict() {
        assertRefused(() -> useCase.restore(command(removed, 1)), ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void anActiveRelationshipIsReturnedUnchanged() {
        FamilyRelationship active = FamilyRelationship.restore(RelationshipId.newId(), FAMILY,
                RelationshipType.PARENT_OF, paul, marie, RelationshipStatus.ACTIVE, CALLER, CALLER, CREATED, CREATED,
                null, 5);
        when(relationships.findInFamily(FAMILY, active.id())).thenReturn(Optional.of(active));

        RestoredRelationship restored = useCase.restore(command(active, 5));

        assertThat(restored.relationship()).isSameAs(active);
        assertThat(restored.warnings()).isEmpty();
        verify(relationships, never()).update(any());
        verifyNoInteractions(auditLog, graphLock);
    }

    @ParameterizedTest
    @EnumSource(value = PersonStatus.class, names = {"ARCHIVED", "MERGED"})
    void aLinkToAPersonNoLongerActiveIsRefused(PersonStatus status) {
        given(paul, status, PartialDate.UNKNOWN);
        assertRefused(() -> useCase.restore(command(removed, 2)), ErrorCode.PERSON_NOT_ACTIVE);

        given(paul, PersonStatus.ACTIVE, PartialDate.UNKNOWN);
        given(marie, status, PartialDate.UNKNOWN);
        assertRefused(() -> useCase.restore(command(removed, 2)), ErrorCode.PERSON_NOT_ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(RelationshipType.class)
    void anIdenticalActiveRelationshipIsADuplicate(RelationshipType type) {
        FamilyRelationship link = archived(type, paul, marie);
        when(relationships.findInFamily(FAMILY, link.id())).thenReturn(Optional.of(link));
        when(relationships.existsActive(FAMILY, type, link.source(), link.target())).thenReturn(true);

        assertRefused(() -> useCase.restore(command(link, 2)), ErrorCode.RELATIONSHIP_ALREADY_EXISTS);
    }

    @Test
    void aParentLinkThatWouldNowCloseACycleIsRefused() {
        when(cycleCheck.wouldCreateCycle(FAMILY, paul, marie)).thenReturn(true);

        assertRefused(() -> useCase.restore(command(removed, 2)), ErrorCode.RELATIONSHIP_CREATES_CYCLE);
    }

    @Test
    void thePartnerLinkNeedsNoCycleCheck() {
        FamilyRelationship partners = archived(RelationshipType.PARTNER_OF, paul, marie);
        when(relationships.findInFamily(FAMILY, partners.id())).thenReturn(Optional.of(partners));

        useCase.restore(command(partners, 2));

        verifyNoInteractions(cycleCheck);
    }

    @Test
    void theGraphChecksRunUnderTheFamilyGraphLock() {
        useCase.restore(command(removed, 2));

        InOrder order = inOrder(graphLock, relationships, cycleCheck);
        order.verify(graphLock).lock(FAMILY);
        order.verify(relationships).existsActive(FAMILY, RelationshipType.PARENT_OF, paul, marie);
        order.verify(cycleCheck).wouldCreateCycle(FAMILY, paul, marie);
        order.verify(relationships).update(any());
    }

    @Test
    void dateWarningsOfTheCurrentBirthDataDoNotBlockAndAreReturned() {
        given(paul, PersonStatus.ACTIVE, PartialDate.yearOnly(2000));
        given(marie, PersonStatus.ACTIVE, PartialDate.yearOnly(1990));

        RestoredRelationship restored = useCase.restore(command(removed, 2));

        assertThat(restored.relationship().status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(restored.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo(RelationshipWarningCode.PARENT_BORN_AFTER_CHILD);
            assertThat(warning.parentBirthYear()).isEqualTo(2000);
            assertThat(warning.childBirthYear()).isEqualTo(1990);
        });
        verify(relationships).update(any());
    }

    private void assertRefused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
        verify(relationships, never()).update(any());
        verifyNoInteractions(auditLog);
    }

    private void given(PersonId id, PersonStatus status, PartialDate birth) {
        Person person = Person.restore(id, FAMILY, new PersonDetails("Someone", null, null, null, null, birth, false,
                null, null), null, status, CALLER, CALLER, CREATED, CREATED,
                status == PersonStatus.ARCHIVED ? CREATED : null,
                status == PersonStatus.MERGED ? PersonId.newId() : null, 0);
        when(persons.findInFamily(FAMILY, id)).thenReturn(Optional.of(person));
    }

    private static FamilyRelationship archived(RelationshipType type, PersonId source, PersonId target) {
        return FamilyRelationship.restore(RelationshipId.newId(), FAMILY, type, source, target,
                RelationshipStatus.ARCHIVED, CALLER, CALLER, CREATED, ARCHIVED, ARCHIVED, 2);
    }

    private static RestoreRelationshipCommand command(FamilyRelationship relationship, long version) {
        return new RestoreRelationshipCommand(FAMILY, relationship.id().value(), version);
    }
}
