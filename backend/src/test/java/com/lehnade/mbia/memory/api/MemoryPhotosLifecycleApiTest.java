package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-42: the photos belong to the Memory, not to its Persons (Phase 4 plan §3.1, data-model.md
 * §13, §14bis, §19). A merge and a Person archival leave them in place; an archived Memory keeps
 * them attached and READY, but serves no photo URL any more.
 */
class MemoryPhotosLifecycleApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private MediaFixtures media;
    private PersonFixtures persons;
    private UUID admin;
    private UUID grandmother;
    private UUID duplicate;

    @BeforeEach
    void givenAGrandmotherAndHerDuplicate() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
        persons = new PersonFixtures(mvc, jdbc);
        admin = families().userId(family.admin());
        grandmother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        duplicate = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
    }

    @Test
    void afterAMergeTheTargetsMemoriesKeepTheirPhotosWithoutDuplicate() {
        UUID p1 = ready();
        UUID p2 = ready();
        UUID p3 = ready();
        UUID p4 = ready();
        UUID ofDuplicate = storyWith(List.of(duplicate), p1, p2);
        UUID ofGrandmother = storyWith(List.of(grandmother), p3);
        UUID ofBoth = storyWith(List.of(grandmother, duplicate), p4);

        assertThat(persons.merge(family.admin(), family.familyId(), duplicate, grandmother,
                persons.version(duplicate), persons.version(grandmother))).hasStatusOk();

        MvcTestResult list = memories.listForPerson(family.admin(), family.familyId(), grandmother, "");
        String body = FamilyFixtures.body(list);
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactlyInAnyOrder(
                ofDuplicate.toString(), ofGrandmother.toString(), ofBoth.toString());
        String photosOfDuplicate = "$.items[?(@.id == '" + ofDuplicate + "')].photos[*].mediaAssetId";
        assertThat(JsonPath.<List<String>>read(body, photosOfDuplicate)).containsExactly(p1.toString(), p2.toString());
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].photos[*].mediaAssetId")).hasSize(4);
        assertThat(memories.photos(ofDuplicate)).containsExactly(p1, p2);
        assertThat(memories.photos(ofGrandmother)).containsExactly(p3);
        assertThat(memories.photos(ofBoth)).containsExactly(p4);
        assertThat(List.of(p1, p2, p3, p4)).allSatisfy(id -> assertThat(media.status(id)).isEqualTo("READY"));
    }

    @Test
    void archivingAPersonLeavesThePhotosOfItsMemories() {
        UUID p1 = ready();
        UUID memory = storyWith(List.of(grandmother), p1);

        assertThat(persons.archive(family.admin(), family.familyId(), grandmother,
                "\"" + persons.version(grandmother) + "\"")).hasStatusOk();

        assertThat(memories.get(family.admin(), family.familyId(), memory))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos[*].mediaAssetId").asArray().containsExactly(p1.toString());
        assertThat(media.status(p1)).isEqualTo("READY");
    }

    @Test
    void anArchivedMemoryServesNoPhotoUrlButKeepsItsPhotos() {
        UUID p1 = ready();
        UUID memory = storyWith(List.of(grandmother), p1);

        assertThat(memories.archive(family.admin(), family.familyId(), memory, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(memories.get(family.admin(), family.familyId(), memory))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_NOT_FOUND");
        for (MvcTestResult list : List.of(memories.listForFamily(family.admin(), family.familyId(), ""),
                memories.listForPerson(family.admin(), family.familyId(), grandmother, ""))) {
            assertThat(FamilyFixtures.body(list)).doesNotContain(memory.toString()).doesNotContain(p1.toString());
        }
        assertThat(memories.photos(memory)).containsExactly(p1);
        assertThat(media.status(p1)).isEqualTo("READY");
    }

    private UUID storyWith(List<UUID> related, UUID... photos) {
        MvcTestResult result = memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                Arrays.stream(photos).map(MemoryFixtures::photo).toList(), related.toArray(UUID[]::new));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return MemoryFixtures.idOf(result);
    }

    private UUID ready() {
        return MediaFixtures.insertRow(jdbc, family.familyId(), admin, "MEMORY_PHOTO", "READY");
    }
}
