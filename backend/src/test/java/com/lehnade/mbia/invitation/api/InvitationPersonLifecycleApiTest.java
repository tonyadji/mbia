package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-56: a pending invitation through the lifecycle of the Person it was sent for (OQ-050, OQ-056;
 * data-model.md §8, §19; mvp.md §18). Archived or merged, the Person stays on the invitation, which
 * stays valid, but it is neither shown nor offered while it is not ACTIVE; restored, it is again; a
 * merge never moves it. Accepting never links anyone: only {@code claimPerson} does.
 */
class InvitationPersonLifecycleApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private InvitationFixtures invitations;
    private PersonFixtures persons;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        invitations = new InvitationFixtures(mvc, jdbc);
        persons = new PersonFixtures(mvc, jdbc);
    }

    @Test
    void anInvitationWhosePersonIsArchivedStaysValidKeepsItsPersonButNeitherShowsNorOffersIt() {
        UUID awa = person("Awa", "Ngo");
        String token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", awa);
        UUID invitation = invitations.idOfToken(token);

        archive(awa);

        assertThat(invitations.row(invitation)).containsEntry("status", "PENDING").containsEntry("person_id", awa);
        assertThat(invitations.list(family.admin(), family.familyId(), ""))
                .bodyJson().extractingPath("$[0].person").isNull();
        assertThat(invitations.preview(token)).hasStatusOk();
        MvcTestResult renewed = invitations.renew(family.admin(), family.familyId(), invitation, "\"0\"");
        assertThat(renewed).hasStatusOk().bodyJson().extractingPath("$.person").isNull();
        assertThat(invitations.row(invitation)).containsEntry("person_id", awa);

        TestJwts.Token invitee = TestJwts.newUserToken();
        MvcTestResult accepted = invitations.acceptLink(invitee, InvitationFixtures.tokenOf(renewed));

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson").isNull();
        assertThat(accepted).bodyJson().extractingPath("$.membership.linkedPersonId").isNull();
        assertNothingLinkedTo(invitee);
        assertThat(persons.linkedUserId(awa)).isNull();
    }

    @Test
    void aRestoredPersonIsShownAndOfferedAgainAndOnlyClaimPersonLinksIt() {
        UUID awa = person("Awa", "Ngo");
        String token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", awa);
        archive(awa);

        assertThat(persons.restore(family.admin(), family.familyId(), awa, version(awa))).hasStatusOk();

        assertThat(invitations.list(family.admin(), family.familyId(), "")).bodyJson()
                .extractingPath("$[0].person.id").isEqualTo(awa.toString());
        assertThat(invitations.list(family.admin(), family.familyId(), "")).bodyJson()
                .extractingPath("$[0].person.displayName").isEqualTo("Awa Ngo");

        TestJwts.Token invitee = TestJwts.newUserToken();
        MvcTestResult accepted = invitations.acceptLink(invitee, token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson.id").isEqualTo(awa.toString());
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson.displayName").isEqualTo("Awa Ngo");
        assertNothingLinkedTo(invitee);

        assertThat(persons.claim(invitee, family.familyId(), awa, version(awa))).hasStatusOk();
        assertThat(persons.linkedUserId(awa)).isEqualTo(families().userId(invitee));
    }

    @Test
    void aPersonRestoredAfterTheInvitationWasAcceptedIsLinkedToNobody() {
        UUID awa = person("Awa", "Ngo");
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", awa);
        archive(awa);
        TestJwts.Token invitee = TestJwts.newUserToken();
        assertThat(invitations.acceptLink(invitee, token)).hasStatusOk();

        assertThat(persons.restore(family.admin(), family.familyId(), awa, version(awa))).hasStatusOk();

        assertThat(persons.linkedUserId(awa)).isNull();
        assertNothingLinkedTo(invitee);
    }

    @Test
    void aMergeNeverMovesTheInvitationOfItsSourceWhichOffersNobody() {
        UUID kept = person("Awa", "Ngo");
        UUID duplicate = person("Awa", "N.");
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", duplicate);
        UUID invitation = invitations.idOfToken(token);

        merge(duplicate, kept);

        assertThat(invitations.row(invitation)).containsEntry("status", "PENDING")
                .containsEntry("person_id", duplicate);
        assertThat(invitations.list(family.admin(), family.familyId(), ""))
                .bodyJson().extractingPath("$[0].person").isNull();

        // One pending invitation per Person: the kept Person has none, so it can be invited too.
        UUID keptInvitation = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", kept);
        assertThat(invitations.status(invitation)).isEqualTo("PENDING");
        assertThat(invitations.row(keptInvitation)).containsEntry("status", "PENDING").containsEntry("person_id", kept);

        TestJwts.Token invitee = TestJwts.newUserToken();
        MvcTestResult accepted = invitations.acceptLink(invitee, token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson").isNull();
        assertNothingLinkedTo(invitee);
    }

    @Test
    void theInvitationOfAMergeTargetKeepsItsPersonWhichIsStillShownAndOffered() {
        UUID kept = person("Awa", "Ngo");
        UUID duplicate = person("Awa", "N.");
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", kept);
        UUID invitation = invitations.idOfToken(token);

        merge(duplicate, kept);

        assertThat(invitations.row(invitation)).containsEntry("status", "PENDING").containsEntry("person_id", kept);
        assertThat(invitations.list(family.admin(), family.familyId(), "")).bodyJson()
                .extractingPath("$[0].person.id").isEqualTo(kept.toString());

        TestJwts.Token invitee = TestJwts.newUserToken();
        MvcTestResult accepted = invitations.acceptLink(invitee, token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson.id").isEqualTo(kept.toString());
        assertNothingLinkedTo(invitee);
    }

    /** data-model.md §19 step 10: the target takes the source's User, so it is no longer offered. */
    @Test
    void aMergeTargetThatTakesTheUserOfItsSourceIsNoLongerOffered() {
        UUID kept = person("Awa", "Ngo");
        UUID duplicate = person("Awa", "N.");
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", kept);
        assertThat(persons.claim(family.viewer(), family.familyId(), duplicate, version(duplicate))).hasStatusOk();

        merge(duplicate, kept);

        UUID viewer = families().userId(family.viewer());
        assertThat(persons.linkedUserId(kept)).isEqualTo(viewer);
        TestJwts.Token invitee = TestJwts.newUserToken();
        MvcTestResult accepted = invitations.acceptLink(invitee, token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson").isNull();
        assertThat(persons.linkedUserId(kept)).isEqualTo(viewer);
        assertNothingLinkedTo(invitee);
    }

    private UUID person(String firstName, String lastName) {
        return persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"%s\", \"lastName\": \"%s\"}".formatted(firstName, lastName));
    }

    private String version(UUID personId) {
        return "\"" + persons.version(personId) + "\"";
    }

    private void archive(UUID personId) {
        assertThat(persons.archive(family.admin(), family.familyId(), personId, version(personId))).hasStatusOk();
    }

    private void merge(UUID source, UUID target) {
        assertThat(persons.merge(family.admin(), family.familyId(), source, target, persons.version(source),
                persons.version(target))).hasStatus(HttpStatus.OK);
    }

    private void assertNothingLinkedTo(TestJwts.Token user) {
        assertThat(persons.countLinkedTo(family.familyId(), families().userId(user))).isZero();
    }
}
