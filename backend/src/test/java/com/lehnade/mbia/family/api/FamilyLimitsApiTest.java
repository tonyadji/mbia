package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * PR-40: a Family tells how many photos a Memory can hold, from the setting
 * {@code MBIA_MEMORY_MAX_PHOTOS} (Phase 4 plan §3.5; openapi {@code FamilyResponse.limits}). A
 * value other than the default proves that the response follows the setting.
 */
@TestPropertySource(properties = "MBIA_MEMORY_MAX_PHOTOS=7")
class FamilyLimitsApiTest extends ApiTestSupport {

    @Test
    void createGetAndUpdateReturnTheSetting() {
        TestJwts.Token admin = TestJwts.newUserToken();
        UUID familyId = families().createFamily(admin, "Famille Mbida");

        assertThat(mvc.get().uri("/api/v1/families/{familyId}", familyId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .hasStatusOk()
                .bodyJson().extractingPath("$.limits.maxPhotosPerMemory").isEqualTo(7);
        assertThat(mvc.patch().uri("/api/v1/families/{familyId}", familyId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Famille Mbida-Adji\"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.limits.maxPhotosPerMemory").isEqualTo(7);
        assertThat(mvc.post().uri("/api/v1/families")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Autre famille\"}"))
                .hasStatus(201)
                .bodyJson().extractingPath("$.limits.maxPhotosPerMemory").isEqualTo(7);
    }
}
