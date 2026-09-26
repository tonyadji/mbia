package com.lehnade.mbia.memory;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Memories created and read through the Memories API, and their rows. */
public final class MemoryFixtures {

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public MemoryFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /** {@code POST …/memories/stories} with a raw body. */
    public MvcTestResult createStory(TestJwts.Token token, UUID familyId, String json) {
        return mvc.post().uri("/api/v1/families/{familyId}/memories/stories", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    /** {@code title} and {@code content} must need no JSON escaping. */
    public MvcTestResult createStory(TestJwts.Token token, UUID familyId, String title, String content,
            UUID... relatedPersonIds) {
        return createStory(token, familyId, """
                {"title": "%s", "content": "%s", "relatedPersonIds": [%s]}
                """.formatted(title, content, Arrays.stream(relatedPersonIds)
                .map(id -> "\"" + id + "\"").collect(Collectors.joining(", "))));
    }

    /** @return the id of a story created by {@code token}, which must be allowed to create it */
    public UUID createStoryId(TestJwts.Token token, UUID familyId, UUID... relatedPersonIds) {
        return idOf(createStory(token, familyId, "Le marché de Yaoundé", "Grand-mère vendait du plantain.",
                relatedPersonIds));
    }

    public MvcTestResult get(TestJwts.Token token, UUID familyId, UUID memoryId) {
        return mvc.get().uri("/api/v1/families/{familyId}/memories/{memoryId}", familyId, memoryId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    /** {@code GET …/persons/{personId}/memories}, with a query string such as {@code ?page=1}, or "". */
    public MvcTestResult listForPerson(TestJwts.Token token, UUID familyId, UUID personId, String query) {
        return mvc.get().uri("/api/v1/families/{familyId}/persons/{personId}/memories" + query, familyId, personId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    /** {@code GET …/families/{familyId}/memories}, with a query string such as {@code ?page=1}, or "". */
    public MvcTestResult listForFamily(TestJwts.Token token, UUID familyId, String query) {
        return mvc.get().uri("/api/v1/families/{familyId}/memories" + query, familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    /**
     * Inserts an ACTIVE story and its Persons directly, without the API, for large fixtures.
     *
     * @return its id
     */
    public UUID insertStory(UUID familyId, UUID createdBy, String title, Instant createdAt,
            UUID... relatedPersonIds) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO memories (id, family_id, type, title, content, created_by, updated_by, created_at,
                                      updated_at)
                VALUES (?, ?, 'STORY', ?, 'Texte', ?, ?, ?, ?)
                """)
                .params(id, familyId, title, createdBy, createdBy, Timestamp.from(createdAt), Timestamp.from(createdAt))
                .update();
        for (UUID personId : relatedPersonIds) {
            jdbc.sql("INSERT INTO memory_persons (family_id, memory_id, person_id, created_at) VALUES (?, ?, ?, now())")
                    .params(familyId, id, personId).update();
        }
        return id;
    }

    /** Sets when the Memory was added, to test the list order. */
    public void createdAt(UUID memoryId, Instant createdAt) {
        jdbc.sql("UPDATE memories SET created_at = ? WHERE id = ?").params(Timestamp.from(createdAt), memoryId)
                .update();
    }

    public static UUID idOf(MvcTestResult result) {
        return UUID.fromString(JsonPath.read(FamilyFixtures.body(result), "$.id"));
    }

    public long count(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM memories WHERE family_id = ?").param(familyId).query(Long.class)
                .single();
    }

    public long countLinks(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM memory_persons WHERE family_id = ?").param(familyId)
                .query(Long.class).single();
    }

    /** Archives the Memory directly: archiving through the API arrives with PR-33. */
    public void archive(UUID memoryId) {
        jdbc.sql("UPDATE memories SET status = 'ARCHIVED', archived_at = now() WHERE id = ?").param(memoryId)
                .update();
    }
}
