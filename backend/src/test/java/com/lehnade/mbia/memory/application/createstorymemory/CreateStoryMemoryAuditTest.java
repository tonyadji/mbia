package com.lehnade.mbia.memory.application.createstorymemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-29, PR-41: a created story is audited as {@code MEMORY_CREATED} in its transaction, with its
 * type, Persons and photo asset ids but never its text nor a caption (data-model.md §17, OQ-039,
 * OQ-042); a failure after the insert leaves neither the Memory, nor its Persons, nor its photos,
 * and the assets stay unattached (technical-specification.md §14).
 */
class CreateStoryMemoryAuditTest extends ApiTestSupport {

    private static final String TITLE = "Titre-sentinelle-7f3a";
    private static final String CONTENT = "Texte-sentinelle-c91e";
    private static final String CAPTION = "Legende-sentinelle-5b2d";

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private MediaFixtures media;
    private UUID grandmother;
    private UUID mother;

    @BeforeEach
    void givenTwoPersons() {
        family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        media = new MediaFixtures(mvc, jdbc);
        grandmother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
    }

    @Test
    void theCreationIsAuditedWithItsTypeAndPersonsOnly() {
        UUID id = MemoryFixtures.idOf(memories.createStory(family.contributor(), family.familyId(), TITLE, CONTENT,
                grandmother, mother));

        Map<String, Object> row = jdbc.sql("""
                SELECT action, resource_type, resource_id, actor_user_id, old_value::text AS old_value,
                       new_value::text AS new_value
                FROM audit_entries WHERE family_id = ? AND resource_type = 'MEMORY'
                """).param(family.familyId()).query().singleRow();
        assertThat(row)
                .containsEntry("action", "MEMORY_CREATED")
                .containsEntry("resource_id", id)
                .containsEntry("actor_user_id", families().userId(family.contributor()));
        List<String> sorted = List.of(grandmother.toString(), mother.toString()).stream().sorted().toList();
        assertThat((String) row.get("new_value"))
                .contains("\"type\": \"STORY\"")
                .contains("\"relatedPersonIds\": [\"" + sorted.get(0) + "\", \"" + sorted.get(1) + "\"]");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_entries WHERE old_value::text LIKE ? OR new_value::text LIKE ?"
                        + " OR old_value::text LIKE ? OR new_value::text LIKE ?")
                .params("%" + TITLE + "%", "%" + TITLE + "%", "%" + CONTENT + "%", "%" + CONTENT + "%")
                .query(Long.class).single()).isZero();
    }

    @Test
    void theCreationWithPhotosIsAuditedWithTheAssetIdsInOrderButNoCaption() {
        UUID first = photo(family.contributor());
        UUID second = photo(family.contributor());

        UUID id = MemoryFixtures.idOf(createWithPhotos(family.contributor(), first, second));

        String newValue = jdbc.sql("SELECT new_value::text FROM audit_entries WHERE resource_id = ?").param(id)
                .query(String.class).single();
        assertThat(newValue).contains("\"photos\": [\"" + first + "\", \"" + second + "\"]");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_entries WHERE new_value::text LIKE ? OR new_value::text"
                        + " LIKE ? OR new_value::text LIKE ? OR new_value::text LIKE ?")
                .params("%" + CAPTION + "%", "%" + CONTENT + "%", "%families/%", "%X-Amz-%")
                .query(Long.class).single()).isZero();
    }

    @Test
    void aRefusedCreationIsNotAudited() {
        memories.createStory(family.viewer(), family.familyId(), TITLE, CONTENT, mother);
        memories.createStory(family.admin(), family.familyId(), " ", CONTENT, mother);
        memories.createStory(family.admin(), family.familyId(), TITLE, CONTENT, UUID.randomUUID());

        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("MEMORY_CREATED")));
    }

    @Test
    void aFailureAfterTheInsertLeavesNeitherTheMemoryNorItsPersons() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("MEMORY_CREATED")));

        assertThat(memories.createStory(family.admin(), family.familyId(), TITLE, CONTENT, grandmother, mother))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(memories.count(family.familyId())).isZero();
        assertThat(memories.countLinks(family.familyId())).isZero();
    }

    @Test
    void aFailureAfterTheInsertLeavesNoPhotoAndTheAssetsCanBePublishedAgain() {
        UUID first = photo(family.admin());
        UUID second = photo(family.admin());
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("MEMORY_CREATED")));

        assertThat(createWithPhotos(family.admin(), first, second)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(memories.count(family.familyId())).isZero();
        assertThat(memories.countLinks(family.familyId())).isZero();
        assertThat(memories.countPhotos(family.familyId())).isZero();
        assertThat(media.status(first)).isEqualTo("READY");
        assertThat(media.status(second)).isEqualTo("READY");

        doCallRealMethod().when(auditLog).append(any());
        assertThat(createWithPhotos(family.admin(), first, second)).hasStatus(HttpStatus.CREATED);
    }

    private UUID photo(TestJwts.Token uploader) {
        return MediaFixtures.insertRow(jdbc, family.familyId(), families().userId(uploader), "MEMORY_PHOTO",
                "READY");
    }

    private MvcTestResult createWithPhotos(TestJwts.Token token, UUID... photos) {
        return memories.createStoryWithPhotos(token, family.familyId(), CONTENT, Arrays.stream(photos)
                .map(id -> "{\"mediaAssetId\": \"%s\", \"caption\": \"%s\"}".formatted(id, CAPTION))
                .toList(), grandmother, mother);
    }
}
