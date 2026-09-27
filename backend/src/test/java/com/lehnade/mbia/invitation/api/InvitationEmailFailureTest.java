package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.MailpitClient;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-51, OQ-055: with the mail provider refusing emails, the invitation still exists with its
 * email FAILED, and a renewal once the provider is back sends the email and sets SENT. The failure
 * is logged without the address, the link or the token.
 */
@ExtendWith(OutputCaptureExtension.class)
class InvitationEmailFailureTest extends ApiTestSupport {

    @Autowired
    MailpitClient mailpit;

    private TestJwts.Token admin;
    private UUID familyId;
    private InvitationFixtures invitations;
    private String address;

    @BeforeEach
    void givenAFamily() {
        admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Ngo");
        invitations = new InvitationFixtures(mvc, jdbc);
        address = "cousin-" + UUID.randomUUID() + "@example.com";
    }

    @AfterEach
    void providerBack() {
        mailpit.refuseEmails(false);
    }

    @Test
    void theInvitationIsKeptWithItsEmailFailedAndARenewalSendsItOnceTheProviderIsBack(CapturedOutput output) {
        mailpit.refuseEmails(true);

        MvcTestResult created = invitations.inviteByEmail(admin, familyId, address, "VIEWER", null, "fr");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.emailDelivery").isEqualTo("FAILED");
        assertThat(created).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        UUID id = InvitationFixtures.idOf(created);
        String firstToken = InvitationFixtures.tokenOf(created);
        assertThat(invitations.row(id)).containsEntry("email_delivery", "FAILED").containsEntry("status", "PENDING");
        assertThat(invitations.list(admin, familyId, "")).bodyJson()
                .extractingPath("$[?(@.id == '" + id + "')].emailDelivery").asArray().containsExactly("FAILED");
        assertThat(mailpit.emailsTo(address)).isEmpty();

        mailpit.refuseEmails(false);
        long version = ((Number) JsonPath.read(FamilyFixtures.body(created), "$.version")).longValue();
        MvcTestResult renewed = invitations.renew(admin, familyId, id, "\"" + version + "\"");

        assertThat(renewed).hasStatus(HttpStatus.OK);
        assertThat(renewed).bodyJson().extractingPath("$.emailDelivery").isEqualTo("SENT");
        assertThat(invitations.row(id)).containsEntry("email_delivery", "SENT");
        String secondToken = InvitationFixtures.tokenOf(renewed);
        assertThat(mailpit.emailsTo(address)).singleElement()
                .satisfies(email -> assertThat(email.text()).contains(InvitationFixtures.LINK_PREFIX + secondToken));
        assertThat(invitations.preview(secondToken)).hasStatus(HttpStatus.OK);

        // Logged, without the address, the link or the token (Phase 5 plan §2.3, §3.5).
        assertThat(output.getAll()).contains("The email of invitation " + id + " could not be sent");
        for (String secret : new String[] {address, firstToken, secondToken}) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }
}
