package com.lehnade.mbia.genealogy.application.searchpersons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.KinshipGraph;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonSearchQuery;
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

/**
 * PR-25: {@code searchPersons} checks the caller's membership first, searches the trimmed text
 * over ACTIVE Persons and resolves every result's {@code relationshipToCurrentUser} over one graph
 * (mvp.md §19; genealogy.md §11, §15). ARCHIVED Persons are listed from PR-26.
 */
class SearchPersonsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final PersonSearchQuery searchQuery = mock(PersonSearchQuery.class);
    private final KinshipGraphQuery graphQuery = mock(KinshipGraphQuery.class);
    private SearchPersonsUseCase useCase;

    private final Person tony = person(Gender.MALE, PersonStatus.ACTIVE, CALLER);
    private final Person marie = person(Gender.FEMALE, PersonStatus.ACTIVE, null);
    private final Person awa = person(Gender.FEMALE, PersonStatus.ACTIVE, null);

    @BeforeEach
    void setUp() {
        CurrentUserAccessor currentUser = () -> new CurrentUser(CALLER, "caller@example.com", null, "fr");
        useCase = new SearchPersonsUseCase(currentUser, familyAccess, persons, searchQuery, graphQuery);
        when(persons.findLinkedTo(FAMILY, CALLER)).thenReturn(Optional.of(tony));
        when(searchQuery.search(eq(FAMILY), eq(PersonStatus.ACTIVE), anyString(), anyInt(), anyInt()))
                .thenReturn(new PersonSearchQuery.Result(List.of(awa, marie, tony), 43));
        when(graphQuery.activeGraph(FAMILY)).thenReturn(KinshipGraph.of(List.of(
                new KinshipGraph.Edge(RelationshipType.PARENT_OF, marie.id(), tony.id()))));
    }

    @Test
    void theTrimmedTextIsSearchedAndThePageKeepsItsTotal() {
        PersonSearchView result = useCase.search(command(PersonStatus.ACTIVE, "  Éloïse "));

        verify(searchQuery).search(FAMILY, PersonStatus.ACTIVE, "Éloïse", 2, 20);
        assertThat(result.items()).extracting(PersonView::person).containsExactly(awa, marie, tony);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(43);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void noTextListsEveryActivePerson() {
        useCase.search(command(PersonStatus.ACTIVE, null));

        verify(searchQuery).search(FAMILY, PersonStatus.ACTIVE, "", 2, 20);
    }

    @Test
    void everyResultGetsItsKinshipFromOneGraph() {
        PersonSearchView result = useCase.search(command(PersonStatus.ACTIVE, "a"));

        assertThat(result.items()).extracting(PersonView::relationshipToCurrentUser).containsExactly(
                Optional.of("NONE_KNOWN"), Optional.of("MOTHER"), Optional.of("SELF"));
        verify(graphQuery, times(1)).activeGraph(FAMILY);
    }

    @Test
    void withoutLinkedPersonNoResultHasAKinship() {
        when(persons.findLinkedTo(FAMILY, CALLER)).thenReturn(Optional.empty());

        PersonSearchView result = useCase.search(command(PersonStatus.ACTIVE, "a"));

        assertThat(result.items()).extracting(PersonView::relationshipToCurrentUser).containsOnly(Optional.empty());
        verifyNoInteractions(graphQuery);
    }

    @Test
    void anArchivedLinkedPersonHasNoKnownKinship() {
        when(persons.findLinkedTo(FAMILY, CALLER))
                .thenReturn(Optional.of(person(Gender.MALE, PersonStatus.ARCHIVED, CALLER)));

        PersonSearchView result = useCase.search(command(PersonStatus.ACTIVE, "a"));

        assertThat(result.items()).extracting(PersonView::relationshipToCurrentUser)
                .containsOnly(Optional.of("NONE_KNOWN"));
        verifyNoInteractions(graphQuery);
    }

    @Test
    void theMembershipIsCheckedFirst() {
        when(familyAccess.requireActiveMember(FAMILY))
                .thenThrow(new DomainException(ErrorCode.FAMILY_NOT_FOUND, "Family not found."));

        assertThatThrownBy(() -> useCase.search(command(PersonStatus.ACTIVE, "a")))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.FAMILY_NOT_FOUND));
        verifyNoInteractions(persons, searchQuery, graphQuery);
    }

    @Test
    void archivedPersonsAreNotListedYet() {
        assertThatThrownBy(() -> useCase.search(command(PersonStatus.ARCHIVED, null)))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(familyAccess).requireActiveMember(FAMILY);
        verifyNoInteractions(searchQuery);
    }

    private static SearchPersonsCommand command(PersonStatus status, String search) {
        return new SearchPersonsCommand(FAMILY, status, search, 2, 20);
    }

    private static Person person(Gender gender, PersonStatus status, UUID linkedUserId) {
        return Person.restore(PersonId.newId(), FAMILY, new PersonDetails("Someone", null, null, null, gender, null,
                false, null, null), linkedUserId, status, CALLER, CALLER, NOW, NOW, 0);
    }
}
