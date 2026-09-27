package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * PR-52, mvp.md §5, data-model.md §7: the ADMIN changes a member's role between CONTRIBUTOR and
 * VIEWER, with {@code If-Match}; the change is audited. Nobody else may, the ADMIN never changes
 * their own role, and the ADMIN role is never granted.
 */
class UpdateMemberRoleApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemberFixtures members;
    private UUID contributorMembership;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        members = new MemberFixtures(mvc, jdbc);
        contributorMembership = members.membershipId(family.familyId(), families().userId(family.contributor()));
    }

    @Test
    void theAdminChangesAContributorToViewerAndBackAndEachChangeIsAudited() {
        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, "\"0\"", "VIEWER"))
                .hasStatusOk()
                .hasHeader("ETag", "\"1\"")
                .bodyJson()
                .hasPathSatisfying("$.id", id -> assertThat(id).asString().isEqualTo(contributorMembership.toString()))
                .hasPathSatisfying("$.role", role -> assertThat(role).asString().isEqualTo("VIEWER"))
                .hasPathSatisfying("$.status", status -> assertThat(status).asString().isEqualTo("ACTIVE"))
                .hasPathSatisfying("$.version", version -> assertThat(version).asNumber().isEqualTo(1));
        assertThat(members.membership(contributorMembership))
                .containsEntry("role", "VIEWER").containsEntry("status", "ACTIVE");

        // The new role applies at once: a VIEWER cannot create a Person.
        assertThat(new PersonFixtures(mvc, jdbc).create(family.contributor(), family.familyId(),
                "{\"firstName\": \"Jean\"}")).hasStatus(HttpStatus.FORBIDDEN);

        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, "\"1\"",
                "CONTRIBUTOR")).hasStatusOk().hasHeader("ETag", "\"2\"");
        assertThat(members.audit(family.familyId(), contributorMembership)).containsExactly(
                "MEMBERSHIP_ROLE_CHANGED {\"role\": \"CONTRIBUTOR\"} {\"role\": \"VIEWER\"}",
                "MEMBERSHIP_ROLE_CHANGED {\"role\": \"VIEWER\"} {\"role\": \"CONTRIBUTOR\"}");
    }

    @Test
    void theSameRoleChangesNothing() {
        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, "\"0\"",
                "CONTRIBUTOR")).hasStatusOk().hasHeader("ETag", "\"0\"");
        assertThat(members.audit(family.familyId(), contributorMembership)).isEmpty();
    }

    @Test
    void contributorsAndViewersCannotChangeRoles() {
        UUID viewerMembership = members.membershipId(family.familyId(), families().userId(family.viewer()));
        for (TestJwts.Token caller : List.of(family.contributor(), family.viewer())) {
            assertThat(members.changeRole(caller, family.familyId(), viewerMembership, "\"0\"", "CONTRIBUTOR"))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
            assertThat(members.changeRole(caller, family.familyId(), contributorMembership, "\"0\"", "VIEWER"))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
        assertThat(members.membership(viewerMembership)).containsEntry("role", "VIEWER").containsEntry("version", 0L);
    }

    @Test
    void theAdminCannotChangeTheirOwnRole() {
        UUID adminMembership = members.membershipId(family.familyId(), families().userId(family.admin()));

        for (String role : List.of("CONTRIBUTOR", "VIEWER")) {
            assertThat(members.changeRole(family.admin(), family.familyId(), adminMembership, "\"0\"", role))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("LAST_ADMIN_REQUIRED");
        }
        assertThat(members.membership(adminMembership)).containsEntry("role", "ADMIN");
    }

    @Test
    void theAdminRoleIsNeverGranted() {
        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, "\"0\"", "ADMIN"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(members.membership(contributorMembership)).containsEntry("role", "CONTRIBUTOR");
    }

    @Test
    void aStaleOrMissingIfMatchIsRefused() {
        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, "\"0\"", "VIEWER"))
                .hasStatusOk();

        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, "\"0\"",
                "CONTRIBUTOR")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(members.changeRole(family.admin(), family.familyId(), contributorMembership, null,
                "CONTRIBUTOR")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(members.membership(contributorMembership)).containsEntry("role", "VIEWER");
    }

    /** OQ-061: an unknown, other-Family or REMOVED membership is not found. */
    @Test
    void aMembershipThatIsNotAnActiveMemberOfTheFamilyIsNotFound() {
        UUID removedMembership = members.membershipId(family.familyId(), families().userId(family.removed()));
        UUID otherFamily = jdbc.sql("SELECT family_id FROM family_memberships WHERE user_id = ?")
                .param(families().userId(family.outsider())).query(UUID.class).single();
        UUID otherFamilyMembership = members.membershipId(otherFamily, families().userId(family.outsider()));

        for (UUID memberId : List.of(UUID.randomUUID(), removedMembership, otherFamilyMembership)) {
            assertThat(members.changeRole(family.admin(), family.familyId(), memberId, "\"0\"", "VIEWER"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
        }
        assertThat(members.membership(removedMembership)).containsEntry("status", "REMOVED");
    }

    @Test
    void anOutsiderOrARemovedMemberGetsFamilyNotFound() {
        for (TestJwts.Token caller : List.of(family.outsider(), family.removed())) {
            assertThat(members.changeRole(caller, family.familyId(), contributorMembership, "\"0\"", "VIEWER"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
    }
}
