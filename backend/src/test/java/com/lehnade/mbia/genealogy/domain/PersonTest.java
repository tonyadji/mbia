package com.lehnade.mbia.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * PR-18: an update changes identity data and authorship, never identity of the record. PR-19: a
 * claim or a release changes only the link and authorship (person-relationships-collaboration.md §2).
 * PR-26: an archive or a restore changes only the status, its date and authorship (§5).
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
                PersonStatus.ACTIVE, creator, creator, CREATED, CREATED, null, 4);

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

    @Test
    void archiveHidesThePersonAndKeepsItsData() {
        UUID admin = UUID.randomUUID();
        Person person = person(PersonStatus.ACTIVE);

        Person archived = person.archive(admin, UPDATED);

        assertThat(archived.status()).isEqualTo(PersonStatus.ARCHIVED);
        assertThat(archived.isActive()).isFalse();
        assertThat(archived.archivedAt()).contains(UPDATED);
        assertThat(archived.updatedBy()).isEqualTo(admin);
        assertThat(archived.updatedAt()).isEqualTo(UPDATED);
        assertThat(archived.details()).isEqualTo(person.details());
        assertThat(archived.createdBy()).isEqualTo(person.createdBy());
        assertThat(archived.version()).isEqualTo(person.version());
        assertThat(person.archivedAt()).isEmpty();
    }

    @Test
    void unarchiveMakesThePersonActiveAgain() {
        UUID admin = UUID.randomUUID();
        Person archived = person(PersonStatus.ARCHIVED);

        Person restored = archived.unarchive(admin, UPDATED);

        assertThat(restored.status()).isEqualTo(PersonStatus.ACTIVE);
        assertThat(restored.archivedAt()).isEmpty();
        assertThat(restored.updatedBy()).isEqualTo(admin);
        assertThat(restored.details()).isEqualTo(archived.details());
    }

    @Test
    void onlyAnActivePersonCanBeArchivedAndOnlyAnArchivedOneRestored() {
        UUID admin = UUID.randomUUID();

        assertThatThrownBy(() -> person(PersonStatus.ARCHIVED).archive(admin, UPDATED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> person(PersonStatus.ACTIVE).unarchive(admin, UPDATED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void archivedAtIsSetExactlyWhenArchived() {
        UUID creator = UUID.randomUUID();

        assertThatThrownBy(() -> Person.restore(PersonId.newId(), UUID.randomUUID(), details("Marie"), null,
                PersonStatus.ARCHIVED, creator, creator, CREATED, CREATED, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Person.restore(PersonId.newId(), UUID.randomUUID(), details("Marie"), null,
                PersonStatus.ACTIVE, creator, creator, CREATED, CREATED, CREATED, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPersonIsLinkedWhenItRepresentsAUser() {
        UUID user = UUID.randomUUID();

        assertThat(person(PersonStatus.ACTIVE).isLinked()).isFalse();
        assertThat(person(PersonStatus.ACTIVE).claim(user, user, UPDATED).isLinked()).isTrue();
    }

    private static Person person(PersonStatus status) {
        UUID creator = UUID.randomUUID();
        return Person.restore(PersonId.newId(), UUID.randomUUID(), details("Marie"), null, status, creator, creator,
                CREATED, CREATED, status == PersonStatus.ARCHIVED ? CREATED : null, 0);
    }

    private static PersonDetails details(String firstName) {
        return new PersonDetails(firstName, null, null, null, null, null, false, null, null);
    }
}
