package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-39: Family isolation across every Memory and media operation of Phase 3 (mvp.md §22;
 * technical-specification.md §17; AGENTS.md §5). A member of another Family gets 404 on each of
 * them, learns nothing of the Family and changes nothing in it, whether they call the Family
 * itself or use its ids through their own Family.
 */
class MemoryAndMediaFamilyIsolationApiTest extends ApiTestSupport {

    private static final String TITLE = "Le marché de Yaoundé";

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MemoryFixtures memories;
    private MediaFixtures media;
    /** The Family under attack and its resources. */
    private Resources theirs;
    /** The Family of the other member, an ADMIN there. */
    private Resources mine;

    @BeforeEach
    void givenTwoFamilies() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
        theirs = resources(family.familyId(), family.admin());
        mine = resources(families().createFamily(family.outsider(), "Autre famille"), family.outsider());
    }

    static Stream<Arguments> everyOperation() {
        return Stream.of(
                operation("listFamilyMemories", test -> test.memories.listForFamily(test.mine.token(),
                        test.theirs.familyId(), "")),
                operation("createStoryMemory", test -> test.memories.createStory(test.mine.token(),
                        test.theirs.familyId(), "Titre", "Texte", test.theirs.personId())),
                operation("createPhotoMemory", test -> test.createPhotoMemory(test.mine.token(),
                        test.theirs.familyId(), test.theirs.readyPhotoId(), test.theirs.personId())),
                operation("getMemory", test -> test.memories.get(test.mine.token(), test.theirs.familyId(),
                        test.theirs.memoryId())),
                operation("updateMemory", test -> test.memories.update(test.mine.token(), test.theirs.familyId(),
                        test.theirs.memoryId(), "\"0\"", "{\"title\": \"Piraté\"}")),
                operation("archiveMemory", test -> test.memories.archive(test.mine.token(), test.theirs.familyId(),
                        test.theirs.memoryId(), "\"0\"")),
                operation("listPersonMemories", test -> test.memories.listForPerson(test.mine.token(),
                        test.theirs.familyId(), test.theirs.personId(), "")),
                operation("createMediaUpload", test -> test.media.createUpload(test.mine.token(),
                        test.theirs.familyId(), "PROFILE_PICTURE", "image/jpeg", 1000)),
                operation("completeMediaUpload", test -> test.media.complete(test.mine.token(),
                        test.theirs.familyId(), test.theirs.pendingUploadId())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    void aMemberOfAnotherFamilyGets404OnTheFamily(String operation, Function<MemoryAndMediaFamilyIsolationApiTest,
            MvcTestResult> call) {
        Snapshot before = snapshot(theirs.familyId());

        MvcTestResult result = call.apply(this);

        // createPhotoMemory does not exist in this iteration (Phase 3 plan §3.1).
        assertNotFound(result, operation.equals("createPhotoMemory") ? "RESOURCE_NOT_FOUND" : "FAMILY_NOT_FOUND");
        assertLeaksNothingOf(result, theirs);
        assertThat(snapshot(theirs.familyId())).isEqualTo(before);
    }

    static Stream<Arguments> everyOperationWithAnId() {
        return Stream.of(
                withIds("createStoryMemory: a Person", "PERSON_NOT_FOUND", test -> test.memories.createStory(
                        test.mine.token(), test.mine.familyId(), "Titre", "Texte", test.theirs.personId())),
                withIds("createPhotoMemory: a photo and a Person", "RESOURCE_NOT_FOUND", test ->
                        test.createPhotoMemory(test.mine.token(), test.mine.familyId(), test.theirs.readyPhotoId(),
                                test.theirs.personId())),
                withIds("getMemory", "MEMORY_NOT_FOUND", test -> test.memories.get(test.mine.token(),
                        test.mine.familyId(), test.theirs.memoryId())),
                withIds("updateMemory", "MEMORY_NOT_FOUND", test -> test.memories.update(test.mine.token(),
                        test.mine.familyId(), test.theirs.memoryId(), "\"0\"", "{\"title\": \"Piraté\"}")),
                withIds("updateMemory: a Person", "PERSON_NOT_FOUND", test -> test.memories.update(test.mine.token(),
                        test.mine.familyId(), test.mine.memoryId(), "\"0\"",
                        "{\"relatedPersonIds\": [\"" + test.theirs.personId() + "\"]}")),
                withIds("archiveMemory", "MEMORY_NOT_FOUND", test -> test.memories.archive(test.mine.token(),
                        test.mine.familyId(), test.theirs.memoryId(), "\"0\"")),
                withIds("listPersonMemories", "PERSON_NOT_FOUND", test -> test.memories.listForPerson(
                        test.mine.token(), test.mine.familyId(), test.theirs.personId(), "")),
                withIds("completeMediaUpload: a pending upload", "MEDIA_NOT_FOUND", test -> test.media.complete(
                        test.mine.token(), test.mine.familyId(), test.theirs.pendingUploadId())),
                withIds("completeMediaUpload: a ready photo", "MEDIA_NOT_FOUND", test -> test.media.complete(
                        test.mine.token(), test.mine.familyId(), test.theirs.readyPhotoId())),
                withIds("createPerson: a photo", "MEDIA_NOT_FOUND", test -> test.persons.create(test.mine.token(),
                        test.mine.familyId(), "{\"firstName\": \"Jean\", \"profileMediaAssetId\": \""
                                + test.theirs.readyPhotoId() + "\"}")),
                withIds("updatePerson: a photo", "MEDIA_NOT_FOUND", test -> test.persons.update(test.mine.token(),
                        test.mine.familyId(), test.mine.personId(), "\"0\"",
                        "{\"profileMediaAssetId\": \"" + test.theirs.readyPhotoId() + "\"}")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperationWithAnId")
    void theIdsOfAnotherFamilyAreNotFoundThroughTheCallersFamily(String operation, String code,
            Function<MemoryAndMediaFamilyIsolationApiTest, MvcTestResult> call) {
        Snapshot before = snapshot(theirs.familyId());
        Snapshot mineBefore = snapshot(mine.familyId());

        MvcTestResult result = call.apply(this);

        assertNotFound(result, code);
        assertLeaksNothingOf(result, theirs);
        assertThat(snapshot(theirs.familyId())).isEqualTo(before);
        assertThat(snapshot(mine.familyId())).isEqualTo(mineBefore);
    }

    /** A Person, a story about them, a READY photo and a pending upload, created by the Family's ADMIN. */
    private Resources resources(UUID familyId, TestJwts.Token admin) {
        UUID person = persons.createId(admin, familyId, "{\"firstName\": \"Awa\"}");
        UUID memory = memories.createStoryId(admin, familyId, person);
        UUID adminId = families().userId(admin);
        return new Resources(familyId, admin, person, memory, media.row(familyId, adminId, "READY"),
                media.row(familyId, adminId, "PENDING_UPLOAD"));
    }

    private MvcTestResult createPhotoMemory(TestJwts.Token token, UUID familyId, UUID mediaAssetId, UUID personId) {
        return mvc.post().uri("/api/v1/families/{familyId}/memories/photos", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mediaAssetId": "%s", "relatedPersonIds": ["%s"]}
                        """.formatted(mediaAssetId, personId))
                .exchange();
    }

    /** What a Family holds: its Persons, Memories, their links and its media, with versions and statuses. */
    private Snapshot snapshot(UUID familyId) {
        return new Snapshot(
                jdbc.sql("SELECT id, status, version, title, content FROM memories WHERE family_id = ? ORDER BY id")
                        .param(familyId).query().listOfRows(),
                jdbc.sql("SELECT memory_id, person_id FROM memory_persons WHERE family_id = ? ORDER BY 1, 2")
                        .param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id, status, version, profile_media_asset_id FROM persons WHERE family_id = ? "
                        + "ORDER BY id").param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id, status FROM media_assets WHERE family_id = ? ORDER BY id")
                        .param(familyId).query().listOfRows());
    }

    private static void assertNotFound(MvcTestResult result, String code) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static void assertLeaksNothingOf(MvcTestResult result, Resources family) {
        assertThat(FamilyFixtures.body(result)).doesNotContain(TITLE, "plantain", "Awa",
                family.personId().toString(), family.memoryId().toString(), family.readyPhotoId().toString(),
                family.pendingUploadId().toString(), "families/" + family.familyId() + "/media/");
    }

    private static Arguments operation(String name, Function<MemoryAndMediaFamilyIsolationApiTest,
            MvcTestResult> call) {
        return Arguments.of(name, call);
    }

    private static Arguments withIds(String name, String code, Function<MemoryAndMediaFamilyIsolationApiTest,
            MvcTestResult> call) {
        return Arguments.of(name, code, call);
    }

    private record Resources(UUID familyId, TestJwts.Token token, UUID personId, UUID memoryId, UUID readyPhotoId,
            UUID pendingUploadId) {}

    private record Snapshot(List<Map<String, Object>> memories, List<Map<String, Object>> links,
            List<Map<String, Object>> persons, List<Map<String, Object>> media) {}
}
