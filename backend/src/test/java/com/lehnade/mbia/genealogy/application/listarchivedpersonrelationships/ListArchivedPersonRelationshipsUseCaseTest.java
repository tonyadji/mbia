package com.lehnade.mbia.genealogy.application.listarchivedpersonrelationships;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.domain.ArchivedRelationship;
import com.lehnade.mbia.genealogy.domain.ArchivedRelationshipsQuery;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * PR-24: the removed relationships of a Person (genealogy.md §11bis): ADMIN only, for a Person of
 * the Family whatever its status.
 */
class ListArchivedPersonRelationshipsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final ArchivedRelationshipsQuery query = mock(ArchivedRelationshipsQuery.class);
    private final ListArchivedPersonRelationshipsUseCase useCase =
            new ListArchivedPersonRelationshipsUseCase(familyAccess, persons, query);

    @ParameterizedTest
    @EnumSource(PersonStatus.class)
    void listsTheRemovedLinksOfAPersonOfTheFamily(PersonStatus status) {
        Person marie = person(status);
        Person paul = person(PersonStatus.ACTIVE);
        ArchivedRelationship removed = new ArchivedRelationship(FamilyRelationship.restore(RelationshipId.newId(),
                FAMILY, RelationshipType.PARENT_OF, paul.id(), marie.id(), RelationshipStatus.ARCHIVED, USER, USER,
                NOW, NOW, NOW, 1), paul);
        when(query.of(FAMILY, marie.id())).thenReturn(List.of(removed));

        assertThat(useCase.list(FAMILY, marie.id().value())).containsExactly(removed);
    }

    @Test
    void onlyAnAdminListsAndTheRoleIsCheckedFirst() {
        when(familyAccess.requireRole(FAMILY, FamilyRole.ADMIN))
                .thenThrow(new DomainException(ErrorCode.PERMISSION_DENIED, "denied"));

        assertThatThrownBy(() -> useCase.list(FAMILY, UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PERMISSION_DENIED));
        verifyNoInteractions(persons, query);
    }

    @Test
    void aPersonUnknownOrOfAnotherFamilyIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(persons.findInFamily(FAMILY, new PersonId(unknown))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.list(FAMILY, unknown))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PERSON_NOT_FOUND));
        verifyNoInteractions(query);
    }

    private Person person(PersonStatus status) {
        Person person = Person.restore(PersonId.newId(), FAMILY, new PersonDetails("Someone", null, null, null, null,
                PartialDate.UNKNOWN, false, null, null), null, status, USER, USER, NOW, NOW, 0);
        when(persons.findInFamily(FAMILY, person.id())).thenReturn(Optional.of(person));
        return person;
    }
}
