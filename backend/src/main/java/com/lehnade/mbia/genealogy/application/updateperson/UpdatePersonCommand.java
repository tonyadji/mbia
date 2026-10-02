package com.lehnade.mbia.genealogy.application.updateperson;

import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import java.util.Optional;
import java.util.UUID;

/**
 * A change of a Person's identity and profile built from the version {@code expectedVersion} (the
 * request's {@code If-Match}). An empty field is left unchanged; a blank text clears an optional
 * name or the biography (OQ-008).
 *
 * @param profileMediaAssetId the uploaded photo that replaces the current one (OQ-040)
 * @param removeProfilePicture removes the current photo; not together with a new photo (OQ-040)
 */
public record UpdatePersonCommand(UUID familyId, UUID personId, long expectedVersion, Optional<String> firstName,
        Optional<String> middleNames, Optional<String> lastName, Optional<String> preferredName,
        Optional<Gender> gender, Optional<PartialDate> birth, Optional<Boolean> deceased,
        Optional<PartialDate> death, Optional<String> biography, Optional<UUID> profileMediaAssetId,
        boolean removeProfilePicture) {}
