package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-42 (phase-4-memory-photos.md): the photos of a Memory are added, described and removed with
 * {@code updateMemory} (mvp.md §17; data-model.md §13, §14bis; openapi {@code updateMemory};
 * OQ-008, OQ-036, OQ-041, OQ-042). {@code photos} is the complete new list: kept photos keep their
 * position, new ones follow, missing ones are removed and their asset becomes ARCHIVED. The limit is
 * the default, 3 (Phase 4 plan §3.5).
 */
class UpdateMemoryPhotosApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private MediaFixtures media;
    private PersonFixtures persons;
    private UUID mother;
    private UUID admin;
    private UUID contributor;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
        persons = new PersonFixtures(mvc, jdbc);
        mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        admin = families().userId(family.admin());
        contributor = families().userId(family.contributor());
    }

    // --- Adding and the limit (§3.5) ---

    @Test
    void photosAreAddedUpToTheLimitAndOneMoreIsRefused() {
        UUID first = ready(admin);
        UUID memory = storyWith(family.admin(), "Texte", first);
        UUID second = ready(admin);
        UUID third = ready(admin);

        assertThat(update(family.admin(), memory, "\"0\"", photos(first, second, third)))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.photos.length()").isEqualTo(3);
                    json.assertThat().extractingPath("$.photos[2].mediaAssetId").isEqualTo(third.toString());
                    json.assertThat().extractingPath("$.photos[2].url").asString().startsWith("http");
                });
        assertThat(memories.photos(memory)).containsExactly(first, second, third);

        UUID fourth = ready(admin);
        assertThat(update(family.admin(), memory, "\"1\"", photos(first, second, third, fourth)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_PHOTO_LIMIT_REACHED");
        assertThat(memories.photos(memory)).containsExactly(first, second, third);
        assertThat(memories.row(memory)).containsEntry("version", 1L);
        assertThat(media.status(fourth)).isEqualTo("READY");
    }

    @Test
    void absentPhotosAreUnchanged() {
        UUID first = ready(admin);
        UUID memory = storyWith(family.admin(), "Texte", first);

        assertThat(update(family.admin(), memory, "\"0\"", "{\"title\": \"Autre\", \"photos\": null}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos[0].mediaAssetId").isEqualTo(first.toString());
        assertThat(memories.photos(memory)).containsExactly(first);
    }

    @Test
    void theSameListChangesNothingAndKeepsTheVersion() {
        UUID first = ready(admin);
        UUID memory = storyWith(family.admin(), "Texte", first);

        assertThat(update(family.admin(), memory, "\"0\"", photos(first)))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"0\"");
    }

    // --- Removing (data-model.md §13, §14bis) ---

    @Test
    void aRemovedPhotoIsArchivedAndCanBeAttachedNowhere() {
        UUID first = ready(admin);
        UUID second = ready(admin);
        UUID memory = storyWith(family.admin(), "Texte", first, second);

        assertThat(update(family.admin(), memory, "\"0\"", photos(first)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos[*].mediaAssetId").asArray().containsExactly(first.toString());
        assertThat(memories.photos(memory)).containsExactly(first);
        assertThat(media.status(second)).isEqualTo("ARCHIVED");
        assertThat(media.status(first)).isEqualTo("READY");

        assertConflict(update(family.admin(), memory, "\"1\"", photos(first, second)), "MEDIA_NOT_READY");
        assertConflict(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(MemoryFixtures.photo(second)), mother), "MEDIA_NOT_READY");
        assertConflict(persons.update(family.admin(), family.familyId(), mother, "\"" + persons.version(mother) + "\"",
                "{\"profileMediaAssetId\": \"" + second + "\"}"), "MEDIA_NOT_READY");
        assertThat(memories.photos(memory)).containsExactly(first);
    }

    // --- Positions (data-model.md §14bis) ---

    @Test
    void keptPhotosKeepTheirPositionWhateverTheOrderAndNewOnesFollowWithoutRenumbering() {
        UUID first = ready(admin);
        UUID second = ready(admin);
        UUID third = ready(admin);
        UUID memory = storyWith(family.admin(), "Texte", first, second, third);
        UUID added = ready(admin);

        assertThat(update(family.admin(), memory, "\"0\"", photos(third, added, first)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos[*].mediaAssetId").asArray()
                .containsExactly(first.toString(), third.toString(), added.toString());
        assertThat(positions(memory)).containsExactly(Map.entry(first, 1), Map.entry(third, 3), Map.entry(added, 4));
        assertThat(memories.get(family.admin(), family.familyId(), memory))
                .bodyJson().extractingPath("$.photos[*].mediaAssetId").asArray()
                .containsExactly(first.toString(), third.toString(), added.toString());
    }

    // --- Caption and taken date ---

    @Test
    void theCaptionAndTakenDateOfAPhotoAreChanged() {
        UUID first = ready(admin);
        UUID second = ready(admin);
        UUID memory = storyWith(family.admin(), "Texte", first, second);

        assertThat(update(family.admin(), memory, "\"0\"", """
                {"photos": [
                  {"mediaAssetId": "%s", "caption": "Au marché", "takenAt": {"precision": "YEAR_ONLY", "year": 1974}},
                  {"mediaAssetId": "%s", "takenAt": {"precision": "EXACT", "date": "1980-05-01"}}]}
                """.formatted(first, second)))
                .hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.photos[0].caption").isEqualTo("Au marché");
                    json.assertThat().extractingPath("$.photos[0].takenAt.year").isEqualTo(1974);
                    json.assertThat().extractingPath("$.photos[1].takenAt.date").isEqualTo("1980-05-01");
                });
        assertThat(positions(memory)).containsExactly(Map.entry(first, 1), Map.entry(second, 2));

        assertThat(update(family.admin(), memory, "\"1\"", photos(first, second)))
                .hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.photos[0].caption").isNull();
                    json.assertThat().extractingPath("$.photos[0].takenAt.precision").isEqualTo("UNKNOWN");
                });
    }

    // --- Text or photo (mvp.md §17) ---

    @Test
    void theTextOfAMemoryWithoutPhotoCannotBeEmptied() {
        UUID memory = memories.createStoryId(family.admin(), family.familyId(), mother);

        assertInvalid(update(family.admin(), memory, "\"0\"", "{\"content\": \"\"}"), "content");
        assertThat(memories.row(memory)).containsEntry("content", "Grand-mère vendait du plantain.");
    }

    @Test
    void theLastPhotoOfAMemoryWithoutTextCannotBeRemoved() {
        UUID first = ready(admin);
        UUID memory = storyWith(family.admin(), null, first);

        assertInvalid(update(family.admin(), memory, "\"0\"", "{\"photos\": []}"), "photos");
        assertThat(memories.photos(memory)).containsExactly(first);
        assertThat(media.status(first)).isEqualTo("READY");
    }

    @Test
    void theTextIsEmptiedWhileAPhotoRemainsAndTheLastPhotoRemovedWhileATextRemains() {
        UUID first = ready(admin);
        UUID withPhoto = storyWith(family.admin(), "Texte", first);

        assertThat(update(family.admin(), withPhoto, "\"0\"", "{\"content\": \"  \"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.content").isNull();
        assertThat(memories.row(withPhoto)).containsEntry("content", null);

        UUID second = ready(admin);
        UUID withText = storyWith(family.admin(), "Texte", second);
        assertThat(update(family.admin(), withText, "\"0\"", "{\"photos\": []}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos").asArray().isEmpty();
        assertThat(media.status(second)).isEqualTo("ARCHIVED");
    }

    // --- Concurrency (technical-specification.md §13) ---

    @Test
    void aStaleIfMatchChangesNothing() {
        UUID first = ready(admin);
        UUID memory = storyWith(family.admin(), "Texte", first);
        update(family.admin(), memory, "\"0\"", "{\"title\": \"Première\"}");
        UUID added = ready(admin);

        assertConflict(update(family.admin(), memory, "\"0\"", photos(added)), "CONCURRENT_MODIFICATION");
        assertThat(memories.photos(memory)).containsExactly(first);
        assertThat(media.status(first)).isEqualTo("READY");
        assertThat(media.status(added)).isEqualTo("READY");
        assertThat(memories.row(memory)).containsEntry("version", 1L);
    }

    // --- Rights (OQ-036, OQ-041) ---

    @Test
    void aContributorEditsThePhotosOfTheirOwnMemoryOnly() {
        UUID own = storyWith(family.contributor(), "Texte", ready(contributor));
        UUID others = storyWith(family.admin(), "Texte", ready(admin));
        UUID added = ready(contributor);

        assertThat(update(family.contributor(), own, "\"0\"", photos(added))).hasStatusOk();
        assertThat(memories.photos(own)).containsExactly(added);
        assertThat(update(family.contributor(), others, "\"0\"", photos(ready(contributor))))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void aViewerNeverEditsPhotos() {
        UUID memory = storyWith(family.admin(), "Texte", ready(admin));

        assertThat(update(family.viewer(), memory, "\"0\"", "{\"photos\": []}"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void anAdminKeepsAnotherMembersPhotosAndAddsOnlyTheirOwn() {
        UUID contributorsPhoto = ready(contributor);
        UUID memory = storyWith(family.contributor(), "Texte", contributorsPhoto);

        assertThat(update(family.admin(), memory, "\"0\"", photos(contributorsPhoto, ready(contributor))))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        UUID adminsPhoto = ready(admin);
        assertThat(update(family.admin(), memory, "\"0\"", photos(contributorsPhoto, adminsPhoto))).hasStatusOk();
        assertThat(memories.photos(memory)).containsExactly(contributorsPhoto, adminsPhoto);
    }

    @Test
    void anAssetOfAnotherFamilyIsNotFound() {
        UUID memory = memories.createStoryId(family.admin(), family.familyId(), mother);
        FamilyWithMembers other = families().givenFamilyWithMembersOfEachRole();
        UUID foreign = MediaFixtures.insertRow(jdbc, other.familyId(), admin, "MEMORY_PHOTO", "READY");

        assertThat(update(family.admin(), memory, "\"0\"", photos(foreign)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_NOT_FOUND");
        assertThat(memories.photos(memory)).isEmpty();
    }

    @Test
    void aPhotoOfAnotherMemoryIsAlreadyUsed() {
        UUID taken = ready(admin);
        storyWith(family.admin(), "Texte", taken);
        UUID memory = memories.createStoryId(family.admin(), family.familyId(), mother);

        assertConflict(update(family.admin(), memory, "\"0\"", photos(taken)), "MEDIA_ALREADY_USED");
    }

    private MvcTestResult update(TestJwts.Token token, UUID memory, String ifMatch, String json) {
        return memories.update(token, family.familyId(), memory, ifMatch, json);
    }

    private UUID storyWith(TestJwts.Token token, String content, UUID... photos) {
        MvcTestResult result = memories.createStoryWithPhotos(token, family.familyId(), content,
                Arrays.stream(photos).map(MemoryFixtures::photo).toList(), mother);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return MemoryFixtures.idOf(result);
    }

    private UUID ready(UUID uploadedBy) {
        return MediaFixtures.insertRow(jdbc, family.familyId(), uploadedBy, "MEMORY_PHOTO", "READY");
    }

    private static String photos(UUID... ids) {
        return "{\"photos\": [" + Arrays.stream(ids).map(MemoryFixtures::photo).collect(Collectors.joining(", "))
                + "]}";
    }

    /** The photos of the Memory with their stored position, in position order. */
    private List<Map.Entry<UUID, Integer>> positions(UUID memory) {
        return jdbc.sql("SELECT media_asset_id, position FROM memory_photos WHERE memory_id = ? ORDER BY position")
                .param(memory)
                .query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getInt(2)))
                .list();
    }

    private static void assertConflict(MvcTestResult result, String code) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static void assertInvalid(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo(field);
        });
    }
}
