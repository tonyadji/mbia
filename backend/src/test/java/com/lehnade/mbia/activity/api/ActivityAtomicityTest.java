package com.lehnade.mbia.activity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.activity.ActivityFixtures;
import com.lehnade.mbia.activity.application.ActivityLog;
import com.lehnade.mbia.activity.application.ActivityType;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-54, data-model.md §16, technical-specification.md §14: an activity is written in the
 * transaction of its operation. A failure right after the activity is written, in one operation of
 * each module, leaves neither the activity nor the operation's changes.
 */
class ActivityAtomicityTest extends ApiTestSupport {

    @MockitoSpyBean
    ActivityLog activityLog;

    private FamilyWithMembers family;
    private ActivityFixtures activities;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        activities = new ActivityFixtures(jdbc);
    }

    @Test
    void genealogyAFailedPersonCreationLeavesNoActivityNorPerson() {
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        failAfterRecording(ActivityType.PERSON_CREATED);

        assertFailed(persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}"));

        assertThat(persons.count(family.familyId())).isZero();
        assertThat(activities.of(family.familyId())).isEmpty();
    }

    @Test
    void memoryAFailedMemoryCreationLeavesNoActivityNorMemory() {
        UUID marie = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\"}");
        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        failAfterRecording(ActivityType.MEMORY_CREATED);

        assertFailed(memories.createStory(family.admin(), family.familyId(), "Le marché", "Texte", marie));

        assertThat(memories.count(family.familyId())).isZero();
        assertThat(activities.types(family.familyId())).containsExactly("PERSON_CREATED");
    }

    @Test
    void invitationAFailedAcceptanceLeavesNoActivityNorMembership() {
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        String token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", null);
        TestJwts.Token invitee = TestJwts.newUserToken();
        UUID inviteeId = families().provisionedUserId(invitee);
        failAfterRecording(ActivityType.INVITATION_ACCEPTED);

        assertFailed(invitations.acceptLink(invitee, token));

        assertThat(invitations.memberships(family.familyId(), inviteeId)).isEmpty();
        assertThat(invitations.status(invitations.idOfToken(token))).isEqualTo("PENDING");
        assertThat(activities.of(family.familyId())).isEmpty();
    }

    @Test
    void familyAFailedRemovalLeavesNoActivityAndTheMemberActive() {
        MemberFixtures members = new MemberFixtures(mvc, jdbc);
        UUID membership = members.membershipId(family.familyId(), families().userId(family.viewer()));
        failAfterRecording(ActivityType.MEMBER_REMOVED);

        assertFailed(members.remove(family.admin(), family.familyId(), membership, "\"0\""));

        assertThat(members.membership(membership)).containsEntry("status", "ACTIVE").containsEntry("version", 0L);
        assertThat(activities.of(family.familyId())).isEmpty();
    }

    /** The activity is really written, then the operation fails. */
    private void failAfterRecording(ActivityType type) {
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("Simulated failure after the activity is written");
        }).when(activityLog).record(argThat(activity -> activity.type() == type));
    }

    private static void assertFailed(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
    }
}
