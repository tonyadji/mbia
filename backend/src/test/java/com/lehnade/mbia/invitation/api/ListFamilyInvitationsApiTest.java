package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-47: {@code GET /families/{familyId}/invitations} (openapi {@code listFamilyInvitations};
 * data-model.md §8; OQ-056): PENDING by default, an expired one marked EXPIRED when read, never a
 * token or a link.
 */
class ListFamilyInvitationsApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private InvitationFixtures invitations;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        invitations = new InvitationFixtures(mvc, jdbc);
    }

    @Test
    void pendingInvitationsAreListedByDefaultMostRecentFirst() {
        UUID first = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
        UUID second = invitations.inviteLinkId(family.admin(), family.familyId(), "CONTRIBUTOR", null);
        UUID revoked = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
        invitations.revoke(family.admin(), family.familyId(), revoked, "\"0\"");

        assertThat(invitations.list(family.admin(), family.familyId(), ""))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$[*].id").asArray()
                .containsExactly(second.toString(), first.toString());
        assertThat(invitations.list(family.admin(), family.familyId(), "?status=REVOKED"))
                .bodyJson().extractingPath("$[*].id").asArray()
                .containsExactly(revoked.toString());
    }

    @Test
    void anExpiredPendingInvitationIsMarkedExpiredWhenRead() {
        UUID expired = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
        invitations.expire(expired);

        assertThat(invitations.list(family.admin(), family.familyId(), ""))
                .bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(invitations.row(expired)).containsEntry("status", "EXPIRED").containsEntry("version", 1L);
        assertThat(invitations.list(family.admin(), family.familyId(), "?status=EXPIRED"))
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$[0].id").isEqualTo(expired.toString());
                    json.assertThat().extractingPath("$[0].version").isEqualTo(1);
                });
    }

    @Test
    void theListNeverHoldsATokenNorALink() {
        MvcTestResult created = invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", null);
        String token = InvitationFixtures.tokenOf(created);
        String hash = InvitationFixtures.sha256Hex(token);

        String body = FamilyFixtures.body(invitations.list(family.admin(), family.familyId(), ""));

        assertThat(body).doesNotContain(token).doesNotContain(hash).doesNotContain("inviteUrl")
                .doesNotContain("/invitations/");
    }

    @Test
    void aPersonIsShownOnlyWhileItIsActive() {
        UUID awa = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", awa);

        assertThat(invitations.list(family.admin(), family.familyId(), ""))
                .bodyJson().extractingPath("$[0].person.displayName").isEqualTo("Awa");

        persons.archive(awa);

        assertThat(invitations.list(family.admin(), family.familyId(), ""))
                .bodyJson().extractingPath("$[0].person").isNull();
    }

    @Test
    void anotherFamilysInvitationsAreNeverListed() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Essomba");
        invitations.inviteLinkId(family.outsider(), otherFamily, "VIEWER", null);

        assertThat(invitations.list(family.admin(), family.familyId(), ""))
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }
}
