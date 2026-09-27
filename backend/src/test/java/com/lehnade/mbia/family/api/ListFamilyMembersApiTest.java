package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-52, SCREEN-008, OQ-050, OQ-061: every ACTIVE member lists the ACTIVE members of the Family,
 * each with their linked Person and what it is to the caller, computed from the relationships and
 * never stored (data-model.md §12). Outsiders and removed members get 404.
 */
class ListFamilyMembersApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemberFixtures members;
    private RelationshipFixtures relationships;
    private UUID admin;
    private UUID contributor;
    private UUID viewer;
    private UUID awa;
    private UUID paul;

    /** The ADMIN is Awa, the CONTRIBUTOR is her son Paul, the VIEWER is in no Person. */
    @BeforeEach
    void givenAFamilyWhereTheAdminIsTheContributorsMother() {
        family = families().givenFamilyWithMembersOfEachRole();
        members = new MemberFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        admin = families().userId(family.admin());
        contributor = families().userId(family.contributor());
        viewer = families().userId(family.viewer());
        awa = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\", \"linkToCurrentUser\": true}");
        paul = persons.createId(family.contributor(), family.familyId(),
                "{\"firstName\": \"Paul\", \"lastName\": \"Ngo\", \"gender\": \"MALE\", \"linkToCurrentUser\": true}");
        relationships.parentOfId(family.admin(), family.familyId(), awa, paul);
    }

    @Test
    void everyActiveMemberListsTheActiveMembersWithTheirLinkedPersonButNotTheirEmail() {
        for (TestJwts.Token caller : List.of(family.admin(), family.contributor(), family.viewer())) {
            MvcTestResult result = members.list(caller, family.familyId());

            assertThat(result).hasStatusOk();
            List<Map<String, Object>> listed = JsonPath.read(FamilyFixtures.body(result), "$");
            assertThat(listed).extracting(member -> member.get("userId"))
                    .containsExactly(admin.toString(), contributor.toString(), viewer.toString());
            assertThat(listed).allSatisfy(member -> assertThat(member)
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("displayName", "Test User")
                    // OQ-061: never an address; the generated models write absent fields as null.
                    .containsEntry("email", null)
                    .containsKeys("id", "joinedAt", "version"));
            assertThat(listed).extracting(member -> member.get("role"))
                    .containsExactly("ADMIN", "CONTRIBUTOR", "VIEWER");
            assertThat(listed).extracting(member -> member.get("linkedPersonDisplayName"))
                    .containsExactly("Awa Ngo", "Paul Ngo", null);
            assertThat(listed).extracting(member -> member.get("linkedPersonId"))
                    .containsExactly(awa.toString(), paul.toString(), null);
            assertThat(listed.get(0).get("id")).isEqualTo(members.membershipId(family.familyId(), admin).toString());
        }
    }

    @Test
    void kinshipIsWhatEachMemberIsToTheCallerAndNullForThemselvesOrWithoutLinkedPerson() {
        assertThat(relationshipsSeenBy(family.contributor())).containsExactly("MOTHER", null, null);
        assertThat(relationshipsSeenBy(family.admin())).containsExactly(null, "SON", null);
        assertThat(relationshipsSeenBy(family.viewer())).containsExactly(null, null, null);
    }

    /** data-model.md §12: kinship is computed from ACTIVE relationships, so it follows their changes. */
    @Test
    void kinshipIsComputedFromTheCurrentRelationshipsNeverStored() {
        UUID relationship = jdbc.sql("SELECT id FROM family_relationships WHERE source_person_id = ?")
                .param(awa).query(UUID.class).single();
        assertThat(relationships.remove(family.admin(), family.familyId(), relationship, "\"0\""))
                .hasStatus2xxSuccessful();

        assertThat(relationshipsSeenBy(family.contributor())).containsExactly("NONE_KNOWN", null, null);
    }

    @Test
    void anOutsiderAndARemovedMemberGetNotFound() {
        for (TestJwts.Token caller : List.of(family.outsider(), family.removed())) {
            assertThat(members.list(caller, family.familyId())).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
        assertThat(members.list(family.admin(), UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    private List<Object> relationshipsSeenBy(TestJwts.Token caller) {
        MvcTestResult result = members.list(caller, family.familyId());
        assertThat(result).hasStatusOk();
        List<Map<String, Object>> listed = JsonPath.read(FamilyFixtures.body(result), "$");
        return listed.stream().map(member -> member.get("relationshipToCurrentUser")).toList();
    }
}
