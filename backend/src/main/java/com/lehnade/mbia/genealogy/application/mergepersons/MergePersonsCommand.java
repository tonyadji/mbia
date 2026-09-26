package com.lehnade.mbia.genealogy.application.mergepersons;

import java.util.UUID;

/**
 * Merge the duplicate {@code sourcePersonId} into {@code targetPersonId}, from the versions of both
 * Persons the ADMIN has seen.
 */
public record MergePersonsCommand(UUID familyId, UUID sourcePersonId, UUID targetPersonId, long sourceVersion,
        long targetVersion) {}
