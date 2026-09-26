package com.lehnade.mbia.genealogy.application.createrelationship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.audit.AuditEntry;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.genealogy.domain.FamilyGraphLock;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.genealogy.domain.RelationshipWarningCode;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
 * PR-20: every hard block of person-relationships-collaboration.md §7 stops the use case before
 * anything is written or audited; date warnings need confirmation (§7.1); the graph checks run
 * under the Family graph lock (genealogy.md §7).
 */
class CreateRelationshipUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final RelationshipRepository relationships = mock(RelationshipRepository.class);
    private final ParentalCycleCheck cycleCheck = mock(ParentalCycleCheck.class);
    private final FamilyGraphLock graphLock = mock(FamilyGraphLock.class);
    private final AuditLog auditLog = mock(AuditLog.class);
    private CreateRelationshipUseCase useCase;

    private final PersonId marie = PersonId.newId();
    private final PersonId paul = PersonId.newId();

    @BeforeEach
    void setUp() {
        CurrentUserAccessor currentUser = () -> new CurrentUser(CALLER, "caller@example.com", null, "fr");
        useCase = new CreateRelationshipUseCase(currentUser, familyAccess, persons, relationships, cycleCheck,
                graphLock, auditLog, Clock.fixed(NOW, ZoneOffset.UTC));
        given(marie, PersonStatus.ACTIVE, PartialDate.UNKNOWN);
        given(paul, PersonStatus.ACTIVE, PartialDate.UNKNOWN);
    }

    @Test
    void createsAndAuditsAParentLink() {
        CreatedRelationship created = useCase.create(parentOf(marie, paul, false));

        FamilyRelationship relationship = created.relationship();
        assertThat(relationship.source()).isEqualTo(marie);
        assertThat(relationship.target()).isEqualTo(paul);
        assertThat(created.warnings()).isEmpty();
        verify(relationships).insert(relationship);
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLog).append(audit.capture());
        assertThat(audit.getValue()).isEqualTo(new AuditEntry(FAMILY, CALLER, "RELATIONSHIP_CREATED",
                "RELATIONSHIP", relationship.id().value(), Map.of(), Map.of("type", "PARENT_OF",
                        "sourcePersonId", marie.value(), "targetPersonId", paul.value()), NOW));
    }

    @Test
    void theRoleIsCheckedBeforeAnyPersonIsLoaded() {
        when(familyAccess.requireRole(FAMILY, FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR))
                .thenThrow(new DomainException(ErrorCode.PERMISSION_DENIED, "denied"));

        assertRefused(() -> useCase.create(parentOf(marie, paul, false)), ErrorCode.PERMISSION_DENIED);
        verifyNoInteractions(persons, relationships, cycleCheck, graphLock);
    }

    @ParameterizedTest
    @EnumSource(RelationshipType.class)
    void selfRelationIsRefused(RelationshipType type) {
        assertRefused(() -> useCase.create(new CreateRelationshipCommand(FAMILY, type, marie.value(), marie.value(),
                true)), ErrorCode.SELF_RELATIONSHIP_NOT_ALLOWED);
        verifyNoInteractions(persons);
    }

    @Test
    void aPersonUnknownOrOfAnotherFamilyIsNotFound() {
        PersonId stranger = PersonId.newId();
        when(persons.findInFamily(FAMILY, stranger)).thenReturn(Optional.empty());

        assertRefused(() -> useCase.create(parentOf(marie, stranger, true)), ErrorCode.PERSON_NOT_FOUND);
        assertRefused(() -> useCase.create(parentOf(stranger, marie, true)), ErrorCode.PERSON_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = PersonStatus.class, names = {"ARCHIVED", "MERGED"})
    void aPersonThatIsNotActiveCannotBeLinked(PersonStatus status) {
        given(paul, status, PartialDate.UNKNOWN);

        assertRefused(() -> useCase.create(parentOf(marie, paul, true)), ErrorCode.PERSON_NOT_ACTIVE);
        assertRefused(() -> useCase.create(partners(paul, marie)), ErrorCode.PERSON_NOT_ACTIVE);
    }

    @Test
    void anExactActiveDuplicateIsRefused() {
        when(relationships.existsActive(FAMILY, RelationshipType.PARENT_OF, marie, paul)).thenReturn(true);

        assertRefused(() -> useCase.create(parentOf(marie, paul, true)), ErrorCode.RELATIONSHIP_ALREADY_EXISTS);
    }

    @Test
    void aReversedPartnerDuplicateIsRefused() {
        // The stored link is in canonical order, whichever order it was created in.
        boolean marieFirst = marie.value().toString().compareTo(paul.value().toString()) < 0;
        when(relationships.existsActive(FAMILY, RelationshipType.PARTNER_OF, marieFirst ? marie : paul,
                marieFirst ? paul : marie)).thenReturn(true);

        assertRefused(() -> useCase.create(partners(paul, marie)), ErrorCode.RELATIONSHIP_ALREADY_EXISTS);
        assertRefused(() -> useCase.create(partners(marie, paul)), ErrorCode.RELATIONSHIP_ALREADY_EXISTS);
    }

    @Test
    void aParentalCycleIsRefused() {
        when(cycleCheck.wouldCreateCycle(FAMILY, marie, paul)).thenReturn(true);

        assertRefused(() -> useCase.create(parentOf(marie, paul, true)), ErrorCode.RELATIONSHIP_CREATES_CYCLE);
    }

    @Test
    void partnersAreNeitherCycleCheckedNorWarned() {
        given(marie, PersonStatus.ACTIVE, PartialDate.yearOnly(2000));
        given(paul, PersonStatus.ACTIVE, PartialDate.yearOnly(1900));

        assertThat(useCase.create(partners(marie, paul)).warnings()).isEmpty();
        verifyNoInteractions(cycleCheck);
    }

    @Test
    void unconfirmedWarningsCreateNothingAndReturnTheWarnings() {
        given(marie, PersonStatus.ACTIVE, PartialDate.yearOnly(1995));
        given(paul, PersonStatus.ACTIVE, PartialDate.yearOnly(1990));

        assertThatThrownBy(() -> useCase.create(parentOf(marie, paul, false)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED);
                    assertThat(e.details()).isEqualTo(Map.of("warnings", List.of(Map.of(
                            "code", "PARENT_BORN_AFTER_CHILD",
                            "context", Map.of("parentBirthYear", 1995, "childBirthYear", 1990)))));
                });
        assertNothingWritten();
    }

    @Test
    void confirmedWarningsCreateTheLinkAndAreReturned() {
        given(marie, PersonStatus.ACTIVE, PartialDate.yearOnly(1980));
        given(paul, PersonStatus.ACTIVE, PartialDate.yearOnly(1990));

        CreatedRelationship created = useCase.create(parentOf(marie, paul, true));

        assertThat(created.warnings()).extracting(w -> w.code())
                .containsExactly(RelationshipWarningCode.IMPLAUSIBLE_PARENT_AGE);
        verify(relationships).insert(created.relationship());
    }

    @Test
    void theGraphIsLockedBeforeItIsChecked() {
        useCase.create(parentOf(marie, paul, false));

        InOrder order = inOrder(graphLock, persons, relationships, cycleCheck);
        order.verify(graphLock).lock(FAMILY);
        order.verify(persons).findInFamily(FAMILY, marie);
        order.verify(relationships).existsActive(any(), any(), any(), any());
        order.verify(cycleCheck).wouldCreateCycle(eq(FAMILY), eq(marie), eq(paul));
        order.verify(relationships).insert(any());
    }

    private void assertRefused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
        assertNothingWritten();
    }

    private void assertNothingWritten() {
        verify(relationships, never()).insert(any());
        verifyNoInteractions(auditLog);
    }

    private void given(PersonId id, PersonStatus status, PartialDate birth) {
        Person person = Person.restore(id, FAMILY, new PersonDetails("Someone", null, null, null, null, birth, false,
                null, null), null, status, CALLER, CALLER, NOW, NOW,
                status == PersonStatus.ARCHIVED ? NOW : null, 0);
        when(persons.findInFamily(FAMILY, id)).thenReturn(Optional.of(person));
    }

    private static CreateRelationshipCommand parentOf(PersonId parent, PersonId child, boolean confirm) {
        return new CreateRelationshipCommand(FAMILY, RelationshipType.PARENT_OF, parent.value(), child.value(),
                confirm);
    }

    private static CreateRelationshipCommand partners(PersonId first, PersonId second) {
        return new CreateRelationshipCommand(FAMILY, RelationshipType.PARTNER_OF, first.value(), second.value(),
                false);
    }
}
