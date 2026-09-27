package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-48: {@code GET /invitations/{token}} (openapi {@code previewInvitation}; mvp.md §18;
 * SCREEN-010; OQ-050, OQ-058): public, names the Family, the inviter, the role and the expiry,
 * never the Person; each invalid state gives its code.
 */
class PreviewInvitationApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private InvitationFixtures invitations;
    private UUID awa;
    private String token;

    @BeforeEach
    void givenALinkInvitationForAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        invitations = new InvitationFixtures(mvc, jdbc);
        awa = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\"}");
        token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", awa);
    }

    @Test
    void anyoneHoldingTheLinkSeesTheFamilyTheInviterTheRoleAndTheExpirySignedOut() {
        MvcTestResult preview = invitations.preview(token);

        assertThat(preview).hasStatusOk();
        assertThat(preview).bodyJson().extractingPath("$.familyId").isEqualTo(family.familyId().toString());
        assertThat(preview).bodyJson().extractingPath("$.familyName").isEqualTo("Famille Mbida");
        assertThat(preview).bodyJson().extractingPath("$.invitedByDisplayName").isEqualTo("Test User");
        assertThat(preview).bodyJson().extractingPath("$.role").isEqualTo("CONTRIBUTOR");
        assertThat(preview).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        OffsetDateTime expiresAt = OffsetDateTime.parse(
                com.jayway.jsonpath.JsonPath.read(FamilyFixtures.body(preview), "$.expiresAt"));
        assertThat(expiresAt).isAfter(OffsetDateTime.now().plusDays(13));
    }

    @Test
    void thePreviewNeverNamesThePersonTheInvitationWasSentFor() {
        String body = FamilyFixtures.body(invitations.preview(token));

        assertThat(body).doesNotContain(awa.toString()).doesNotContain("Awa").doesNotContain("Ngo")
                .doesNotContain("person");
    }

    @Test
    void aSignedInUserWhoseEmailIsNotVerifiedMayPreviewToo() {
        TestJwts.Token unverified = TestJwts.newUserToken().emailVerified(false);

        assertThat(mvc.get().uri("/api/v1/invitations/{token}", token)
                .header(HttpHeaders.AUTHORIZATION, unverified.bearer()).exchange())
                .hasStatusOk();
        assertThat(userRowsWithSubject(unverified.subject())).isZero();
    }

    @Test
    void anUnknownTokenIsNotFound() {
        assertThat(invitations.preview("unknown-token-of-an-invitation-000000000"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_NOT_FOUND");
    }

    @Test
    void thePreviousLinkOfARenewedInvitationIsNotFound() {
        UUID id = invitations.idOfToken(token);
        String renewed = InvitationFixtures.tokenOf(invitations.renew(family.admin(), family.familyId(), id, "\"0\""));

        assertThat(invitations.preview(token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_NOT_FOUND");
        assertThat(invitations.preview(renewed)).hasStatusOk();
    }

    @Test
    void anExpiredInvitationIsGoneEvenBeforeItsStatusIsUpdated() {
        invitations.expire(invitations.idOfToken(token));

        assertThat(invitations.preview(token))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_EXPIRED");

        invitations.list(family.admin(), family.familyId(), "");
        assertThat(invitations.status(invitations.idOfToken(token))).isEqualTo("EXPIRED");
        assertThat(invitations.preview(token))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_EXPIRED");
    }

    @Test
    void aRevokedInvitationIsGone() {
        invitations.revoke(family.admin(), family.familyId(), invitations.idOfToken(token), "\"0\"");

        assertThat(invitations.preview(token))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_REVOKED");
    }

    @Test
    void aUsedInvitationIsGone() {
        assertThat(invitations.acceptLink(TestJwts.newUserToken(), token)).hasStatusOk();

        assertThat(invitations.preview(token))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_USED");
    }
}
