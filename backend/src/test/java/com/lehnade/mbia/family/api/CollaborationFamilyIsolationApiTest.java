package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.activity.ActivityFixtures;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-57: Family isolation across every Family-scoped operation of Phase 5, members, invitations,
 * claims and activity (mvp.md §22; technical-specification.md §17; AGENTS.md §5; Phase 5 plan §6).
 * A member of another Family, or a member removed from this one, gets 404 on each of them, learns
 * nothing of the Family and changes nothing in it; the ids of the Family used through the caller's
 * own Family are not found either; and the lists of the caller's own Family hold nothing of it.
 */
class CollaborationFamilyIsolationApiTest extends ApiTestSupport {

    private PersonFixtures persons;
    private MemberFixtures members;
    private InvitationFixtures invitations;
    private ActivityFixtures activities;
    /** The Family under attack and its resources. */
    private Resources theirs;
    /** The Family of the attacker, an ADMIN there, with resources of its own. */
    private Resources mine;
    /** A former member of the Family under attack. */
    private TestJwts.Token removed;

    @BeforeEach
    void givenTwoFamilies() {
        persons = new PersonFixtures(mvc, jdbc);
        members = new MemberFixtures(mvc, jdbc);
        invitations = new InvitationFixtures(mvc, jdbc);
        activities = new ActivityFixtures(mvc, jdbc);
        theirs = resources(TestJwts.newUserToken().name("Tony Mbida"), "Famille Mbida",
                TestJwts.newUserToken().name("Awa Ngo"), "Awa", "Paul");
        mine = resources(TestJwts.newUserToken().name("Jean Atangana"), "Famille Atangana",
                TestJwts.newUserToken().name("Rose Atangana"), "Rose", "Marc");
        removed = TestJwts.newUserToken().name("Luc Mbida");
        families().insertMembership(theirs.familyId(), families().provisionedUserId(removed), "CONTRIBUTOR",
                "REMOVED");
    }

    static Stream<Arguments> everyOperation() {
        return Stream.of(
                operation("listFamilyMembers", (test, caller) -> test.members.list(caller, test.theirs.familyId())),
                operation("updateMemberRole", (test, caller) -> test.members.changeRole(caller,
                        test.theirs.familyId(), test.theirs.memberId(), "\"0\"", "VIEWER")),
                operation("removeFamilyMember", (test, caller) -> test.members.remove(caller,
                        test.theirs.familyId(), test.theirs.memberId(), "\"0\"")),
                operation("listFamilyInvitations", (test, caller) -> test.invitations.list(caller,
                        test.theirs.familyId(), "")),
                operation("inviteFamilyMember", (test, caller) -> test.invitations.inviteLink(caller,
                        test.theirs.familyId(), "CONTRIBUTOR", null)),
                operation("inviteFamilyMember: for a Person", (test, caller) -> test.invitations.inviteLink(caller,
                        test.theirs.familyId(), "CONTRIBUTOR", test.theirs.freePersonId())),
                operation("renewInvitation", (test, caller) -> test.invitations.renew(caller,
                        test.theirs.familyId(), test.theirs.invitationId(), "\"0\"")),
                operation("revokeInvitation", (test, caller) -> test.invitations.revoke(caller,
                        test.theirs.familyId(), test.theirs.invitationId(), "\"0\"")),
                operation("listClaimablePersons", (test, caller) -> test.claimable(caller, test.theirs.familyId())),
                operation("claimPerson", (test, caller) -> test.persons.claim(caller, test.theirs.familyId(),
                        test.theirs.freePersonId(), "\"0\"")),
                operation("listFamilyActivities", (test, caller) -> test.activities.list(caller,
                        test.theirs.familyId(), "")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    void aMemberOfAnotherFamilyAndARemovedMemberGet404OnTheFamily(String operation,
            BiFunction<CollaborationFamilyIsolationApiTest, TestJwts.Token, MvcTestResult> call) {
        for (TestJwts.Token caller : List.of(mine.admin(), removed)) {
            Snapshot before = snapshot(theirs.familyId());

            MvcTestResult result = call.apply(this, caller);

            assertNotFound(result, "FAMILY_NOT_FOUND");
            assertLeaksNothingOf(result, theirs);
            assertThat(snapshot(theirs.familyId())).isEqualTo(before);
        }
    }

    static Stream<Arguments> everyOperationWithAnId() {
        return Stream.of(
                withIds("updateMemberRole: their member", "RESOURCE_NOT_FOUND", test -> test.members.changeRole(
                        test.mine.admin(), test.mine.familyId(), test.theirs.memberId(), "\"0\"", "VIEWER")),
                withIds("removeFamilyMember: their member", "RESOURCE_NOT_FOUND", test -> test.members.remove(
                        test.mine.admin(), test.mine.familyId(), test.theirs.memberId(), "\"0\"")),
                withIds("inviteFamilyMember: their Person", "PERSON_NOT_FOUND", test -> test.invitations.inviteLink(
                        test.mine.admin(), test.mine.familyId(), "CONTRIBUTOR", test.theirs.freePersonId())),
                withIds("renewInvitation: their invitation", "INVITATION_NOT_FOUND", test -> test.invitations.renew(
                        test.mine.admin(), test.mine.familyId(), test.theirs.invitationId(), "\"0\"")),
                withIds("revokeInvitation: their invitation", "INVITATION_NOT_FOUND", test -> test.invitations.revoke(
                        test.mine.admin(), test.mine.familyId(), test.theirs.invitationId(), "\"0\"")),
                withIds("claimPerson: their Person", "PERSON_NOT_FOUND", test -> test.persons.claim(
                        test.mine.member(), test.mine.familyId(), test.theirs.freePersonId(), "\"0\"")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperationWithAnId")
    void theIdsOfAnotherFamilyAreNotFoundThroughTheCallersFamily(String operation, String code,
            Function<CollaborationFamilyIsolationApiTest, MvcTestResult> call) {
        Snapshot before = snapshot(theirs.familyId());
        Snapshot mineBefore = snapshot(mine.familyId());

        MvcTestResult result = call.apply(this);

        assertNotFound(result, code);
        assertLeaksNothingOf(result, theirs);
        assertThat(snapshot(theirs.familyId())).isEqualTo(before);
        assertThat(snapshot(mine.familyId())).isEqualTo(mineBefore);
    }

    static Stream<Arguments> everyList() {
        return Stream.of(
                list("listFamilyMembers", test -> test.members.list(test.mine.admin(), test.mine.familyId())),
                list("listFamilyInvitations", test -> test.invitations.list(test.mine.admin(), test.mine.familyId(),
                        "?status=PENDING")),
                list("listClaimablePersons", test -> test.claimable(test.mine.admin(), test.mine.familyId())),
                list("listFamilyActivities", test -> test.activities.list(test.mine.admin(), test.mine.familyId(),
                        "")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyList")
    void theListsOfTheCallersFamilyHoldNothingOfAnotherFamily(String operation,
            Function<CollaborationFamilyIsolationApiTest, MvcTestResult> call) {
        MvcTestResult result = call.apply(this);

        assertThat(result).hasStatusOk();
        assertThat(FamilyFixtures.body(result)).isNotEqualTo("[]");
        assertLeaksNothingOf(result, theirs);
    }

    /**
     * A Family whose ADMIN added a Person linked to a CONTRIBUTOR, and a free Person with a pending
     * link invitation; the Family's activity holds these actions.
     */
    private Resources resources(TestJwts.Token admin, String familyName, TestJwts.Token member, String memberName,
            String freeName) {
        UUID familyId = families().createFamily(admin, familyName);
        UUID memberUserId = families().provisionedUserId(member);
        families().insertMembership(familyId, memberUserId, "CONTRIBUTOR", "ACTIVE");
        UUID linkedPersonId = persons.createId(admin, familyId, "{\"firstName\": \"" + memberName + "\"}");
        assertThat(persons.claim(member, familyId, linkedPersonId, "\"0\"")).hasStatus2xxSuccessful();
        UUID freePersonId = persons.createId(admin, familyId, "{\"firstName\": \"" + freeName + "\"}");
        UUID invitationId = invitations.inviteLinkId(admin, familyId, "VIEWER", freePersonId);
        return new Resources(familyId, admin, member, members.membershipId(familyId, memberUserId), memberUserId,
                families().userId(admin), linkedPersonId, freePersonId, invitationId, familyName, memberName,
                freeName);
    }

    private MvcTestResult claimable(TestJwts.Token token, UUID familyId) {
        return mvc.get().uri("/api/v1/families/{familyId}/claimable-persons", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    /** What a Family holds for collaboration: memberships, invitations, Persons and their links, activity, audit. */
    private Snapshot snapshot(UUID familyId) {
        return new Snapshot(
                jdbc.sql("SELECT id, user_id, role, status, version FROM family_memberships WHERE family_id = ? "
                        + "ORDER BY id").param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id, status, version, token_hash, expires_at FROM family_invitations "
                        + "WHERE family_id = ? ORDER BY id").param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id, status, version, linked_user_id FROM persons WHERE family_id = ? ORDER BY id")
                        .param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id FROM activities WHERE family_id = ? ORDER BY id")
                        .param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id FROM audit_entries WHERE family_id = ? ORDER BY id")
                        .param(familyId).query().listOfRows());
    }

    private static void assertNotFound(MvcTestResult result, String code) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static void assertLeaksNothingOf(MvcTestResult result, Resources family) {
        assertThat(FamilyFixtures.body(result)).doesNotContain(family.familyName(), family.memberName(),
                family.freeName(), "Tony", "Mbida", family.familyId().toString(), family.memberId().toString(),
                family.memberUserId().toString(), family.adminUserId().toString(),
                family.linkedPersonId().toString(), family.freePersonId().toString(),
                family.invitationId().toString(), InvitationFixtures.LINK_PREFIX);
    }

    private static Arguments operation(String name,
            BiFunction<CollaborationFamilyIsolationApiTest, TestJwts.Token, MvcTestResult> call) {
        return Arguments.of(name, call);
    }

    private static Arguments withIds(String name, String code,
            Function<CollaborationFamilyIsolationApiTest, MvcTestResult> call) {
        return Arguments.of(name, code, call);
    }

    private static Arguments list(String name, Function<CollaborationFamilyIsolationApiTest, MvcTestResult> call) {
        return Arguments.of(name, call);
    }

    private record Resources(UUID familyId, TestJwts.Token admin, TestJwts.Token member, UUID memberId,
            UUID memberUserId, UUID adminUserId, UUID linkedPersonId, UUID freePersonId, UUID invitationId,
            String familyName, String memberName, String freeName) {}

    private record Snapshot(List<Map<String, Object>> memberships, List<Map<String, Object>> invitations,
            List<Map<String, Object>> persons, List<Map<String, Object>> activities,
            List<Map<String, Object>> audit) {}
}
