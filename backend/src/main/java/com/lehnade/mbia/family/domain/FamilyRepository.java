package com.lehnade.mbia.family.domain;

import java.util.Optional;

public interface FamilyRepository {

    /** Inserts a new Family. */
    void insert(Family family);

    Optional<Family> findById(FamilyId id);

    /**
     * Persists the new state of an existing Family, provided its persisted version is still
     * {@link Family#version()}; returns it with its incremented version. Fails, and the API
     * answers {@code CONCURRENT_MODIFICATION}, when another transaction changed it meanwhile.
     */
    Family update(Family family);
}
