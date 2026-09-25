package com.lehnade.mbia.genealogy.domain;

import static com.lehnade.mbia.genealogy.domain.KinshipRelation.CHILD;
import static com.lehnade.mbia.genealogy.domain.KinshipRelation.PARENT;
import static com.lehnade.mbia.genealogy.domain.KinshipRelation.PARTNER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PR-21: the kinship resolver of genealogy.md §9 on graph fixtures: every supported pattern,
 * half-siblings, several partners, RELATED, NONE_KNOWN and the tie-breaking of
 * person-relationships-collaboration.md §10.
 */
class KinshipGraphTest {

    /** A graph built by name; ids are fixed so that the tie-breaking is known. */
    private static final class Fixture {

        private final Map<String, PersonId> ids = new HashMap<>();
        private final Map<String, Gender> genders = new HashMap<>();
        private final List<KinshipGraph.Edge> edges = new ArrayList<>();

        Fixture person(String name, Gender gender, String uuid) {
            ids.put(name, new PersonId(UUID.fromString(uuid)));
            genders.put(name, gender);
            return this;
        }

        Fixture parentOf(String parent, String... children) {
            for (String child : children) {
                edges.add(new KinshipGraph.Edge(RelationshipType.PARENT_OF, id(parent), id(child)));
            }
            return this;
        }

        Fixture partners(String first, String second) {
            edges.add(new KinshipGraph.Edge(RelationshipType.PARTNER_OF, id(first), id(second)));
            return this;
        }

        PersonId id(String name) {
            return ids.get(name);
        }

        Kinship kinship(String from, String to) {
            return KinshipGraph.of(edges).kinship(id(from), id(to), genders.get(to));
        }

        Kinship kinshipWithShuffledEdges(String from, String to, long seed) {
            List<KinshipGraph.Edge> shuffled = new ArrayList<>(edges);
            Collections.shuffle(shuffled, new Random(seed));
            return KinshipGraph.of(shuffled).kinship(id(from), id(to), genders.get(to));
        }

        /** The Persons of the path after {@code from}, by name. */
        List<String> via(Kinship kinship) {
            return kinship.path().stream().map(step -> nameOf(step.to())).toList();
        }

        private String nameOf(PersonId id) {
            return ids.entrySet().stream().filter(entry -> entry.getValue().equals(id)).findFirst()
                    .orElseThrow().getKey();
        }
    }

    private static String uuid(int n) {
        return "00000000-0000-0000-0000-%012d".formatted(n);
    }

    /**
     * Fixture 1–5, reference Tony: Paul & Jeanne (partners) → Marie, Jean; Marie & André → Tony,
     * Awa; Jean → Luc, Sophie; Tony → Eric → Nina; Awa & Marc → Léa, Hugo; Tony partner of Chloé
     * and Inès; Rose → Chloé; André & Clara → Samuel (Tony's half-brother); Gaston → Paul, Robert;
     * Robert → Denise → Yves (second cousin); Zoé and Kim have no relationship.
     */
    private static Fixture family() {
        String[][] persons = {
                {"Paul", "MALE"}, {"Jeanne", "FEMALE"}, {"Marie", "FEMALE"}, {"Jean", "MALE"},
                {"André", "MALE"}, {"Tony", "MALE"}, {"Awa", "FEMALE"}, {"Luc", "MALE"}, {"Sophie", "FEMALE"},
                {"Eric", "MALE"}, {"Nina", "FEMALE"}, {"Léa", "FEMALE"}, {"Hugo", "MALE"}, {"Marc", "MALE"},
                {"Chloé", "FEMALE"}, {"Inès", "FEMALE"}, {"Rose", "FEMALE"}, {"Clara", "FEMALE"},
                {"Samuel", "MALE"}, {"Gaston", "MALE"}, {"Robert", "MALE"}, {"Denise", "FEMALE"},
                {"Yves", "MALE"}, {"Zoé", "FEMALE"}, {"Kim", "OTHER"}, {"Alex", "OTHER"}, {"Sam", "UNKNOWN"}};
        Fixture fixture = new Fixture();
        for (int i = 0; i < persons.length; i++) {
            fixture.person(persons[i][0], Gender.valueOf(persons[i][1]), uuid(i + 1));
        }
        return fixture
                .partners("Paul", "Jeanne").parentOf("Paul", "Marie", "Jean").parentOf("Jeanne", "Marie", "Jean")
                .parentOf("Marie", "Tony", "Awa").parentOf("André", "Tony", "Awa")
                .parentOf("Jean", "Luc", "Sophie")
                .parentOf("Tony", "Eric").parentOf("Eric", "Nina")
                .parentOf("Awa", "Léa", "Hugo").parentOf("Marc", "Léa", "Hugo")
                .partners("Tony", "Chloé").partners("Inès", "Tony").parentOf("Rose", "Chloé")
                .parentOf("André", "Samuel").parentOf("Clara", "Samuel")
                .parentOf("Gaston", "Paul", "Robert").parentOf("Robert", "Denise").parentOf("Denise", "Yves")
                // Neutral genders: Alex is Tony's child, Sam is Eric's child.
                .parentOf("Tony", "Alex").parentOf("Eric", "Sam");
    }

    @ParameterizedTest(name = "{1} is Tony''s {2}")
    @CsvSource({
            "Tony, Tony, SELF",
            "Tony, Marie, MOTHER", "Tony, André, FATHER",
            "Tony, Eric, SON", "Tony, Alex, CHILD",
            "Tony, Paul, GRANDFATHER", "Tony, Jeanne, GRANDMOTHER",
            "Tony, Nina, GRANDDAUGHTER", "Tony, Sam, GRANDCHILD",
            "Tony, Awa, SISTER",
            "Tony, Jean, UNCLE",
            "Tony, Léa, NIECE", "Tony, Hugo, NEPHEW",
            "Tony, Luc, FIRST_COUSIN", "Tony, Sophie, FIRST_COUSIN",
            "Tony, Chloé, PARTNER",
            // The other direction of the same patterns.
            "Marie, Tony, SON", "Eric, Tony, FATHER", "Paul, Tony, GRANDSON", "Nina, Tony, GRANDFATHER",
            "Awa, Tony, BROTHER", "Jean, Tony, NEPHEW", "Léa, Tony, UNCLE", "Luc, Tony, FIRST_COUSIN",
            "Chloé, Tony, PARTNER", "Jean, Awa, NIECE", "Hugo, Marie, GRANDMOTHER", "Eric, Awa, AUNT"})
    void everySupportedPatternIsGenderedByTheTarget(String from, String to, KinshipCode expected) {
        assertThat(family().kinship(from, to).code()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} targets are neutral")
    @CsvSource({"OTHER", "UNKNOWN"})
    void targetsOfOtherOrUnknownGenderGetNeutralCodes(Gender gender) {
        Fixture fixture = family();
        fixture.genders.replaceAll((name, ignored) -> gender);

        assertThat(List.of("Marie", "Eric", "Paul", "Nina", "Awa", "Jean", "Léa", "Luc", "Chloé")
                .stream().map(to -> fixture.kinship("Tony", to).code()).toList())
                .containsExactly(KinshipCode.PARENT, KinshipCode.CHILD, KinshipCode.GRANDPARENT,
                        KinshipCode.GRANDCHILD, KinshipCode.SIBLING, KinshipCode.PARENT_SIBLING,
                        KinshipCode.SIBLING_CHILD, KinshipCode.FIRST_COUSIN, KinshipCode.PARTNER);
    }

    @Test
    void thePathGoesFromTheReferenceToTheTarget() {
        Fixture fixture = family();

        Kinship kinship = fixture.kinship("Tony", "Paul");

        assertThat(kinship.path()).containsExactly(
                new KinshipPathStep(fixture.id("Tony"), fixture.id("Marie"), PARENT),
                new KinshipPathStep(fixture.id("Marie"), fixture.id("Paul"), PARENT));
        assertThat(fixture.kinship("Paul", "Tony").path()).extracting(KinshipPathStep::relation)
                .containsExactly(CHILD, CHILD);
        assertThat(fixture.kinship("Tony", "Tony").path()).isEmpty();
    }

    @Test
    void halfSiblingsAreSiblings() {
        Fixture fixture = family();

        assertThat(fixture.kinship("Tony", "Samuel").code()).isEqualTo(KinshipCode.BROTHER);
        assertThat(fixture.via(fixture.kinship("Tony", "Samuel"))).containsExactly("André", "Samuel");
        assertThat(fixture.kinship("Samuel", "Awa").code()).isEqualTo(KinshipCode.SISTER);
    }

    @Test
    void everyPartnerIsAPartnerAndPartnersOfAPartnerAreOnlyRelated() {
        Fixture fixture = family();

        assertThat(fixture.kinship("Tony", "Chloé").code()).isEqualTo(KinshipCode.PARTNER);
        assertThat(fixture.kinship("Tony", "Inès").code()).isEqualTo(KinshipCode.PARTNER);
        assertThat(fixture.kinship("Chloé", "Inès").code()).isEqualTo(KinshipCode.RELATED);
        assertThat(fixture.kinship("Chloé", "Inès").path()).extracting(KinshipPathStep::relation)
                .containsExactly(PARTNER, PARTNER);
    }

    @Test
    void anExistingPathWithoutConciseLabelIsRelatedWithItsFullPath() {
        Fixture fixture = family();

        Kinship inLaw = fixture.kinship("Tony", "Rose");
        assertThat(inLaw.code()).isEqualTo(KinshipCode.RELATED);
        assertThat(inLaw.path()).extracting(KinshipPathStep::relation).containsExactly(PARTNER, PARENT);

        Kinship secondCousin = fixture.kinship("Tony", "Yves");
        assertThat(secondCousin.code()).isEqualTo(KinshipCode.RELATED);
        assertThat(fixture.via(secondCousin)).containsExactly("Marie", "Paul", "Gaston", "Robert", "Denise", "Yves");

        Kinship childsOtherParent = fixture.kinship("Awa", "Marc");
        assertThat(childsOtherParent.code()).isEqualTo(KinshipCode.RELATED);
        assertThat(childsOtherParent.path()).extracting(KinshipPathStep::relation).containsExactly(CHILD, PARENT);

        assertThat(fixture.kinship("Tony", "Gaston").code())
                .as("great-grandparent: no generic Nth-degree label").isEqualTo(KinshipCode.RELATED);
    }

    @Test
    void noPathIsNoneKnownWithAnEmptyPath() {
        Fixture fixture = family();

        assertThat(fixture.kinship("Tony", "Zoé")).isEqualTo(Kinship.noneKnown());
        assertThat(fixture.kinship("Zoé", "Kim")).isEqualTo(Kinship.noneKnown());
        assertThat(KinshipGraph.of(List.of()).kinship(fixture.id("Tony"), fixture.id("Zoé"), Gender.FEMALE))
                .isEqualTo(Kinship.noneKnown());
    }

    @Test
    void resultsDoNotDependOnTheOrderOfTheEdges() {
        Fixture fixture = family();

        for (long seed = 0; seed < 20; seed++) {
            for (String to : List.of("Jean", "Luc", "Samuel", "Yves", "Rose", "Hugo")) {
                assertThat(fixture.kinshipWithShuffledEdges("Tony", to, seed)).isEqualTo(fixture.kinship("Tony", to));
            }
        }
    }

    /** Fixture 6: Tony → Marie → {Paul, Jeanne} → Jean: two shortest paths to the uncle. */
    @Nested
    class SeveralShortestPaths {

        private Fixture uncle(String paulId, String jeanneId) {
            return new Fixture()
                    .person("Tony", Gender.MALE, uuid(1)).person("Marie", Gender.FEMALE, uuid(2))
                    .person("Jean", Gender.MALE, uuid(3))
                    .person("Paul", Gender.MALE, paulId).person("Jeanne", Gender.FEMALE, jeanneId)
                    .parentOf("Marie", "Tony").parentOf("Paul", "Marie", "Jean").parentOf("Jeanne", "Marie", "Jean");
        }

        @Test
        void theSmallestPersonIdWins() {
            Fixture paulFirst = uncle(uuid(10), uuid(20));
            Fixture jeanneFirst = uncle(uuid(20), uuid(10));

            assertThat(paulFirst.via(paulFirst.kinship("Tony", "Jean"))).containsExactly("Marie", "Paul", "Jean");
            assertThat(jeanneFirst.via(jeanneFirst.kinship("Tony", "Jean"))).containsExactly("Marie", "Jeanne", "Jean");
            assertThat(paulFirst.kinship("Tony", "Jean").code()).isEqualTo(KinshipCode.UNCLE);
        }

        @Test
        void idsAreOrderedAsPostgresqlOrdersThemNotAsUuidCompareTo() {
            // UUID.compareTo sees 8000… as negative, hence smaller than 1000…; PostgreSQL does not.
            Fixture fixture = uncle("80000000-0000-0000-0000-000000000000", "10000000-0000-0000-0000-000000000000");

            assertThat(fixture.via(fixture.kinship("Tony", "Jean"))).containsExactly("Marie", "Jeanne", "Jean");
        }

        @Test
        void theChoiceIsStableWhateverTheEdgeOrder() {
            Fixture fixture = uncle(uuid(20), uuid(10));

            for (long seed = 0; seed < 20; seed++) {
                assertThat(fixture.via(fixture.kinshipWithShuffledEdges("Tony", "Jean", seed)))
                        .containsExactly("Marie", "Jeanne", "Jean");
            }
        }
    }

    /** Fixture 7: a shortest path of PARENT/CHILD steps wins over one through a partner, whatever the ids. */
    @Test
    void parentAndChildStepsArePreferredToAPartnerStep() {
        Fixture fixture = new Fixture()
                .person("Quentin", Gender.MALE, uuid(1)).person("Tony", Gender.MALE, uuid(2))
                .person("Paulette", Gender.FEMALE, uuid(3)).person("Pierre", Gender.MALE, uuid(9))
                .parentOf("Pierre", "Tony").partners("Tony", "Quentin").parentOf("Paulette", "Pierre", "Quentin");

        Kinship kinship = fixture.kinship("Tony", "Paulette");

        assertThat(kinship.code()).isEqualTo(KinshipCode.GRANDMOTHER);
        assertThat(fixture.via(kinship)).containsExactly("Pierre", "Paulette");
    }

    /** Fixture 8: two relationships between the same two Persons. */
    @Test
    void aParentStepIsPreferredToAPartnerStepBetweenTheSamePersons() {
        Fixture fixture = new Fixture()
                .person("Ben", Gender.MALE, uuid(2)).person("Ana", Gender.FEMALE, uuid(1))
                .partners("Ben", "Ana").parentOf("Ana", "Ben");

        assertThat(fixture.kinship("Ben", "Ana").code()).isEqualTo(KinshipCode.MOTHER);
        assertThat(fixture.kinship("Ana", "Ben").code()).isEqualTo(KinshipCode.SON);
    }

    @Test
    void theShortestPathWinsOverALongerParentAndChildPath() {
        Fixture fixture = family();

        // Tony → Chloé is one PARTNER step, although no PARENT/CHILD path exists at all.
        assertThat(fixture.kinship("Tony", "Chloé").path()).hasSize(1);
        // Tony → Awa: sibling through Marie or André; both are PARENT/CHILD, André (id 5) loses to Marie (id 3).
        assertThat(fixture.via(fixture.kinship("Tony", "Awa"))).containsExactly("Marie", "Awa");
    }
}
