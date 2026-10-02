package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-14: {@code GET} and {@code PATCH /families/{familyId}}, the template of every family-scoped
 * endpoint: non-members get 404, members without the role get 403, stale versions get 409
 * (mvp.md §22, architecture.md §11–12, data-model.md §2.3, §7).
 */
class FamilyAccessApiTest extends ApiTestSupport {

    private FamilyWithMembers family;

    @BeforeEach
    void givenAFamilyWithMembersOfEachRole() {
        family = families().givenFamilyWithMembersOfEachRole();
    }

    // --- Membership: not an ACTIVE member → 404, the Family's existence is never revealed ---

    @Test
    void outsiderGets404OnGetAndPatch() {
        assertFamilyNotFound(get(family.outsider(), family.familyId()));
        assertFamilyNotFound(patch(family.outsider(), family.familyId(), "\"0\"", "{\"name\": \"Hacked\"}"));

        assertUnchanged();
    }

    @Test
    void removedMemberGets404OnGetAndPatch() {
        assertFamilyNotFound(get(family.removed(), family.familyId()));
        assertFamilyNotFound(patch(family.removed(), family.familyId(), "\"0\"", "{\"name\": \"Hacked\"}"));

        assertUnchanged();
    }

    @Test
    void unknownFamilyAndForeignFamilyAreIndistinguishable() {
        MvcTestResult foreign = get(family.outsider(), family.familyId());
        MvcTestResult unknown = get(family.outsider(), UUID.randomUUID());

        assertFamilyNotFound(unknown);
        assertThat(problemWithoutTraceId(foreign)).isEqualTo(problemWithoutTraceId(unknown));
        assertThat(problemWithoutTraceId(patch(family.outsider(), family.familyId(), "\"0\"", "{\"name\": \"X\"}")))
                .isEqualTo(problemWithoutTraceId(patch(family.outsider(), UUID.randomUUID(), "\"0\"", "{\"name\": \"X\"}")));
    }

    // --- Role: an ACTIVE member reads, only ADMIN renames ---

    @Test
    void adminGetsFamilyWithEtag() {
        MvcTestResult result = get(family.admin(), family.familyId());

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON).hasHeader(HttpHeaders.ETAG, "\"0\"");
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.id").isEqualTo(family.familyId().toString());
            json.assertThat().extractingPath("$.name").isEqualTo("Famille Mbida");
            json.assertThat().extractingPath("$.myRole").isEqualTo("ADMIN");
            json.assertThat().extractingPath("$.version").isEqualTo(0);
            json.assertThat().extractingPath("$.stats.activeMemberCount").isEqualTo(3);
            json.assertThat().extractingPath("$.stats.personCount").isEqualTo(0);
            json.assertThat().extractingPath("$.stats.memoryCount").isEqualTo(0);
            json.assertThat().extractingPath("$.createdAt").isNotNull();
            json.assertThat().extractingPath("$.updatedAt").isNotNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTRIBUTOR", "VIEWER"})
    void contributorAndViewerCanReadButNotRename(String role) {
        TestJwts.Token member = role.equals("VIEWER") ? family.viewer() : family.contributor();

        assertThat(get(member, family.familyId())).hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"")
                .bodyJson().extractingPath("$.myRole").isEqualTo(role);

        assertThat(patch(member, family.familyId(), "\"0\"", "{\"name\": \"Renamed\"}"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertUnchanged();
    }

    // --- Optimistic concurrency: If-Match must carry the current version ---

    @Test
    void currentIfMatchRenamesAndIncrementsVersion() {
        MvcTestResult result = patch(family.admin(), family.familyId(), "\"0\"", "{\"name\": \"  Famille Mbida-Ndongo \"}");

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON).hasHeader(HttpHeaders.ETAG, "\"1\"");
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.name").isEqualTo("Famille Mbida-Ndongo");
            json.assertThat().extractingPath("$.version").isEqualTo(1);
            json.assertThat().extractingPath("$.myRole").isEqualTo("ADMIN");
            json.assertThat().extractingPath("$.stats.activeMemberCount").isEqualTo(3);
        });
        assertThat(families().familyName(family.familyId())).isEqualTo("Famille Mbida-Ndongo");
        assertThat(families().familyVersion(family.familyId())).isEqualTo(1);
        assertThat(get(family.viewer(), family.familyId())).hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.name").isEqualTo("Famille Mbida-Ndongo");

        // The form that was loaded at version 0 is now stale.
        assertConcurrentModification(patch(family.admin(), family.familyId(), "\"0\"", "{\"name\": \"Old form\"}"));
        assertThat(families().familyName(family.familyId())).isEqualTo("Famille Mbida-Ndongo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1\"", "\"5\""})
    void staleIfMatchReturns409(String ifMatch) {
        assertConcurrentModification(patch(family.admin(), family.familyId(), ifMatch, "{\"name\": \"Renamed\"}"));

        assertUnchanged();
    }

    @Test
    void missingIfMatchReturns400() {
        MvcTestResult result = mvc.patch().uri("/api/v1/families/{id}", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Renamed\"}")
                .exchange();

        assertValidationFailed(result);
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "W/\"0\"", "*", "\"0\", \"1\"", "\"abc\"", "\"-1\"", "\"\"", "\"99999999999999999999\""})
    void malformedIfMatchReturns400(String ifMatch) {
        assertValidationFailed(patch(family.admin(), family.familyId(), ifMatch, "{\"name\": \"Renamed\"}"));

        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": \"\"}", "{\"name\": \"   \"}", "{}"})
    void blankNameReturns400(String body) {
        assertValidationFailed(patch(family.admin(), family.familyId(), "\"0\"", body));

        assertUnchanged();
    }

    @Test
    void nameLongerThan200CharactersReturns400() {
        assertValidationFailed(patch(family.admin(), family.familyId(), "\"0\"",
                "{\"name\": \"" + "x".repeat(201) + "\"}"));

        assertUnchanged();
    }

    @Test
    void anonymousCallsReturn401() {
        assertThat(mvc.get().uri("/api/v1/families/{id}", family.familyId()).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.patch().uri("/api/v1/families/{id}", family.familyId())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Hacked\"}").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertUnchanged();
    }

    private MvcTestResult get(TestJwts.Token token, UUID familyId) {
        return mvc.get().uri("/api/v1/families/{id}", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    private MvcTestResult patch(TestJwts.Token token, UUID familyId, String ifMatch, String body) {
        return mvc.patch().uri("/api/v1/families/{id}", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private void assertUnchanged() {
        assertThat(families().familyName(family.familyId())).isEqualTo("Famille Mbida");
        assertThat(families().familyVersion(family.familyId())).isZero();
    }

    private static void assertFamilyNotFound(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .doesNotContainHeader(HttpHeaders.ETAG)
                .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
    }

    private static void assertConcurrentModification(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
    }

    private static void assertValidationFailed(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    private static Map<String, Object> problemWithoutTraceId(MvcTestResult result) {
        Map<String, Object> problem = new HashMap<>(JsonPath.read(FamilyFixtures.body(result), "$"));
        problem.remove("traceId");
        return problem;
    }
}
