package com.lehnade.mbia.genealogy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/** V004: the database is the last guard of data-model.md §11 invariants. */
class RelationshipsSchemaTest extends ApiTestSupport {

    private UUID familyId;
    private UUID userId;
    private UUID low;
    private UUID high;

    @BeforeEach
    void givenAFamilyWithTwoPersons() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        userId = families().userId(admin);
        // Byte order and Java's signed UUID order disagree on these two.
        low = person(familyId, startingWith('0'));
        high = person(familyId, startingWith('8'));
    }

    @Test
    void aSelfRelationIsRefused() {
        assertThatThrownBy(() -> insert("PARENT_OF", low, low, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void partnersAreStoredInByteOrderAsComputedByTheDomain() {
        FamilyRelationship canonical = FamilyRelationship.create(RelationshipId.newId(), familyId,
                RelationshipType.PARTNER_OF, new PersonId(high), new PersonId(low), userId, Instant.now());

        assertThatThrownBy(() -> insert("PARTNER_OF", high, low, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insert("PARTNER_OF", canonical.source().value(), canonical.target().value(), "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"type = 'SIBLING_OF'", "status = 'DELETED'"})
    void enumerationsAreChecked(String assignment) {
        UUID id = insert("PARENT_OF", low, high, "ACTIVE");

        assertThatThrownBy(() -> jdbc.sql("UPDATE family_relationships SET " + assignment + " WHERE id = ?")
                .param(id).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARENT_OF", "PARTNER_OF"})
    void onlyOneActiveRelationPerTypeAndPair(String type) {
        insert(type, low, high, "ACTIVE");

        assertThatThrownBy(() -> insert(type, low, high, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(type.equals("PARENT_OF")
                        ? "uq_active_parent_relationship" : "uq_active_partner_relationship");
        assertThatCode(() -> insert(type, low, high, "ARCHIVED")).doesNotThrowAnyException();
    }

    @Test
    void bothPersonsBelongToTheRelationshipsFamily() {
        TestJwts.Token other = TestJwts.newUserToken();
        UUID otherFamily = families().createFamily(other, "Autre famille");
        UUID stranger = person(otherFamily, UUID.randomUUID());

        assertThatThrownBy(() -> insert("PARENT_OF", stranger, low, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("PARENT_OF", low, stranger, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aNewRowIsActiveAtVersionZero() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO family_relationships (id, family_id, type, source_person_id, target_person_id,
                                                  created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'PARENT_OF', ?, ?, ?, ?, ?, ?)
                """).params(id, familyId, low, high, userId, userId, now, now).update();

        assertThat(jdbc.sql("SELECT status || ':' || version FROM family_relationships WHERE id = ?").param(id)
                .query(String.class).single()).isEqualTo("ACTIVE:0");
    }

    private static UUID startingWith(char hexDigit) {
        return UUID.fromString(hexDigit + UUID.randomUUID().toString().substring(1));
    }

    private UUID person(UUID family, UUID id) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO persons (id, family_id, first_name, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Someone', ?, ?, ?, ?)
                """).params(id, family, userId, userId, now, now).update();
        return id;
    }

    private UUID insert(String type, UUID source, UUID target, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO family_relationships (id, family_id, type, source_person_id, target_person_id, status,
                                                  created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(id, familyId, type, source, target, status, userId, userId, now, now).update();
        return id;
    }
}
