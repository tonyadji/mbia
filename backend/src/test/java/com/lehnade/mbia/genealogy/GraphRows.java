package com.lehnade.mbia.genealogy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Persons and relationships of one Family written straight to the database, for read-model tests
 * that need exact creation times, statuses or large Families without going through the API.
 */
public final class GraphRows {

    private final JdbcClient jdbc;
    private final UUID familyId;
    private final UUID userId;
    private Instant clock = Instant.parse("2026-01-01T00:00:00Z");

    public GraphRows(JdbcClient jdbc, UUID familyId, UUID userId) {
        this.jdbc = jdbc;
        this.familyId = familyId;
        this.userId = userId;
    }

    /** An ACTIVE Person without dates, created one second after the previous row. */
    public UUID person(String firstName) {
        return person(UUID.randomUUID(), firstName, null, null);
    }

    /** An ACTIVE Person born on {@code birthDate} (EXACT) or in {@code birthYear} (YEAR_ONLY). */
    public UUID person(UUID id, String firstName, LocalDate birthDate, Integer birthYear) {
        String precision = birthDate != null ? "EXACT" : birthYear != null ? "YEAR_ONLY" : "UNKNOWN";
        Timestamp createdAt = tick();
        jdbc.sql("""
                INSERT INTO persons (id, family_id, first_name, birth_date, birth_year, birth_date_precision, status,
                                     created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """).params(id, familyId, firstName, birthDate, birthYear, precision, userId, userId, createdAt,
                createdAt).update();
        return id;
    }

    /** An ACTIVE Person without dates with these names, created one second after the previous row. */
    public UUID person(String firstName, String lastName, String preferredName) {
        UUID id = person(firstName);
        jdbc.sql("UPDATE persons SET last_name = ?, preferred_name = ? WHERE id = ?")
                .params(lastName, preferredName, id).update();
        return id;
    }

    public void archivePerson(UUID personId) {
        jdbc.sql("UPDATE persons SET status = 'ARCHIVED', archived_at = now() WHERE id = ?").param(personId).update();
    }

    public void mergePerson(UUID personId, UUID into) {
        jdbc.sql("UPDATE persons SET status = 'MERGED', merged_into_person_id = ? WHERE id = ?")
                .params(into, personId).update();
    }

    public void link(UUID personId, UUID user) {
        jdbc.sql("UPDATE persons SET linked_user_id = ? WHERE id = ?").params(user, personId).update();
    }

    public void createdAt(UUID personId, Instant createdAt) {
        jdbc.sql("UPDATE persons SET created_at = ? WHERE id = ?").params(Timestamp.from(createdAt), personId)
                .update();
    }

    public UUID parentOf(UUID parent, UUID child) {
        return relation("PARENT_OF", parent, child, "ACTIVE");
    }

    /** {@code PARTNER_OF} stored once, endpoints in UUID order (data-model.md §11.2). */
    public UUID partners(UUID a, UUID b) {
        boolean aFirst = a.toString().compareTo(b.toString()) < 0;
        return relation("PARTNER_OF", aFirst ? a : b, aFirst ? b : a, "ACTIVE");
    }

    public UUID relation(String type, UUID source, UUID target, String status) {
        UUID id = UUID.randomUUID();
        Timestamp createdAt = tick();
        jdbc.sql("""
                INSERT INTO family_relationships (id, family_id, type, source_person_id, target_person_id, status,
                                                  created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(id, familyId, type, source, target, status, userId, userId, createdAt, createdAt)
                .update();
        return id;
    }

    private Timestamp tick() {
        clock = clock.plusSeconds(1);
        return Timestamp.from(clock);
    }
}
