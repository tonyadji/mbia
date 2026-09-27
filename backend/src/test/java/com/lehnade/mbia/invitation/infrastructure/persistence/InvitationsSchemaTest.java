package com.lehnade.mbia.invitation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.PersonFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/** V010: the database is the last guard of data-model.md §8 invariants. */
class InvitationsSchemaTest extends ApiTestSupport {

    private UUID familyId;
    private UUID userId;
    private UUID personId;

    @BeforeEach
    void givenAFamilyAndAPerson() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        userId = families().userId(admin);
        personId = new PersonFixtures(mvc, jdbc).createId(admin, familyId, "{\"firstName\": \"Awa\"}");
    }

    @Test
    void aValidLinkInvitationIsAccepted() {
        assertThatCode(() -> insert(familyId, personId)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "role = 'ADMIN'",
            "channel = 'SMS'",
            "status = 'USED'",
            "locale = 'de'",
            "channel = 'EMAIL', email_delivery = 'PENDING'",
            "channel = 'EMAIL', email = 'a@example.com'",
            "email_delivery = 'SENT'",
            "status = 'ACCEPTED'",
            "status = 'REVOKED'",
            "revoked_at = now()"})
    void invariantsAreEnforced(String assignments) {
        UUID id = insert(familyId, null);

        assertThatThrownBy(() -> jdbc.sql("UPDATE family_invitations SET " + assignments + " WHERE id = ?")
                .param(id).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thePersonBelongsToTheInvitationsFamily() {
        UUID otherFamily = families().createFamily(TestJwts.newUserToken(), "Famille Essomba");

        assertThatThrownBy(() -> insert(otherFamily, personId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_invitation_person");
    }

    @Test
    void aPersonHasAtMostOnePendingInvitation() {
        UUID first = insert(familyId, personId);

        assertThatThrownBy(() -> insert(familyId, personId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_invitations_person_pending");

        jdbc.sql("UPDATE family_invitations SET status = 'EXPIRED' WHERE id = ?").param(first).update();
        assertThatCode(() -> insert(familyId, personId)).doesNotThrowAnyException();
    }

    @Test
    void aTokenHashIsUnique() {
        UUID first = insert(familyId, null);
        String hash = jdbc.sql("SELECT token_hash FROM family_invitations WHERE id = ?").param(first)
                .query(String.class).single();
        UUID second = insert(familyId, null);

        assertThatThrownBy(() -> jdbc.sql("UPDATE family_invitations SET token_hash = ? WHERE id = ?")
                .params(hash, second).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insert(UUID family, UUID person) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO family_invitations (id, family_id, channel, locale, role, person_id, token_hash,
                                                invited_by, expires_at, created_at, updated_at)
                VALUES (?, ?, 'LINK', 'fr', 'VIEWER', ?, ?, ?, ?, ?, ?)
                """).params(id, family, person, UUID.randomUUID().toString(), userId, now, now, now).update();
        return id;
    }
}
