package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * PR-28: every important genealogy mutation (genealogy.md §13) writes an {@code audit_entries} row
 * (data-model.md §17) with its Family, actor, resource and request id, in its transaction, and
 * never a secret (AGENTS.md §5).
 */
class AuditTrailApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
    }

    @Test
    void everyImportantMutationIsAudited() {
        UUID admin = families().userId(family.admin());
        UUID contributor = families().userId(family.contributor());
        UUID me = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Alice\", \"linkToCurrentUser\": true}");
        UUID marie = persons.createId(family.contributor(), family.familyId(), "{\"firstName\": \"Marie\"}");
        assertThat(persons.update(family.contributor(), family.familyId(), marie, "\"0\"",
                "{\"lastName\": \"Mbida\"}")).hasStatusOk();
        assertThat(persons.claim(family.contributor(), family.familyId(), marie, "\"1\"")).hasStatusOk();
        assertThat(persons.unclaim(family.contributor(), family.familyId(), marie, "\"2\"")).hasStatusOk();
        assertThat(persons.archive(family.admin(), family.familyId(), marie, "\"3\"")).hasStatusOk();
        assertThat(persons.restore(family.admin(), family.familyId(), marie, "\"4\"")).hasStatusOk();
        UUID link = relationships.parentOfId(family.admin(), family.familyId(), marie, me);
        assertThat(relationships.remove(family.admin(), family.familyId(), link, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(relationships.restore(family.admin(), family.familyId(), link, "\"1\"")).hasStatusOk();
        UUID duplicate = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        assertThat(persons.merge(family.admin(), family.familyId(), duplicate, marie,
                persons.version(duplicate), persons.version(marie))).hasStatusOk();

        assertThat(jdbc.sql("""
                SELECT action || ' ' || resource_type || ' ' || actor_user_id FROM audit_entries
                WHERE family_id = ? ORDER BY occurred_at, action
                """).param(family.familyId()).query(String.class).list())
                .containsExactly(
                        "PERSON_CLAIMED PERSON " + admin,
                        "PERSON_CREATED PERSON " + admin,
                        "PERSON_CREATED PERSON " + contributor,
                        "PERSON_UPDATED PERSON " + contributor,
                        "PERSON_CLAIMED PERSON " + contributor,
                        "PERSON_UNCLAIMED PERSON " + contributor,
                        "PERSON_ARCHIVED PERSON " + admin,
                        "PERSON_RESTORED PERSON " + admin,
                        "RELATIONSHIP_CREATED RELATIONSHIP " + admin,
                        "RELATIONSHIP_ARCHIVED RELATIONSHIP " + admin,
                        "RELATIONSHIP_RESTORED RELATIONSHIP " + admin,
                        "PERSON_CREATED PERSON " + admin,
                        "PERSONS_MERGED PERSON " + admin,
                        "PERSONS_MERGED PERSON " + admin);
        assertThat(resourcesOf("PERSON_ARCHIVED")).containsExactly(marie);
        assertThat(resourcesOf("RELATIONSHIP_RESTORED")).containsExactly(link);
        assertThat(resourcesOf("PERSONS_MERGED")).containsExactlyInAnyOrder(marie, duplicate);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_entries WHERE family_id = ? AND trace_id IS NULL")
                .param(family.familyId()).query(Long.class).single()).isZero();
    }

    @Test
    void theRequestIdIsKeptAndNoSecretIsWritten() {
        assertThat(mvc.post().uri("/api/v1/families/{familyId}/persons", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.contributor().bearer())
                .header("X-Request-Id", "req-pr28-audit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"Paul\", \"linkToCurrentUser\": true}")
                .exchange()).hasStatus(HttpStatus.CREATED);

        List<String> rows = jdbc.sql("""
                SELECT concat_ws(' ', action, trace_id, old_value::text, new_value::text) FROM audit_entries
                WHERE family_id = ?
                """).param(family.familyId()).query(String.class).list();

        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row).contains("req-pr28-audit")
                .doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("password")
                .doesNotContain("@mbia.test", family.contributor().subject(), "Bearer", "eyJ"));
    }

    @Test
    void aRefusedMergeWritesNoAuditEntry() {
        UUID paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        UUID jean = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Jean\"}");
        relationships.parentOfId(family.admin(), family.familyId(), paul, jean);
        long before = entries();

        // Merging a parent into their child would make a self relation (OQ-026).
        assertThat(persons.merge(family.admin(), family.familyId(), paul, jean, 0, 0))
                .hasStatus(HttpStatus.CONFLICT);

        assertThat(entries()).isEqualTo(before);
    }

    private List<UUID> resourcesOf(String action) {
        return jdbc.sql("SELECT resource_id FROM audit_entries WHERE family_id = ? AND action = ?")
                .params(family.familyId(), action).query(UUID.class).list();
    }

    private long entries() {
        return jdbc.sql("SELECT count(*) FROM audit_entries WHERE family_id = ?").param(family.familyId())
                .query(Long.class).single();
    }
}
