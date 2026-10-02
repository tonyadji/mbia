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
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * PR-54, data-model.md §16, OQ-054: each of the ten operations writes one activity with its actor,
 * resource and the names at the time of the action; edits, role changes, invitations and the
 * operations that change nothing or are refused write none.
 */
class ActivityRecordingApiTest extends ApiTestSupport {

    private final TestJwts.Token admin = TestJwts.newUserToken().name("Awa Mbida");
    private final TestJwts.Token contributor = TestJwts.newUserToken().name("Paul Essomba");
    private final TestJwts.Token viewer = TestJwts.newUserToken().name("Rose Abena");

    private UUID familyId;
    private UUID adminId;
    private UUID contributorId;
    private UUID viewerId;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private MemoryFixtures memories;
    private InvitationFixtures invitations;
    private MemberFixtures members;
    private ActivityFixtures activities;

    @BeforeEach
    void givenAFamilyWithMembers() {
        FamilyFixtures families = families();
        familyId = families.createFamily(admin, "Famille Mbida");
        adminId = families.userId(admin);
        contributorId = families.provisionedUserId(contributor);
        viewerId = families.provisionedUserId(viewer);
        families.insertMembership(familyId, contributorId, "CONTRIBUTOR", "ACTIVE");
        families.insertMembership(familyId, viewerId, "VIEWER", "ACTIVE");
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        invitations = new InvitationFixtures(mvc, jdbc);
        members = new MemberFixtures(mvc, jdbc);
        activities = new ActivityFixtures(jdbc);
    }

    @Test
    void theFeedStartsEmpty() {
        assertThat(activities.of(familyId)).isEmpty();
    }

    @Test
    void creatingAPersonRecordsItsNameAndActor() {
        UUID marie = persons.createId(contributor, familyId, "{\"firstName\": \"Marie\", \"lastName\": \"Ngo\"}");

        assertThat(activities.of(familyId)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo("PERSON_CREATED");
            assertPersonActivity(row, contributorId, marie, "Marie Ngo");
        });
    }

    @Test
    void startWithMeRecordsOnePersonCreated() {
        UUID me = persons.createId(contributor, familyId,
                "{\"firstName\": \"Paul\", \"preferredName\": \"Popol\", \"linkToCurrentUser\": true}");

        assertThat(activities.of(familyId)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo("PERSON_CREATED");
            assertThat(row.resourceId()).isEqualTo(me);
            assertThat(row.payload()).isEqualTo(Map.of("personDisplayName", "Popol"));
        });
    }

    @Test
    void archivingAndRestoringAPersonKeepTheNameOfTheirTime() {
        UUID marie = persons.createId(admin, familyId, "{\"firstName\": \"Marie\"}");

        assertThat(persons.archive(admin, familyId, marie, "\"0\"")).hasStatusOk();
        assertThat(persons.archive(admin, familyId, marie, "\"1\"")).as("already archived").hasStatusOk();
        assertThat(persons.restore(admin, familyId, marie, "\"1\"")).hasStatusOk();
        assertThat(persons.restore(admin, familyId, marie, "\"2\"")).as("already active").hasStatusOk();
        assertThat(persons.update(admin, familyId, marie, "\"2\"", "{\"firstName\": \"Mariette\"}")).hasStatusOk();

        assertThat(activities.types(familyId)).containsExactly("PERSON_CREATED", "PERSON_ARCHIVED", "PERSON_RESTORED");
        assertPersonActivity(activities.of(familyId, "PERSON_ARCHIVED").getFirst(), adminId, marie, "Marie");
        assertPersonActivity(activities.of(familyId, "PERSON_RESTORED").getFirst(), adminId, marie, "Marie");
    }

    @Test
    void aMergeRecordsOneActivityOnThePersonKept() {
        UUID kept = persons.createId(admin, familyId, "{\"firstName\": \"Marie\", \"lastName\": \"Ngo\"}");
        UUID duplicate = persons.createId(admin, familyId,
                "{\"firstName\": \"Maria\", \"lastName\": \"Ngo\", \"confirmPossibleDuplicate\": true}");
        UUID child = persons.createId(admin, familyId, "{\"firstName\": \"Jean\"}");
        relationships.parentOfId(admin, familyId, kept, child);
        relationships.parentOfId(admin, familyId, duplicate, child);
        int before = activities.of(familyId).size();

        assertThat(persons.merge(admin, familyId, duplicate, kept, 0, 0)).hasStatusOk();

        assertThat(activities.of(familyId)).hasSize(before + 1);
        assertThat(activities.of(familyId).getLast()).satisfies(row -> {
            assertThat(row.type()).isEqualTo("PERSON_MERGED");
            assertThat(row.actorUserId()).isEqualTo(adminId);
            assertThat(row.resourceType()).isEqualTo("PERSON");
            assertThat(row.resourceId()).isEqualTo(kept);
            assertThat(row.payload()).isEqualTo(Map.of("personDisplayName", "Marie Ngo",
                    "mergedPersonDisplayName", "Maria Ngo"));
        });
        assertThat(activities.of(familyId, "RELATIONSHIP_ARCHIVED"))
                .as("the duplicate relationship archived by the merge is part of the merge").isEmpty();
    }

    @Test
    void addingAndRemovingARelationshipNameBothPersons() {
        UUID marie = persons.createId(admin, familyId, "{\"firstName\": \"Marie\"}");
        UUID jean = persons.createId(admin, familyId, "{\"firstName\": \"Jean\"}");
        UUID link = relationships.parentOfId(contributor, familyId, marie, jean);
        persons.update(admin, familyId, marie, "\"0\"", "{\"firstName\": \"Mariette\"}");

        assertThat(relationships.remove(contributor, familyId, link, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(relationships.remove(contributor, familyId, link, "\"1\"")).as("already removed")
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(relationships.restore(admin, familyId, link, "\"1\"")).hasStatusOk();

        assertThat(activities.types(familyId)).containsExactly("PERSON_CREATED", "PERSON_CREATED",
                "RELATIONSHIP_CREATED", "RELATIONSHIP_ARCHIVED");
        assertRelationshipActivity(activities.of(familyId, "RELATIONSHIP_CREATED").getFirst(), link, marie, "Marie",
                jean);
        assertRelationshipActivity(activities.of(familyId, "RELATIONSHIP_ARCHIVED").getFirst(), link, marie,
                "Mariette", jean);
    }

    @Test
    void addingAMemoryRecordsItsTitle() {
        UUID marie = persons.createId(admin, familyId, "{\"firstName\": \"Marie\"}");
        UUID memory = MemoryFixtures.idOf(memories.createStory(contributor, familyId, "Le marché", "Texte", marie));
        assertThat(memories.update(contributor, familyId, memory, "\"0\"", "{\"title\": \"Au marché\"}"))
                .hasStatusOk();
        assertThat(memories.archive(contributor, familyId, memory, "\"1\"")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(activities.of(familyId, "MEMORY_CREATED")).singleElement().satisfies(row -> {
            assertThat(row.actorUserId()).isEqualTo(contributorId);
            assertThat(row.resourceType()).isEqualTo("MEMORY");
            assertThat(row.resourceId()).isEqualTo(memory);
            assertThat(row.payload()).isEqualTo(Map.of("memoryTitle", "Le marché"));
        });
        assertThat(activities.types(familyId)).containsExactly("PERSON_CREATED", "MEMORY_CREATED");
    }

    @Test
    void joiningRecordsTheNewMemberOnTheirMembership() {
        TestJwts.Token invitee = TestJwts.newUserToken().name("Chantal Eto");
        String token = invitations.linkToken(admin, familyId, "VIEWER", null);

        assertThat(invitations.acceptLink(invitee, token)).hasStatusOk();

        UUID inviteeId = families().userId(invitee);
        assertThat(activities.of(familyId)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo("INVITATION_ACCEPTED");
            assertThat(row.actorUserId()).isEqualTo(inviteeId);
            assertThat(row.resourceType()).isEqualTo("MEMBERSHIP");
            assertThat(row.resourceId()).isEqualTo(members.membershipId(familyId, inviteeId));
            assertThat(row.payload()).isEqualTo(Map.of("memberDisplayName", "Chantal Eto"));
        });
    }

    @Test
    void anActiveMemberOpeningALinkRecordsNothing() {
        String token = invitations.linkToken(admin, familyId, "VIEWER", null);

        assertThat(invitations.acceptLink(contributor, token)).hasStatusOk()
                .bodyJson().extractingPath("$.alreadyMember").isEqualTo(true);

        assertThat(activities.of(familyId)).isEmpty();
    }

    @Test
    void leavingRecordsTheMemberWhoLeft() {
        UUID membership = members.membershipId(familyId, contributorId);

        assertThat(members.remove(contributor, familyId, membership, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(activities.of(familyId)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo("MEMBER_LEFT");
            assertThat(row.actorUserId()).isEqualTo(contributorId);
            assertThat(row.resourceType()).isEqualTo("MEMBERSHIP");
            assertThat(row.resourceId()).isEqualTo(membership);
            assertThat(row.payload()).isEqualTo(Map.of("memberDisplayName", "Paul Essomba"));
        });
    }

    @Test
    void removingRecordsTheMemberRemovedAndTheAdmin() {
        UUID membership = members.membershipId(familyId, viewerId);

        assertThat(members.remove(admin, familyId, membership, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(activities.of(familyId)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo("MEMBER_REMOVED");
            assertThat(row.actorUserId()).isEqualTo(adminId);
            assertThat(row.resourceType()).isEqualTo("MEMBERSHIP");
            assertThat(row.resourceId()).isEqualTo(membership);
            assertThat(row.payload()).isEqualTo(Map.of("memberDisplayName", "Rose Abena"));
        });
    }

    @Test
    void roleChangesAndInvitationsRecordNothing() {
        UUID membership = members.membershipId(familyId, contributorId);
        assertThat(members.changeRole(admin, familyId, membership, "\"0\"", "VIEWER")).hasStatusOk();
        UUID invitation = invitations.inviteLinkId(admin, familyId, "CONTRIBUTOR", null);
        assertThat(invitations.renew(admin, familyId, invitation, "\"0\"")).hasStatusOk();
        assertThat(invitations.revoke(admin, familyId, invitation, "\"1\"")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(activities.of(familyId)).isEmpty();
    }

    @Test
    void claimingAndReleasingAPersonRecordNothingMore() {
        UUID marie = persons.createId(admin, familyId, "{\"firstName\": \"Marie\"}");
        assertThat(persons.claim(contributor, familyId, marie, "\"0\"")).hasStatusOk();
        assertThat(persons.unclaim(contributor, familyId, marie, "\"1\"")).hasStatusOk();

        assertThat(activities.types(familyId)).containsExactly("PERSON_CREATED");
    }

    @Test
    void aRefusedOperationRecordsNothing() {
        assertThat(persons.create(viewer, familyId, "{\"firstName\": \"Marie\"}")).hasStatus(HttpStatus.FORBIDDEN);
        UUID marie = persons.createId(admin, familyId, "{\"firstName\": \"Marie\"}");
        UUID jean = persons.createId(admin, familyId, "{\"firstName\": \"Jean\"}");
        relationships.parentOfId(admin, familyId, marie, jean);
        assertThat(relationships.parentOf(admin, familyId, marie, jean)).hasStatus(HttpStatus.CONFLICT);
        assertThat(persons.archive(admin, familyId, marie, "\"7\"")).hasStatus(HttpStatus.CONFLICT);
        UUID adminMembership = members.membershipId(familyId, adminId);
        assertThat(members.remove(admin, familyId, adminMembership, "\"0\"")).hasStatus(HttpStatus.CONFLICT);

        assertThat(activities.types(familyId)).containsExactly("PERSON_CREATED", "PERSON_CREATED",
                "RELATIONSHIP_CREATED");
    }

    private static void assertPersonActivity(Row row, UUID actor, UUID person, String name) {
        assertThat(row.actorUserId()).isEqualTo(actor);
        assertThat(row.resourceType()).isEqualTo("PERSON");
        assertThat(row.resourceId()).isEqualTo(person);
        assertThat(row.payload()).isEqualTo(Map.of("personDisplayName", name));
    }

    private void assertRelationshipActivity(Row row, UUID link, UUID parent, String parentName, UUID child) {
        assertThat(row.actorUserId()).isEqualTo(contributorId);
        assertThat(row.resourceType()).isEqualTo("RELATIONSHIP");
        assertThat(row.resourceId()).isEqualTo(link);
        assertThat(row.payload()).isEqualTo(Map.of("relationshipType", "PARENT_OF",
                "sourcePersonId", parent.toString(), "sourcePersonDisplayName", parentName,
                "targetPersonId", child.toString(), "targetPersonDisplayName", "Jean"));
    }
}
