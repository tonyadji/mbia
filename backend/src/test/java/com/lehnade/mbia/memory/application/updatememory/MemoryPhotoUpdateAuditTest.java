package com.lehnade.mbia.memory.application.updatememory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-42: a change of photos is audited as {@code MEMORY_UPDATED} (data-model.md §17, OQ-039,
 * OQ-042): {@code photos} with the asset ids before and after when photos are added or removed,
 * {@code photoDetails} with the asset ids only when a caption or taken date changes. Never a
 * caption, a text, a storage key nor a URL. In the transaction of the change
 * (technical-specification.md §14).
 */
class MemoryPhotoUpdateAuditTest extends ApiTestSupport {

    private static final String CAPTION = "Legende-secrete-5c1e";

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private MediaFixtures media;
    private UUID admin;
    private UUID first;
    private UUID second;
    private UUID memory;

    @BeforeEach
    void givenAStoryWithTwoPhotos() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
        admin = families().userId(family.admin());
        UUID grandmother = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\"}");
        first = ready();
        second = ready();
        memory = MemoryFixtures.idOf(memories.createStoryWithPhotos(family.admin(), family.familyId(), "Texte",
                List.of(MemoryFixtures.photo(first), MemoryFixtures.photo(second)), grandmother));
    }

    @Test
    void addedAndRemovedPhotosAreAuditedWithTheIdsBeforeAndAfter() {
        UUID added = ready();

        assertThat(update("\"0\"", "{\"photos\": [%s, %s]}".formatted(MemoryFixtures.photo(second),
                MemoryFixtures.photo(added)))).hasStatusOk();

        assertThat(updates()).singleElement().satisfies(row -> {
            assertThat(row.get("old_value")).isEqualTo("{\"photos\": [\"" + first + "\", \"" + second + "\"]}");
            assertThat(row.get("new_value")).isEqualTo("{\"photos\": [\"" + second + "\", \"" + added + "\"]}");
            assertThat(row.get("actor_user_id")).isEqualTo(admin);
        });
    }

    @Test
    void aChangedCaptionOrTakenDateIsAuditedWithTheAssetIdOnly() {
        assertThat(update("\"0\"", """
                {"photos": [{"mediaAssetId": "%s", "caption": "%s"},
                            {"mediaAssetId": "%s", "takenAt": {"precision": "YEAR_ONLY", "year": 1974}}]}
                """.formatted(first, CAPTION, second))).hasStatusOk();

        assertThat(updates()).singleElement().satisfies(row -> {
            assertThat(row.get("old_value")).isEqualTo("{}");
            assertThat(row.get("new_value")).isEqualTo("{\"field\": \"photoDetails\", \"mediaAssetIds\": [\""
                    + first + "\", \"" + second + "\"]}");
        });
        assertThat(jdbc.sql("SELECT count(*) FROM audit_entries WHERE old_value::text LIKE ? OR new_value::text"
                        + " LIKE ? OR new_value::text LIKE '%1974%' OR new_value::text LIKE '%http%'"
                        + " OR new_value::text LIKE '%/display%'")
                .params("%" + CAPTION + "%", "%" + CAPTION + "%")
                .query(Long.class).single()).isZero();
    }

    @Test
    void aTextEmptiedAndAPhotoRemovedAreTwoEntries() {
        assertThat(update("\"0\"", "{\"content\": \"\", \"photos\": [" + MemoryFixtures.photo(first) + "]}"))
                .hasStatusOk();

        assertThat(updates()).extracting(row -> row.get("new_value")).containsExactlyInAnyOrder(
                "{\"field\": \"content\"}", "{\"photos\": [\"" + first + "\"]}");
    }

    @Test
    void aFailureAfterTheWriteLeavesThePhotosAndTheirAssetsUnchanged() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("MEMORY_UPDATED")));
        UUID added = ready();

        assertThat(update("\"0\"", "{\"photos\": [%s, %s]}".formatted(MemoryFixtures.photo(second),
                MemoryFixtures.photo(added)))).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(memories.photos(memory)).containsExactly(first, second);
        assertThat(media.status(first)).isEqualTo("READY");
        assertThat(memories.row(memory)).containsEntry("version", 0L);
        assertThat(updates()).isEmpty();
    }

    private MvcTestResult update(String ifMatch, String json) {
        return memories.update(family.admin(), family.familyId(), memory, ifMatch, json);
    }

    private UUID ready() {
        return MediaFixtures.insertRow(jdbc, family.familyId(), admin, "MEMORY_PHOTO", "READY");
    }

    private List<Map<String, Object>> updates() {
        return jdbc.sql("""
                SELECT actor_user_id, old_value::text AS old_value, new_value::text AS new_value
                FROM audit_entries WHERE resource_id = ? AND action = 'MEMORY_UPDATED'
                """).param(memory).query().listOfRows();
    }
}
