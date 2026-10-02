package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/**
 * PR-41: the photo limit of a new Memory is the setting {@code MBIA_MEMORY_MAX_PHOTOS} (Phase 4
 * plan §3.5, data-model.md §14bis), here other than the default: 7 photos are published, 8 are
 * refused and nothing is stored.
 */
@TestPropertySource(properties = "MBIA_MEMORY_MAX_PHOTOS=7")
class MemoryPhotoLimitApiTest extends ApiTestSupport {

    @Test
    void theLimitFollowsTheSetting() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        UUID mother = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\"}");
        UUID admin = families().userId(family.admin());
        List<String> photos = IntStream.range(0, 8)
                .mapToObj(i -> MemoryFixtures.photo(MediaFixtures.insertRow(jdbc, family.familyId(), admin,
                        "MEMORY_PHOTO", "READY")))
                .toList();

        assertThat(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte", photos, mother))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_PHOTO_LIMIT_REACHED");
        assertThat(memories.count(family.familyId())).isZero();
        assertThat(memories.countPhotos(family.familyId())).isZero();

        assertThat(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte", photos.subList(0, 7),
                mother))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.photos").asArray().hasSize(7);
    }
}
