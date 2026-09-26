package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which uploaded images are the photo of a Person, and which may become one (data-model.md §10,
 * §13, OQ-036, OQ-040). The media cleanup calls it so that it never touches an attached asset;
 * genealogy never depends on the memory module, which provides the media through
 * {@link ProfilePictureMediaPort}.
 */
@Service
public class ProfilePictures {

    private final PersonRepository persons;
    private final ProfilePictureMediaPort media;

    public ProfilePictures(PersonRepository persons, ProfilePictureMediaPort media) {
        this.persons = persons;
        this.media = media;
    }

    /**
     * The caller holds a lock on the assets; attaching an asset locks it too, so the answer stays
     * true until the end of the caller's transaction.
     *
     * @return among these media assets, those that are the photo of a Person, whatever its status
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Set<UUID> inUse(Collection<UUID> mediaAssetIds) {
        return persons.findProfilePicturesAmong(mediaAssetIds);
    }

    /**
     * Checks that the caller may make this asset a Person's photo, and locks it until the end of
     * the caller's transaction: a READY {@code PROFILE_PICTURE} of the Family, uploaded by the
     * caller and the photo of no Person, whatever its status (OQ-036).
     *
     * @throws DomainException {@code MEDIA_NOT_FOUND}, {@code PERMISSION_DENIED},
     *     {@code MEDIA_NOT_READY} (see {@link ProfilePictureMediaPort#lockAttachable}), then
     *     {@code MEDIA_ALREADY_USED}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAttachable(UUID familyId, UUID mediaAssetId, UUID callerId) {
        media.lockAttachable(familyId, mediaAssetId, callerId);
        if (!persons.findProfilePicturesAmong(Set.of(mediaAssetId)).isEmpty()) {
            throw new DomainException(ErrorCode.MEDIA_ALREADY_USED, "This photo is already used.");
        }
    }

    /** A replaced or removed photo becomes ARCHIVED and serves no URL any more (OQ-040, OQ-047). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void archive(UUID familyId, UUID mediaAssetId, Instant now) {
        media.archive(familyId, mediaAssetId, now);
    }
}
