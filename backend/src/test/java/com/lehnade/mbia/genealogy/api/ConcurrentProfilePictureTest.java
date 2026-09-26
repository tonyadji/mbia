package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.domain.MediaAssetRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-37, OQ-036: an upload becomes the photo of one Person only. Two requests attaching the same
 * upload at the same time are serialized by the lock on the asset: the second one, which waits
 * while the first holds it, then sees the photo used and answers {@code MEDIA_ALREADY_USED}.
 */
class ConcurrentProfilePictureTest extends ApiTestSupport {

    @MockitoSpyBean
    MediaAssetRepository mediaAssets;

    @Test
    void theSameUploadAttachedTwiceAtOnceGoesToOnePersonOnly() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        UUID marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        UUID awa = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        UUID photo = new MediaFixtures(mvc, jdbc).row(family.familyId(), families().userId(family.admin()), "READY");
        String json = "{\"profileMediaAssetId\": \"" + photo + "\"}";

        AtomicBoolean first = new AtomicBoolean(true);
        AtomicReference<CompletableFuture<MvcTestResult>> second = new AtomicReference<>();
        doAnswer(invocation -> {
            Object locked = invocation.callRealMethod();
            if (first.getAndSet(false)) {
                // While this request holds the asset, another one attaches it to Awa on its own connection.
                second.set(CompletableFuture.supplyAsync(
                        () -> persons.update(family.admin(), family.familyId(), awa, "\"0\"", json)));
                TimeUnit.MILLISECONDS.sleep(500);
                assertThat(second.get()).as("the second request waits for the lock").isNotDone();
            }
            return locked;
        }).when(mediaAssets).lockInFamily(any(), any());

        assertThat(persons.update(family.admin(), family.familyId(), marie, "\"0\"", json)).hasStatusOk();
        assertThat(second.get().join()).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_ALREADY_USED");

        assertThat(jdbc.sql("SELECT count(*) FROM persons WHERE profile_media_asset_id = ?").param(photo)
                .query(Long.class).single()).isEqualTo(1);
        assertThat(persons.version(awa)).isZero();
    }
}
