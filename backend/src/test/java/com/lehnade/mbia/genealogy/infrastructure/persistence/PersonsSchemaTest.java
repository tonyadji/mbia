package com.lehnade.mbia.genealogy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/** V003: the database is the last guard of data-model.md §9–10 invariants. */
class PersonsSchemaTest extends ApiTestSupport {

    private UUID familyId;
    private UUID userId;

    @BeforeEach
    void givenAFamily() {
        TestJwts.Token admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        userId = families().userId(admin);
    }

    @Test
    void aUserRepresentsAtMostOneNonMergedPersonPerFamily() {
        insert("linked_user_id", userId);

        assertThatThrownBy(() -> insert("linked_user_id", userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_person_linked_user_per_family");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "first_name = '  '",
            "gender = 'ALIEN'",
            "status = 'DELETED'",
            "birth_date_precision = 'EXACT'",
            "birth_date_precision = 'YEAR_ONLY', birth_date = DATE '1954-03-12'",
            "birth_year = 1954",
            "birth_date_precision = 'EXACT', birth_date = DATE '1954-03-12', birth_year = 1954",
            "death_date_precision = 'YEAR_ONLY', death_year = 2020",
            "is_deceased = TRUE, death_date_precision = 'EXACT'",
            "status = 'MERGED'",
            "merged_into_person_id = id"})
    void invariantsAreEnforced(String assignments) {
        UUID id = insert(null, null);

        assertThatThrownBy(() -> jdbc.sql("UPDATE persons SET " + assignments + " WHERE id = ?").param(id).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consistentPartialDatesAreAccepted() {
        UUID id = insert(null, null);

        assertThatCode(() -> jdbc.sql("""
                UPDATE persons SET birth_date_precision = 'YEAR_ONLY', birth_year = 1954,
                                   is_deceased = TRUE, death_date_precision = 'EXACT', death_date = DATE '2020-01-01'
                WHERE id = ?
                """).param(id).update()).doesNotThrowAnyException();
    }

    private UUID insert(String extraColumn, Object extraValue) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        String column = extraColumn == null ? "" : ", " + extraColumn;
        String value = extraColumn == null ? "" : ", ?";
        var statement = jdbc.sql("INSERT INTO persons (id, family_id, first_name, created_by, updated_by, created_at,"
                + " updated_at" + column + ") VALUES (?, ?, 'Marie', ?, ?, ?, ?" + value + ")")
                .params(id, familyId, userId, userId, now, now);
        if (extraColumn != null) {
            statement = statement.param(extraValue);
        }
        statement.update();
        return id;
    }
}
