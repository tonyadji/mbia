package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-29: photo Memories are not created in this iteration (Phase 3 plan §3.1, OQ-042): the
 * operation answers like a route that does not exist yet. Update and archive exist since PR-33.
 */
class MemoryOperationsNotAvailableApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private UUID person;

    @BeforeEach
    void givenAStory() {
        family = families().givenFamilyWithMembersOfEachRole();
        person = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        new MemoryFixtures(mvc, jdbc).createStoryId(family.admin(), family.familyId(), person);
    }

    @Test
    void createPhotoMemoryAnswers404AndCreatesNothing() {
        assertNotAvailable(mvc.post().uri("/api/v1/families/{familyId}/memories/photos", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mediaAssetId": "%s", "relatedPersonIds": ["%s"]}
                        """.formatted(UUID.randomUUID(), person))
                .exchange());
        assertThat(new MemoryFixtures(mvc, jdbc).count(family.familyId())).isEqualTo(1);
    }

    /** Until PR-59 (Phase 6 plan): the family story strip and filters are declared, not delivered. */
    @Test
    void theFamilyStoryAnswers404UntilItIsDelivered() {
        assertNotAvailable(mvc.get().uri("/api/v1/families/{familyId}/story/years", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer())
                .exchange());
        for (String filter : List.of("year=1975", "undated=true")) {
            assertNotAvailable(mvc.get().uri("/api/v1/families/{familyId}/memories?" + filter, family.familyId())
                    .header(HttpHeaders.AUTHORIZATION, family.admin().bearer())
                    .exchange());
        }
    }

    /** Until PR-58 (Phase 6 plan): every Memory reads as undated, and a known date is refused, never lost. */
    @Test
    void aMemoryIsUndatedAndAKnownDateIsRefusedUntilItIsStored() {
        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        assertThat(memories.createStory(family.admin(), family.familyId(), """
                {"title": "Le marché", "content": "Texte", "relatedPersonIds": ["%s"],
                 "happenedAt": {"precision": "UNKNOWN"}}
                """.formatted(person)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.happenedAt.precision").isEqualTo("UNKNOWN");
        MvcTestResult dated = memories.createStory(family.admin(), family.familyId(), """
                {"title": "Le marché", "content": "Texte", "relatedPersonIds": ["%s"],
                 "happenedAt": {"precision": "YEAR_ONLY", "year": 1975}}
                """.formatted(person));
        assertThat(dated).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("happenedAt");
        assertThat(memories.count(family.familyId())).isEqualTo(2);
    }

    private static void assertNotAvailable(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
    }
}
