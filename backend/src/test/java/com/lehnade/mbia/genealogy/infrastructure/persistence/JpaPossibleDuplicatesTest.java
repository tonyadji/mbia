package com.lehnade.mbia.genealogy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.application.PossibleDuplicates;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PR-27: the possible duplicate candidate rule (person-relationships-collaboration.md §4.1,
 * data-model.md §22), criterion by criterion: (1) same first name after trim, whitespace collapse,
 * case folding and accent removal; (2) same last name or same preferred name, compared the same
 * way, when present on both; (3) same birth year when both are known. A missing value never
 * matches by itself. ACTIVE Persons of the Family only.
 */
class JpaPossibleDuplicatesTest extends ApiTestSupport {

    @Autowired
    PossibleDuplicates possibleDuplicates;

    private UUID familyId;
    private GraphRows rows;

    @BeforeEach
    void givenAFamily() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        rows = new GraphRows(jdbc, familyId, families().userId(admin));
    }

    // --- (1) first name ---

    @Test
    void theFirstNameMatchesWhateverTheCaseAccentsAndSpaces() {
        UUID eloise = rows.person("Éloïse", "Ngo", null);
        UUID jeanPaul = rows.person("Jean  Paul", "Ngo", null);

        assertThat(candidates(named("eloise", "Ngo", null))).containsExactly(eloise);
        assertThat(candidates(named("  ÉLOÏSE ", "Ngo", null))).containsExactly(eloise);
        assertThat(candidates(named("jean paul", "Ngo", null))).containsExactly(jeanPaul);
        assertThat(candidates(named("Jean   PAUL", "Ngo", null))).containsExactly(jeanPaul);
    }

    @Test
    void aDifferentFirstNameIsNotACandidate() {
        rows.person("Marie", "Ngo", null);

        assertThat(candidates(named("Mariette", "Ngo", null))).isEmpty();
        assertThat(candidates(named("Marie Claire", "Ngo", null))).isEmpty();
    }

    // --- (2) last name or preferred name ---

    @Test
    void theLastNameMatchesWhateverTheCaseAccentsAndSpaces() {
        UUID awa = rows.person("Awa", "Dupont Ékané", null);

        assertThat(candidates(named("Awa", "dupont  ekane", null))).containsExactly(awa);
        assertThat(candidates(named("Awa", "Dupont", null))).isEmpty();
    }

    @Test
    void thePreferredNameMatchesWhenTheLastNamesDiffer() {
        UUID jean = rows.person("Jean", "Mbarga", "Papa Jean");

        assertThat(candidates(named("Jean", "Mbida", "papa jéan"))).containsExactly(jean);
        assertThat(candidates(named("Jean", "Mbida", "Tonton"))).isEmpty();
    }

    @Test
    void aValueMissingOnOneSideNeverMatchesByItself() {
        rows.person("Marie", "Dupont", null);
        rows.person("Paul", null, "Popol");
        rows.person("Awa", null, null);

        assertThat(candidates(named("Marie", null, null))).as("no last name on the new Person").isEmpty();
        assertThat(candidates(named("Marie", null, "Dupont"))).as("last name vs preferred name").isEmpty();
        assertThat(candidates(named("Paul", "Popol", null))).as("preferred name vs last name").isEmpty();
        assertThat(candidates(named("Paul", "Ndzi", null))).as("no last name on the existing Person").isEmpty();
        assertThat(candidates(named("Awa", null, null))).as("first name only on both").isEmpty();
    }

    // --- (3) birth year ---

    @Test
    void bothKnownBirthYearsMustBeEqual() {
        UUID marie = rows.person("Marie", "Dupont", null);
        birthYear(marie, 1954);

        assertThat(candidates(named("Marie", "Dupont", null, PartialDate.yearOnly(1954)))).containsExactly(marie);
        assertThat(candidates(named("Marie", "Dupont", null, PartialDate.yearOnly(1956)))).isEmpty();
    }

    @Test
    void anUnknownBirthYearOnEitherSideDoesNotPreventAMatch() {
        UUID known = rows.person("Marie", "Dupont", null);
        birthYear(known, 1954);
        UUID unknown = rows.person("Paul", "Dupont", null);

        assertThat(candidates(named("Marie", "Dupont", null))).containsExactly(known);
        assertThat(candidates(named("Paul", "Dupont", null, PartialDate.yearOnly(1990)))).containsExactly(unknown);
    }

    @Test
    void theYearOfAnExactBirthDateIsItsBirthYear() {
        UUID exact = rows.person(UUID.randomUUID(), "Marie", LocalDate.of(1954, 3, 12), null);
        lastName(exact, "Dupont");
        UUID yearOnly = rows.person(UUID.randomUUID(), "Paul", null, 1960);
        lastName(yearOnly, "Dupont");

        assertThat(candidates(named("Marie", "Dupont", null, PartialDate.yearOnly(1954)))).containsExactly(exact);
        assertThat(candidates(named("Marie", "Dupont", null, PartialDate.exact(LocalDate.of(1955, 1, 1)))))
                .isEmpty();
        assertThat(candidates(named("Paul", "Dupont", null, PartialDate.exact(LocalDate.of(1960, 8, 1)))))
                .containsExactly(yearOnly);
    }

    // --- scope ---

    @Test
    void onlyActivePersonsOfTheFamilyAreCandidates() {
        UUID active = rows.person("Marie", "Dupont", null);
        UUID archived = rows.person("Marie", "Dupont", null);
        rows.archivePerson(archived);
        UUID merged = rows.person("Marie", "Dupont", null);
        rows.mergePerson(merged, active);
        TestJwts.Token other = TestJwts.newUserToken();
        UUID otherFamily = families().createFamily(other, "Famille Ngo");
        new GraphRows(jdbc, otherFamily, families().userId(other)).person("Marie", "Dupont", null);

        assertThat(candidates(named("Marie", "Dupont", null))).containsExactly(active);
    }

    @Test
    void candidatesAreBoundedAndOrderedByDisplayNameThenCreation() {
        UUID first = rows.person("Marie", "Dupont", null);
        UUID nickname = rows.person("Marie", "Dupont", "Tata Marie");
        for (int i = 0; i < PossibleDuplicates.MAX_CANDIDATES + 2; i++) {
            rows.person("Marie", "Dupont", null);
        }

        List<UUID> found = candidates(named("Marie", "Dupont", null));
        assertThat(found).hasSize(PossibleDuplicates.MAX_CANDIDATES).doesNotContain(nickname);
        assertThat(found.getFirst()).isEqualTo(first);
    }

    private List<UUID> candidates(PersonDetails details) {
        return possibleDuplicates.candidatesFor(familyId, details).stream().map(person -> person.id().value())
                .toList();
    }

    private static PersonDetails named(String firstName, String lastName, String preferredName) {
        return named(firstName, lastName, preferredName, null);
    }

    private static PersonDetails named(String firstName, String lastName, String preferredName, PartialDate birth) {
        return new PersonDetails(firstName, null, lastName, preferredName, null, birth, false, null, null);
    }

    private void birthYear(UUID personId, int year) {
        jdbc.sql("UPDATE persons SET birth_year = ?, birth_date_precision = 'YEAR_ONLY' WHERE id = ?")
                .params(year, personId).update();
    }

    private void lastName(UUID personId, String lastName) {
        jdbc.sql("UPDATE persons SET last_name = ? WHERE id = ?").params(lastName, personId).update();
    }
}
