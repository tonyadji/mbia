package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which uploaded images are the photo of a Person (data-model.md §10, §13). The media cleanup
 * calls it so that it never touches an attached asset (OQ-036); genealogy never depends on the
 * memory module.
 */
@Service
public class ProfilePictures {

    private final PersonRepository persons;

    public ProfilePictures(PersonRepository persons) {
        this.persons = persons;
    }

    /**
     * The caller holds a lock on the assets; attaching an asset locks it too (PR-37), so the answer
     * stays true until the end of the caller's transaction.
     *
     * @return among these media assets, those that are the photo of a Person, whatever its status
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Set<UUID> inUse(Collection<UUID> mediaAssetIds) {
        return persons.findProfilePicturesAmong(mediaAssetIds);
    }
}
