package com.lehnade.mbia.genealogy.application.resolvekinship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.KinshipResolver;
import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.Kinship;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.KinshipGraph;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.KinshipPathStep;
import com.lehnade.mbia.genealogy.domain.KinshipRelation;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * PR-21: {@code getKinship} checks the caller's membership first, hides other Families' Persons,
 * and resolves over ACTIVE Persons only (OQ-013).
 */
class ResolveKinshipUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final KinshipGraphQuery graphQuery = mock(KinshipGraphQuery.class);
    private ResolveKinshipUseCase useCase;

    private final PersonId tony = PersonId.newId();
    private final PersonId marie = PersonId.newId();

    @BeforeEach
    void setUp() {
        CurrentUserAccessor currentUser = () -> new CurrentUser(CALLER, "caller@example.com", null, "fr");
        useCase = new ResolveKinshipUseCase(currentUser, familyAccess, persons, new KinshipResolver(graphQuery));
        given(tony, Gender.MALE, PersonStatus.ACTIVE);
        given(marie, Gender.FEMALE, PersonStatus.ACTIVE);
        when(graphQuery.activeGraph(FAMILY)).thenReturn(
                KinshipGraph.of(List.of(new KinshipGraph.Edge(RelationshipType.PARENT_OF, marie, tony))));
    }

    @Test
    void resolvesWhatToIsToFrom() {
        Kinship kinship = useCase.resolve(FAMILY, tony.value(), marie.value());

        assertThat(kinship.code()).isEqualTo(KinshipCode.MOTHER);
        assertThat(kinship.path()).containsExactly(new KinshipPathStep(tony, marie, KinshipRelation.PARENT));
        assertThat(useCase.resolve(FAMILY, marie.value(), tony.value()).code()).isEqualTo(KinshipCode.SON);
    }

    @Test
    void membershipIsCheckedBeforeAnyPersonIsLoaded() {
        when(familyAccess.requireActiveMember(FAMILY))
                .thenThrow(new DomainException(ErrorCode.FAMILY_NOT_FOUND, "Family not found."));

        assertRefused(tony, marie, ErrorCode.FAMILY_NOT_FOUND);
        verifyNoInteractions(persons, graphQuery);
    }

    @Test
    void aPersonUnknownOrOfAnotherFamilyIsNotFound() {
        PersonId stranger = PersonId.newId();
        when(persons.findInFamily(FAMILY, stranger)).thenReturn(Optional.empty());

        assertRefused(tony, stranger, ErrorCode.PERSON_NOT_FOUND);
        assertRefused(stranger, tony, ErrorCode.PERSON_NOT_FOUND);
        assertRefused(stranger, stranger, ErrorCode.PERSON_NOT_FOUND);
    }

    @Test
    void aPersonIsItself() {
        assertThat(useCase.resolve(FAMILY, marie.value(), marie.value())).isEqualTo(Kinship.self());
        verifyNoInteractions(graphQuery);
    }

    @ParameterizedTest
    @EnumSource(value = PersonStatus.class, names = {"ARCHIVED", "MERGED"})
    void aPersonThatIsNotActiveHasNoKnownKinship(PersonStatus status) {
        given(marie, Gender.FEMALE, status);

        assertThat(useCase.resolve(FAMILY, tony.value(), marie.value())).isEqualTo(Kinship.noneKnown());
        assertThat(useCase.resolve(FAMILY, marie.value(), tony.value())).isEqualTo(Kinship.noneKnown());
        verifyNoInteractions(graphQuery);
    }

    private void given(PersonId id, Gender gender, PersonStatus status) {
        Person person = Person.restore(id, FAMILY, new PersonDetails("Someone", null, null, null, gender, null, false,
                null, null), null, status, CALLER, CALLER, NOW, NOW,
                status == PersonStatus.ARCHIVED ? NOW : null,
                status == PersonStatus.MERGED ? PersonId.newId() : null, 0);
        when(persons.findInFamily(FAMILY, id)).thenReturn(Optional.of(person));
    }

    private void assertRefused(PersonId from, PersonId to, ErrorCode code) {
        assertThatThrownBy(() -> useCase.resolve(FAMILY, from.value(), to.value()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
