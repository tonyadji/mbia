package com.lehnade.mbia.memory.api;

import static com.lehnade.mbia.memory.MediaImages.assertHasGpsAndXmp;
import static com.lehnade.mbia.memory.MediaImages.assertHasNoMetadata;
import static com.lehnade.mbia.memory.MediaImages.fixture;
import static com.lehnade.mbia.memory.MediaImages.isJpeg;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MediaFixtures.Slot;
import com.lehnade.mbia.memory.MediaImages;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-39: what Phase 3 serves of a photo, across every response that carries one (mvp.md §23;
 * ADR-007; Phase 3 plan §3.5). For each image fixture with a location, uploaded as the browser
 * does and given to a Person: every image URL returned by the API serves a JPEG without any
 * metadata, and no storage key, bucket or original file name appears in a response outside the
 * short-lived pre-signed URLs, nor in the logs with the story text.
 */
@ExtendWith(OutputCaptureExtension.class)
class ServedMediaApiTest extends ApiTestSupport {

    /** A pre-signed URL: the only place a storage key may appear (Phase 3 plan §3.5). */
    private static final Pattern URL = Pattern.compile("https?://[^\"\\s]+X-Amz-[^\"\\s]+");
    private static final String FILE_NAME = "grand-mere.jpg";
    private static final String STORY = "Grand-mère vendait du plantain.";

    @Value("${mbia.storage.bucket}")
    String bucket;

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MemoryFixtures memories;
    private MediaFixtures media;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({MediaImages.JPEG_ORIENTATION_6 + ", image/jpeg", MediaImages.PNG_WITH_GPS + ", image/png",
            MediaImages.WEBP_WITH_GPS + ", image/webp", MediaImages.LARGE_WITH_GPS + ", image/jpeg"})
    void everyServedImageIsAJpegWithoutMetadataAndNoResponseHoldsAStorageKey(String name, String mimeType,
            CapturedOutput output) {
        byte[] photo = fixture(name);
        // The source really carries a location: the test proves it is gone.
        assertHasGpsAndXmp(photo);

        Map<String, String> responses = servedWithAPhoto(photo, mimeType);

        // Every image the API serves (the upload slot only returns where to PUT the file).
        Set<String> urls = new TreeSet<>();
        responses.forEach((operation, body) -> {
            if (!operation.equals("createMediaUpload")) urls.addAll(urlsIn(body));
        });
        // Display and thumbnail of the completion, the thumbnail wherever the Person is returned.
        assertThat(urls.stream().map(url -> URI.create(url).getPath().replaceAll(".*/", "")).distinct())
                .containsExactlyInAnyOrder("display", "thumbnail");
        for (String url : urls) {
            HttpResponse<byte[]> served = MediaFixtures.get(URI.create(url));
            assertThat(served.statusCode()).as(url).isEqualTo(200);
            assertThat(isJpeg(served.body())).as(url).isTrue();
            assertHasNoMetadata(served.body());
        }

        // The API paths (…/families/{familyId}/media/uploads/{id}/complete) look like a key prefix: look
        // for whole keys of every asset of the Family.
        String[] keys = jdbc.sql("SELECT id FROM media_assets WHERE family_id = ?").param(family.familyId())
                .query(UUID.class).list().stream()
                .map(id -> MediaFixtures.key(family.familyId(), id, ""))
                .toArray(String[]::new);
        assertThat(keys).hasSize(2);
        responses.forEach((operation, body) -> assertThat(withoutUrls(body)).as(operation)
                .doesNotContain(keys)
                .doesNotContain("/display", "/thumbnail", bucket + "/", "X-Amz-", FILE_NAME));
        assertThat(output.getAll())
                .doesNotContain(keys)
                .doesNotContain("X-Amz-", FILE_NAME, STORY)
                .doesNotContain(urls.toArray(String[]::new))
                .doesNotContain(slot(responses));
    }

    /**
     * A photo uploaded and completed by the ADMIN, then given to Marie, who has a story; returns every
     * response of the phase that may show it, by operation, and the refusals that concern it.
     */
    private Map<String, String> servedWithAPhoto(byte[] photo, String mimeType) {
        Map<String, String> responses = new LinkedHashMap<>();
        Slot slot = media.uploaded(family.admin(), family.familyId(), mimeType, photo);
        responses.put("createMediaUpload", slot.body());
        responses.put("completeMediaUpload", body(media.complete(family.admin(), family.familyId(),
                slot.mediaAssetId())));
        responses.put("completeMediaUpload again", body(media.complete(family.admin(), family.familyId(),
                slot.mediaAssetId())));
        responses.put("completeMediaUpload by another member", body(media.complete(family.contributor(),
                family.familyId(), slot.mediaAssetId())));

        MvcTestResult created = persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"profileMediaAssetId\": \"" + slot.mediaAssetId() + "\"}");
        responses.put("createPerson", body(created));
        UUID marie = UUID.fromString(JsonPath.read(body(created), "$.id"));
        responses.put("createPerson with a used photo", body(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + slot.mediaAssetId() + "\"}")));
        Slot invalid = media.uploaded(family.admin(), family.familyId(), "image/jpeg",
                fixture(MediaImages.TEXT_RENAMED_JPG));
        responses.put("completeMediaUpload invalid", body(media.complete(family.admin(), family.familyId(),
                invalid.mediaAssetId())));
        responses.put("updatePerson with an invalid photo", body(persons.update(family.admin(), family.familyId(),
                marie, "\"0\"", "{\"profileMediaAssetId\": \"" + invalid.mediaAssetId() + "\"}")));

        UUID story = memories.createStoryId(family.admin(), family.familyId(), marie);
        responses.put("getPerson", body(persons.get(family.viewer(), family.familyId(), marie)));
        responses.put("getFamilyTree", body(get(family.viewer(),
                "/api/v1/families/{familyId}/tree?focusPersonId={personId}", family.familyId(), marie)));
        responses.put("searchPersons", body(get(family.viewer(), "/api/v1/families/{familyId}/persons?search=marie",
                family.familyId())));
        responses.put("getMemory", body(memories.get(family.viewer(), family.familyId(), story)));
        responses.put("listPersonMemories", body(memories.listForPerson(family.viewer(), family.familyId(), marie,
                "")));
        responses.put("listFamilyMemories", body(memories.listForFamily(family.viewer(), family.familyId(), "")));
        return responses;
    }

    private static String slot(Map<String, String> responses) {
        return JsonPath.read(responses.get("createMediaUpload"), "$.uploadUrl");
    }

    private MvcTestResult get(TestJwts.Token token, String uri, Object... variables) {
        return mvc.get().uri(uri, variables).header(HttpHeaders.AUTHORIZATION, token.bearer()).exchange();
    }

    private static String body(MvcTestResult result) {
        return FamilyFixtures.body(result);
    }

    private static Set<String> urlsIn(String body) {
        Set<String> urls = new TreeSet<>();
        Matcher matcher = URL.matcher(body);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

    private static String withoutUrls(String body) {
        return URL.matcher(body).replaceAll("");
    }
}
