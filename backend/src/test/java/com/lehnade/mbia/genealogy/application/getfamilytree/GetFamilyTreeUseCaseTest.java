package com.lehnade.mbia.genealogy.application.getfamilytree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.domain.FamilyTree;
import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.KinshipGraph;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.genealogy.domain.TreeQuery;
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
 * PR-22: {@code getFamilyTree} checks the caller's membership first, then chooses the focus in the
 * order of family-tree-ux.md §6 (requested ACTIVE Person, linked ACTIVE Person, most connected
 * ACTIVE Person; OQ-014), and resolves every node's {@code relationshipToCurrentUser} over one graph.
 */
class GetFamilyTreeUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    private final FamilyAccess familyAccess = mock(FamilyAccess.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final TreeQuery treeQuery = mock(TreeQuery.class);
    private final KinshipGraphQuery graphQuery = mock(KinshipGraphQuery.class);
    private GetFamilyTreeUseCase useCase;

    private final Person tony = person(Gender.MALE, PersonStatus.ACTIVE, CALLER);
    private final Person marie = person(Gender.FEMALE, PersonStatus.ACTIVE, null);
    private final PersonId mostConnected = PersonId.newId();

    @BeforeEach
    void setUp() {
        CurrentUserAccessor currentUser = () -> new CurrentUser(CALLER, "caller@example.com", null, "fr");
        useCase = new GetFamilyTreeUseCase(currentUser, familyAccess, persons, treeQuery, graphQuery);
        when(persons.findInFamily(FAMILY, tony.id())).thenReturn(Optional.of(tony));
        when(persons.findInFamily(FAMILY, marie.id())).thenReturn(Optional.of(marie));
        when(persons.findLinkedTo(FAMILY, CALLER)).thenReturn(Optional.of(tony));
        when(treeQuery.mostConnectedActivePerson(FAMILY)).thenReturn(Optional.of(mostConnected));
        when(treeQuery.neighbourhood(eq(FAMILY), any(), anyInt()))
                .thenAnswer(call -> tree(call.getArgument(1), tony, marie));
        when(graphQuery.activeGraph(FAMILY)).thenReturn(KinshipGraph.of(List.of(
                new KinshipGraph.Edge(RelationshipType.PARENT_OF, marie.id(), tony.id()))));
    }

    @Test
    void aRequestedActivePersonIsTheFocus() {
        assertThat(focusOf(useCase.get(FAMILY, marie.id().value(), null))).isEqualTo(marie.id());
        verify(treeQuery).neighbourhood(FAMILY, marie.id(), 1);
        verify(treeQuery, never()).mostConnectedActivePerson(any());
    }

    @Test
    void withoutARequestedFocusTheLinkedPersonIsTheFocus() {
        assertThat(focusOf(useCase.get(FAMILY, null, null))).isEqualTo(tony.id());
        verify(treeQuery, never()).mostConnectedActivePerson(any());
    }

    @ParameterizedTest
    @EnumSource(value = PersonStatus.class, names = {"ARCHIVED", "MERGED"})
    void aRequestedFocusThatIsNotActiveFallsBackToTheLinkedPerson(PersonStatus status) {
        Person gone = person(Gender.UNKNOWN, status, null);
        when(persons.findInFamily(FAMILY, gone.id())).thenReturn(Optional.of(gone));

        assertThat(focusOf(useCase.get(FAMILY, gone.id().value(), null))).isEqualTo(tony.id());
    }

    @Test
    void withoutLinkedPersonTheMostConnectedPersonIsTheFocus() {
        when(persons.findLinkedTo(FAMILY, CALLER)).thenReturn(Optional.empty());

        assertThat(focusOf(useCase.get(FAMILY, null, null))).isEqualTo(mostConnected);
    }

    @Test
    void aLinkedPersonThatIsNotActiveFallsBackToTheMostConnectedPerson() {
        Person archivedMe = person(Gender.MALE, PersonStatus.ARCHIVED, CALLER);
        when(persons.findLinkedTo(FAMILY, CALLER)).thenReturn(Optional.of(archivedMe));
        Person gone = person(Gender.UNKNOWN, PersonStatus.ARCHIVED, null);
        when(persons.findInFamily(FAMILY, gone.id())).thenReturn(Optional.of(gone));

        assertThat(focusOf(useCase.get(FAMILY, null, null))).isEqualTo(mostConnected);
        assertThat(focusOf(useCase.get(FAMILY, gone.id().value(), null))).isEqualTo(mostConnected);
    }

    @Test
    void aFamilyWithoutActivePersonHasAnEmptyTree() {
        when(persons.findLinkedTo(FAMILY, CALLER)).thenReturn(Optional.empty());
        when(treeQuery.mostConnectedActivePerson(FAMILY)).thenReturn(Optional.empty());

        FamilyTreeView view = useCase.get(FAMILY, null, null);

        assertThat(view.tree()).isEmpty();
        assertThat(view.relationshipToCurrentUser()).isEmpty();
        verify(treeQuery, never()).neighbourhood(any(), any(), anyInt());
    }

    @Test
    void theRequestedDepthIsPassedOn() {
        useCase.get(FAMILY, null, 2);

        verify(treeQuery).neighbourhood(FAMILY, tony.id(), 2);
    }

    @Test
    void membershipIsCheckedBeforeAnyPersonIsLoaded() {
        when(familyAccess.requireActiveMember(FAMILY))
                .thenThrow(new DomainException(ErrorCode.FAMILY_NOT_FOUND, "Family not found."));

        assertRefused(marie.id().value(), ErrorCode.FAMILY_NOT_FOUND);
        verifyNoInteractions(persons, treeQuery, graphQuery);
    }

    @Test
    void aFocusUnknownOrOfAnotherFamilyIsNotFound() {
        PersonId stranger = PersonId.newId();
        when(persons.findInFamily(FAMILY, stranger)).thenReturn(Optional.empty());

        assertRefused(stranger.value(), ErrorCode.PERSON_NOT_FOUND);
        verifyNoInteractions(treeQuery);
    }

    @Test
    void everyNodeGetsItsKinshipToTheLinkedPersonFromOneGraph() {
        FamilyTreeView view = useCase.get(FAMILY, marie.id().value(), null);

        assertThat(view.relationshipToCurrentUser())
                .containsEntry(tony.id(), KinshipCode.SELF)
                .containsEntry(marie.id(), KinshipCode.MOTHER);
        verify(graphQuery).activeGraph(FAMILY);
    }

    @Test
    void withoutLinkedPersonNodesHaveNoKinship() {
        when(persons.findLinkedTo(FAMILY, CALLER)).thenReturn(Optional.empty());

        assertThat(useCase.get(FAMILY, marie.id().value(), null).relationshipToCurrentUser()).isEmpty();
        verifyNoInteractions(graphQuery);
    }

    @Test
    void aLinkedPersonThatIsNotActiveHasNoKnownKinshipWithTheNodes() {
        when(persons.findLinkedTo(FAMILY, CALLER))
                .thenReturn(Optional.of(person(Gender.MALE, PersonStatus.ARCHIVED, CALLER)));

        assertThat(useCase.get(FAMILY, marie.id().value(), null).relationshipToCurrentUser())
                .containsEntry(tony.id(), KinshipCode.NONE_KNOWN)
                .containsEntry(marie.id(), KinshipCode.NONE_KNOWN);
        verifyNoInteractions(graphQuery);
    }

    private static Person person(Gender gender, PersonStatus status, UUID linkedUserId) {
        return Person.restore(PersonId.newId(), FAMILY, new PersonDetails("Someone", null, null, null, gender, null,
                false, null, null), linkedUserId, status, CALLER, CALLER, NOW, NOW, 0);
    }

    private static FamilyTree tree(PersonId focus, Person... nodes) {
        return new FamilyTree(focus,
                List.of(nodes).stream().map(node -> new FamilyTree.Node(node, false, false)).toList(),
                List.of(new FamilyTree.Edge(RelationshipId.newId(), RelationshipType.PARENT_OF, nodes[1].id(),
                        nodes[0].id(), 0)));
    }

    private static PersonId focusOf(FamilyTreeView view) {
        return view.tree().orElseThrow().focus();
    }

    private void assertRefused(UUID focus, ErrorCode code) {
        assertThatThrownBy(() -> useCase.get(FAMILY, focus, null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
