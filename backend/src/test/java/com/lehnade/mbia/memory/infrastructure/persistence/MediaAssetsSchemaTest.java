package com.lehnade.mbia.memory.infrastructure.persistence;

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

/**
 * V008: the database is the last guard of data-model.md §13 invariants and of the Person photo
 * foreign key (§10). Only Person photos exist in this iteration (Phase 3 plan §3.3).
 */
class MediaAssetsSchemaTest extends ApiTestSupport {

    private TestJwts.Token admin;
    private UUID familyId;
    private UUID userId;

    @BeforeEach
    void givenAFamily() {
        admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        userId = families().userId(admin);
    }

    @Test
    void aPendingProfilePictureIsAccepted() {
        assertThatCode(() -> insert(familyId, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "purpose = 'MEMORY_PHOTO'",
            "status = 'DELETED'",
            "upload_size_bytes = 0",
            "upload_size_bytes = 15728641",
            "status = 'READY'",
            "status = 'READY', display_storage_key = 'd'"})
    void invariantsAreEnforced(String assignments) {
        UUID id = insert(familyId, UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.sql("UPDATE media_assets SET " + assignments + " WHERE id = ?")
                .param(id).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aReadyAssetHasBothDerivatives() {
        UUID id = insert(familyId, UUID.randomUUID());

        assertThatCode(() -> jdbc.sql("""
                UPDATE media_assets SET status = 'READY', display_storage_key = 'd', thumbnail_storage_key = 't'
                WHERE id = ?
                """).param(id).update()).doesNotThrowAnyException();
    }

    @Test
    void anUploadKeyIsUnique() {
        UUID id = insert(familyId, UUID.randomUUID());
        String key = jdbc.sql("SELECT upload_storage_key FROM media_assets WHERE id = ?").param(id)
                .query(String.class).single();

        assertThatThrownBy(() -> jdbc.sql("UPDATE media_assets SET upload_storage_key = ? WHERE id = ?")
                .param(key).param(insert(familyId, UUID.randomUUID())).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPersonPhotoBelongsToThePersonsFamily() {
        UUID person = new PersonFixtures(mvc, jdbc).createId(admin, familyId, "{\"firstName\": \"Awa\"}");
        UUID otherFamily = families().createFamily(admin, "Autre famille");
        UUID ownPhoto = insert(familyId, UUID.randomUUID());
        UUID otherFamilyPhoto = insert(otherFamily, UUID.randomUUID());

        assertThatCode(() -> setPhoto(person, ownPhoto)).doesNotThrowAnyException();
        assertThatThrownBy(() -> setPhoto(person, otherFamilyPhoto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_person_profile_media");
        assertThatThrownBy(() -> setPhoto(person, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_person_profile_media");
    }

    private void setPhoto(UUID person, UUID mediaAssetId) {
        jdbc.sql("UPDATE persons SET profile_media_asset_id = ? WHERE id = ?").param(mediaAssetId).param(person)
                .update();
    }

    private UUID insert(UUID family, UUID id) {
        jdbc.sql("""
                INSERT INTO media_assets (id, family_id, purpose, upload_storage_key, upload_mime_type,
                                          upload_size_bytes, uploaded_by, created_at)
                VALUES (?, ?, 'PROFILE_PICTURE', ?, 'image/jpeg', 1000, ?, ?)
                """)
                .param(id).param(family).param("families/" + family + "/media/" + id + "/upload").param(userId)
                .param(Timestamp.from(Instant.now()))
                .update();
        return id;
    }
}
