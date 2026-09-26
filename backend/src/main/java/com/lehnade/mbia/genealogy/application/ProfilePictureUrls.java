package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Person;
import java.net.URI;
import org.springframework.stereotype.Service;

/**
 * The {@code profilePictureUrl} of a Person (openapi {@code PersonSummary}): the pre-signed URL of
 * its photo's thumbnail, signed from the Person alone, so that a list of Persons costs no extra
 * query (data-model.md §13).
 */
@Service
public class ProfilePictureUrls {

    private final ProfilePictureMediaPort media;

    public ProfilePictureUrls(ProfilePictureMediaPort media) {
        this.media = media;
    }

    /** @return the URL of the Person's photo, or {@code null} when it has none */
    public URI of(Person person) {
        return person.profileMediaAssetId().map(asset -> media.thumbnailUrl(person.familyId(), asset)).orElse(null);
    }
}
