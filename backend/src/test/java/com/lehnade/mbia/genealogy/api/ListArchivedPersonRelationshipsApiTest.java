package com.lehnade.mbia.genealogy.api;

import static com.lehnade.mbia.genealogy.api.GetPersonApiTest.assertPersonNotFound;
import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/**
 * PR-24: {@code GET /families/{familyId}/persons/{personId}/archived-relationships} (openapi
 * {@code listArchivedPersonRelationships}; mvp.md §13; genealogy.md §11bis; data-model.md
 * §23.2bis). ADMIN only; only the ARCHIVED relationships of that Person, each with the other
 * Person, most recently removed first, in a bounded number of queries.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ListArchivedPersonRelationshipsApiTest extends ApiTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID marie;
    private UUID paul;
    private UUID awa;
    private UUID leo;

    @BeforeEach
    void givenMarieWithRelatives() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\", \"gender\": \"MALE\"}");
        awa = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        leo = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Léo\"}");
    }

    @Test
    void returnsOnlyTheRemovedLinksOfThePersonMostRecentFirstWithTheOtherPerson() {
        UUID father = relationships.parentOfId(family.admin(), family.familyId(), paul, marie);
        UUID child = relationships.parentOfId(family.admin(), family.familyId(), marie, leo);
        relationships.parentOfId(family.admin(), family.familyId(), awa, marie);
        UUID notMarie = relationships.parentOfId(family.admin(), family.familyId(), paul, leo);
        remove(father);
        remove(notMarie);
        remove(child);
        persons.archive(leo);

        assertThat(relationships.archivedOf(family.admin(), family.familyId(), marie))
                .hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$").asArray().hasSize(2);
                    json.assertThat().extractingPath("$[0].id").isEqualTo(child.toString());
                    json.assertThat().extractingPath("$[0].type").isEqualTo("PARENT_OF");
                    json.assertThat().extractingPath("$[0].sourcePersonId").isEqualTo(marie.toString());
                    json.assertThat().extractingPath("$[0].targetPersonId").isEqualTo(leo.toString());
                    json.assertThat().extractingPath("$[0].version").isEqualTo(1);
                    json.assertThat().extractingPath("$[0].archivedAt").isNotNull();
                    json.assertThat().extractingPath("$[0].relatedPerson.id").isEqualTo(leo.toString());
                    json.assertThat().extractingPath("$[0].relatedPerson.displayName").isEqualTo("Léo");
                    json.assertThat().extractingPath("$[0].relatedPerson.status").isEqualTo("ARCHIVED");
                    json.assertThat().extractingPath("$[1].id").isEqualTo(father.toString());
                    json.assertThat().extractingPath("$[1].relatedPerson.id").isEqualTo(paul.toString());
                    json.assertThat().extractingPath("$[1].relatedPerson.gender").isEqualTo("MALE");
                    json.assertThat().extractingPath("$[1].relatedPerson.status").isEqualTo("ACTIVE");
                });
    }

    @Test
    void anEmptyListWhenNothingWasRemoved() {
        relationships.parentOfId(family.admin(), family.familyId(), paul, marie);

        assertThat(relationships.archivedOf(family.admin(), family.familyId(), marie))
                .hasStatusOk().bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void theRemovedLinksOfAnArchivedPersonAreListed() {
        remove(relationships.parentOfId(family.admin(), family.familyId(), paul, marie));
        persons.archive(marie);

        assertThat(relationships.archivedOf(family.admin(), family.familyId(), marie))
                .hasStatusOk().bodyJson().extractingPath("$").asArray().hasSize(1);
    }

    @Test
    void contributorAndViewerGet403() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.contributor(), family.viewer()}) {
            assertThat(relationships.archivedOf(caller, family.familyId(), marie))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        }
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(relationships.archivedOf(caller, family.familyId(), marie))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
    }

    @Test
    void aPersonOfAnotherFamilyOrUnknownIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID jean = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        UUID luc = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Luc\"}");
        UUID foreign = relationships.parentOfId(family.outsider(), otherFamily, jean, luc);
        relationships.remove(family.outsider(), otherFamily, foreign, "\"0\"");

        assertPersonNotFound(relationships.archivedOf(family.admin(), family.familyId(), jean));
        assertPersonNotFound(relationships.archivedOf(family.admin(), family.familyId(), UUID.randomUUID()));
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheRemovedLinks() {
        remove(relationships.parentOfId(family.admin(), family.familyId(), paul, marie));
        long withOne = statements();

        remove(relationships.parentOfId(family.admin(), family.familyId(), awa, marie));
        remove(relationships.parentOfId(family.admin(), family.familyId(), marie, leo));
        for (int i = 0; i < 5; i++) {
            UUID partner = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"P%d\"}".formatted(i));
            remove(RelationshipFixtures.idOf(relationships.partnersOf(family.admin(), family.familyId(), marie,
                    partner)));
        }

        assertThat(statements()).isEqualTo(withOne);
    }

    private long statements() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(relationships.archivedOf(family.admin(), family.familyId(), marie)).hasStatusOk();
        return statistics.getPrepareStatementCount();
    }

    private void remove(UUID relationshipId) {
        assertThat(relationships.remove(family.admin(), family.familyId(), relationshipId, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);
    }
}
