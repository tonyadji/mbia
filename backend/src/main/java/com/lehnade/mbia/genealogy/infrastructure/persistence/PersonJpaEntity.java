package com.lehnade.mbia.genealogy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Row of {@code persons} (data-model.md §10). */
@Entity
@Table(name = "persons")
class PersonJpaEntity {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "middle_names")
    private String middleNames;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "preferred_name")
    private String preferredName;

    @Column(nullable = false)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_year")
    private Short birthYear;

    @Column(name = "birth_date_precision", nullable = false)
    private String birthDatePrecision;

    @Column(name = "is_deceased", nullable = false)
    private boolean deceased;

    @Column(name = "death_date")
    private LocalDate deathDate;

    @Column(name = "death_year")
    private Short deathYear;

    @Column(name = "death_date_precision", nullable = false)
    private String deathDatePrecision;

    @Column(columnDefinition = "text")
    private String biography;

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    @Column(nullable = false)
    private String status;

    @Column(name = "merged_into_person_id")
    private UUID mergedIntoPersonId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** {@code null} until inserted, so that Spring Data persists a new row instead of merging it. */
    @Version
    private Long version;

    protected PersonJpaEntity() {}

    PersonJpaEntity(UUID id, UUID familyId, String firstName, String middleNames, String lastName,
            String preferredName, String gender, LocalDate birthDate, Short birthYear, String birthDatePrecision,
            boolean deceased, LocalDate deathDate, Short deathYear, String deathDatePrecision, String biography,
            UUID linkedUserId, String status, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.firstName = firstName;
        this.middleNames = middleNames;
        this.lastName = lastName;
        this.preferredName = preferredName;
        this.gender = gender;
        this.birthDate = birthDate;
        this.birthYear = birthYear;
        this.birthDatePrecision = birthDatePrecision;
        this.deceased = deceased;
        this.deathDate = deathDate;
        this.deathYear = deathYear;
        this.deathDatePrecision = deathDatePrecision;
        this.biography = biography;
        this.linkedUserId = linkedUserId;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
