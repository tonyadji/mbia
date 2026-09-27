package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-47, Phase 5 plan §2.3, §3.1: the raw token and the link are never logged nor audited, and the
 * audit holds neither the token hash nor the email (data-model.md §8, §17). Creation, renewal and
 * revocation are audited.
 */
@ExtendWith(OutputCaptureExtension.class)
class InvitationSecretsTest extends ApiTestSupport {

    private static final String EMAIL = "cousin-sentinelle@example.com";

    @Test
    void neitherTokenNorLinkNorEmailIsLoggedOrAudited(CapturedOutput output) {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        UUID awa = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");

        MvcTestResult created = invitations.invite(family.admin(), family.familyId(), """
                {"channel": "LINK", "role": "VIEWER", "personId": "%s", "email": "%s"}
                """.formatted(awa, EMAIL));
        UUID id = InvitationFixtures.idOf(created);
        MvcTestResult renewed = invitations.renew(family.admin(), family.familyId(), id, "\"0\"");
        // A request rejected after the creation logs its path: still no secret in it.
        invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", awa);
        invitations.revoke(family.admin(), family.familyId(), id, "\"1\"");

        List<String> secrets = List.of(InvitationFixtures.tokenOf(created), InvitationFixtures.tokenOf(renewed),
                InvitationFixtures.sha256Hex(InvitationFixtures.tokenOf(created)),
                InvitationFixtures.sha256Hex(InvitationFixtures.tokenOf(renewed)));
        for (String secret : secrets) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
        assertThat(output.getAll()).doesNotContain("/invitations/" + InvitationFixtures.tokenOf(created));

        List<Map<String, Object>> audit = jdbc.sql("""
                SELECT action, actor_user_id, resource_id, old_value::text AS old_value, new_value::text AS new_value
                FROM audit_entries WHERE family_id = ? AND resource_type = 'INVITATION' ORDER BY occurred_at
                """).param(family.familyId()).query().listOfRows();
        assertThat(audit).extracting(row -> row.get("action"))
                .containsExactly("INVITATION_CREATED", "INVITATION_RENEWED", "INVITATION_REVOKED");
        assertThat(audit).allSatisfy(row -> {
            assertThat(row).containsEntry("resource_id", id)
                    .containsEntry("actor_user_id", families().userId(family.admin()));
            String values = row.get("old_value") + " " + row.get("new_value");
            secrets.forEach(secret -> assertThat(values).doesNotContain(secret));
            assertThat(values).doesNotContain(EMAIL).doesNotContain("http").doesNotContain("token");
        });
        assertThat(audit.getFirst().get("new_value").toString())
                .contains("\"role\": \"VIEWER\"").contains("\"channel\": \"LINK\"").contains(awa.toString());
        assertThat(audit.getLast().get("new_value").toString()).contains("\"status\": \"REVOKED\"");
    }
}
