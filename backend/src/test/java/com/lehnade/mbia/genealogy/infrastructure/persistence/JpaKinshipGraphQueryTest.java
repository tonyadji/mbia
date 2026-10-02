package com.lehnade.mbia.genealogy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PR-21, genealogy.md §9: the kinship graph holds the ACTIVE relationships of one Family whose two
 * Persons are ACTIVE; archived relationships and Persons are not traversed.
 */
class JpaKinshipGraphQueryTest extends ApiTestSupport {

    @Autowired
    KinshipGraphQuery graphQuery;

    private UUID familyId;
    private UUID userId;

    @BeforeEach
    void givenAFamily() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        userId = families().userId(admin);
    }

    @Test
    void activeRelationsOfBothTypesAreWalked() {
        UUID grandparent = person(familyId, "ACTIVE");
        UUID parent = person(familyId, "ACTIVE");
        UUID child = person(familyId, "ACTIVE");
        UUID partner = person(familyId, "ACTIVE");
        relation(familyId, "PARENT_OF", grandparent, parent, "ACTIVE");
        relation(familyId, "PARENT_OF", parent, child, "ACTIVE");
        partners(child, partner);

        assertThat(kinship(child, grandparent)).isEqualTo(KinshipCode.GRANDPARENT);
        assertThat(kinship(grandparent, child)).isEqualTo(KinshipCode.GRANDCHILD);
        assertThat(kinship(partner, child)).isEqualTo(KinshipCode.PARTNER);
        assertThat(kinship(partner, parent)).isEqualTo(KinshipCode.RELATED);
    }

    @Test
    void anArchivedRelationshipIsNotTraversed() {
        UUID parent = person(familyId, "ACTIVE");
        UUID child = person(familyId, "ACTIVE");
        relation(familyId, "PARENT_OF", parent, child, "ARCHIVED");

        assertThat(kinship(child, parent)).isEqualTo(KinshipCode.NONE_KNOWN);
    }

    @Test
    void archivedAndMergedPersonsAreNotTraversed() {
        for (String status : new String[] {"ARCHIVED", "MERGED"}) {
            UUID grandparent = person(familyId, "ACTIVE");
            UUID parent = person(familyId, status);
            UUID child = person(familyId, "ACTIVE");
            relation(familyId, "PARENT_OF", grandparent, parent, "ACTIVE");
            relation(familyId, "PARENT_OF", parent, child, "ACTIVE");

            assertThat(kinship(child, grandparent)).as(status).isEqualTo(KinshipCode.NONE_KNOWN);
        }
    }

    @Test
    void relationsOfAnotherFamilyAreNotLoaded() {
        TestJwts.Token other = TestJwts.newUserToken();
        UUID otherFamily = families().createFamily(other, "Autre famille");
        UUID parent = person(otherFamily, "ACTIVE");
        UUID child = person(otherFamily, "ACTIVE");
        relation(otherFamily, "PARENT_OF", parent, child, "ACTIVE");

        assertThat(kinship(child, parent)).isEqualTo(KinshipCode.NONE_KNOWN);
    }

    private KinshipCode kinship(UUID from, UUID to) {
        return graphQuery.activeGraph(familyId).kinship(new PersonId(from), new PersonId(to), Gender.UNKNOWN).code();
    }

    private void partners(UUID a, UUID b) {
        boolean aFirst = a.toString().compareTo(b.toString()) < 0;
        relation(familyId, "PARTNER_OF", aFirst ? a : b, aFirst ? b : a, "ACTIVE");
    }

    private UUID person(UUID family, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO persons (id, family_id, first_name, status, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Someone', 'ACTIVE', ?, ?, ?, ?)
                """).params(id, family, userId, userId, now, now).update();
        if (status.equals("ARCHIVED")) {
            jdbc.sql("UPDATE persons SET status = 'ARCHIVED', archived_at = now() WHERE id = ?").param(id).update();
        } else if (status.equals("MERGED")) {
            UUID survivor = person(family, "ACTIVE");
            jdbc.sql("UPDATE persons SET status = 'MERGED', merged_into_person_id = ? WHERE id = ?")
                    .params(survivor, id).update();
        }
        return id;
    }

    private void relation(UUID family, String type, UUID source, UUID target, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO family_relationships (id, family_id, type, source_person_id, target_person_id, status,
                                                  created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(UUID.randomUUID(), family, type, source, target, status, userId, userId, now, now)
                .update();
    }
}
