package com.lehnade.mbia.genealogy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonSearchQuery;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PR-25: the Family people search against PostgreSQL (mvp.md §19; genealogy.md §11;
 * data-model.md §23.1; OQ-022): case- and accent-insensitive substring matching in first name,
 * last name, preferred name and "first name last name"; ACTIVE Persons of the Family only; ordered
 * by folded display name, then creation, then id.
 */
class JpaPersonSearchQueryTest extends ApiTestSupport {

    @Autowired
    PersonSearchQuery searchQuery;

    private UUID familyId;
    private GraphRows rows;

    @BeforeEach
    void givenAFamily() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        rows = new GraphRows(jdbc, familyId, families().userId(admin));
    }

    @Test
    void matchingIgnoresAccentsAndCaseBothWays() {
        UUID eloise = rows.person("Éloïse", "Ngo", null);
        UUID plain = rows.person("Eloise", "Ngo", null);
        rows.person("Paul", null, null);

        for (String text : new String[] {"Eloise", "éloïse", "ELOÏSE", "loï", "ELO"}) {
            assertThat(ids(search(text))).as(text).containsExactlyInAnyOrder(eloise, plain);
        }
    }

    @Test
    void matchesFirstLastPreferredNameAndFirstNameFollowedByLastName() {
        UUID marie = rows.person("Marie", "Dupont", null);
        UUID jean = rows.person("Jean", "Mbarga", "Papa Jean");
        UUID awa = rows.person("Awa", "Dupont-Ékané", null);

        assertThat(ids(search("dupont"))).containsExactlyInAnyOrder(marie, awa);
        assertThat(ids(search("papa"))).containsExactly(jean);
        assertThat(ids(search("Marie Dup"))).containsExactly(marie);
        assertThat(ids(search("jean mbar"))).containsExactly(jean);
        assertThat(ids(search("ekane"))).containsExactly(awa);
        assertThat(ids(search("Marie Jean"))).isEmpty();
    }

    @Test
    void wildcardsAndBackslashesAreTypedCharacters() {
        UUID percent = rows.person("Cent%", null, null);
        UUID underscore = rows.person("A_B", null, null);
        UUID backslash = rows.person("C\\D", null, null);
        rows.person("AxB", null, null);
        rows.person("Centaine", null, null);

        assertThat(ids(search("%"))).containsExactly(percent);
        assertThat(ids(search("a_b"))).containsExactly(underscore);
        assertThat(ids(search("\\"))).containsExactly(backslash);
    }

    @Test
    void archivedMergedAndOtherFamilyPersonsAreNeverReturned() {
        UUID active = rows.person("Éloïse", null, null);
        UUID archived = rows.person("Eloise", "Archivée", null);
        rows.archivePerson(archived);
        UUID merged = rows.person("Eloïse", "Fusionnée", null);
        rows.mergePerson(merged, active);
        TestJwts.Token other = TestJwts.newUserToken();
        UUID otherFamily = families().createFamily(other, "Famille Ndongo");
        new GraphRows(jdbc, otherFamily, families().userId(other)).person("Eloise", "Ailleurs", null);

        assertThat(ids(search("eloise"))).containsExactly(active);
        assertThat(ids(search(""))).containsExactly(active);
        assertThat(searchQuery.search(familyId, PersonStatus.ACTIVE, "eloise", 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void orderIsFoldedDisplayNameThenCreationThenId() {
        UUID zoe = rows.person("Zoé", null, null);
        UUID fabien = rows.person("fabien", null, null);
        UUID eloise = rows.person("Éloïse", null, null);
        UUID denis = rows.person("Denis", null, null);
        // Display name "Papa" (preferred name) sorts under P, not under its first name.
        UUID papa = rows.person("Albert", "Mvondo", "Papa");
        UUID marieB = rows.person("Marie", "Biya", null);

        assertThat(ids(search(""))).containsExactly(denis, eloise, fabien, marieB, papa, zoe);
    }

    @Test
    void equalDisplayNamesAreOrderedByCreationThenId() {
        UUID first = rows.person("Marie", "Ngo", null);
        UUID second = rows.person("marie", "NGO", null);
        UUID sameTimeA = rows.person("Marie", "Ngo", null);
        UUID sameTimeB = rows.person("Marie", "Ngo", null);
        Instant later = Instant.parse("2027-01-01T00:00:00Z");
        rows.createdAt(sameTimeA, later);
        rows.createdAt(sameTimeB, later);
        boolean aFirst = sameTimeA.toString().compareTo(sameTimeB.toString()) < 0;

        assertThat(ids(search("marie ngo")))
                .containsExactly(first, second, aFirst ? sameTimeA : sameTimeB, aFirst ? sameTimeB : sameTimeA);
    }

    @Test
    void pagesFollowTheOrderAndTheTotalCountsEveryMatch() {
        for (int i = 0; i < 25; i++) {
            rows.person("Personne %02d".formatted(i), null, null);
        }
        rows.person("Autre", null, null);

        PersonSearchQuery.Result first = searchQuery.search(familyId, PersonStatus.ACTIVE, "personne", 0, 10);
        PersonSearchQuery.Result last = searchQuery.search(familyId, PersonStatus.ACTIVE, "personne", 2, 10);

        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.items()).extracting(person -> person.details().firstName())
                .startsWith("Personne 00", "Personne 01").hasSize(10);
        assertThat(last.items()).extracting(person -> person.details().firstName())
                .containsExactly("Personne 20", "Personne 21", "Personne 22", "Personne 23", "Personne 24");
        assertThat(last.totalElements()).isEqualTo(25);
    }

    private List<Person> search(String text) {
        return searchQuery.search(familyId, PersonStatus.ACTIVE, text, 0, 100).items();
    }

    private static List<UUID> ids(List<Person> persons) {
        return persons.stream().map(person -> person.id().value()).toList();
    }
}
