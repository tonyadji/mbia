package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-41: the photos of a Memory are returned, in position order, wherever the Memory is
 * ({@code getMemory}, {@code listPersonMemories}, {@code listFamilyMemories}, and the response of
 * {@code updateMemory}), each with its caption, taken date, dimensions and pre-signed URLs
 * (data-model.md §14bis; openapi {@code MemoryResponse.photos}). Neither the captions, the story,
 * a storage key nor a URL reaches the logs (Phase 4 plan §2.3).
 */
@ExtendWith(OutputCaptureExtension.class)
class MemoryPhotosReadApiTest extends ApiTestSupport {

    private static final String STORY = "Histoire-sentinelle-40d1";
    private static final String CAPTION = "Legende-sentinelle-9e7a";

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private UUID mother;
    private UUID admin;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        mother = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\"}");
        admin = families().userId(family.admin());
    }

    @Test
    void everyReadReturnsThePhotosInPositionOrderEvenWithAGap(CapturedOutput output) {
        // Positions 1, 3 and 7: a removal leaves a gap and never renumbers (data-model.md §14bis).
        UUID memory = memories.insertStory(family.familyId(), admin, "Le marché", Instant.now(), mother);
        UUID seventh = photoAt(memory, 7);
        UUID first = photoAt(memory, 1);
        UUID third = photoAt(memory, 3);
        jdbc.sql("""
                UPDATE memory_photos SET caption = ?, taken_year = 1974, taken_date_precision = 'YEAR_ONLY'
                WHERE media_asset_id = ?
                """).params(CAPTION, third).update();
        List<String> expected = List.of(first.toString(), third.toString(), seventh.toString());

        Map<String, MvcTestResult> reads = Map.of(
                "getMemory", memories.get(family.viewer(), family.familyId(), memory),
                "listPersonMemories", memories.listForPerson(family.viewer(), family.familyId(), mother, ""),
                "listFamilyMemories", memories.listForFamily(family.viewer(), family.familyId(), ""),
                "updateMemory", memories.update(family.admin(), family.familyId(), memory, "\"0\"",
                        "{\"title\": \"Au marché\"}"));

        reads.forEach((operation, result) -> {
            assertThat(result).as(operation).hasStatusOk();
            String body = FamilyFixtures.body(result);
            String photos = operation.startsWith("list") ? "$.items[0].photos" : "$.photos";
            assertThat(JsonPath.<List<String>>read(body, photos + "[*].mediaAssetId")).as(operation)
                    .containsExactlyElementsOf(expected);
            assertThat(JsonPath.<String>read(body, photos + "[1].caption")).isEqualTo(CAPTION);
            assertThat(JsonPath.<String>read(body, photos + "[1].takenAt.precision")).isEqualTo("YEAR_ONLY");
            assertThat(JsonPath.<Integer>read(body, photos + "[1].takenAt.year")).isEqualTo(1974);
            assertThat(JsonPath.<String>read(body, photos + "[0].takenAt.precision")).isEqualTo("UNKNOWN");
            assertThat(JsonPath.<Integer>read(body, photos + "[0].widthPx")).isEqualTo(800);
            assertThat(JsonPath.<String>read(body, photos + "[0].url")).contains("/display", "X-Amz-Signature");
            assertThat(JsonPath.<String>read(body, photos + "[0].thumbnailUrl"))
                    .contains("/thumbnail", "X-Amz-Signature");
        });
        assertThat(output.getAll()).doesNotContain(CAPTION, "X-Amz-",
                MediaFixtures.key(family.familyId(), first, ""));
    }

    @Test
    void aStoryWithoutPhotoHasAnEmptyList() {
        UUID memory = memories.createStoryId(family.admin(), family.familyId(), mother);

        assertThat(memories.get(family.viewer(), family.familyId(), memory))
                .hasStatusOk().bodyJson().extractingPath("$.photos").asArray().isEmpty();
    }

    @Test
    void publishingAndReadingAStoryWithPhotosLogsNeitherTextsNorKeysNorUrls(CapturedOutput output) {
        UUID first = MediaFixtures.insertRow(jdbc, family.familyId(), admin, "MEMORY_PHOTO", "READY");
        UUID second = MediaFixtures.insertRow(jdbc, family.familyId(), admin, "MEMORY_PHOTO", "READY");
        List<String> photos = List.of(
                "{\"mediaAssetId\": \"%s\", \"caption\": \"%s\"}".formatted(first, CAPTION),
                "{\"mediaAssetId\": \"%s\", \"caption\": \"%s\"}".formatted(second, CAPTION));

        MvcTestResult created = memories.createStoryWithPhotos(family.admin(), family.familyId(), STORY, photos,
                mother);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        UUID memory = MemoryFixtures.idOf(created);
        // Refusals too: the same assets again, and more than the limit.
        assertThat(memories.createStoryWithPhotos(family.admin(), family.familyId(), STORY, photos, mother))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(memories.createStoryWithPhotos(family.admin(), family.familyId(), STORY,
                List.of(photos.get(0), photos.get(1), MemoryFixtures.photo(UUID.randomUUID()),
                        MemoryFixtures.photo(UUID.randomUUID())), mother))
                .hasStatus(HttpStatus.CONFLICT);
        memories.get(family.viewer(), family.familyId(), memory);
        memories.listForPerson(family.viewer(), family.familyId(), mother, "");
        memories.listForFamily(family.viewer(), family.familyId(), "");

        assertThat(output.getAll()).doesNotContain(CAPTION, STORY, "X-Amz-",
                MediaFixtures.key(family.familyId(), first, ""), MediaFixtures.key(family.familyId(), second, ""));
    }

    private UUID photoAt(UUID memory, int position) {
        UUID id = MediaFixtures.insertRow(jdbc, family.familyId(), admin, "MEMORY_PHOTO", "READY");
        memories.insertPhoto(family.familyId(), memory, id, position);
        return id;
    }
}
