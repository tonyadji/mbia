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

    private static FamilyRelationship create(RelationshipType type, PersonId source, PersonId target) {
        return FamilyRelationship.create(RelationshipId.newId(), FAMILY, type, source, target, USER, NOW);
    }
}
