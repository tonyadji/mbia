package com.lehnade.mbia.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * PR-18: an update changes identity data and authorship, never identity of the record. PR-19: a
 * claim or a release changes only the link and authorship (person-relationships-collaboration.md §2).
 */
class PersonTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void updateKeepsIdFamilyLinkStatusCreationAndVersion() {
        UUID creator = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        UUID linkedUser = UUID.randomUUID();
        Person person = Person.restore(PersonId.newId(), UUID.randomUUID(), details("Marie"), linkedUser,
                PersonStatus.ACTIVE, creator, creator, CREATED, CREATED, 4);

        Person updated = person.update(details("Maria"), editor, UPDATED);

        assertThat(updated.details().firstName()).isEqualTo("Maria");
        assertThat(updated.updatedBy()).isEqualTo(editor);
        assertThat(updated.updatedAt()).isEqualTo(UPDATED);
        assertThat(updated.id()).isEqualTo(person.id());
        assertThat(updated.familyId()).isEqualTo(person.familyId());
        assertThat(updated.linkedUserId()).contains(linkedUser);
        assertThat(updated.status()).isEqualTo(PersonStatus.ACTIVE);
        assertThat(updated.createdBy()).isEqualTo(creator);
        assertThat(updated.createdAt()).isEqualTo(CREATED);
        assertThat(updated.version()).isEqualTo(4);
        assertThat(person.details().firstName()).isEqualTo("Marie");
    }

    @Test
    void onlyAnActivePersonIsActive() {
        assertThat(person(PersonStatus.ACTIVE).isActive()).isTrue();
        assertThat(person(PersonStatus.ARCHIVED).isActive()).isFalse();
    }

    @Test
    void claimLinksTheUserAndKeepsEverythingElse() {
        UUID user = UUID.randomUUID();
        Person person = person(PersonStatus.ACTIVE);

        Person claimed = person.claim(user, user, UPDATED);

        assertThat(claimed.linkedUserId()).contains(user);
        assertThat(claimed.isLinkedTo(user)).isTrue();
        assertThat(claimed.isLinkedTo(UUID.randomUUID())).isFalse();
        assertThat(claimed.updatedBy()).isEqualTo(user);
        assertThat(claimed.updatedAt()).isEqualTo(UPDATED);
        assertThat(claimed.details()).isEqualTo(person.details());
        assertThat(claimed.version()).isEqualTo(person.version());
        assertThat(person.linkedUserId()).isEmpty();
    }

    @Test
    void unclaimReleasesTheLinkAndKeepsTheData() {
        UUID user = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        Person claimed = person(PersonStatus.ACTIVE).claim(user, user, CREATED);

        Person released = claimed.unclaim(admin, UPDATED);

        assertThat(released.linkedUserId()).isEmpty();
        assertThat(released.isLinkedTo(user)).isFalse();
        assertThat(released.updatedBy()).isEqualTo(admin);
        assertThat(released.details()).isEqualTo(claimed.details());
        assertThat(released.status()).isEqualTo(PersonStatus.ACTIVE);
    }

    private static Person person(PersonStatus status) {
        UUID creator = UUID.randomUUID();
        return Person.restore(PersonId.newId(), UUID.randomUUID(), details("Marie"), null, status, creator, creator,
                CREATED, CREATED, 0);
    }

    private static PersonDetails details(String firstName) {
        return new PersonDetails(firstName, null, null, null, null, null, false, null, null);
    }
}
