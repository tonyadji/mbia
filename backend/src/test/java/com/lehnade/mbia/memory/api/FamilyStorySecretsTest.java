package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-63 (Phase 6 plan, PR-63; mvp.md §17, §20; data-model.md §17): over the family story, a first
 * memory told about me with its year and a captioned photo, a Person added on the way, its date
 * changed then removed, a future date refused, the strip of years, a year and the undated Memories
 * read by the Family and refused to a member of another Family, no story text, title or caption
 * reaches the logs, the audit or the activity; the audit keeps the dates only.
 */
@ExtendWith(OutputCaptureExtension.class)
class FamilyStorySecretsTest extends ApiTestSupport {

    private static final String TITLE = "Sentinelle: le premier vélo";
    private static final String STORY = "Sentinelle: papa me tenait par la selle";
    private static final String CAPTION = "Sentinelle: devant la maison de Douala";
    private static final String OTHER_TITLE = "Sentinelle: le mariage de Marie";
    private static final String OTHER_STORY = "Sentinelle: tout le quartier était là";

    @Test
    void noStoryTitleOrCaptionIsLoggedAuditedOrInTheActivity(CapturedOutput output) {
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        TestJwts.Token admin = TestJwts.newUserToken().name("Tony Mbida");
        TestJwts.Token outsider = TestJwts.newUserToken().name("Jean Atangana");
        UUID familyId = families().createFamily(admin, "Famille Mbida");
        families().createFamily(outsider, "Famille Atangana");

        // A first memory about me (OQ-065): my Person, then the story, in 1975, with a captioned photo.
        UUID me = persons.createId(admin, familyId, "{\"firstName\": \"Tony\", \"linkToCurrentUser\": true}");
        UUID photo = MediaFixtures.insertRow(jdbc, familyId, families().userId(admin), "MEMORY_PHOTO", "READY");
        MvcTestResult first = memories.createStory(admin, familyId, """
                {"title": "%s", "content": "%s",
                 "happenedAt": {"precision": "YEAR_ONLY", "year": 1975},
                 "photos": [{"mediaAssetId": "%s", "caption": "%s"}],
                 "relatedPersonIds": ["%s"]}
                """.formatted(TITLE, STORY, photo, CAPTION, me));
        assertThat(first).hasStatus(HttpStatus.CREATED);
        UUID firstId = MemoryFixtures.idOf(first);

        // Marie, added on the way, then an undated memory about her; a future date is refused.
        UUID marie = persons.createId(admin, familyId, "{\"firstName\": \"Marie\", \"lastName\": \"Ngo\"}");
        assertThat(memories.createStory(admin, familyId, OTHER_TITLE, OTHER_STORY, marie))
                .hasStatus(HttpStatus.CREATED);
        int nextYear = LocalDate.now(ZoneOffset.UTC).getYear() + 1;
        assertThat(memories.createStory(admin, familyId, """
                {"title": "%s", "content": "%s", "happenedAt": {"precision": "YEAR_ONLY", "year": %d},
                 "relatedPersonIds": ["%s"]}
                """.formatted(OTHER_TITLE, OTHER_STORY, nextYear, marie))).hasStatus(HttpStatus.BAD_REQUEST);

        // The date of the first memory changes to 12 March 1962, is refused in the future, then is removed.
        assertThat(memories.update(admin, familyId, firstId, "\"0\"",
                "{\"happenedAt\": {\"precision\": \"EXACT\", \"date\": \"1962-03-12\"}}")).hasStatusOk();
        assertThat(memories.update(admin, familyId, firstId, "\"1\"",
                "{\"happenedAt\": {\"precision\": \"YEAR_ONLY\", \"year\": " + nextYear + "}}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(memories.update(admin, familyId, firstId, "\"1\"",
                "{\"happenedAt\": {\"precision\": \"UNKNOWN\"}}")).hasStatusOk();
        assertThat(memories.update(admin, familyId, firstId, "\"2\"",
                "{\"happenedAt\": {\"precision\": \"YEAR_ONLY\", \"year\": 1975}}")).hasStatusOk();

        // The Family reads its story; a member of another Family is refused, and the refusal is logged.
        assertThat(memories.storyYears(admin, familyId)).hasStatusOk();
        assertThat(memories.listForFamily(admin, familyId, "?year=1975")).hasStatusOk();
        assertThat(memories.listForFamily(admin, familyId, "?undated=true")).hasStatusOk();
        assertThat(memories.listForFamily(admin, familyId, "?year=1975&undated=true"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(memories.storyYears(outsider, familyId)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(memories.listForFamily(outsider, familyId, "?year=1975")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(memories.listForFamily(outsider, familyId, "?undated=true")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(memories.update(outsider, familyId, firstId, "\"3\"",
                "{\"happenedAt\": {\"precision\": \"UNKNOWN\"}}")).hasStatus(HttpStatus.NOT_FOUND);

        List<String> texts = List.of(TITLE, STORY, CAPTION, OTHER_TITLE, OTHER_STORY);
        for (String text : texts) {
            assertThat(output.getAll()).as("logs").doesNotContain(text);
        }
        String audit = String.join("\n", jdbc.sql("""
                SELECT action || ' ' || coalesce(old_value::text, '') || ' ' || coalesce(new_value::text, '')
                FROM audit_entries WHERE family_id = ?
                """).param(familyId).query(String.class).list());
        // The audit keeps the dates of happenedAt (data-model.md §17).
        assertThat(audit).contains("MEMORY_CREATED", "MEMORY_UPDATED", "1975", "1962-03-12", "UNKNOWN");
        String activity = String.join("\n", jdbc.sql("""
                SELECT activity_type || ' ' || coalesce(payload::text, '') FROM activities WHERE family_id = ?
                """).param(familyId).query(String.class).list());
        assertThat(activity).contains("MEMORY_CREATED");
        // MEMORY_CREATED keeps the title of the Memory only (OQ-063, data-model.md §17): never its text,
        // its caption nor its date.
        for (String text : List.of(STORY, CAPTION, OTHER_STORY)) {
            assertThat(audit).as("audit").doesNotContain(text);
            assertThat(activity).as("activity").doesNotContain(text);
        }
        assertThat(audit).as("audit").doesNotContain(TITLE).doesNotContain(OTHER_TITLE);
        assertThat(activity).as("activity").doesNotContain("happenedAt").doesNotContain("1962");
    }
}
