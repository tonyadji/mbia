package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** PR-13: {@code POST /families} and {@code GET /families} (mvp.md §14, data-model.md §6–7). */
class FamiliesApiTest extends ApiTestSupport {

    @Test
    void createReturns201WithAdminRoleAndEtag() {
        TestJwts.Token alice = TestJwts.newUserToken();

        MvcTestResult result = create(alice, "{\"name\": \"  Famille Mbida  \"}");

        assertThat(result)
                .hasStatus(HttpStatus.CREATED)
                .hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"0\"");
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.id").isNotNull();
            json.assertThat().extractingPath("$.name").isEqualTo("Famille Mbida");
            json.assertThat().extractingPath("$.myRole").isEqualTo("ADMIN");
            json.assertThat().extractingPath("$.version").isEqualTo(0);
            json.assertThat().extractingPath("$.stats.activeMemberCount").isEqualTo(1);
            json.assertThat().extractingPath("$.stats.personCount").isEqualTo(0);
            json.assertThat().extractingPath("$.stats.memoryCount").isEqualTo(0);
            json.assertThat().extractingPath("$.createdAt").isNotNull();
            json.assertThat().extractingPath("$.updatedAt").isNotNull();
        });
        assertThat(familyNamesOf(alice)).containsExactly("Famille Mbida");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": \"\"}", "{\"name\": \"   \"}", "{\"name\": null}", "{}"})
    void blankOrMissingNameReturns400AndCreatesNothing(String body) {
        TestJwts.Token alice = TestJwts.newUserToken();

        assertThat(create(alice, body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
        assertThat(familyNamesOf(alice)).isEmpty();
    }

    @Test
    void nameLongerThan200CharactersReturns400() {
        TestJwts.Token alice = TestJwts.newUserToken();

        assertThat(create(alice, "{\"name\": \"" + "x".repeat(201) + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(familyNamesOf(alice)).isEmpty();
    }

    @Test
    void listReturnsOnlyOwnFamilies() {
        TestJwts.Token alice = TestJwts.newUserToken();
        TestJwts.Token bob = TestJwts.newUserToken();
        createOk(alice, "Famille Mbida");
        createOk(alice, "Famille Ndongo");
        createOk(bob, "Famille Atangana");

        MvcTestResult aliceList = list(alice);

        assertThat(aliceList).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(aliceList).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.length()").isEqualTo(2);
            json.assertThat().extractingPath("$[*].name").asArray()
                    .containsExactly("Famille Mbida", "Famille Ndongo");
            json.assertThat().extractingPath("$[*].myRole").asArray().containsOnly("ADMIN");
            json.assertThat().extractingPath("$[*].stats.activeMemberCount").asArray().containsOnly(1);
            json.assertThat().extractingPath("$[*].stats.personCount").asArray().containsOnly(0);
            json.assertThat().extractingPath("$[*].stats.memoryCount").asArray().containsOnly(0);
        });
        assertThat(familyNamesOf(bob)).containsExactly("Famille Atangana");
    }

    @Test
    void familyWhereTheCallerIsRemovedIsNotListed() {
        TestJwts.Token alice = TestJwts.newUserToken();
        TestJwts.Token bob = TestJwts.newUserToken();
        UUID aliceFamily = createOk(alice, "Famille Mbida");
        createOk(bob, "Famille Atangana");
        UUID bobId = userId(bob);
        families().insertMembership(aliceFamily, bobId, "CONTRIBUTOR", "ACTIVE");
        assertThat(familyNamesOf(bob)).containsExactlyInAnyOrder("Famille Atangana", "Famille Mbida");

        jdbc.sql("""
                UPDATE family_memberships SET status = 'REMOVED', removed_at = now()
                WHERE family_id = ? AND user_id = ?
                """).params(aliceFamily, bobId).update();

        assertThat(familyNamesOf(bob)).containsExactly("Famille Atangana");
        assertThat(list(alice)).bodyJson().extractingPath("$[0].stats.activeMemberCount").isEqualTo(1);
    }

    @Test
    void listShowsTheCallersOwnRoleAndCountsOnlyActiveMembers() {
        TestJwts.Token alice = TestJwts.newUserToken();
        TestJwts.Token bob = TestJwts.newUserToken();
        UUID aliceFamily = createOk(alice, "Famille Mbida");
        assertThat(list(bob)).hasStatusOk();
        families().insertMembership(aliceFamily, userId(bob), "VIEWER", "ACTIVE");

        assertThat(list(bob)).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$[0].myRole").isEqualTo("VIEWER");
            json.assertThat().extractingPath("$[0].stats.activeMemberCount").isEqualTo(2);
        });
    }

    @Test
    void anotherUsersFamilyIsNeverListed() {
        TestJwts.Token alice = TestJwts.newUserToken();
        createOk(alice, "Famille Mbida");

        MvcTestResult result = list(TestJwts.newUserToken());

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void anonymousCallsReturn401() {
        assertThat(mvc.get().uri("/api/v1/families").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/v1/families")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Famille\"}").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult create(TestJwts.Token token, String body) {
        return mvc.post().uri("/api/v1/families")
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private UUID createOk(TestJwts.Token token, String name) {
        return families().createFamily(token, name);
    }

    private MvcTestResult list(TestJwts.Token token) {
        return mvc.get().uri("/api/v1/families").header(HttpHeaders.AUTHORIZATION, token.bearer()).exchange();
    }

    private List<Object> familyNamesOf(TestJwts.Token token) {
        MvcTestResult result = list(token);
        assertThat(result).hasStatusOk();
        return JsonPath.read(FamilyFixtures.body(result), "$[*].name");
    }

    private UUID userId(TestJwts.Token token) {
        return families().userId(token);
    }
}
