package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.MailpitClient;
import com.lehnade.mbia.MailpitClient.Email;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-51: invitations by email (openapi {@code inviteFamilyMember}, {@code renewInvitation}; mvp.md
 * §18; OQ-055; technical-specification.md §16bis). The email reaches Mailpit in the inviter's
 * language with the Family, the inviter, the role, a working link and its expiry, never the Person
 * it was sent for; a renewal sends a new email and the previous link stops working.
 */
class InvitationEmailApiTest extends ApiTestSupport {

    private static final Pattern LINK =
            Pattern.compile(Pattern.quote(InvitationFixtures.LINK_PREFIX) + "[A-Za-z0-9_-]+");

    @Autowired
    MailpitClient mailpit;

    private TestJwts.Token admin;
    private UUID familyId;
    private UUID awa;
    private InvitationFixtures invitations;
    private String address;

    @BeforeEach
    void givenAnEnglishSpeakingAdminAndAwa() {
        admin = TestJwts.newUserToken().name("Tony Adji").locale("en");
        familyId = families().createFamily(admin, "ADJI");
        awa = new PersonFixtures(mvc, jdbc)
                .createId(admin, familyId, "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\"}");
        invitations = new InvitationFixtures(mvc, jdbc);
        address = "awa-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void theEmailArrivesInTheInvitersLanguageWithTheFamilyTheInviterTheRoleTheLinkAndTheExpiry() {
        // No locale in the request: the inviter's preferred language, English here.
        MvcTestResult created = invitations.inviteByEmail(admin, familyId, address, "CONTRIBUTOR", awa, null);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.channel").isEqualTo("EMAIL");
        assertThat(created).bodyJson().extractingPath("$.email").isEqualTo(address);
        assertThat(created).bodyJson().extractingPath("$.emailDelivery").isEqualTo("SENT");
        assertThat(created).bodyJson().extractingPath("$.person.displayName").isEqualTo("Awa Ngo");
        UUID id = InvitationFixtures.idOf(created);
        assertThat(invitations.row(id)).containsEntry("email_delivery", "SENT").containsEntry("locale", "en");

        Email email = onlyEmailTo(address);
        assertThat(email.subject()).isEqualTo("Tony Adji invites you to the ADJI family on Mbia");
        assertThat(email.from()).isEqualTo("no-reply@mbia.test");
        assertThat(email.text())
                .contains("Tony Adji invites you to join the ADJI family on Mbia.")
                .contains("Your permission: Can contribute.")
                .contains(InvitationFixtures.LINK_PREFIX + InvitationFixtures.tokenOf(created))
                .contains("This link works only once and expires on " + expiry(created, Locale.ENGLISH) + ".");
        assertThat(email.html())
                .contains("href=\"" + InvitationFixtures.LINK_PREFIX + InvitationFixtures.tokenOf(created) + "\"")
                .contains("Join the family");
        // Never the Person the invitation was sent for (OQ-050).
        assertThat(email.subject() + email.text() + email.html()).doesNotContain("Awa").doesNotContain("Ngo");
    }

    @Test
    void theEmailIsInFrenchWhenTheInviterUsesMbiaInFrench() {
        MvcTestResult created = invitations.inviteByEmail(admin, familyId, address, "VIEWER", null, "fr");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.emailDelivery").isEqualTo("SENT");
        Email email = onlyEmailTo(address);
        assertThat(email.subject()).isEqualTo("Tony Adji vous invite dans la famille ADJI sur Mbia");
        assertThat(email.text())
                .contains("Tony Adji vous invite à rejoindre la famille ADJI sur Mbia.")
                .contains("Votre permission : Lecture seule.")
                .contains(InvitationFixtures.LINK_PREFIX + InvitationFixtures.tokenOf(created))
                .contains("Ce lien ne fonctionne qu'une fois et expire le " + expiry(created, Locale.FRENCH) + ".");
        assertThat(email.html()).contains("Rejoindre la famille").contains("lang=\"fr\"");
    }

    @Test
    void theLinkOfTheEmailOpensTheInvitation() {
        assertThat(invitations.inviteByEmail(admin, familyId, address, "CONTRIBUTOR", awa, "en"))
                .hasStatus(HttpStatus.CREATED);

        MvcTestResult preview = invitations.preview(tokenIn(onlyEmailTo(address)));

        assertThat(preview).hasStatus(HttpStatus.OK);
        assertThat(preview).bodyJson().extractingPath("$.familyName").isEqualTo("ADJI");
        assertThat(preview).bodyJson().extractingPath("$.invitedByDisplayName").isEqualTo("Tony Adji");
    }

    @Test
    void aRenewalSendsANewEmailWhoseLinkWorksAndThePreviousLinkStopsWorking() {
        MvcTestResult created = invitations.inviteByEmail(admin, familyId, address, "CONTRIBUTOR", awa, "fr");
        UUID id = InvitationFixtures.idOf(created);
        String firstToken = tokenIn(onlyEmailTo(address));
        long version = ((Number) JsonPath.read(FamilyFixtures.body(created), "$.version")).longValue();

        MvcTestResult renewed = invitations.renew(admin, familyId, id, "\"" + version + "\"");

        assertThat(renewed).hasStatus(HttpStatus.OK);
        assertThat(renewed).bodyJson().extractingPath("$.emailDelivery").isEqualTo("SENT");
        List<Email> emails = mailpit.emailsTo(address);
        assertThat(emails).hasSize(2);
        // Sent again in the invitation's language.
        assertThat(emails.getFirst().subject()).isEqualTo("Tony Adji vous invite dans la famille ADJI sur Mbia");
        String secondToken = tokenIn(emails.getFirst());
        assertThat(secondToken).isEqualTo(InvitationFixtures.tokenOf(renewed)).isNotEqualTo(firstToken);
        assertThat(invitations.preview(secondToken)).hasStatus(HttpStatus.OK);
        assertThat(invitations.preview(firstToken)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theListShowsWhetherTheEmailWasSent() {
        assertThat(invitations.inviteByEmail(admin, familyId, address, "VIEWER", null, "en"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(invitations.inviteLink(admin, familyId, "VIEWER", null)).hasStatus(HttpStatus.CREATED);

        MvcTestResult list = invitations.list(admin, familyId, "");

        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$[?(@.channel == 'EMAIL')].emailDelivery").asArray()
                .containsExactly("SENT");
        assertThat(list).bodyJson().extractingPath("$[?(@.channel == 'LINK')].emailDelivery").asArray()
                .containsExactly((Object) null);
    }

    @Test
    void aLinkInvitationSendsNothingEvenWithAnEmail() {
        MvcTestResult created = invitations.invite(admin, familyId, """
                {"channel": "LINK", "role": "VIEWER", "email": "%s"}
                """.formatted(address));

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.emailDelivery").isNull();
        assertThat(mailpit.emailsTo(address)).isEmpty();
    }

    @Test
    void anEmailInvitationNeedsAnEmailAddress() {
        for (String body : new String[] {
                "{\"channel\": \"EMAIL\", \"role\": \"VIEWER\"}",
                "{\"channel\": \"EMAIL\", \"role\": \"VIEWER\", \"email\": null}",
                "{\"channel\": \"EMAIL\", \"role\": \"VIEWER\", \"email\": \"  \"}",
                "{\"channel\": \"EMAIL\", \"role\": \"VIEWER\", \"email\": \"not-an-email\"}"}) {
            assertThat(invitations.invite(admin, familyId, body)).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        }
        assertThat(invitations.count(familyId)).isZero();
    }

    private Email onlyEmailTo(String to) {
        List<Email> emails = mailpit.emailsTo(to);
        assertThat(emails).hasSize(1);
        return emails.getFirst();
    }

    private static String tokenIn(Email email) {
        Matcher link = LINK.matcher(email.text());
        assertThat(link.find()).as("invitation link in the email").isTrue();
        return link.group().substring(InvitationFixtures.LINK_PREFIX.length());
    }

    private static String expiry(MvcTestResult result, Locale locale) {
        Instant expiresAt =
                OffsetDateTime.parse(JsonPath.read(FamilyFixtures.body(result), "$.expiresAt")).toInstant();
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
                .format(expiresAt.atZone(ZoneOffset.UTC));
    }
}
