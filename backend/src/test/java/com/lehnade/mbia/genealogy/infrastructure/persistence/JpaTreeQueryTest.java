package com.lehnade.mbia.genealogy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.domain.FamilyTree;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.TreeQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PR-22: the tree read model against PostgreSQL (openapi {@code getFamilyTree}, family-tree-ux.md
 * §6, genealogy.md §10): the depth-1 and depth-2 neighbourhoods over ACTIVE Persons and
 * relationships, continuation indicators, the order of OQ-015 and the focus fallback of §6.
 */
class JpaTreeQueryTest extends ApiTestSupport {

    @Autowired
    TreeQuery treeQuery;

    private UUID familyId;
    private GraphRows rows;

    @BeforeEach
    void givenAFamily() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        rows = new GraphRows(jdbc, familyId, families().userId(admin));
    }

    /**
     * Gaston → Paul; Paul & Jeanne → Marie; Marie & André (partners) → Tony, Awa; André & Clara →
     * Samuel; Tony partner of Chloé, then Inès; Tony & Chloé → Eric; Tony → Alex; Eric → Nina;
     * Rose → Chloé; Awa → Léa; Tony → Ghost (archived); Tony and Ex partners (archived relationship).
     */
    @Nested
    class Neighbourhood {

        private final Map<String, UUID> ids = new HashMap<>();

        @BeforeEach
        void givenThreeGenerations() {
            for (String name : List.of("Gaston", "Paul", "Jeanne", "Marie", "André", "Tony", "Awa", "Clara",
                    "Samuel", "Chloé", "Inès", "Eric", "Alex", "Nina", "Rose", "Léa", "Ghost", "Ex")) {
                ids.put(name, rows.person(name));
            }
            parentOf("Gaston", "Paul");
            parentOf("Paul", "Marie");
            parentOf("Jeanne", "Marie");
            rows.partners(id("Paul"), id("Jeanne"));
            parentOf("Marie", "Tony");
            parentOf("André", "Tony");
            parentOf("Marie", "Awa");
            parentOf("André", "Awa");
            rows.partners(id("Marie"), id("André"));
            parentOf("André", "Samuel");
            parentOf("Clara", "Samuel");
            rows.partners(id("Tony"), id("Chloé"));
            rows.partners(id("Tony"), id("Inès"));
            parentOf("Tony", "Eric");
            parentOf("Chloé", "Eric");
            parentOf("Tony", "Alex");
            parentOf("Eric", "Nina");
            parentOf("Rose", "Chloé");
            parentOf("Awa", "Léa");
            parentOf("Tony", "Ghost");
            rows.archivePerson(id("Ghost"));
            rows.relation("PARTNER_OF", min("Tony", "Ex"), max("Tony", "Ex"), "ARCHIVED");
        }

        @Test
        void depthOneHoldsParentsPartnersChildrenAndSiblingsOfActivePersonsOnly() {
            FamilyTree tree = tree("Tony", 1);

            assertThat(tree.focus()).isEqualTo(new PersonId(id("Tony")));
            assertThat(names(tree)).containsExactlyInAnyOrder(
                    "Tony", "Marie", "André", "Chloé", "Inès", "Eric", "Alex", "Awa", "Samuel");
        }

        @Test
        void edgesAreTheActiveRelationshipsAmongTheNodes() {
            FamilyTree tree = tree("Tony", 1);

            assertThat(tree.edges()).extracting(edge -> nameOf(edge.source()) + " " + edge.type() + " "
                    + nameOf(edge.target())).containsExactlyInAnyOrder(
                    "Marie PARENT_OF Tony", "André PARENT_OF Tony", "Marie PARENT_OF Awa", "André PARENT_OF Awa",
                    "André PARENT_OF Samuel", "Tony PARENT_OF Eric", "Chloé PARENT_OF Eric", "Tony PARENT_OF Alex",
                    partnersEdge("Marie", "André"), partnersEdge("Tony", "Chloé"), partnersEdge("Tony", "Inès"));
        }

        @Test
        void continuationIndicatorsShowParentsAndChildrenOutsideTheTree() {
            FamilyTree tree = tree("Tony", 1);

            assertThat(flagged(tree, true)).containsExactlyInAnyOrder("Marie", "Chloé", "Samuel");
            assertThat(flagged(tree, false)).containsExactlyInAnyOrder("Eric", "Awa");
        }

        @Test
        void depthTwoAddsGrandparentsAndGrandchildren() {
            FamilyTree tree = tree("Tony", 2);

            assertThat(names(tree)).containsExactlyInAnyOrder("Tony", "Marie", "André", "Chloé", "Inès", "Eric",
                    "Alex", "Awa", "Samuel", "Paul", "Jeanne", "Nina");
            assertThat(tree.edges()).extracting(edge -> nameOf(edge.source()) + " " + nameOf(edge.target()))
                    .contains("Paul Marie", "Jeanne Marie", "Eric Nina", String.join(" ", sorted("Paul", "Jeanne")));
            assertThat(flagged(tree, true)).containsExactlyInAnyOrder("Paul", "Chloé", "Samuel");
            assertThat(flagged(tree, false)).containsExactlyInAnyOrder("Awa");
        }

        @Test
        void halfSiblingsShareOneParent() {
            assertThat(names(tree("Samuel", 1))).containsExactlyInAnyOrder("Samuel", "André", "Clara", "Tony", "Awa");
        }

        @Test
        void aPersonWithoutRelationshipsIsAloneInTheTree() {
            UUID zoe = rows.person("Zoé");

            FamilyTree tree = treeQuery.neighbourhood(familyId, new PersonId(zoe), 1);

            assertThat(tree.nodes()).extracting(node -> node.person().id().value()).containsExactly(zoe);
            assertThat(tree.edges()).isEmpty();
        }

        private FamilyTree tree(String focus, int depth) {
            return treeQuery.neighbourhood(familyId, new PersonId(id(focus)), depth);
        }

        private List<String> names(FamilyTree tree) {
            return tree.nodes().stream().map(node -> nameOf(node.person().id())).toList();
        }

        /** Nodes with parents outside the tree ({@code parents}), or children outside it. */
        private List<String> flagged(FamilyTree tree, boolean parents) {
            return tree.nodes().stream()
                    .filter(node -> parents ? node.hasMoreParents() : node.hasMoreChildren())
                    .map(node -> nameOf(node.person().id()))
                    .toList();
        }

        private String partnersEdge(String a, String b) {
            List<String> pair = sorted(a, b);
            return pair.get(0) + " PARTNER_OF " + pair.get(1);
        }

        private List<String> sorted(String a, String b) {
            return id(a).toString().compareTo(id(b).toString()) < 0 ? List.of(a, b) : List.of(b, a);
        }

        private void parentOf(String parent, String child) {
            rows.parentOf(id(parent), id(child));
        }

        private UUID id(String name) {
            return ids.get(name);
        }

        private UUID min(String a, String b) {
            return id(sorted(a, b).get(0));
        }

        private UUID max(String a, String b) {
            return id(sorted(a, b).get(1));
        }

        private String nameOf(PersonId id) {
            return ids.entrySet().stream().filter(entry -> entry.getValue().equals(id.value())).findFirst()
                    .orElseThrow().getKey();
        }
    }

    @Test
    void nodesStartWithTheFocusThenFollowBirthCreationAndId() {
        UUID focus = rows.person("Focus");
        UUID bornInJune1990 = rows.person(UUID.randomUUID(), "Juin 1990", LocalDate.of(1990, 6, 1), null);
        UUID bornIn1990 = rows.person(UUID.randomUUID(), "1990", null, 1990);
        UUID unknownFirst = rows.person("Inconnu 1");
        UUID unknownSameTimeHigherId = rows.person(UUID.fromString("ffffffff-0000-0000-0000-000000000000"), "Inconnu 3",
                null, null);
        UUID unknownSameTimeLowerId = rows.person(UUID.fromString("80000000-0000-0000-0000-000000000000"), "Inconnu 2",
                null, null);
        UUID bornIn1980 = rows.person(UUID.randomUUID(), "1980", LocalDate.of(1980, 12, 31), null);
        Instant sameTime = Instant.parse("2027-01-01T00:00:00Z");
        rows.createdAt(unknownSameTimeHigherId, sameTime);
        rows.createdAt(unknownSameTimeLowerId, sameTime);
        List<UUID> relationships = List.of(bornInJune1990, bornIn1990, unknownFirst, unknownSameTimeHigherId,
                unknownSameTimeLowerId, bornIn1980).stream().map(child -> rows.parentOf(focus, child)).toList();

        FamilyTree tree = treeQuery.neighbourhood(familyId, new PersonId(focus), 1);

        assertThat(tree.nodes()).extracting(node -> node.person().id().value()).containsExactly(focus, bornIn1980,
                bornIn1990, bornInJune1990, unknownFirst, unknownSameTimeLowerId, unknownSameTimeHigherId);
        assertThat(tree.edges()).extracting(edge -> edge.id().value()).containsExactlyElementsOf(relationships);
    }

    @Nested
    class MostConnectedPerson {

        @Test
        void anEmptyFamilyHasNone() {
            assertThat(treeQuery.mostConnectedActivePerson(familyId)).isEmpty();
        }

        @Test
        void theActivePersonWithTheMostActiveRelationshipsWins() {
            UUID loner = rows.person("Seul");
            UUID parent = rows.person("Parent");
            UUID child = rows.person("Enfant");
            UUID partner = rows.person("Partenaire");
            rows.parentOf(parent, child);
            rows.partners(child, partner);

            assertThat(treeQuery.mostConnectedActivePerson(familyId)).contains(new PersonId(child));
            assertThat(loner).isNotNull();
        }

        @Test
        void relationshipsThatAreArchivedOrLeadToAPersonThatIsNotActiveDoNotCount() {
            UUID counted = rows.person("Compté");
            UUID first = rows.person("Premier");
            UUID other = rows.person("Autre");
            rows.parentOf(counted, other);
            for (int i = 0; i < 3; i++) {
                UUID archived = rows.person("Archivé");
                rows.parentOf(first, archived);
                rows.archivePerson(archived);
                UUID merged = rows.person("Fusionné");
                rows.parentOf(first, merged);
                rows.mergePerson(merged, other);
                rows.relation("PARENT_OF", first, rows.person("Lien retiré"), "ARCHIVED");
            }

            // counted and other have 1 relationship each; counted was created first.
            assertThat(treeQuery.mostConnectedActivePerson(familyId)).contains(new PersonId(counted));
        }

        @Test
        void tiesGoToTheEarliestCreatedThenToTheLowestId() {
            UUID later = rows.person("Plus tard");
            UUID earlier = rows.person("Plus tôt");
            rows.createdAt(later, Instant.parse("2027-02-01T00:00:00Z"));
            rows.createdAt(earlier, Instant.parse("2027-01-01T00:00:00Z"));
            rows.partners(later, earlier);

            assertThat(treeQuery.mostConnectedActivePerson(familyId)).contains(new PersonId(earlier));

            UUID highId = rows.person(UUID.fromString("ffffffff-0000-0000-0000-000000000001"), "Haut", null, null);
            UUID lowId = rows.person(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Bas", null, null);
            for (UUID id : List.of(later, earlier, highId, lowId)) {
                rows.createdAt(id, Instant.parse("2027-01-01T00:00:00Z"));
            }
            rows.partners(highId, lowId);

            assertThat(treeQuery.mostConnectedActivePerson(familyId)).contains(new PersonId(lowId));
        }

        @Test
        void anotherFamilysPersonsAreNeverChosen() {
            TestJwts.Token other = TestJwts.newUserToken();
            UUID otherFamily = families().createFamily(other, "Autre famille");
            GraphRows otherRows = new GraphRows(jdbc, otherFamily, families().userId(other));
            otherRows.parentOf(otherRows.person("A"), otherRows.person("B"));
            UUID mine = rows.person("Moi");

            assertThat(treeQuery.mostConnectedActivePerson(familyId)).contains(new PersonId(mine));
        }
    }
}
