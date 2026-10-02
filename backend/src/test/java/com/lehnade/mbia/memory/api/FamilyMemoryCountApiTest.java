package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** PR-29: {@code FamilyStats.memoryCount} counts the ACTIVE Memories of the Family only. */
class FamilyMemoryCountApiTest extends ApiTestSupport {

    @Test
    void theFamilyAndTheFamilyListCountActiveMemoriesOnly() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        UUID person = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\"}");
        memories.createStoryId(family.admin(), family.familyId(), person);
        memories.createStoryId(family.contributor(), family.familyId(), person);
        memories.archive(memories.createStoryId(family.admin(), family.familyId(), person));
        UUID emptyFamily = families().createFamily(family.viewer(), "Famille vide");

        assertThat(mvc.get().uri("/api/v1/families/{familyId}", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.viewer().bearer()).exchange())
                .hasStatusOk()
                .bodyJson().extractingPath("$.stats.memoryCount").isEqualTo(2);
        assertThat(mvc.get().uri("/api/v1/families").header(HttpHeaders.AUTHORIZATION, family.viewer().bearer())
                .exchange())
                .hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$[?(@.id == '" + family.familyId() + "')].stats.memoryCount")
                            .asArray().containsExactly(2);
                    json.assertThat().extractingPath("$[?(@.id == '" + emptyFamily + "')].stats.memoryCount")
                            .asArray().containsExactly(0);
                });
    }
}
