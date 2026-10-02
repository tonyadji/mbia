package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-42: a Memory published with 3 photos, then the limit lowered to 2 (Phase 4 plan §3.5,
 * mvp.md §17, data-model.md §14bis). It keeps its photos and may still change, and lose some, but
 * gains none until it is below the limit.
 */
@TestPropertySource(properties = "MBIA_MEMORY_MAX_PHOTOS=2")
class UpdateMemoryPhotosLoweredLimitApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private UUID admin;
    private UUID memory;
    private List<UUID> photos;

    @BeforeEach
    void givenAMemoryAboveTheLimit() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        admin = families().userId(family.admin());
        UUID mother = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\"}");
        memory = memories.insertStory(family.familyId(), admin, "Le marché", Instant.now(), mother);
        photos = IntStream.rangeClosed(1, 3).mapToObj(position -> {
            UUID id = ready();
            memories.insertPhoto(family.familyId(), memory, id, position);
            return id;
        }).toList();
    }

    @Test
    void itKeepsItsPhotos() {
        assertThat(memories.get(family.admin(), family.familyId(), memory))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos.length()").isEqualTo(3);
    }

    @Test
    void itsTitleAndACaptionMayChange() {
        assertThat(update("\"0\"", "{\"title\": \"Autre titre\"}")).hasStatusOk();
        assertThat(update("\"1\"", """
                {"photos": [{"mediaAssetId": "%s", "caption": "Mamie"}, {"mediaAssetId": "%s"}, {"mediaAssetId": "%s"}]}
                """.formatted(photos.get(0), photos.get(1), photos.get(2))))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos[0].caption").isEqualTo("Mamie");
        assertThat(memories.photos(memory)).containsExactlyElementsOf(photos);
    }

    @Test
    void aPhotoMayBeRemoved() {
        assertThat(update("\"0\"", photos(photos.get(0), photos.get(2)))).hasStatusOk();
        assertThat(memories.photos(memory)).containsExactly(photos.get(0), photos.get(2));
    }

    @Test
    void noPhotoMayBeAddedUntilItIsBelowTheLimit() {
        assertLimitReached(update("\"0\"", photos(photos.get(0), photos.get(1), photos.get(2), ready())));
        // Removing one while adding one still leaves more than the limit after an addition.
        assertLimitReached(update("\"0\"", photos(photos.get(0), photos.get(1), ready())));
        assertThat(memories.photos(memory)).containsExactlyElementsOf(photos);

        assertThat(update("\"0\"", photos(photos.get(0)))).hasStatusOk();
        UUID added = ready();
        assertThat(update("\"1\"", photos(photos.get(0), added))).hasStatusOk();
        assertThat(memories.photos(memory)).containsExactly(photos.get(0), added);
    }

    private MvcTestResult update(String ifMatch, String json) {
        return memories.update(family.admin(), family.familyId(), memory, ifMatch, json);
    }

    private UUID ready() {
        return MediaFixtures.insertRow(jdbc, family.familyId(), admin, "MEMORY_PHOTO", "READY");
    }

    private static String photos(UUID... ids) {
        return "{\"photos\": [" + Arrays.stream(ids).map(MemoryFixtures::photo).collect(Collectors.joining(", "))
                + "]}";
    }

    private static void assertLimitReached(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_PHOTO_LIMIT_REACHED");
    }
}
