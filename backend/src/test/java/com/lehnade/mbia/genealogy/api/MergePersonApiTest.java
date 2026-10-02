package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.PersonFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-27: {@code POST /families/{familyId}/persons/{personId}/merge} (openapi {@code mergePerson};
 * mvp.md §12; person-relationships-collaboration.md §4.2; data-model.md §19; genealogy.md §12;
 * OQ-026 to OQ-028). ADMIN only; the duplicate becomes MERGED into the kept Person, which stays
 * ACTIVE; relationships move, are put in canonical order and deduplicated; the linked User moves
 * when unambiguous; a refusal leaves every row as it was.
 */
class MergePersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private GraphRows rows;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        rows = new GraphRows(jdbc, family.familyId(), families().userId(family.admin()));
    }

    @Test
    void adminMergesTheDuplicateIntoTheKeptPerson() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "marie", "DUPONT", "Mamie");
        UUID mother = rows.person("Awa");
        UUID son = rows.person("Paul");
        rows.parentOf(mother, duplicate);
        rows.parentOf(duplicate, son);

        assertThat(merge(duplicate, kept, 0, 0))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.id").isEqualTo(kept.toString());
                    json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
                    json.assertThat().extractingPath("$.version").isEqualTo(1);
                    json.assertThat().extractingPath("$.lastName").isEqualTo("Dupont");
                    json.assertThat().extractingPath("$.preferredName").isEqualTo("Mamie");
                });
        assertThat(persons.status(duplicate)).isEqualTo("MERGED");
        assertThat(mergedInto(duplicate)).isEqualTo(kept);
        assertThat(persons.version(duplicate)).isEqualTo(1);
        assertThat(relations()).containsExactlyInAnyOrder(
                "PARENT_OF " + mother + " " + kept + " ACTIVE",
                "PARENT_OF " + kept + " " + son + " ACTIVE");
    }

    @Test
    void theMergedPersonLeavesTheSearchAndStaysReadableWithItsKeptPerson() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);

        merge(duplicate, kept, 0, 0);

        assertThat(mvc.get().uri("/api/v1/families/{familyId}/persons?search=marie", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.viewer().bearer()).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly(kept.toString());
        assertThat(persons.get(family.viewer(), family.familyId(), duplicate)).hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.status").isEqualTo("MERGED");
                    json.assertThat().extractingPath("$.mergedIntoPersonId").isEqualTo(kept.toString());
                });
    }

    // --- Relationships (data-model.md §19, OQ-027) ---

    @Test
    void aMovedPartnerRelationIsPutBackInCanonicalOrder() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID partner = person('5', "Jean", null, null);
        rows.partners(duplicate, partner);

        assertThat(merge(duplicate, kept, 0, 0)).hasStatusOk();

        assertThat(relations()).containsExactly("PARTNER_OF " + partner + " " + kept + " ACTIVE");
    }

    @Test
    void anActiveRelationTheKeptPersonAlreadyHasIsArchivedOnTheDuplicate() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID father = rows.person("Jean");
        UUID partner = person('5', "Luc", null, null);
        rows.parentOf(father, kept);
        rows.parentOf(father, duplicate);
        rows.partners(kept, partner);
        rows.partners(duplicate, partner);

        assertThat(merge(duplicate, kept, 0, 0)).hasStatusOk();

        assertThat(relations()).containsExactlyInAnyOrder(
                "PARENT_OF " + father + " " + kept + " ACTIVE",
                "PARTNER_OF " + partner + " " + kept + " ACTIVE",
                "PARENT_OF " + father + " " + duplicate + " ARCHIVED",
                "PARTNER_OF " + duplicate + " " + partner + " ARCHIVED");
    }

    @Test
    void removedLinksMoveTooExceptOneBetweenTheTwoPersons() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID son = rows.person("Paul");
        UUID removedChild = archived(rows.parentOf(duplicate, son));
        UUID removedBetween = archived(rows.partners(duplicate, kept));

        assertThat(merge(duplicate, kept, 0, 0)).hasStatusOk();

        assertThat(relations()).containsExactlyInAnyOrder(
                "PARENT_OF " + kept + " " + son + " ARCHIVED",
                "PARTNER_OF " + duplicate + " " + kept + " ARCHIVED");
        assertThat(relationVersion(removedChild)).isEqualTo(1);
        assertThat(relationVersion(removedBetween)).isZero();
    }

    @Test
    void anActiveLinkBetweenTheTwoPersonsRollsTheWholeMergeBack() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID partner = rows.person("Luc");
        rows.partners(duplicate, partner);
        rows.parentOf(kept, duplicate);
        List<Map<String, Object>> before = snapshot();

        assertConflict(merge(duplicate, kept, 0, 0), "SELF_RELATIONSHIP");

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aMergeMakingSomeoneTheirOwnAncestorRollsTheWholeMergeBack() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID partner = rows.person("Luc");
        UUID child = rows.person("Paul");
        rows.partners(duplicate, partner);
        rows.parentOf(kept, child);
        rows.parentOf(child, duplicate);
        List<Map<String, Object>> before = snapshot();

        assertConflict(merge(duplicate, kept, 0, 0), "PARENTAL_CYCLE");

        assertThat(snapshot()).isEqualTo(before);
    }

    // --- Linked Users ---

    @Test
    void twoDifferentLinkedUsersAreRefused() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        rows.link(kept, families().userId(family.admin()));
        rows.link(duplicate, families().userId(family.contributor()));
        List<Map<String, Object>> before = snapshot();

        assertConflict(merge(duplicate, kept, 0, 0), "DIFFERENT_LINKED_USERS");

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void theUserOfTheDuplicateMovesToTheKeptPerson() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID viewer = families().userId(family.viewer());
        rows.link(duplicate, viewer);

        assertThat(merge(duplicate, kept, 0, 0)).hasStatusOk()
                .bodyJson().extractingPath("$.linkedUserId").isEqualTo(viewer.toString());

        assertThat(persons.linkedUserId(kept)).isEqualTo(viewer);
        assertThat(persons.linkedUserId(duplicate)).isNull();
    }

    @Test
    void theKeptPersonKeepsItsOwnUser() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID viewer = families().userId(family.viewer());
        rows.link(kept, viewer);

        assertThat(merge(duplicate, kept, 0, 0)).hasStatusOk();

        assertThat(persons.linkedUserId(kept)).isEqualTo(viewer);
    }

    // --- Optimistic concurrency ---

    @Test
    void aStaleSourceOrTargetVersionIsRefusedAndChangesNothing() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        rows.parentOf(rows.person("Jean"), duplicate);
        List<Map<String, Object>> before = snapshot();

        assertRefused(merge(duplicate, kept, 1, 0), HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertRefused(merge(duplicate, kept, 0, 1), HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");

        assertThat(snapshot()).isEqualTo(before);
    }

    // --- Roles, Family isolation and Person status (OQ-026) ---

    @Test
    void contributorAndViewerCannotMerge() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);

        for (TestJwts.Token caller : new TestJwts.Token[] {family.contributor(), family.viewer()}) {
            assertRefused(persons.merge(caller, family.familyId(), duplicate, kept, 0, 0),
                    HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        }
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertRefused(persons.merge(caller, family.familyId(), duplicate, kept, 0, 0),
                    HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND");
        }
        assertThat(persons.status(duplicate)).isEqualTo("ACTIVE");
    }

    @Test
    void aPersonOfAnotherFamilyUnknownOrMergedIsNotFound() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID alreadyMerged = person('5', "Marie", "Dupont", null);
        rows.mergePerson(alreadyMerged, kept);
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID foreign = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Marie\"}");

        assertRefused(merge(duplicate, foreign, 0, 0), HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertRefused(merge(foreign, kept, 0, 0), HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertRefused(merge(duplicate, UUID.randomUUID(), 0, 0), HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertRefused(merge(alreadyMerged, kept, 0, 0), HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertRefused(merge(duplicate, alreadyMerged, 0, 0), HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertThat(persons.status(duplicate)).isEqualTo("ACTIVE");
        assertThat(persons.status(foreign)).isEqualTo("ACTIVE");
    }

    @Test
    void anArchivedPersonIsNotActive() {
        UUID kept = person('9', "Marie", "Dupont", null);
        UUID duplicate = person('1', "Marie", "Dupont", null);
        UUID archived = person('5', "Marie", "Dupont", null);
        rows.archivePerson(archived);

        assertRefused(merge(archived, kept, 0, 0), HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
        assertRefused(merge(duplicate, archived, 0, 0), HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
        assertThat(persons.status(archived)).isEqualTo("ARCHIVED");
    }

    @Test
    void aPersonCannotBeMergedIntoThemselves() {
        UUID kept = person('9', "Marie", "Dupont", null);

        assertRefused(merge(kept, kept, 0, 0), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(persons.status(kept)).isEqualTo("ACTIVE");
    }

    @Test
    void theTargetAndBothVersionsAreRequired() {
        UUID duplicate = person('1', "Marie", "Dupont", null);

        assertRefused(mvc.post().uri("/api/v1/families/{familyId}/persons/{personId}/merge", family.familyId(),
                        duplicate)
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceVersion\": 0}")
                .exchange(), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    private MvcTestResult merge(UUID source, UUID target, long sourceVersion, long targetVersion) {
        return persons.merge(family.admin(), family.familyId(), source, target, sourceVersion, targetVersion);
    }

    /** A Person whose id starts with {@code first}, to control the UUID order of the test. */
    private UUID person(char first, String firstName, String lastName, String preferredName) {
        UUID id = UUID.fromString(first + UUID.randomUUID().toString().substring(1));
        rows.person(id, firstName, null, null);
        jdbc.sql("UPDATE persons SET last_name = ?, preferred_name = ? WHERE id = ?")
                .params(lastName, preferredName, id).update();
        return id;
    }

    private UUID archived(UUID relationshipId) {
        jdbc.sql("UPDATE family_relationships SET status = 'ARCHIVED', archived_at = now() WHERE id = ?")
                .param(relationshipId).update();
        return relationshipId;
    }

    private UUID mergedInto(UUID personId) {
        return jdbc.sql("SELECT merged_into_person_id FROM persons WHERE id = ?").param(personId)
                .query(UUID.class).single();
    }

    private long relationVersion(UUID relationshipId) {
        return jdbc.sql("SELECT version FROM family_relationships WHERE id = ?").param(relationshipId)
                .query(Long.class).single();
    }

    /** Each relationship of the Family as "TYPE source target STATUS". */
    private List<String> relations() {
        return jdbc.sql("""
                SELECT type || ' ' || source_person_id || ' ' || target_person_id || ' ' || status
                FROM family_relationships WHERE family_id = ?
                """).param(family.familyId()).query(String.class).list();
    }

    /** Every Person and relationship row of the Family, all columns. */
    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> all = new java.util.ArrayList<>(jdbc.sql(
                "SELECT * FROM persons WHERE family_id = ? ORDER BY id").param(family.familyId()).query().listOfRows());
        all.addAll(jdbc.sql("SELECT * FROM family_relationships WHERE family_id = ? ORDER BY id")
                .param(family.familyId()).query().listOfRows());
        return all;
    }

    private static void assertConflict(MvcTestResult result, String reason) {
        assertRefused(result, HttpStatus.CONFLICT, "PERSON_MERGE_CONFLICT");
        assertThat(result).bodyJson().extractingPath("$.details.reason").isEqualTo(reason);
    }

    private static void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
