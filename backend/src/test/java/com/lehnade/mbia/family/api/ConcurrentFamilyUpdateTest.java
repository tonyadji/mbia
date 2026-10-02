package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.family.domain.FamilyRepository;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-14 / architecture.md §12: a rename that committed after the version check but before the
 * write is not silently overwritten.
 */
class ConcurrentFamilyUpdateTest extends ApiTestSupport {

    @MockitoSpyBean
    FamilyRepository familyRepository;

    @Test
    void updateCommittedBetweenCheckAndWriteReturns409() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        doAnswer(invocation -> {
            Object loaded = invocation.callRealMethod();
            // Another request renames the Family once this one has read version 0. It runs on another
            // thread so that it uses its own connection and commits, outside this request's transaction.
            CompletableFuture.runAsync(() -> jdbc
                    .sql("UPDATE families SET name = 'Concurrent', version = version + 1 WHERE id = ?")
                    .param(family.familyId()).update()).join();
            return loaded;
        }).when(familyRepository).findById(any());

        assertThat(mvc.patch().uri("/api/v1/families/{id}", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Mine\"}")
                .exchange())
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");

        assertThat(families().familyName(family.familyId())).isEqualTo("Concurrent");
        assertThat(families().familyVersion(family.familyId())).isEqualTo(1);
    }
}
