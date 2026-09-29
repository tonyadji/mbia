package com.lehnade.mbia.memory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V009: the database is the last guard of the photos of a Memory (data-model.md §14, §14bis; Phase
 * 4 plan §3.3). The rules that span several rows or depend on the settings are the application's.
 */
class MemoryPhotosSchemaTest extends ApiTestSupport {

    private TestJwts.Token admin;
    private UUID familyId;
    private UUID userId;
    private UUID memoryId;
    private MemoryFixtures memories;

    @BeforeEach
    void givenAMemory() {
        admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        userId = families().userId(admin);
        memories = new MemoryFixtures(mvc, jdbc);
        UUID person = new PersonFixtures(mvc, jdbc).createId(admin, familyId, "{\"firstName\": \"Awa\"}");
        memoryId = memories.createStoryId(admin, familyId, person);
    }

    @Test
    void aMemoryHasPhotosInPositions() {
        UUID first = photo(familyId);
        UUID second = photo(familyId);

        memories.insertPhoto(familyId, memoryId, first, 1);
        memories.insertPhoto(familyId, memoryId, second, 3);

        assertThat(jdbc.sql("SELECT media_asset_id FROM memory_photos WHERE memory_id = ? ORDER BY position")
                .param(memoryId).query(UUID.class).list()).containsExactly(first, second);
    }

    @Test
    void anAssetOfAnotherFamilyIsRefused() {
        UUID otherFamily = families().createFamily(admin, "Autre famille");

        assertThatThrownBy(() -> memories.insertPhoto(familyId, memoryId, photo(otherFamily), 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_memory_photo_media");
        assertThatThrownBy(() -> memories.insertPhoto(familyId, memoryId, UUID.randomUUID(), 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_memory_photo_media");
    }

    @Test
    void aMemoryOfAnotherFamilyIsRefused() {
        UUID otherFamily = families().createFamily(admin, "Autre famille");

        assertThatThrownBy(() -> memories.insertPhoto(otherFamily, memoryId, photo(otherFamily), 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_memory_photo_memory");
    }

    @Test
    void theSameAssetIsThePhotoOfOneMemoryOnly() {
        UUID asset = photo(familyId);
        memories.insertPhoto(familyId, memoryId, asset, 1);
        UUID otherMemory = memories.insertStory(familyId, userId, "Autre", Instant.now());

        assertThatThrownBy(() -> memories.insertPhoto(familyId, memoryId, asset, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> memories.insertPhoto(familyId, otherMemory, asset, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_memory_photo_asset");
    }

    @Test
    void twoPhotosCannotShareAPosition() {
        memories.insertPhoto(familyId, memoryId, photo(familyId), 1);

        assertThatThrownBy(() -> memories.insertPhoto(familyId, memoryId, photo(familyId), 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_memory_photo_position");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "caption = repeat('a', 5001)",
            "taken_date_precision = 'MONTH'",
            "taken_date_precision = 'EXACT'",
            "taken_date_precision = 'EXACT', taken_date = DATE '1972-05-01', taken_year = 1972",
            "taken_date_precision = 'YEAR_ONLY'",
            "taken_date_precision = 'YEAR_ONLY', taken_year = 1972, taken_date = DATE '1972-05-01'",
            "taken_date = DATE '1972-05-01'",
            "taken_year = 1972"})
    void photoInvariantsAreEnforced(String assignments) {
        UUID asset = photo(familyId);
        memories.insertPhoto(familyId, memoryId, asset, 1);

        assertThatThrownBy(() -> jdbc.sql("UPDATE memory_photos SET " + assignments + " WHERE media_asset_id = ?")
                .param(asset).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "caption = repeat('a', 5000)",
            "taken_date_precision = 'EXACT', taken_date = DATE '1972-05-01'",
            "taken_date_precision = 'YEAR_ONLY', taken_year = 1972"})
    void aCaptionAndAPartialTakenDateAreAccepted(String assignments) {
        UUID asset = photo(familyId);
        memories.insertPhoto(familyId, memoryId, asset, 1);

        assertThatCode(() -> jdbc.sql("UPDATE memory_photos SET " + assignments + " WHERE media_asset_id = ?")
                .param(asset).update()).doesNotThrowAnyException();
    }

    @Test
    void aMemoryWithoutTextIsAcceptedByTheDatabase() {
        assertThatCode(() -> jdbc.sql("UPDATE memories SET content = NULL WHERE id = ?").param(memoryId).update())
                .doesNotThrowAnyException();
    }

    @Test
    void aMemoryStillNeedsATitle() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE memories SET title = NULL WHERE id = ?").param(memoryId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_memory_story_title");
    }

    @ParameterizedTest
    @ValueSource(strings = {"type = 'PHOTO'", "status = 'DELETED'"})
    void memoryInvariantsAreStillEnforced(String assignments) {
        assertThatThrownBy(() -> jdbc.sql("UPDATE memories SET " + assignments + " WHERE id = ?").param(memoryId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theChecksOfMemoriesAndMediaAssetsAreNamed() {
        assertThat(jdbc.sql("""
                SELECT conname FROM pg_constraint
                WHERE contype = 'c' AND conrelid IN ('memories'::regclass, 'media_assets'::regclass)
                """).query(String.class).list())
                .containsExactlyInAnyOrder("ck_memory_type", "ck_memory_status", "ck_memory_story_title",
                        "ck_memory_happened_date_precision", "ck_memory_happened_date", "ck_media_asset_purpose",
                        "ck_media_asset_status", "ck_media_asset_size", "ck_media_asset_ready_derivatives");
    }

    private UUID photo(UUID family) {
        return MediaFixtures.insertRow(jdbc, family, userId, "MEMORY_PHOTO", "READY");
    }
}
