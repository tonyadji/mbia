package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-57 (Phase 5 plan §2.3, §3.1, §3.7, §6): over the whole collaboration cycle, an invitation by
 * email for a Person, its preview and acceptance, the claim, a story with a captioned photo by the
 * new member, a role change, a removal and a departure, no raw token, token hash, link, email
 * address, story text or caption reaches the logs, the audit or the activity; and the invitation
 * token exists in the database only as its SHA-256 hash (data-model.md §8, §16, §17).
 */
@ExtendWith(OutputCaptureExtension.class)
class CollaborationSecretsTest extends ApiTestSupport {

    private static final String STORY = "Sentinelle: elle vendait le plantain au marché Mokolo";
    private static final String CAPTION = "Sentinelle: grand-mère devant son étal";

    @Test
    void noTokenLinkEmailStoryOrCaptionIsLoggedAuditedOrInTheActivity(CapturedOutput output) {
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        MemberFixtures members = new MemberFixtures(mvc, jdbc);
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        String suffix = UUID.randomUUID().toString();
        String adminEmail = "admin-" + suffix + "@example.com";
        String invitedEmail = "invitee-" + suffix + "@example.com";
        String inviteeAccountEmail = "awa-" + suffix + "@example.com";
        String cousinEmail = "cousin-" + suffix + "@example.com";
        TestJwts.Token admin = TestJwts.newUserToken().email(adminEmail).name("Tony Mbida");
        TestJwts.Token invitee = TestJwts.newUserToken().email(inviteeAccountEmail).name("Awa Ngo");
        TestJwts.Token cousin = TestJwts.newUserToken().email(cousinEmail).name("Paul Ngo");
        UUID familyId = families().createFamily(admin, "Famille Mbida");
        UUID awa = persons.createId(admin, familyId, "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\"}");

        // Invited by email for her Person, Awa previews, joins and links herself.
        MvcTestResult created = invitations.inviteByEmail(admin, familyId, invitedEmail, "CONTRIBUTOR", awa, "fr");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        UUID invitationId = InvitationFixtures.idOf(created);
        MvcTestResult renewed = invitations.renew(admin, familyId, invitationId, "\"1\"");
        assertThat(renewed).hasStatusOk();
        String firstToken = InvitationFixtures.tokenOf(created);
        String token = InvitationFixtures.tokenOf(renewed);
        assertThat(invitations.preview(token)).hasStatusOk();
        assertThat(invitations.acceptLink(invitee, token)).hasStatusOk();
        assertThat(persons.claim(invitee, familyId, awa, "\"0\"")).hasStatus2xxSuccessful();

        // She writes a story with a captioned photo about her mother.
        UUID marie = persons.createId(invitee, familyId, "{\"firstName\": \"Marie\"}");
        UUID photo = MediaFixtures.insertRow(jdbc, familyId, families().userId(invitee), "MEMORY_PHOTO", "READY");
        assertThat(memories.createStoryWithPhotos(invitee, familyId, STORY,
                List.of("{\"mediaAssetId\": \"" + photo + "\", \"caption\": \"" + CAPTION + "\"}"), marie))
                .hasStatus(HttpStatus.CREATED);

        // A cousin joins by a link; the ADMIN changes Awa's role and removes the cousin; Awa leaves.
        assertThat(invitations.acceptLink(cousin, invitations.linkToken(admin, familyId, "VIEWER", null)))
                .hasStatusOk();
        UUID awaMembership = members.membershipId(familyId, families().userId(invitee));
        UUID cousinMembership = members.membershipId(familyId, families().userId(cousin));
        assertThat(members.changeRole(admin, familyId, awaMembership, "\"0\"", "VIEWER")).hasStatusOk();
        assertThat(members.remove(admin, familyId, cousinMembership, "\"0\"")).hasStatus2xxSuccessful();
        assertThat(members.remove(invitee, familyId, awaMembership, "\"1\"")).hasStatus2xxSuccessful();
        // Rejected requests log their path: still no secret in it.
        invitations.preview(token);
        invitations.acceptLink(cousin, token);

        List<String> secrets = List.of(firstToken, token, InvitationFixtures.sha256Hex(firstToken),
                InvitationFixtures.sha256Hex(token), adminEmail, invitedEmail, inviteeAccountEmail, cousinEmail,
                STORY, CAPTION);
        assertThat(output.getAll()).contains("/api/v1/invitations/***");
        for (String secret : secrets) {
            assertThat(output.getAll()).as("logs").doesNotContain(secret);
        }
        String audit = String.join("\n", jdbc.sql("""
                SELECT action || ' ' || coalesce(old_value::text, '') || ' ' || coalesce(new_value::text, '')
                FROM audit_entries WHERE family_id = ?
                """).param(familyId).query(String.class).list());
        assertThat(audit).contains("INVITATION_ACCEPTED", "PERSON_CLAIMED", "MEMBERSHIP_ROLE_CHANGED",
                "MEMBERSHIP_REMOVED", "MEMBERSHIP_LEFT");
        String activity = String.join("\n", jdbc.sql("""
                SELECT activity_type || ' ' || coalesce(payload::text, '') FROM activities WHERE family_id = ?
                """).param(familyId).query(String.class).list());
        assertThat(activity).contains("INVITATION_ACCEPTED", "MEMORY_CREATED", "MEMBER_REMOVED", "MEMBER_LEFT");
        for (String secret : secrets) {
            assertThat(audit).as("audit").doesNotContain(secret);
            assertThat(activity).as("activity").doesNotContain(secret);
        }
        assertThat(audit + activity).doesNotContain("http").doesNotContain("@");
    }

    @Test
    void anEmailInvitationTokenIsStoredOnlyAsItsHash() {
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        TestJwts.Token admin = TestJwts.newUserToken();
        UUID familyId = families().createFamily(admin, "Famille Mbida");

        MvcTestResult created = invitations.inviteByEmail(admin, familyId,
                "hash-" + UUID.randomUUID() + "@example.com", "VIEWER", null, "en");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        String token = InvitationFixtures.tokenOf(created);
        assertThat(invitations.row(InvitationFixtures.idOf(created)))
                .containsEntry("token_hash", InvitationFixtures.sha256Hex(token));
        assertThat(tablesHolding(token)).isEmpty();
    }

    /** Every text column of every table of the schema that holds this value, as {@code table.column}. */
    private List<String> tablesHolding(String value) {
        List<Map<String, Object>> columns = jdbc.sql("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND data_type IN ('text', 'character varying', 'character', 'json', 'jsonb')
                """).query().listOfRows();
        return columns.stream()
                .filter(column -> jdbc.sql("SELECT count(*) FROM \"%s\" WHERE strpos(\"%s\"::text, ?) > 0"
                                .formatted(column.get("table_name"), column.get("column_name")))
                        .param(value).query(Long.class).single() > 0)
                .map(column -> column.get("table_name") + "." + column.get("column_name"))
                .toList();
    }
}
