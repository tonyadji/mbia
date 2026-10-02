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
 * PR-56: a member's linked Person archived or merged (data-model.md §19, §21;
 * person-relationships-collaboration.md, OQ-023, OQ-026). The member list (SCREEN-008) follows the
 * link where the merge put it; a linked Person cannot be archived until the link is released.
 */
class MemberLinkedPersonLifecycleApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemberFixtures members;
    private PersonFixtures persons;
    private UUID awa;

    /** The ADMIN is Awa. */
    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        members = new MemberFixtures(mvc, jdbc);
        persons = new PersonFixtures(mvc, jdbc);
        awa = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\", \"linkToCurrentUser\": true}");
    }

    @Test
    void aMemberWhoseLinkedPersonIsMergedIsListedWithTheKeptPersonAndItsKinship() {
        UUID paul = son("Paul");
        UUID duplicate = person("Paulo");
        assertThat(persons.claim(family.contributor(), family.familyId(), duplicate, "\"0\"")).hasStatusOk();

        merge(duplicate, paul);

        Map<String, Object> contributor = listedBy(family.admin()).get(1);
        assertThat(contributor)
                .containsEntry("linkedPersonId", paul.toString())
                .containsEntry("linkedPersonDisplayName", "Paul Ngo")
                .containsEntry("relationshipToCurrentUser", "SON");
        assertThat(listedBy(family.contributor()).getFirst()).containsEntry("relationshipToCurrentUser", "MOTHER");
    }

    @Test
    void aMemberLinkedToTheKeptPersonStaysLinkedToIt() {
        UUID paul = son("Paul");
        UUID duplicate = person("Paulo");
        assertThat(persons.claim(family.contributor(), family.familyId(), paul, "\"0\"")).hasStatusOk();

        merge(duplicate, paul);

        assertThat(listedBy(family.admin()).get(1))
                .containsEntry("linkedPersonId", paul.toString())
                .containsEntry("linkedPersonDisplayName", "Paul Ngo");
        assertThat(persons.linkedUserId(duplicate)).isNull();
    }

    @Test
    void twoPersonsLinkedToTwoMembersCannotBeMergedAndTheMembersKeepTheirPerson() {
        UUID paul = son("Paul");
        UUID marie = person("Marie");
        assertThat(persons.claim(family.contributor(), family.familyId(), paul, "\"0\"")).hasStatusOk();
        assertThat(persons.claim(family.viewer(), family.familyId(), marie, "\"0\"")).hasStatusOk();
        List<Map<String, Object>> before = listedBy(family.admin());

        MvcTestResult refused = persons.merge(family.admin(), family.familyId(), marie, paul,
                persons.version(marie), persons.version(paul));

        assertThat(refused).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_MERGE_CONFLICT");
        assertThat(refused).bodyJson().extractingPath("$.details.reason").isEqualTo("DIFFERENT_LINKED_USERS");
        assertThat(listedBy(family.admin())).isEqualTo(before);
    }

    @Test
    void aMembersLinkedPersonCannotBeArchivedUntilTheMemberLeaves() {
        UUID marie = person("Marie");
        assertThat(persons.claim(family.viewer(), family.familyId(), marie, "\"0\"")).hasStatusOk();

        assertThat(persons.archive(family.admin(), family.familyId(), marie, "\"1\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_ALREADY_CLAIMED");
        assertThat(persons.status(marie)).isEqualTo("ACTIVE");

        UUID viewerMembership = members.membershipId(family.familyId(), families().userId(family.viewer()));
        assertThat(members.remove(family.viewer(), family.familyId(), viewerMembership, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(persons.archive(family.admin(), family.familyId(), marie, "\"" + persons.version(marie) + "\""))
                .hasStatusOk();
        assertThat(listedBy(family.admin())).hasSize(2).extracting(member -> member.get("linkedPersonId"))
                .doesNotContain(marie.toString());
    }

    private UUID person(String firstName) {
        return persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"%s\", \"lastName\": \"Ngo\", \"gender\": \"MALE\"}".formatted(firstName));
    }

    private UUID son(String firstName) {
        UUID son = person(firstName);
        new RelationshipFixtures(mvc, jdbc).parentOfId(family.admin(), family.familyId(), awa, son);
        return son;
    }

    private void merge(UUID source, UUID target) {
        assertThat(persons.merge(family.admin(), family.familyId(), source, target, persons.version(source),
                persons.version(target))).hasStatusOk();
    }

    private List<Map<String, Object>> listedBy(TestJwts.Token caller) {
        MvcTestResult result = members.list(caller, family.familyId());
        assertThat(result).hasStatusOk();
        return JsonPath.read(FamilyFixtures.body(result), "$");
    }
}
