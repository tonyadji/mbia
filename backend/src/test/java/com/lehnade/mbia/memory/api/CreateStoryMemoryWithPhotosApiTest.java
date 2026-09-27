package com.lehnade.mbia.memory.api;

import static com.lehnade.mbia.memory.MemoryFixtures.photo;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-41 (phase-4-memory-photos.md): a story is published with its photos (openapi
 * {@code createStoryMemory}; mvp.md §17; data-model.md §13, §14, §14bis; technical-specification.md
 * §12; OQ-036, OQ-042). The limit is the default, 3 (Phase 4 plan §3.5). Every refusal stores
 * nothing and leaves the assets READY and unattached.
 */
class CreateStoryMemoryWithPhotosApiTest extends ApiTestSupport {

    private static final int LIMIT = 3;

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private MediaFixtures media;
    private UUID mother;
    private UUID admin;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
        mother = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\"}");
        admin = families().userId(family.admin());
    }

    // --- Creation ---

    @Test
    void storiesWithZeroOneAndNPhotosAreCreatedWithThePhotosInTheirOrder() {
        for (int count = 0; count <= LIMIT; count++) {
            List<UUID> ids = readyRows(admin, count);

            MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                    ids.stream().map(MemoryFixtures::photo).toList(), mother);

            assertThat(result).as("%d photos", count).hasStatus(HttpStatus.CREATED);
            assertThat(JsonPath.<List<String>>read(FamilyFixtures.body(result), "$.photos[*].mediaAssetId"))
                    .containsExactlyElementsOf(ids.stream().map(UUID::toString).toList());
            assertThat(memories.photos(MemoryFixtures.idOf(result))).containsExactlyElementsOf(ids);
            assertThat(ids).allSatisfy(id -> assertThat(media.status(id)).isEqualTo("READY"));
        }
    }

    @Test
    void eachPhotoKeepsItsCaptionAndTakenDate() {
        List<UUID> ids = readyRows(admin, 3);

        MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte", List.of(
                "{\"mediaAssetId\": \"%s\", \"caption\": \"Au marché\\nde Mvog-Mbi\", \"takenAt\": {\"precision\": \"EXACT\", \"date\": \"1974-08-15\"}}"
                        .formatted(ids.get(0)),
                "{\"mediaAssetId\": \"%s\", \"caption\": \"  \", \"takenAt\": {\"precision\": \"YEAR_ONLY\", \"year\": 1980}}"
                        .formatted(ids.get(1)),
                "{\"mediaAssetId\": \"%s\", \"caption\": null, \"takenAt\": null}".formatted(ids.get(2))), mother);

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.photos[0].caption").isEqualTo("Au marché\nde Mvog-Mbi");
            json.assertThat().extractingPath("$.photos[0].takenAt.precision").isEqualTo("EXACT");
            json.assertThat().extractingPath("$.photos[0].takenAt.date").isEqualTo("1974-08-15");
            json.assertThat().extractingPath("$.photos[0].takenAt.year").isNull();
            json.assertThat().extractingPath("$.photos[1].caption").isNull();
            json.assertThat().extractingPath("$.photos[1].takenAt.precision").isEqualTo("YEAR_ONLY");
            json.assertThat().extractingPath("$.photos[1].takenAt.year").isEqualTo(1980);
            json.assertThat().extractingPath("$.photos[2].caption").isNull();
            json.assertThat().extractingPath("$.photos[2].takenAt.precision").isEqualTo("UNKNOWN");
            json.assertThat().extractingPath("$.photos[0].widthPx").isEqualTo(800);
            json.assertThat().extractingPath("$.photos[0].heightPx").isEqualTo(600);
            json.assertThat().extractingPath("$.photos[0].url").asString().contains("X-Amz-Signature");
            json.assertThat().extractingPath("$.photos[0].thumbnailUrl").asString().contains("X-Amz-Signature");
        });
        Map<String, Object> row = jdbc.sql("""
                SELECT position::int AS position, caption, taken_date::text AS taken_date, taken_year, taken_date_precision
                FROM memory_photos WHERE media_asset_id = ?
                """).param(ids.get(0)).query().singleRow();
        assertThat(row).containsEntry("position", 1).containsEntry("taken_date", "1974-08-15")
                .containsEntry("taken_year", null).containsEntry("taken_date_precision", "EXACT");
    }

    @Test
    void theTextIsOptionalWithAPhoto() {
        UUID id = readyRows(admin, 1).getFirst();

        MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), null,
                List.of(photo(id)), mother);

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.content").isNull();
        assertThat(memories.row(MemoryFixtures.idOf(result))).containsEntry("content", null);
    }

    @Test
    void withoutTextAndWithoutPhotoTheStoryIsRefused() {
        assertValidationFailed(memories.createStoryWithPhotos(family.admin(), family.familyId(), null, List.of(),
                mother), "content");
        assertValidationFailed(memories.createStory(family.admin(), family.familyId(), """
                {"title": "Le marché", "relatedPersonIds": ["%s"]}
                """.formatted(mother)), "content");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \""})
    void aBlankTextIsRefusedEvenWithAPhoto(String content) {
        UUID id = readyRows(admin, 1).getFirst();

        assertValidationFailed(memories.createStory(family.admin(), family.familyId(), """
                {"title": "Le marché", "content": %s, "photos": [%s], "relatedPersonIds": ["%s"]}
                """.formatted(content, photo(id), mother)), "content");
        assertUnattached(id);
    }

    @Test
    void theContributorPublishesTheirOwnUploadAndWorkingUrlsAreReturned() {
        UUID first = media.readyPhoto(family.contributor(), family.familyId(), "MEMORY_PHOTO");
        UUID second = media.readyPhoto(family.contributor(), family.familyId(), "MEMORY_PHOTO");

        MvcTestResult result = memories.createStoryWithPhotos(family.contributor(), family.familyId(), "Texte",
                List.of(photo(first), photo(second)), mother);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = FamilyFixtures.body(result);
        List<String> urls = new ArrayList<>(JsonPath.<List<String>>read(body, "$.photos[*].url"));
        urls.addAll(JsonPath.read(body, "$.photos[*].thumbnailUrl"));
        assertThat(urls).hasSize(4);
        for (String url : urls) {
            HttpResponse<byte[]> served = MediaFixtures.get(URI.create(url));
            assertThat(served.statusCode()).as(url).isEqualTo(200);
            assertThat(served.headers().firstValue("Content-Type")).as(url).hasValue("image/jpeg");
        }
    }

    // --- Refusals: nothing stored, assets unattached ---

    @Test
    void morePhotosThanTheLimitAreRefused() {
        List<UUID> ids = readyRows(admin, LIMIT + 1);

        MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                ids.stream().map(MemoryFixtures::photo).toList(), mother);

        assertConflict(result, "MEMORY_PHOTO_LIMIT_REACHED");
        ids.forEach(this::assertUnattached);
    }

    @Test
    void anotherMembersUploadIsForbidden() {
        UUID mine = readyRows(admin, 1).getFirst();
        UUID theirs = MediaFixtures.insertRow(jdbc, family.familyId(), families().userId(family.contributor()),
                "MEMORY_PHOTO", "READY");

        MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(photo(mine), photo(theirs)), mother);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertNothingStored();
        assertUnattached(mine);
        assertUnattached(theirs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_UPLOAD", "FAILED", "ARCHIVED"})
    void anAssetThatIsNotReadyIsRefused(String status) {
        UUID ready = readyRows(admin, 1).getFirst();
        UUID notReady = MediaFixtures.insertRow(jdbc, family.familyId(), admin, "MEMORY_PHOTO", status);

        assertConflict(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(photo(ready), photo(notReady)), mother), "MEDIA_NOT_READY");
        assertUnattached(ready);
        assertThat(media.status(notReady)).isEqualTo(status);
    }

    @Test
    void aPhotoOfAnotherMemoryIsAlreadyUsed() {
        UUID used = readyRows(admin, 1).getFirst();
        UUID other = MemoryFixtures.idOf(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(photo(used)), mother));
        UUID fresh = readyRows(admin, 1).getFirst();

        MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(photo(fresh), photo(used)), mother);

        assertThat(result).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_ALREADY_USED");
        assertThat(memories.count(family.familyId())).isOne();
        assertThat(memories.photos(other)).containsExactly(used);
        assertUnattached(fresh);
    }

    /** Not possible through the API, whose Person photos are PROFILE_PICTUREs: the rule still holds. */
    @Test
    void aPhotoOfAPersonIsAlreadyUsed() {
        UUID used = readyRows(admin, 1).getFirst();
        jdbc.sql("UPDATE persons SET profile_media_asset_id = ? WHERE id = ?").params(used, mother).update();

        assertConflict(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(photo(used)), mother), "MEDIA_ALREADY_USED");
    }

    @Test
    void aProfilePictureIsNotAMemoryPhoto() {
        UUID profile = MediaFixtures.insertRow(jdbc, family.familyId(), admin, "PROFILE_PICTURE", "READY");

        assertValidationFailed(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(photo(profile)), mother), "photos");
        assertUnattached(profile);
    }

    @Test
    void anAssetOfAnotherFamilyOrUnknownIsNotFound() {
        TestJwts.Token stranger = TestJwts.newUserToken();
        UUID otherFamily = families().createFamily(stranger, "Autre famille");
        UUID theirs = MediaFixtures.insertRow(jdbc, otherFamily, families().userId(stranger), "MEMORY_PHOTO",
                "READY");

        for (UUID id : List.of(theirs, UUID.randomUUID())) {
            MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                    List.of(photo(id)), mother);

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_NOT_FOUND");
            assertNothingStored();
        }
        assertThat(media.status(theirs)).isEqualTo("READY");
    }

    @Test
    void theSameAssetTwiceIsRefused() {
        UUID id = readyRows(admin, 1).getFirst();

        assertValidationFailed(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(photo(id), photo(id)), mother), "photos");
        assertUnattached(id);
    }

    @Test
    void anInconsistentTakenDateOrATooLongCaptionIsRefused() {
        UUID id = readyRows(admin, 1).getFirst();

        assertValidationFailed(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte", List.of(
                "{\"mediaAssetId\": \"%s\", \"takenAt\": {\"precision\": \"YEAR_ONLY\"}}".formatted(id)), mother),
                "photos");
        assertThat(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte", List.of(
                "{\"mediaAssetId\": \"%s\", \"caption\": \"%s\"}".formatted(id, "x".repeat(5001))), mother))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertUnattached(id);
    }

    @Test
    void aViewerIsRefusedBeforeThePhotos() {
        UUID viewers = MediaFixtures.insertRow(jdbc, family.familyId(), families().userId(family.viewer()),
                "MEMORY_PHOTO", "READY");

        assertThat(memories.createStoryWithPhotos(family.viewer(), family.familyId(), "Texte",
                List.of(photo(viewers)), mother))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertNothingStored();
    }

    @Test
    void refusedAssetsCanStillBePublishedAfterwards() {
        List<UUID> ids = readyRows(admin, LIMIT + 1);
        memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                ids.stream().map(MemoryFixtures::photo).toList(), mother);

        assertThat(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                ids.subList(0, LIMIT).stream().map(MemoryFixtures::photo).toList(), mother))
                .hasStatus(HttpStatus.CREATED);
    }

    private List<UUID> readyRows(UUID uploadedBy, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(MediaFixtures.insertRow(jdbc, family.familyId(), uploadedBy, "MEMORY_PHOTO", "READY"));
        }
        return ids;
    }

    private void assertConflict(MvcTestResult result, String code) {
        assertThat(result)
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
        assertNothingStored();
    }

    private void assertValidationFailed(MvcTestResult result, String field) {
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo(field);
                });
        assertNothingStored();
    }

    private void assertNothingStored() {
        assertThat(memories.count(family.familyId())).isZero();
        assertThat(memories.countLinks(family.familyId())).isZero();
        assertThat(memories.countPhotos(family.familyId())).isZero();
    }

    private void assertUnattached(UUID mediaAssetId) {
        assertThat(media.status(mediaAssetId)).isEqualTo("READY");
        assertThat(jdbc.sql("SELECT count(*) FROM memory_photos WHERE media_asset_id = ?").param(mediaAssetId)
                .query(Long.class).single()).isZero();
    }
}
