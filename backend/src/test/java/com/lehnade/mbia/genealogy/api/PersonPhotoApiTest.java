package com.lehnade.mbia.genealogy.api;

import static com.lehnade.mbia.memory.MediaFixtures.key;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MediaImages;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-37: a Person carries a photo (mvp.md §16; data-model.md §10, §13; openapi {@code createPerson},
 * {@code updatePerson}). The photo is an upload of the caller, READY and not yet used (OQ-036); it
 * follows the edit rules of the Person, linked-Person protection included
 * (person-relationships-collaboration.md §2, OQ-040); a replaced or removed photo becomes ARCHIVED;
 * a change is audited without values in the history (OQ-046); a merge keeps one photo (OQ-047).
 * {@code profilePictureUrl} is the pre-signed thumbnail wherever a Person is returned.
 */
class PersonPhotoApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MediaFixtures media;
    private UUID marie;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"lastName\": \"Mbida\"}");
    }

    // --- Set, replace and remove (OQ-040) ---

    @Test
    void aPhotoUploadedThroughTheApiBecomesThePhotoOfANewPerson() {
        UUID photo = media.readyPhoto(family.contributor(), family.familyId());

        MvcTestResult result = persons.create(family.contributor(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + photo + "\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = FamilyFixtures.body(result);
        UUID awa = UUID.fromString(JsonPath.read(body, "$.id"));
        URI url = URI.create(JsonPath.read(body, "$.profilePictureUrl"));
        assertThat(url.getPath()).endsWith(key(family.familyId(), photo, "thumbnail"));
        HttpResponse<byte[]> thumbnail = MediaFixtures.get(url);
        assertThat(thumbnail.statusCode()).isEqualTo(200);
        assertThat(MediaImages.isJpeg(thumbnail.body())).isTrue();
        // The storage key appears only inside the short-lived URL (Phase 3 plan §3.5).
        assertThat(body.replace(url.toString(), "")).doesNotContain(key(family.familyId(), photo, ""));

        assertThat(photoOf(awa)).isEqualTo(photo);
        assertThat(media.status(photo)).isEqualTo("READY");
        assertThat(persons.get(family.viewer(), family.familyId(), awa)).bodyJson()
                .extractingPath("$.profilePictureUrl").asString().contains(key(family.familyId(), photo, "thumbnail"));
    }

    @Test
    void aPhotoIsSetReplacedAndRemovedAndTheOldOnesAreArchived() {
        UUID first = readyOf(family.admin());
        UUID second = readyOf(family.admin());

        assertThat(update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + first + "\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.profilePictureUrl").asString()
                .contains(key(family.familyId(), first, "thumbnail"));
        assertThat(photoOf(marie)).isEqualTo(first);

        assertThat(update(family.admin(), marie, "\"1\"", "{\"profileMediaAssetId\": \"" + second + "\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"2\"")
                .bodyJson().extractingPath("$.profilePictureUrl").asString()
                .contains(key(family.familyId(), second, "thumbnail"));
        assertThat(media.row(first)).containsEntry("status", "ARCHIVED").extractingByKey("archived_at").isNotNull();
        assertThat(media.status(second)).isEqualTo("READY");

        assertThat(update(family.admin(), marie, "\"2\"", "{\"removeProfilePicture\": true}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"3\"")
                .bodyJson().extractingPath("$.profilePictureUrl").isNull();
        assertThat(photoOf(marie)).isNull();
        assertThat(media.status(second)).isEqualTo("ARCHIVED");
    }

    @Test
    void theCurrentPhotoAgainOrARemovalWithoutPhotoChangesNothing() {
        assertThat(update(family.admin(), marie, "\"0\"", "{\"removeProfilePicture\": true}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"");
        assertThat(update(family.admin(), marie, "\"0\"", "{\"removeProfilePicture\": false}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"");

        UUID photo = readyOf(family.admin());
        update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}");
        assertThat(update(family.admin(), marie, "\"1\"", "{\"profileMediaAssetId\": \"" + photo + "\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"1\"");
        assertThat(media.status(photo)).isEqualTo("READY");
    }

    @Test
    void aNewPhotoWithARemovalIsAValidationError() {
        UUID photo = readyOf(family.admin());

        assertThat(update(family.admin(), marie, "\"0\"",
                "{\"profileMediaAssetId\": \"" + photo + "\", \"removeProfilePicture\": true}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo("removeProfilePicture");
                });
        assertUnchanged(photo);
    }

    // --- Which upload may become a photo (OQ-036) ---

    @Test
    void anotherMembersUploadIsRefused() {
        UUID photo = readyOf(family.contributor());

        assertRefused(update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}"),
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        assertRefused(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + photo + "\"}"),
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        assertUnchanged(photo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_UPLOAD", "FAILED", "ARCHIVED"})
    void anUploadThatIsNotReadyIsRefused(String status) {
        UUID photo = media.row(family.familyId(), families().userId(family.admin()), status);

        assertRefused(update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}"),
                HttpStatus.CONFLICT, "MEDIA_NOT_READY");
        assertRefused(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + photo + "\"}"),
                HttpStatus.CONFLICT, "MEDIA_NOT_READY");
        assertThat(media.status(photo)).isEqualTo(status);
        assertThat(persons.version(marie)).isZero();
    }

    @Test
    void anUploadAlreadyUsedIsRefusedEvenByAnArchivedPerson() {
        UUID photo = readyOf(family.admin());
        UUID awa = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + photo + "\"}");

        assertRefused(update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}"),
                HttpStatus.CONFLICT, "MEDIA_ALREADY_USED");
        persons.archive(awa);
        assertRefused(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Paul\", \"profileMediaAssetId\": \"" + photo + "\"}"),
                HttpStatus.CONFLICT, "MEDIA_ALREADY_USED");
        assertThat(photoOf(marie)).isNull();
        assertThat(photoOf(awa)).isEqualTo(photo);
        // An archived Person keeps its photo and stays readable with it.
        assertThat(persons.get(family.admin(), family.familyId(), awa)).bodyJson()
                .extractingPath("$.profilePictureUrl").isNotNull();
    }

    @Test
    void anUnknownUploadOrOneOfAnotherFamilyIsNotFound() {
        UUID otherFamily = families().createFamily(family.admin(), "Autre famille");
        UUID elsewhere = media.row(otherFamily, families().userId(family.admin()), "READY");

        for (UUID photo : new UUID[] {UUID.randomUUID(), elsewhere}) {
            assertRefused(update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}"),
                    HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
        }
        assertThat(media.status(elsewhere)).isEqualTo("READY");
        assertThat(persons.version(marie)).isZero();
    }

    // --- The photo follows the Person edit rules (person-relationships-collaboration.md §2, OQ-040) ---

    @Test
    void aViewerCannotChangeAPhoto() {
        UUID photo = readyOf(family.viewer());

        assertRefused(update(family.viewer(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}"),
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        assertUnchanged(photo);
    }

    @Test
    void onlyTheLinkedMemberOrAnAdminChangesTheLinkedPersonsPhoto() {
        persons.claim(family.contributor(), family.familyId(), marie, "\"0\"");
        TestJwts.Token otherContributor = TestJwts.newUserToken();
        families().insertMembership(family.familyId(), families().provisionedUserId(otherContributor),
                "CONTRIBUTOR", "ACTIVE");
        UUID theirs = readyOf(otherContributor);

        assertRefused(update(otherContributor, marie, "\"1\"", "{\"profileMediaAssetId\": \"" + theirs + "\"}"),
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        assertRefused(update(otherContributor, marie, "\"1\"", "{\"removeProfilePicture\": true}"),
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        assertUnchanged(theirs, 1);

        UUID own = readyOf(family.contributor());
        assertThat(update(family.contributor(), marie, "\"1\"", "{\"profileMediaAssetId\": \"" + own + "\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"2\"");
        UUID admins = readyOf(family.admin());
        assertThat(update(family.admin(), marie, "\"2\"", "{\"profileMediaAssetId\": \"" + admins + "\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"3\"");
        assertThat(media.status(own)).isEqualTo("ARCHIVED");
    }

    @Test
    void aStaleVersionChangesNoPhoto() {
        UUID first = readyOf(family.admin());
        update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + first + "\"}");
        UUID second = readyOf(family.admin());

        assertRefused(update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + second + "\"}"),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertThat(photoOf(marie)).isEqualTo(first);
        assertThat(media.status(first)).isEqualTo("READY");
        assertThat(media.status(second)).isEqualTo("READY");
    }

    // --- Audit and history (OQ-046) ---

    @Test
    void aPhotoChangeIsAuditedAndShownWithoutValues() {
        UUID photo = readyOf(family.admin());
        update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}");
        update(family.admin(), marie, "\"1\"", "{\"removeProfilePicture\": true}");

        List<String> audited = jdbc.sql("""
                SELECT old_value::text || ' -> ' || new_value::text FROM audit_entries
                WHERE resource_id = ? AND action = 'PERSON_UPDATED' ORDER BY occurred_at
                """).param(marie).query(String.class).list();
        assertThat(audited).containsExactly("{} -> {\"profilePicture\": \"" + photo + "\"}",
                "{\"profilePicture\": \"" + photo + "\"} -> {}");

        String history = FamilyFixtures.body(persons.history(family.viewer(), family.familyId(), marie));
        List<Map<String, Object>> changes = JsonPath.read(history, "$.items[?(@.action == 'PERSON_UPDATED')]");
        assertThat(changes).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.get("field")).isEqualTo("profilePicture");
            assertThat(entry.get("oldValue")).isNull();
            assertThat(entry.get("newValue")).isNull();
        });
        assertThat(history).doesNotContain(photo.toString());
    }

    // --- profilePictureUrl wherever a Person is returned ---

    @Test
    void theUrlIsInSearchTreeArchivedRelationshipsDuplicatesAndMemories() {
        UUID photo = readyOf(family.admin());
        update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + photo + "\"}");
        String thumbnail = key(family.familyId(), photo, "thumbnail");

        assertThat(JsonPath.<List<String>>read(FamilyFixtures.body(SearchPersonsApiTest.search(mvc, family.viewer(),
                family.familyId())), "$.items[*].profilePictureUrl")).singleElement().asString().contains(thumbnail);

        assertThat(JsonPath.<List<String>>read(FamilyFixtures.body(mvc.get()
                .uri("/api/v1/families/{familyId}/tree?focusPersonId={marie}", family.familyId(), marie)
                .header(HttpHeaders.AUTHORIZATION, family.viewer().bearer()).exchange()),
                "$.nodes[?(@.id == '" + marie + "')].profilePictureUrl"))
                .singleElement().asString().contains(thumbnail);

        RelationshipFixtures relationships = new RelationshipFixtures(mvc, jdbc);
        UUID child = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        UUID link = relationships.parentOfId(family.admin(), family.familyId(), marie, child);
        assertThat(relationships.remove(family.admin(), family.familyId(), link, "\"0\"")).hasStatus2xxSuccessful();
        assertThat(relationships.archivedOf(family.admin(), family.familyId(), child)).bodyJson()
                .extractingPath("$[0].relatedPerson.profilePictureUrl").asString().contains(thumbnail);

        MvcTestResult duplicate = persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"lastName\": \"Mbida\"}");
        assertThat(duplicate).hasStatus(HttpStatus.CONFLICT).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.code").isEqualTo("POSSIBLE_DUPLICATE");
            json.assertThat().extractingPath("$.details.candidates[0].profilePictureUrl").asString()
                    .contains(thumbnail);
        });

        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        UUID story = memories.createStoryId(family.admin(), family.familyId(), marie);
        assertThat(memories.get(family.viewer(), family.familyId(), story)).bodyJson()
                .extractingPath("$.relatedPersons[0].profilePictureUrl").asString().contains(thumbnail);
    }

    @Test
    void aPersonWithoutPhotoHasNoUrl() {
        assertThat(persons.get(family.viewer(), family.familyId(), marie)).bodyJson()
                .extractingPath("$.profilePictureUrl").isNull();
    }

    // --- Merge (OQ-047) ---

    @Test
    void aMergedTargetWithoutPhotoTakesTheDuplicatesPhoto() {
        UUID photo = readyOf(family.admin());
        UUID duplicate = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + photo + "\"}");

        assertThat(persons.merge(family.admin(), family.familyId(), duplicate, marie, 0, 0)).hasStatusOk()
                .bodyJson().extractingPath("$.profilePictureUrl").asString()
                .contains(key(family.familyId(), photo, "thumbnail"));
        assertThat(photoOf(marie)).isEqualTo(photo);
        assertThat(photoOf(duplicate)).isNull();
        assertThat(media.status(photo)).isEqualTo("READY");
        assertThat(persons.get(family.admin(), family.familyId(), duplicate)).bodyJson()
                .extractingPath("$.profilePictureUrl").isNull();
    }

    @Test
    void aMergedTargetKeepsItsPhotoAndTheDuplicatesPhotoIsArchived() {
        UUID kept = readyOf(family.admin());
        update(family.admin(), marie, "\"0\"", "{\"profileMediaAssetId\": \"" + kept + "\"}");
        UUID lost = readyOf(family.admin());
        UUID duplicate = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + lost + "\"}");

        assertThat(persons.merge(family.admin(), family.familyId(), duplicate, marie, 0, 1)).hasStatusOk();

        assertThat(photoOf(marie)).isEqualTo(kept);
        assertThat(photoOf(duplicate)).isNull();
        assertThat(media.status(kept)).isEqualTo("READY");
        assertThat(media.status(lost)).isEqualTo("ARCHIVED");
    }

    private MvcTestResult update(TestJwts.Token token, UUID personId, String ifMatch, String json) {
        return persons.update(token, family.familyId(), personId, ifMatch, json);
    }

    /** A READY photo of the Family uploaded by {@code token}. */
    private UUID readyOf(TestJwts.Token token) {
        return media.row(family.familyId(), families().provisionedUserId(token), "READY");
    }

    private UUID photoOf(UUID personId) {
        return (UUID) jdbc.sql("SELECT profile_media_asset_id FROM persons WHERE id = ?").param(personId)
                .query().singleRow().get("profile_media_asset_id");
    }

    private void assertUnchanged(UUID photo) {
        assertUnchanged(photo, 0);
    }

    private void assertUnchanged(UUID photo, long version) {
        assertThat(persons.version(marie)).isEqualTo(version);
        assertThat(photoOf(marie)).isNull();
        assertThat(media.status(photo)).isEqualTo("READY");
    }

    private static void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
