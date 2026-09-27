package com.lehnade.mbia.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.activity.ActivityFixtures;
import com.lehnade.mbia.activity.ActivityFixtures.Row;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * PR-54, data-model.md §16, Phase 5 plan §3.7: over every activity type, the payload holds only
 * its presentation-safe keys, and never a story text, a biography, a caption, an email address, a
 * token (raw or hashed) or a storage key.
 */
class ActivityPayloadSafetyTest extends ApiTestSupport {

    private static final String STORY = "STORY-TEXT-7f3a";
    private static final String BIOGRAPHY = "BIOGRAPHY-TEXT-51c2";
    private static final String CAPTION = "CAPTION-TEXT-9d04";

    private static final Map<String, Set<String>> ALLOWED_KEYS = Map.of(
            "PERSON_CREATED", Set.of("personDisplayName"),
            "PERSON_ARCHIVED", Set.of("personDisplayName"),
            "PERSON_RESTORED", Set.of("personDisplayName"),
            "PERSON_MERGED", Set.of("personDisplayName", "mergedPersonDisplayName"),
            "RELATIONSHIP_CREATED", Set.of("relationshipType", "sourcePersonId", "sourcePersonDisplayName",
                    "targetPersonId", "targetPersonDisplayName"),
            "RELATIONSHIP_ARCHIVED", Set.of("relationshipType", "sourcePersonId", "sourcePersonDisplayName",
                    "targetPersonId", "targetPersonDisplayName"),
            "MEMORY_CREATED", Set.of("memoryTitle"),
            "INVITATION_ACCEPTED", Set.of("memberDisplayName"),
            "MEMBER_LEFT", Set.of("memberDisplayName"),
            "MEMBER_REMOVED", Set.of("memberDisplayName"));

    @Test
    void noPayloadOfAnyTypeHoldsASecretOrAPrivateText() {
        TestJwts.Token admin = TestJwts.newUserToken().name("Awa Mbida");
        TestJwts.Token contributor = TestJwts.newUserToken().name("Paul Essomba");
        TestJwts.Token viewer = TestJwts.newUserToken().name("Rose Abena");
        TestJwts.Token invitee = TestJwts.newUserToken().name("Chantal Eto");
        FamilyFixtures families = families();
        UUID familyId = families.createFamily(admin, "Famille Mbida");
        UUID contributorId = families.provisionedUserId(contributor);
        UUID viewerId = families.provisionedUserId(viewer);
        families.insertMembership(familyId, contributorId, "CONTRIBUTOR", "ACTIVE");
        families.insertMembership(familyId, viewerId, "VIEWER", "ACTIVE");
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        RelationshipFixtures relationships = new RelationshipFixtures(mvc, jdbc);
        MemberFixtures members = new MemberFixtures(mvc, jdbc);
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);

        UUID marie = persons.createId(admin, familyId,
                "{\"firstName\": \"Marie\", \"lastName\": \"Ngo\", \"biography\": \"" + BIOGRAPHY + "\"}");
        UUID duplicate = persons.createId(admin, familyId,
                "{\"firstName\": \"Marie\", \"biography\": \"" + BIOGRAPHY + "\", \"confirmPossibleDuplicate\": true}");
        UUID jean = persons.createId(admin, familyId, "{\"firstName\": \"Jean\"}");
        assertThat(persons.archive(admin, familyId, jean, "\"0\"")).hasStatusOk();
        assertThat(persons.restore(admin, familyId, jean, "\"1\"")).hasStatusOk();
        UUID link = relationships.parentOfId(admin, familyId, marie, jean);
        assertThat(relationships.remove(admin, familyId, link, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(persons.merge(admin, familyId, duplicate, marie, 0, 0)).hasStatusOk();
        UUID photo = MediaFixtures.insertRow(jdbc, familyId, families.userId(admin), "MEMORY_PHOTO", "READY");
        assertThat(new MemoryFixtures(mvc, jdbc).createStory(admin, familyId, """
                {"title": "Le marché", "content": "%s",
                 "photos": [{"mediaAssetId": "%s", "caption": "%s"}], "relatedPersonIds": ["%s"]}
                """.formatted(STORY, photo, CAPTION, marie))).hasStatus(HttpStatus.CREATED);
        String rawToken = invitations.linkToken(admin, familyId, "CONTRIBUTOR", null);
        assertThat(invitations.acceptLink(invitee, rawToken)).hasStatusOk();
        assertThat(members.remove(contributor, familyId, members.membershipId(familyId, contributorId), "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(members.remove(admin, familyId, members.membershipId(familyId, viewerId), "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        List<Row> rows = new ActivityFixtures(jdbc).of(familyId);
        assertThat(rows).extracting(Row::type).as("every type is covered")
                .containsExactlyInAnyOrderElementsOf(expectedTypes());
        List<String> secrets = secrets(familyId, rawToken);
        for (Row row : rows) {
            assertThat(ALLOWED_KEYS.get(row.type())).as(row.type()).containsAll(row.payload().keySet());
            assertThat(row.payloadJson()).as(row.type()).doesNotContain(secrets);
        }
    }

    /** The three Persons, the archive and restore of Jean, and one of each other type. */
    private static List<String> expectedTypes() {
        return List.of("PERSON_CREATED", "PERSON_CREATED", "PERSON_CREATED", "PERSON_ARCHIVED", "PERSON_RESTORED",
                "RELATIONSHIP_CREATED", "RELATIONSHIP_ARCHIVED", "PERSON_MERGED", "MEMORY_CREATED",
                "INVITATION_ACCEPTED", "MEMBER_LEFT", "MEMBER_REMOVED");
    }

    private List<String> secrets(UUID familyId, String rawToken) {
        List<String> secrets = new ArrayList<>(List.of(STORY, BIOGRAPHY, CAPTION, "@", rawToken,
                InvitationFixtures.sha256Hex(rawToken)));
        secrets.addAll(jdbc.sql("""
                SELECT unnest(ARRAY[upload_storage_key, display_storage_key, thumbnail_storage_key])
                FROM media_assets WHERE family_id = ?
                """).param(familyId).query(String.class).list().stream().filter(key -> key != null).toList());
        secrets.addAll(jdbc.sql("SELECT token_hash FROM family_invitations WHERE family_id = ?")
                .param(familyId).query(String.class).list());
        return secrets;
    }
}
