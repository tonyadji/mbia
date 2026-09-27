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
import com.lehnade.mbia.invitation.InvitationFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * PR-52, mvp.md §5, data-model.md §7, person-relationships-collaboration.md §2: the ADMIN removes a
 * member, a member leaves; in one transaction the membership becomes REMOVED and the linked Person
 * is released, and every contribution stays. The last ADMIN can neither leave nor be removed.
 */
class RemoveFamilyMemberApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemberFixtures members;
    private PersonFixtures persons;
    private UUID contributor;
    private UUID contributorMembership;
    private UUID viewerMembership;
    private UUID adminMembership;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        members = new MemberFixtures(mvc, jdbc);
        persons = new PersonFixtures(mvc, jdbc);
        contributor = families().userId(family.contributor());
        contributorMembership = members.membershipId(family.familyId(), contributor);
        viewerMembership = members.membershipId(family.familyId(), families().userId(family.viewer()));
        adminMembership = members.membershipId(family.familyId(), families().userId(family.admin()));
    }

    @Test
    void aRemovedMemberLosesAccessTheirPersonIsReleasedAndEveryContributionStays() {
        UUID paul = persons.createId(family.contributor(), family.familyId(),
                "{\"firstName\": \"Paul\", \"lastName\": \"Ngo\", \"linkToCurrentUser\": true}");
        UUID mother = persons.createId(family.contributor(), family.familyId(),
                "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\"}");
        UUID relationship = new RelationshipFixtures(mvc, jdbc)
                .parentOfId(family.contributor(), family.familyId(), mother, paul);
        UUID memory = MemoryFixtures.idOf(new MemoryFixtures(mvc, jdbc)
                .createStory(family.contributor(), family.familyId(), "Le marché", "Awa vendait du plantain.", mother));

        assertThat(members.remove(family.admin(), family.familyId(), contributorMembership, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(members.membership(contributorMembership))
                .containsEntry("status", "REMOVED").containsEntry("role", "CONTRIBUTOR").containsEntry("version", 1L)
                .extractingByKey("removed_at").isNotNull();
        assertThat(jdbc.sql("SELECT status, linked_user_id FROM persons WHERE id = ?").param(paul).query().singleRow())
                .containsEntry("status", "ACTIVE").containsEntry("linked_user_id", null);
        assertThat(statusOf("persons", mother)).isEqualTo("ACTIVE");
        assertThat(statusOf("family_relationships", relationship)).isEqualTo("ACTIVE");
        assertThat(statusOf("memories", memory)).isEqualTo("ACTIVE");
        assertThat(members.audit(family.familyId(), contributorMembership)).containsExactly(
                "MEMBERSHIP_REMOVED {\"role\": \"CONTRIBUTOR\", \"status\": \"ACTIVE\"} {\"status\": \"REMOVED\"}");
        assertThat(members.audit(family.familyId(), paul))
                .contains("PERSON_UNCLAIMED {\"linkedUserId\": \"" + contributor + "\"} {}");

        // A removed member gets 404 on the Family and everything in it.
        assertThat(mvc.get().uri("/api/v1/families/{familyId}", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.contributor().bearer()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        assertThat(persons.get(family.contributor(), family.familyId(), mother)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(members.list(family.contributor(), family.familyId())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(persons.get(family.admin(), family.familyId(), paul)).hasStatusOk()
                .bodyJson().extractingPath("$.linkedUserId").isNull();
        List<String> left = JsonPath.read(FamilyFixtures.body(members.list(family.admin(), family.familyId())),
                "$[*].id");
        assertThat(left).doesNotContain(contributorMembership.toString()).hasSize(2);
    }

    @Test
    void aContributorAndAViewerLeaveTheFamilyByThemselves() {
        assertThat(members.remove(family.contributor(), family.familyId(), contributorMembership, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(members.remove(family.viewer(), family.familyId(), viewerMembership, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(members.membership(contributorMembership)).containsEntry("status", "REMOVED");
        assertThat(members.membership(viewerMembership)).containsEntry("status", "REMOVED");
        assertThat(members.audit(family.familyId(), viewerMembership)).containsExactly(
                "MEMBERSHIP_LEFT {\"role\": \"VIEWER\", \"status\": \"ACTIVE\"} {\"status\": \"REMOVED\"}");
        assertThat(members.list(family.viewer(), family.familyId())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aMemberWhoLeavesReleasesTheirPerson() {
        UUID viewerPerson = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        assertThat(persons.claim(family.viewer(), family.familyId(), viewerPerson, "\"0\"")).hasStatusOk();

        assertThat(members.remove(family.viewer(), family.familyId(), viewerMembership, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.sql("SELECT linked_user_id FROM persons WHERE id = ?").param(viewerPerson)
                .query().singleRow()).containsEntry("linked_user_id", null);
    }

    @Test
    void contributorsAndViewersCannotRemoveSomeoneElse() {
        for (TestJwts.Token caller : List.of(family.contributor(), family.viewer())) {
            for (UUID target : List.of(adminMembership, contributorMembership, viewerMembership)) {
                if (target.equals(members.membershipId(family.familyId(), families().userId(caller)))) {
                    continue;
                }
                assertThat(members.remove(caller, family.familyId(), target, "\"0\""))
                        .hasStatus(HttpStatus.FORBIDDEN)
                        .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
            }
            assertThat(members.remove(caller, family.familyId(), UUID.randomUUID(), "\"0\""))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
        assertThat(List.of(adminMembership, contributorMembership, viewerMembership))
                .allSatisfy(id -> assertThat(members.membership(id)).containsEntry("status", "ACTIVE"));
    }

    @Test
    void theOnlyAdminCannotLeave() {
        assertThat(members.remove(family.admin(), family.familyId(), adminMembership, "\"0\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("LAST_ADMIN_REQUIRED");
        // Whatever the version it sends, the ADMIN reads why.
        assertThat(members.remove(family.admin(), family.familyId(), adminMembership, "\"7\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("LAST_ADMIN_REQUIRED");

        assertThat(members.membership(adminMembership)).containsEntry("status", "ACTIVE").containsEntry("version", 0L);
        assertThat(members.audit(family.familyId(), adminMembership)).isEmpty();
    }

    @Test
    void aStaleOrMissingIfMatchIsRefused() {
        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, "\"0\"", "VIEWER"))
                .hasStatusOk();

        assertThat(members.remove(family.admin(), family.familyId(), contributorMembership, "\"0\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(members.remove(family.contributor(), family.familyId(), contributorMembership, null))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(members.membership(contributorMembership)).containsEntry("status", "ACTIVE");
    }

    /** OQ-061: an unknown, other-Family or already REMOVED membership is not found. */
    @Test
    void aMembershipThatIsNotAnActiveMemberOfTheFamilyIsNotFound() {
        UUID removedMembership = members.membershipId(family.familyId(), families().userId(family.removed()));
        assertThat(members.remove(family.admin(), family.familyId(), contributorMembership, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        for (UUID memberId : List.of(UUID.randomUUID(), removedMembership, contributorMembership)) {
            assertThat(members.remove(family.admin(), family.familyId(), memberId, "\"1\""))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
        }
        assertThat(members.remove(family.outsider(), family.familyId(), viewerMembership, "\"0\""))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
    }

    /** mvp.md §5: a removed User invited again returns with the role of the new invitation. */
    @Test
    void aRemovedMemberInvitedAgainComesBackWithTheNewRoleAndCanLinkThemselvesAgain() {
        UUID paul = persons.createId(family.contributor(), family.familyId(),
                "{\"firstName\": \"Paul\", \"linkToCurrentUser\": true}");
        assertThat(members.remove(family.admin(), family.familyId(), contributorMembership, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", paul);

        assertThat(invitations.acceptLink(family.contributor(), token)).hasStatusOk();

        assertThat(members.membership(contributorMembership))
                .containsEntry("status", "ACTIVE").containsEntry("role", "VIEWER").containsEntry("removed_at", null);
        assertThat(members.list(family.contributor(), family.familyId())).hasStatusOk();
        assertThat(persons.claim(family.contributor(), family.familyId(), paul, "\"1\"")).hasStatusOk();
    }

    private String statusOf(String table, UUID id) {
        return jdbc.sql("SELECT status FROM " + table + " WHERE id = ?").param(id).query(String.class).single();
    }
}
