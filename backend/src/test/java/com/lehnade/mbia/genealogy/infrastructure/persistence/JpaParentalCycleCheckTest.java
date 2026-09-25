package com.lehnade.mbia.genealogy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * genealogy.md §7: {@code parent PARENT_OF child} closes a cycle when a path of ACTIVE
 * {@code PARENT_OF} relations of the same Family already leads from the child to the parent.
 */
class JpaParentalCycleCheckTest extends ApiTestSupport {

    @Autowired
    ParentalCycleCheck cycleCheck;

    private UUID familyId;
    private UUID userId;

    @BeforeEach
    void givenAFamily() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        userId = families().userId(admin);
    }

    @Test
    void aDescendantAtAnyDepthCannotBecomeAnAncestor() {
        List<UUID> line = new ArrayList<>();
        for (int generation = 0; generation < 30; generation++) {
            line.add(person(familyId));
            if (generation > 0) {
                relation(familyId, "PARENT_OF", line.get(generation - 1), line.get(generation), "ACTIVE");
            }
        }

        assertThat(wouldCreateCycle(line.getLast(), line.getFirst())).isTrue();
        assertThat(wouldCreateCycle(line.get(1), line.get(0))).isTrue();
        assertThat(wouldCreateCycle(line.getFirst(), line.getLast())).isFalse();
    }

    @Test
    void aDiamondIsNotACycle() {
        UUID grandparent = person(familyId);
        UUID father = person(familyId);
        UUID mother = person(familyId);
        UUID child = person(familyId);
        relation(familyId, "PARENT_OF", grandparent, father, "ACTIVE");
        relation(familyId, "PARENT_OF", grandparent, mother, "ACTIVE");
        relation(familyId, "PARENT_OF", father, child, "ACTIVE");

        assertThat(wouldCreateCycle(mother, child)).isFalse();
        assertThat(wouldCreateCycle(child, grandparent)).isTrue();
    }

    @Test
    void onlyActiveParentRelationsOfTheFamilyAreFollowed() {
        UUID a = person(familyId);
        UUID b = person(familyId);
        UUID c = person(familyId);
        relation(familyId, "PARTNER_OF", a.toString().compareTo(b.toString()) < 0 ? a : b,
                a.toString().compareTo(b.toString()) < 0 ? b : a, "ACTIVE");
        relation(familyId, "PARENT_OF", b, c, "ARCHIVED");

        assertThat(wouldCreateCycle(b, a)).isFalse();
        assertThat(wouldCreateCycle(c, b)).isFalse();

        TestJwts.Token other = TestJwts.newUserToken();
        UUID otherFamily = families().createFamily(other, "Autre famille");
        UUID x = person(otherFamily);
        UUID y = person(otherFamily);
        relation(otherFamily, "PARENT_OF", x, y, "ACTIVE");
        assertThat(cycleCheck.wouldCreateCycle(familyId, new PersonId(y), new PersonId(x))).isFalse();
    }

    private boolean wouldCreateCycle(UUID parent, UUID child) {
        return cycleCheck.wouldCreateCycle(familyId, new PersonId(parent), new PersonId(child));
    }

    private UUID person(UUID family) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO persons (id, family_id, first_name, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Someone', ?, ?, ?, ?)
                """).params(id, family, userId, userId, now, now).update();
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
