package com.lehnade.mbia.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * PR-20: a relationship never links a Person to themselves; {@code PARENT_OF} keeps its direction,
 * {@code PARTNER_OF} is stored once in canonical UUID order (mvp.md §10, data-model.md §11.1–11.2).
 * PR-24: removal archives the relationship and restoration makes it ACTIVE again
 * (person-relationships-collaboration.md §8, data-model.md §20). PR-27: a merge replaces the
 * duplicate by the kept Person and puts {@code PARTNER_OF} back in canonical order (data-model.md
 * §19).
 */
class FamilyRelationshipTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    // Signed comparison (UUID.compareTo) and byte order disagree on these two: 8… is negative as a long.
    private static final PersonId LOW = new PersonId(UUID.fromString("0fffffff-0000-4000-8000-000000000000"));
    private static final PersonId HIGH = new PersonId(UUID.fromString("80000000-0000-4000-8000-000000000000"));

    @ParameterizedTest
    @EnumSource(RelationshipType.class)
    void aPersonCannotBeRelatedToThemselves(RelationshipType type) {
        PersonId marie = PersonId.newId();

        assertThatThrownBy(() -> create(type, marie, new PersonId(marie.value())))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code().name()).isEqualTo("SELF_RELATIONSHIP_NOT_ALLOWED"));
    }

    @Test
    void parentOfKeepsParentAsSourceAndChildAsTarget() {
        FamilyRelationship relationship = create(RelationshipType.PARENT_OF, HIGH, LOW);

        assertThat(relationship.source()).isEqualTo(HIGH);
        assertThat(relationship.target()).isEqualTo(LOW);
    }

    @Test
    void partnerOfIsStoredInUuidByteOrderWhateverTheRequestOrder() {
        FamilyRelationship given = create(RelationshipType.PARTNER_OF, LOW, HIGH);
        FamilyRelationship reversed = create(RelationshipType.PARTNER_OF, HIGH, LOW);

        assertThat(LOW.value().compareTo(HIGH.value())).as("signed Java order").isPositive();
        assertThat(given.source()).isEqualTo(LOW);
        assertThat(given.target()).isEqualTo(HIGH);
        assertThat(reversed.source()).isEqualTo(LOW);
        assertThat(reversed.target()).isEqualTo(HIGH);
    }

    @Test
    void aNewRelationshipIsActiveAtVersionZeroAndAuthoredByItsCreator() {
        FamilyRelationship relationship = create(RelationshipType.PARENT_OF, LOW, HIGH);

        assertThat(relationship.status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(relationship.version()).isZero();
        assertThat(relationship.familyId()).isEqualTo(FAMILY);
        assertThat(relationship.createdBy()).isEqualTo(USER);
        assertThat(relationship.updatedBy()).isEqualTo(USER);
        assertThat(relationship.createdAt()).isEqualTo(NOW);
        assertThat(relationship.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void removalArchivesTheRelationshipAndRecordsWhoAndWhen() {
        UUID remover = UUID.randomUUID();
        Instant later = NOW.plusSeconds(60);

        FamilyRelationship archived = create(RelationshipType.PARENT_OF, LOW, HIGH).archive(remover, later);

        assertThat(archived.status()).isEqualTo(RelationshipStatus.ARCHIVED);
        assertThat(archived.isActive()).isFalse();
        assertThat(archived.archivedAt()).isEqualTo(later);
        assertThat(archived.updatedBy()).isEqualTo(remover);
        assertThat(archived.updatedAt()).isEqualTo(later);
        assertThat(archived.createdBy()).isEqualTo(USER);
        assertThat(archived.source()).isEqualTo(LOW);
        assertThat(archived.target()).isEqualTo(HIGH);
        assertThat(archived.version()).as("incremented when stored").isZero();
    }

    @Test
    void restorationMakesTheRelationshipActiveAgain() {
        UUID admin = UUID.randomUUID();
        Instant later = NOW.plusSeconds(120);

        FamilyRelationship restored = create(RelationshipType.PARTNER_OF, LOW, HIGH)
                .archive(USER, NOW.plusSeconds(60))
                .unarchive(admin, later);

        assertThat(restored.status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(restored.archivedAt()).isNull();
        assertThat(restored.updatedBy()).isEqualTo(admin);
        assertThat(restored.updatedAt()).isEqualTo(later);
    }

    @Test
    void aRelationshipIsArchivedOnlyOnceAndRestoredOnlyWhenArchived() {
        FamilyRelationship active = create(RelationshipType.PARENT_OF, LOW, HIGH);

        assertThatThrownBy(() -> active.unarchive(USER, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> active.archive(USER, NOW).archive(USER, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMergeReplacesTheDuplicateAndKeepsDirectionStatusAndCreation() {
        PersonId child = PersonId.newId();
        UUID admin = UUID.randomUUID();
        Instant later = NOW.plusSeconds(60);
        FamilyRelationship removed = create(RelationshipType.PARENT_OF, LOW, child).archive(USER, NOW);

        FamilyRelationship moved = removed.replacePerson(LOW, HIGH, admin, later);

        assertThat(moved.source()).isEqualTo(HIGH);
        assertThat(moved.target()).isEqualTo(child);
        assertThat(moved.id()).isEqualTo(removed.id());
        assertThat(moved.status()).isEqualTo(RelationshipStatus.ARCHIVED);
        assertThat(moved.archivedAt()).isEqualTo(NOW);
        assertThat(moved.createdAt()).isEqualTo(NOW);
        assertThat(moved.updatedBy()).isEqualTo(admin);
        assertThat(moved.updatedAt()).isEqualTo(later);
        assertThat(create(RelationshipType.PARENT_OF, child, LOW).replacePerson(LOW, HIGH, admin, later).target())
                .isEqualTo(HIGH);
    }

    @Test
    void aMergedPartnerOfIsPutBackInCanonicalOrder() {
        PersonId middle = new PersonId(UUID.fromString("40000000-0000-4000-8000-000000000000"));
        FamilyRelationship partners = create(RelationshipType.PARTNER_OF, middle, HIGH);

        FamilyRelationship moved = partners.replacePerson(HIGH, LOW, USER, NOW);

        assertThat(moved.source()).isEqualTo(LOW);
        assertThat(moved.target()).isEqualTo(middle);
    }

    @Test
    void aMergeNeverRelatesAPersonToThemselves() {
        FamilyRelationship between = create(RelationshipType.PARENT_OF, LOW, HIGH);

        assertThat(between.links(HIGH, LOW)).isTrue();
        assertThatThrownBy(() -> between.replacePerson(HIGH, LOW, USER, NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code().name()).isEqualTo("SELF_RELATIONSHIP_NOT_ALLOWED"));
    }

    private static FamilyRelationship create(RelationshipType type, PersonId source, PersonId target) {
        return FamilyRelationship.create(RelationshipId.newId(), FAMILY, type, source, target, USER, NOW);
    }
}
